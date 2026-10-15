package com.fleetpulse.ingestion;

import java.util.UUID;

import com.fleetpulse.event.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    public TelemetryController(KafkaTemplate<String, TelemetryEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<Void> send(@RequestBody TelemetryEvent event) {
        // A client may resend the same eventId on purpose (idempotency test); only fill it when missing.
        TelemetryEvent toSend = event.eventId() != null ? event : new TelemetryEvent(
                UUID.randomUUID().toString(), event.vehicleId(), event.lat(), event.lon(),
                event.speedKmh(), event.timestamp());

        // The vehicle id is the key: same vehicle -> same partition -> ordered.
        kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_TOPIC, toSend.vehicleId(), toSend)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Send failed for {}", toSend.eventId(), ex);
                    } else {
                        log.info("Sent {} to partition {} offset {}", toSend.eventId(),
                                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
        return ResponseEntity.accepted().build();
    }
}
