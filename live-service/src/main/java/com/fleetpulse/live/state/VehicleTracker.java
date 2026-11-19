package com.fleetpulse.live.state;

import com.fleetpulse.live.event.VehicleUpdate;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class VehicleTracker {

    private static final Duration OFFLINE_AFTER = Duration.ofSeconds(30);

    private record Seen(VehicleUpdate update, Instant receivedAt) {}

    // Latest state per vehicle, kept in memory: it rebuilds within seconds after a restart because
    // every vehicle keeps reporting. receivedAt is this service's clock, so a vehicle with a wrong
    // clock cannot look online or offline by mistake.
    private final Map<String, Seen> latest = new ConcurrentHashMap<>();
    private final Set<String> offlineAnnounced = ConcurrentHashMap.newKeySet();
    private final Sinks.Many<VehicleStatus> sink = Sinks.many().multicast().directBestEffort();

    // The shared consumer setting points at AlertEvent, so this listener names its own target type.
    @KafkaListener(topics = "vehicle-updates", groupId = "live-vehicles",
            properties = "spring.json.value.default.type=com.fleetpulse.live.event.VehicleUpdate")
    public void onUpdate(VehicleUpdate update) {
        Seen seen = new Seen(update, Instant.now());
        latest.put(update.vehicleId(), seen);
        offlineAnnounced.remove(update.vehicleId());
        emit(toStatus(seen));
    }

    // A vehicle that goes silent sends nothing, so nothing would tell the dashboard. Check periodically
    // and announce each silent vehicle once as OFFLINE.
    @Scheduled(fixedRate = 5000)
    void announceOfflineVehicles() {
        for (Seen seen : latest.values()) {
            if (isOffline(seen) && offlineAnnounced.add(seen.update().vehicleId())) {
                emit(toStatus(seen));
            }
        }
    }

    public List<VehicleStatus> snapshot() {
        return latest.values().stream()
                .map(this::toStatus)
                .sorted(Comparator.comparing(VehicleStatus::vehicleId))
                .toList();
    }

    public Flux<VehicleStatus> updates() {
        return sink.asFlux();
    }

    // The Kafka thread and the scheduler can emit at the same time, and a sink only accepts
    // one emission at a time.
    private synchronized void emit(VehicleStatus status) {
        sink.tryEmitNext(status);
    }

    private boolean isOffline(Seen seen) {
        return Duration.between(seen.receivedAt(), Instant.now()).compareTo(OFFLINE_AFTER) > 0;
    }

    private VehicleStatus toStatus(Seen seen) {
        VehicleUpdate u = seen.update();
        String status;
        if (isOffline(seen)) {
            status = "OFFLINE";
        } else if (u.outsideTunisia()) {
            status = "OUTSIDE_TUNISIA";
        } else if (u.speeding()) {
            status = "SPEEDING";
        } else {
            status = "OK";
        }
        return new VehicleStatus(u.vehicleId(), u.lat(), u.lon(), u.speedKmh(), status, seen.receivedAt());
    }
}
