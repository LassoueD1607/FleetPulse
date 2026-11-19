package com.fleetpulse.simulator;

import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TelemetrySender {

    private static final Logger log = LoggerFactory.getLogger(TelemetrySender.class);

    private final List<Vehicle> vehicles;
    private final WebClient client;
    private final double simulatedSecondsPerTick;

    public TelemetrySender(@Value("${simulator.ingestion-url}") String ingestionUrl,
                           @Value("${simulator.vehicle-count}") int vehicleCount,
                           @Value("${simulator.interval-ms}") long intervalMs,
                           @Value("${simulator.time-scale}") double timeScale) {
        this.client = WebClient.create(ingestionUrl);
        this.simulatedSecondsPerTick = intervalMs / 1000.0 * timeScale;
        this.vehicles = IntStream.rangeClosed(1, vehicleCount)
                .mapToObj(i -> new Vehicle(String.format("V-%02d", i), personalityFor(i)))
                .toList();
    }

    // With the default 10 vehicles: V-05 and V-10 drive aggressively, V-03 and V-08 cross the border.
    private static Personality personalityFor(int index) {
        return switch (index % 5) {
            case 0 -> Personality.AGGRESSIVE;
            case 3 -> Personality.ROGUE;
            default -> Personality.CALM;
        };
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms}")
    public void tick() {
        for (Vehicle vehicle : vehicles) {
            TelemetryReading reading = vehicle.move(simulatedSecondsPerTick);
            // subscribe() fires the request without waiting, so one slow call never delays the other vehicles.
            client.post()
                    .uri("/telemetry")
                    .bodyValue(reading)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(
                            response -> { },
                            error -> log.warn("Failed to send reading for {}: {}", reading.vehicleId(), error.getMessage()));
        }
    }
}
