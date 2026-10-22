package com.fleetpulse.ingestion;

import java.time.Duration;
import java.util.UUID;

import com.fleetpulse.event.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
public class TelemetryController {

    private static final Logger log = LoggerFactory.getLogger(TelemetryController.class);

    private final KafkaTemplate<String, TelemetryEvent> kafkaTemplate;

    public TelemetryController(KafkaTemplate<String, TelemetryEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/telemetry")
    public Mono<ResponseEntity<Void>> send(@RequestBody TelemetryEvent event) {
        // A client may resend the same eventId on purpose (idempotency test); only fill it when missing.
        TelemetryEvent toSend = event.eventId() != null ? event : new TelemetryEvent(
                UUID.randomUUID().toString(), event.vehicleId(), event.lat(), event.lon(),
                event.speedKmh(), event.timestamp());

        // The supplier form is lazy: send() only runs when the framework subscribes, not when the Mono is built.
        return Mono.fromFuture(() -> kafkaTemplate.send(KafkaTopicConfig.TELEMETRY_TOPIC, toSend.vehicleId(), toSend))
                // send() itself can block on the first metadata fetch, so keep it off the Netty event-loop threads.
                .subscribeOn(Schedulers.boundedElastic())
                // Safety net slightly above delivery.timeout.ms: Kafka's own timeout should fire first.
                .timeout(Duration.ofSeconds(6))
                .map(result -> {
                    log.info("Stored {} in partition {} offset {}", toSend.eventId(),
                            result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    return ResponseEntity.ok().<Void>build();
                })
                .onErrorResume(ex -> {
                    log.error("Send failed for {}", toSend.eventId(), ex);
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).<Void>build());
                });
    }
}
