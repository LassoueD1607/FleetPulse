package com.fleetpulse.processing.listener;

import com.fleetpulse.processing.event.TelemetryEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);

    // The container runs in MANUAL ack mode, so without ack.acknowledge() the offset is never
    // committed and every restart would log all dead letters again.
    @KafkaListener(topics = "telemetry.DLT", groupId = "processing-dlt")
    public void handle(ConsumerRecord<String, TelemetryEvent> record, Acknowledgment ack) {
        // DeadLetterPublishingRecoverer stores the failure reason in this header.
        Header reason = record.headers().lastHeader("kafka_dlt-exception-message");
        log.error("Dead letter — key={} | event={} | reason={}",
                record.key(), record.value(),
                reason == null ? "unknown" : new String(reason.value(), StandardCharsets.UTF_8));
        ack.acknowledge();
    }
}
