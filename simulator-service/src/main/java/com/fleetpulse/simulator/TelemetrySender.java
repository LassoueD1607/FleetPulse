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
    private final double intervalSeconds;

    public TelemetrySender(@Value("${simulator.ingestion-url}") String ingestionUrl,
                           @Value("${simulator.vehicle-count}") int vehicleCount,
                           @Value("${simulator.interval-ms}") long intervalMs) {
        this.client = WebClient.create(ingestionUrl);
        this.intervalSeconds = intervalMs / 1000.0;
        this.vehicles = IntStream.rangeClosed(1, vehicleCount)
                .mapToObj(i -> new Vehicle(String.format("V-%02d", i)))
                .toList();
    }

    @Scheduled(fixedRateString = "${simulator.interval-ms}")
    public void tick() {
        for (Vehicle vehicle : vehicles) {
            TelemetryReading reading = vehicle.move(intervalSeconds);
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
