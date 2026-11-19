# FleetPulse

Live monitoring of a car-rental fleet, built on Kafka and Spring (WebFlux). Each car reports its position and
speed; the rental company's rules are applied to every reading, and the owner is alerted in real time when a
car breaks them.

**The rules:** a car must not exceed **110 km/h** and must not leave **Tunisia**.

## What it does

1. A car sends a telemetry reading over HTTP. Here the cars are fake: a simulator drives 10 cars between
   Tunisian cities and posts a reading per car every second (any HTTP client works too).
2. The reading is processed exactly once, even if Kafka delivers it twice.
3. The rules are checked against the car's previous state, and an alert is raised only when something
   **changes**: a car starts speeding (`SPEEDING`, warning), leaves Tunisia (`LEFT_TUNISIA`, critical) or
   comes back (`RETURNED_TO_TUNISIA`, info). A car that speeds for a minute raises one alert, not sixty.
4. The owner's dashboard shows every car on a map with its status, and receives each alert instantly (a
   pop-up and an optional beep for critical ones), with no refresh and no polling.
5. A reading that cannot be processed is retried, then parked in a dead-letter topic instead of blocking the pipeline.

## Flow

```
                     telemetry                         alerts, vehicle-updates
simulator ─HTTP─> ingestion-service ──> [Kafka] ──> processing-service ──> [Kafka] ──> live-service ──> dashboard
 POST /telemetry     (port 8081)      topic         (port 8082)           topics       (port 8083)      (Angular, port 4200)
                                                                                      GET /vehicles, /alerts, /stream (SSE)
                                                       │   ▲
                                          event_id     │   │ duplicate? skip
                                                       ▼   │
                                                    PostgreSQL
                                                       │
                                       fails 3 times   ▼
                                                  telemetry.DLT
```

| Service | Role |
|---|---|
| **simulator-service** | Drives 10 cars (`V-01` to `V-10`) along roads between Tunisian cities and posts their readings to ingestion every second. Most cars are calm, `V-05` and `V-10` drive aggressively and often pass 110 km/h, `V-03` and `V-08` regularly drive over the Algerian or Libyan border and come back. Has no HTTP server. |
| **ingestion-service** | Receives `POST /telemetry`, assigns an `eventId` if missing, publishes to the `telemetry` topic, answers `202 Accepted` immediately. Also declares the Kafka topics at startup. |
| **processing-service** | Consumes `telemetry`, skips duplicates, applies the speed and geofence rules against each car's previous state, publishes alerts to `alerts` and the car's new state to `vehicle-updates`. |
| **live-service** | The API for the dashboard. Consumes `alerts` and `vehicle-updates`, keeps the latest state of every car and the last 100 alerts in memory, and serves them over HTTP: `GET /vehicles` (all cars now), `GET /alerts` (recent alerts) and `GET /stream` (Server-Sent Events). Marks a car `OFFLINE` after 30 s of silence. Serves a small test page at `/`. |
| **dashboard** | The owner's web app (Angular, served by nginx on port 4200). Map of the fleet, status cards, live alert feed, vehicle table and a detail panel. nginx forwards `/api/*` to live-service. |

| Topic | Purpose |
|---|---|
| `telemetry` | Raw readings, 3 partitions |
| `alerts` | Generated alerts, 3 partitions |
| `vehicle-updates` | Every car's position, speed and rule flags after each reading, 3 partitions |
| `telemetry.DLT` | Dead letters: readings that failed all retries, 3 partitions |

## Technical choices

### The vehicle ID is the Kafka key
All readings of one vehicle go to the same partition, so they are processed in order. Different vehicles are
spread over the 3 partitions and processed in parallel.

### At-least-once delivery with manual acknowledgment
The consumer commits an offset only after the work succeeded (`ack.acknowledge()`). If the service crashes
mid-processing, Kafka redelivers the record. Nothing is lost, but a record can arrive twice, which is why
the next point exists.

### Idempotency guard in PostgreSQL
Before processing, the consumer inserts the `eventId` into `processed_events`. The primary key makes a
duplicate insert fail, and that failure means "already processed, skip". It is atomic and survives restarts.

- **Why a database and not an in-memory set:** an in-memory set is lost on restart, so duplicates would be
  processed again.
- **Why PostgreSQL and not Redis:** a unique constraint gives the guarantee with no extra logic. Redis would
  be faster and is a valid choice at very high throughput, but adds another piece of infrastructure.
- **Why `ProcessedEvent` implements `Persistable`:** Spring Data's `save()` does an UPSERT (`merge`) when the
  entity already has its ID set, so a duplicate would never fail. Forcing `isNew()` to return `true` makes
  `save()` always INSERT, so the constraint fires.

### Rules: alert on change of state, not on every reading
`processing-service` keeps one row per car in `vehicle_state` (is it speeding, is it outside Tunisia) and
compares each new reading with it. Only a difference raises an alert. The state lives in PostgreSQL and not
in memory, so a restart or a partition rebalance does not repeat alerts.

- **Hysteresis on speed:** speeding starts above 110 km/h but only ends below 105 (`fleet.rules` in
  `application.yml`). Without the gap, a speed hovering around 110 would raise a new alert on every crossing.
- **Geofence:** `GeofenceService` checks whether the position is inside the Tunisia polygon in
  `tunisia.geojson`, using ray casting (a ray from the point crosses the border an odd number of times if the
  point is inside). The polygon is a hand-simplified outline, accurate to roughly 10-20 km and padded slightly
  into the sea so coastal cities stay inside. Swap the file for a precise GeoJSON polygon if needed.
- **One transaction per reading:** the idempotency record and the state change commit together, so a crash
  cannot mark a reading as processed without its state change. One car's readings are handled in order by
  one thread (the vehicle ID is the Kafka key), so two readings never update the same state row at once.
- **Known limit:** the alert is published to Kafka after the transaction commits, and the two are not atomic
  (the dual-write problem). A crash in between could lose one alert. The usual fix is the outbox pattern,
  which is not implemented here.

### Retries and dead-letter topic
A `DefaultErrorHandler` retries a failing record 2 more times, 1 second apart (3 attempts in total). After
that a `DeadLetterPublishingRecoverer` publishes it to `telemetry.DLT` and the consumer moves on. Without
this, one bad record ("poison pill") blocks its partition forever. The failure reason is stored in the
`kafka_dlt-exception-message` header and logged by `DeadLetterListener`.

Because the custom listener container factory replaces Spring Boot's default one, the `ack-mode: manual`
property in `application.yml` no longer applies to it, so the mode is set in code in `KafkaConfig`.

### WebFlux and Server-Sent Events for the live view
A live dashboard keeps one HTTP connection open per browser. With a thread-per-request model (Spring MVC)
each idle connection would hold a thread. WebFlux runs on a few Netty event-loop threads, so many open
streams are cheap.

SSE is used instead of WebSocket because the data only flows server to browser; it is plain HTTP and the
browser reconnects on its own.

The Kafka listener is a normal blocking callback, so it is bridged to the reactive side with a
`Sinks.Many` (`AlertStream`): the listener emits each alert into the sink, and every connected browser is a
subscriber of its `Flux`. The sink does not buffer, so alerts sent while nobody is connected are dropped,
which is the intended behaviour for a live view. `live-service` uses its own consumer groups (`live` and
`live-vehicles`), so it receives every message independently of the other services.

Because the sink forgets, a dashboard that opens late would be empty. So it first loads `GET /vehicles` and
`GET /alerts` (state kept in memory by `VehicleTracker` and `AlertStream`, rebuilt within seconds after a
restart because the cars keep reporting), then applies the updates from `GET /stream` on top. That stream
carries both kinds of event on one connection, told apart by the SSE event name (`vehicle` or `alert`), and
a keep-alive comment every 15 s so proxies do not close it. A car that goes silent sends nothing, so a
scheduled check announces it as `OFFLINE` once after 30 s.

`processing-service` publishes `vehicle-updates` so that `live-service` needs no knowledge of the rules: the
flags (speeding, outside Tunisia) are computed once, where the state lives.

### JSON serialization across services
Each service has its own copy of the event classes. The producer does not write the class name into a Kafka
header (`spring.json.add.type.headers: false`) and consumers ignore it
(`spring.json.use.type.headers: false`), so each side deserializes into its own class. Consumers also
restrict which packages may be deserialized (`spring.json.trusted.packages`).

### Producer settings
`acks: all` waits for all in-sync replicas before confirming a write. The ingestion producer also uses short
timeouts, so an unreachable broker fails fast instead of hanging a request.

### The dashboard (Angular)
Angular 22 with standalone components, signals and no zone.js, plain CSS and Leaflet for the map. It follows
the same split as the backend: one `FleetStore` is the only code that talks to the API, and the components
only read its signals.

- **Snapshot, then stream:** on load it fetches `GET /vehicles` and `GET /alerts`, then applies the events
  of `GET /stream` on top. After a connection drop it reloads the snapshot to fill the gap.
- **Batching:** about ten `vehicle` events arrive per second. They are buffered and applied twice a second,
  so the page re-renders at that pace however fast the stream is. A snapshot never overwrites a newer
  stream update (compared on `lastSeen`).
- **Same-origin API:** the browser only talks to nginx, which forwards `/api/*` to live-service with the
  prefix removed. That needs no CORS setup, and the nginx location turns buffering off, because buffering
  would hold the SSE stream back. In development, `ng serve` does the same through `proxy.conf.json`.
- **Geofence on the map:** the dashboard draws `tunisia.geojson`, a copy of the polygon the processing
  service checks positions against, so the owner sees the allowed area.
- **Details worth knowing:** the table is sorted by ID, not by status, because rows that move when a status
  changes make the row you are about to click jump away. Vehicle markers are built with DOM calls and not
  HTML strings, so an ID can never inject markup. Map tiles come from OpenStreetMap.

### Docker images
The four Java services are built by the Jib Maven plugin straight from each project: no Dockerfiles, and it
splits dependencies and application code into separate layers, so rebuilds after a code change are fast.
Jib only builds Java, so the dashboard has a multi-stage Dockerfile (Node builds the app, nginx serves the
result), which also means Node is not needed on the machine. The only cost is a build step before
`docker-compose up` (the `build-images` scripts).

### Two Kafka listeners
A Kafka client is told which address to reconnect to, and `localhost:9092` only means the right thing from
your machine. Inside the compose network `localhost` is the client's own container. The broker therefore
exposes two listeners: `localhost:9092` for apps running on your machine and `kafka:29092` for the
containers. Containers also get `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATASOURCE_URL` and
`SIMULATOR_INGESTION_URL` as environment variables, which override the `localhost` values in each
`application.yml`. Startup order is enforced with health checks (Kafka and PostgreSQL) and `depends_on`.

## Running locally

Requirements: Java 17 and Docker.

### Everything in Docker (two commands)

```bash
# 1. Build the four service images with Jib and the dashboard image
./build-images.sh          # Windows PowerShell: .\build-images.ps1

# 2. Start Kafka, PostgreSQL, the four services and the dashboard
docker-compose up -d
```

Open **`http://localhost:4200/`**, the dashboard. The simulator is already sending readings, so cars move and
alerts appear on their own. Stop everything with `docker-compose down`.

### Development: services on your machine

Run only the infrastructure in Docker and start the services from your IDE or terminal, each in its own
terminal. Start ingestion first, because it creates the topics.

```bash
docker-compose up -d kafka postgres

cd ingestion-service  && ./mvnw spring-boot:run
cd processing-service && ./mvnw spring-boot:run
cd live-service       && ./mvnw spring-boot:run
cd simulator-service  && ./mvnw spring-boot:run   # optional: or post readings by hand, see below
cd dashboard          && npm install && npm start # needs Node 22.12+; http://localhost:4200, /api proxied to 8083
```

| Component | Port |
|---|---|
| Kafka | 9092 (from your machine), 29092 (inside the compose network) |
| PostgreSQL | 5433 (mapped from 5432 to avoid a clash with a local installation) |
| ingestion-service | 8081 |
| processing-service | 8082 (no endpoints) |
| live-service | 8083 |
| dashboard | 4200 (nginx in Docker, `ng serve` in development) |
| simulator-service | none |

### Try it by hand

With the simulator stopped, send a reading yourself:

1. Open the dashboard at `http://localhost:4200/` and wait for the `Live` badge.
2. Send a speeding reading (`V-90` appears on the map):

```bash
# Tunis, 130 km/h
curl -X POST http://localhost:8081/telemetry -H "Content-Type: application/json" \
  -d '{"vehicleId":"V-90","lat":36.80,"lon":10.18,"speedKmh":130.5,"timestamp":"2026-11-16T15:00:00Z"}'
```

A `SPEEDING` alert appears on the page. Send the same reading again: no second alert, because the car is
already speeding. Then send one at 60 km/h from Tebessa, Algeria (`"lat":35.40,"lon":8.12`) for a
`LEFT_TUNISIA` alert, and one back in Tunis for `RETURNED_TO_TUNISIA`.

Useful commands:

```bash
# Watch dead letters, with the failure reason in the headers
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic telemetry.DLT --from-beginning --property print.headers=true

# Watch the stream without a browser
curl -N http://localhost:8083/stream

# Current state of every car, and the latest alerts
curl http://localhost:8083/vehicles
curl http://localhost:8083/alerts
```

## Event shapes

Telemetry (`POST /telemetry`):

```json
{ "vehicleId": "V-07", "lat": 36.80, "lon": 10.18, "speedKmh": 130.5, "timestamp": "2026-11-16T15:00:00Z" }
```

Alert (`alerts` topic and SSE stream). `alertType` is `SPEEDING`, `LEFT_TUNISIA` or `RETURNED_TO_TUNISIA`;
`severity` is `INFO`, `WARNING` or `CRITICAL`:

```json
{ "alertId": "…", "vehicleId": "V-07", "alertType": "SPEEDING", "severity": "WARNING",
  "message": "Speeding at 130.5 km/h (limit 110)", "speedKmh": 130.5, "lat": 36.80, "lon": 10.18,
  "timestamp": "2026-11-16T15:00:00Z" }
```

## Project layout

```
FleetPulse/
├── docker-compose.yml        Kafka (KRaft), PostgreSQL and the four services
├── build-images.sh / .ps1    Builds the Docker images: Jib for the Java services, a Dockerfile for the dashboard
├── simulator-service/        Fake cars driving between Tunisian cities, posting readings on a schedule
├── ingestion-service/        HTTP in, Kafka out, topic declarations
├── processing-service/       Idempotency, speed + geofence rules, vehicle state, retries + DLT
├── live-service/             Kafka in, vehicle state + alerts out over HTTP and SSE, test page
└── dashboard/                Angular app (map, KPIs, alert feed, vehicle table) served by nginx
```
