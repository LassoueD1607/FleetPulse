# FleetPulse

Live fleet monitoring built on Kafka and Spring (WebFlux). Vehicles send telemetry (position and speed);
the system processes it, detects speeding, and pushes alerts to a browser in real time.

## What it does

1. A vehicle sends a telemetry reading over HTTP. Here the vehicles are fake: a simulator moves 10 cars and
   posts a reading per car every second (any HTTP client works too).
2. The reading is processed exactly once, even if Kafka delivers it twice.
3. If the vehicle is faster than **90 km/h**, a `SPEEDING` alert is created.
4. Every open browser dashboard receives that alert instantly, with no refresh and no polling.
5. A reading that cannot be processed is retried, then parked in a dead-letter topic instead of blocking the pipeline.

## Flow

```
                     telemetry                            alerts
simulator ─HTTP─> ingestion-service ──> [Kafka] ──> processing-service ──> [Kafka] ──> live-service ──SSE──> browser
 POST /telemetry     (port 8081)      topic         (port 8082)           topic        (port 8083)        GET /alerts/stream
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
| **simulator-service** | Plays 10 vehicles (`V-01` to `V-10`) that drift in speed and position around Tunis, with occasional bursts above the limit, and posts their readings to ingestion every second. Has no HTTP server. |
| **ingestion-service** | Receives `POST /telemetry`, assigns an `eventId` if missing, publishes to the `telemetry` topic, answers `202 Accepted` immediately. Also declares the Kafka topics at startup. |
| **processing-service** | Consumes `telemetry`, skips duplicates, applies the speeding rule, publishes alerts to `alerts`. |
| **live-service** | Consumes `alerts` and streams them to browsers over Server-Sent Events. Serves a small test page at `/`. |

| Topic | Purpose |
|---|---|
| `telemetry` | Raw readings, 3 partitions |
| `alerts` | Generated alerts, 3 partitions |
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
which is the intended behaviour for a live view. `live-service` uses its own consumer group (`live`), so it
receives every alert independently of the other services.

### JSON serialization across services
Each service has its own copy of the event classes. The producer does not write the class name into a Kafka
header (`spring.json.add.type.headers: false`) and consumers ignore it
(`spring.json.use.type.headers: false`), so each side deserializes into its own class. Consumers also
restrict which packages may be deserialized (`spring.json.trusted.packages`).

### Producer settings
`acks: all` waits for all in-sync replicas before confirming a write. The ingestion producer also uses short
timeouts, so an unreachable broker fails fast instead of hanging a request.

### Docker images with Jib
The service images are built by the Jib Maven plugin straight from each project: no Dockerfiles, and it
splits dependencies and application code into separate layers, so rebuilds after a code change are fast.
The only cost is a build step before `docker-compose up` (the `build-images` scripts).

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
# 1. Build the four service images with Jib
./build-images.sh          # Windows PowerShell: .\build-images.ps1

# 2. Start Kafka, PostgreSQL and the four services
docker-compose up -d
```

Open `http://localhost:8083/`. The simulator is already sending readings, so alerts appear on their own.
Stop everything with `docker-compose down`.

### Development: services on your machine

Run only the infrastructure in Docker and start the services from your IDE or terminal, each in its own
terminal. Start ingestion first, because it creates the topics.

```bash
docker-compose up -d kafka postgres

cd ingestion-service  && ./mvnw spring-boot:run
cd processing-service && ./mvnw spring-boot:run
cd live-service       && ./mvnw spring-boot:run
cd simulator-service  && ./mvnw spring-boot:run   # optional: or post readings by hand, see below
```

| Component | Port |
|---|---|
| Kafka | 9092 (from your machine), 29092 (inside the compose network) |
| PostgreSQL | 5433 (mapped from 5432 to avoid a clash with a local installation) |
| ingestion-service | 8081 |
| processing-service | 8082 (no endpoints) |
| live-service | 8083 |
| simulator-service | none |

### Try it by hand

With the simulator stopped, send a reading yourself:

1. Open `http://localhost:8083/` and wait for `connected`.
2. Send a speeding reading:

```bash
curl -X POST http://localhost:8081/telemetry -H "Content-Type: application/json" \
  -d '{"vehicleId":"V-07","lat":31.6,"lon":22.1,"speedKmh":130.5,"timestamp":"2026-11-16T15:00:00Z"}'
```

The alert appears on the page. A reading at or below 90 km/h produces no alert.

Useful commands:

```bash
# Watch dead letters, with the failure reason in the headers
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic telemetry.DLT --from-beginning --property print.headers=true

# Watch the stream without a browser
curl -N http://localhost:8083/alerts/stream
```

## Event shapes

Telemetry (`POST /telemetry`):

```json
{ "vehicleId": "V-07", "lat": 31.6, "lon": 22.1, "speedKmh": 130.5, "timestamp": "2026-11-16T15:00:00Z" }
```

Alert (`alerts` topic and SSE stream):

```json
{ "eventId": "…", "vehicleId": "V-07", "speedKmh": 130.5, "alertType": "SPEEDING", "timestamp": "2026-11-16T15:00:00Z" }
```

## Project layout

```
FleetPulse/
├── docker-compose.yml        Kafka (KRaft), PostgreSQL and the four services
├── build-images.sh / .ps1    Builds the four Docker images with Jib
├── simulator-service/        Fake vehicles posting readings on a schedule
├── ingestion-service/        HTTP in, Kafka out, topic declarations
├── processing-service/       Idempotency, speeding rule, retries + DLT
└── live-service/             Kafka in, SSE out, test page
```
