package com.fleetpulse.ingestion.controller;

import com.fleetpulse.ingestion.event.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);
    private static final String TOPIC = "telemetry";

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    public TelemetryController(KafkaTemplate<String, TelemetryEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/telemetry")
    public Mono<ResponseEntity<Void>> receive(@RequestBody TelemetryEvent event) {
        String id = event.eventId() != null ? event.eventId() : UUID.randomUUID().toString();

        TelemetryEvent enriched = new TelemetryEvent(
                id,
                event.vehicleId(),
                event.lat(),
                event.lon(),
                event.speedKmh(),
                event.timestamp()
        );

        kafkaTemplate.send(TOPIC, enriched.vehicleId(), enriched)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event {} for vehicle {}: {}",
                                enriched.eventId(), enriched.vehicleId(), ex.getMessage());
                    } else {
                        log.info("Sent event {} for vehicle {} to partition {}",
                                enriched.eventId(), enriched.vehicleId(),
                                result.getRecordMetadata().partition());
                    }
                });

        return Mono.just(ResponseEntity.<Void>accepted().build());
    }
}
