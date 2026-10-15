package com.fleetpulse.processing;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorConfig {

        // Boot wires a CommonErrorHandler bean into the listener container factory automatically.
    @Bean
    DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {
        // Spring Kafka 4 defaults to "<topic>-dlt"; our topic is "<topic>.DLT", so the destination is explicit.
        // Same partition number as the failed record, which is why the DLT needs as many partitions as the source.
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        // 1s between attempts, 3 retries after the first failure (4 attempts total).
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
    }
}
