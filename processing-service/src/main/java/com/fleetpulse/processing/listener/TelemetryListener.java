package com.fleetpulse.processing.listener;

import com.fleetpulse.processing.event.AlertEvent;
import com.fleetpulse.processing.event.TelemetryEvent;
import com.fleetpulse.processing.rules.ProcessingResult;
import com.fleetpulse.processing.rules.TelemetryProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TelemetryListener {

    private static final Logger log = LoggerFactory.getLogger(TelemetryListener.class);
    private static final String ALERTS_TOPIC = "alerts";
    private static final String VEHICLE_UPDATES_TOPIC = "vehicle-updates";

    private final TelemetryProcessor processor;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TelemetryListener(TelemetryProcessor processor, KafkaTemplate<String, Object> kafkaTemplate) {
        this.processor = processor;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "telemetry", groupId = "processing")
    public void handle(ConsumerRecord<String, TelemetryEvent> record, Acknowledgment ack) {
        TelemetryEvent event = record.value();

        // Kafka delivers at-least-once, so a record can arrive twice. The processor inserts the eventId
        // first and the DB unique constraint rejects a duplicate, which we skip.
        ProcessingResult result;
        try {
            result = processor.process(event);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event {} for vehicle {} — skipping", event.eventId(), event.vehicleId());
            ack.acknowledge();
            return;
        }

        log.info("Received event {} for vehicle {} | speed={}km/h | partition={}",
                event.eventId(), event.vehicleId(), event.speedKmh(), record.partition());

        // Published after the transaction commits, so they only ever describe committed state.
        for (AlertEvent alert : result.alerts()) {
            kafkaTemplate.send(ALERTS_TOPIC, alert.vehicleId(), alert);
            log.warn("{} alert for vehicle {} — {}", alert.alertType(), alert.vehicleId(), alert.message());
        }
        // Same key as the telemetry, so one vehicle's updates stay in order on one partition.
        kafkaTemplate.send(VEHICLE_UPDATES_TOPIC, result.update().vehicleId(), result.update());

        ack.acknowledge();
    }
}
