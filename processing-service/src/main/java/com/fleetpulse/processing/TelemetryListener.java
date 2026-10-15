package com.fleetpulse.processing;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fleetpulse.event.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class TelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryListener.class);

    // In-memory on purpose: lost on restart. A real service would use a DB unique constraint on eventId.
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(topics = "telemetry")
    public void onTelemetry(@Payload TelemetryEvent event,
                            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                            @Header(KafkaHeaders.OFFSET) long offset,
                            Acknowledgment ack) {
        if (processedEventIds.contains(event.eventId())) {
            log.info("Duplicate {} skipped (partition {} offset {})", event.eventId(), partition, offset);
            ack.acknowledge();
            return;
        }

        log.info("Processing {} vehicle={} speed={} (partition {} offset {})",
                event.eventId(), event.vehicleId(), event.speedKmh(), partition, offset);

        // Simulated poison message: always fails, so it ends in telemetry.DLT.
        if (event.speedKmh() < 0) {
            throw new IllegalArgumentException("Invalid speed " + event.speedKmh() + " for event " + event.eventId());
        }

        // Recorded only after the work succeeded, so a failed event can still be retried.
        processedEventIds.add(event.eventId());

        // Ack only after the work succeeded: a crash before this line means redelivery (at-least-once).
        ack.acknowledge();
    }
}
