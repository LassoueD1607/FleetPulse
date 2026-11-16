package com.fleetpulse.processing.listener;

import com.fleetpulse.processing.entity.ProcessedEvent;
import com.fleetpulse.processing.event.AlertEvent;
import com.fleetpulse.processing.event.TelemetryEvent;
import com.fleetpulse.processing.repository.ProcessedEventRepository;
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
    private static final double SPEED_LIMIT_KMH = 90.0;

    // Kafka delivers at-least-once: after a crash, a record can be redelivered.
    // We attempt to INSERT the eventId — the DB unique constraint rejects duplicates atomically.
    // ProcessedEvent implements Persistable so save() always calls persist() (INSERT), never merge() (UPSERT).
    private final ProcessedEventRepository processedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TelemetryListener(ProcessedEventRepository processedEventRepository,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.processedEventRepository = processedEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "telemetry", groupId = "processing")
    public void handle(ConsumerRecord<String, TelemetryEvent> record, Acknowledgment ack) {
        TelemetryEvent event = record.value();

        try {
            processedEventRepository.save(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate event {} for vehicle {} — skipping", event.eventId(), event.vehicleId());
            ack.acknowledge();
            return;
        }

        log.info("Received event {} for vehicle {} | speed={}km/h | partition={}",
                event.eventId(), event.vehicleId(), event.speedKmh(), record.partition());

        if (event.speedKmh() > SPEED_LIMIT_KMH) {
            AlertEvent alert = new AlertEvent(event.eventId(), event.vehicleId(), event.speedKmh(), "SPEEDING", event.timestamp());
            kafkaTemplate.send(ALERTS_TOPIC, alert.vehicleId(), alert);
            log.warn("SPEEDING alert for vehicle {} — {}km/h", event.vehicleId(), event.speedKmh());
        }

        ack.acknowledge();
    }
}
