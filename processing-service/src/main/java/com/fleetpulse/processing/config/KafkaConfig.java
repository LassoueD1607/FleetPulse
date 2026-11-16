package com.fleetpulse.processing.config;

import com.fleetpulse.processing.event.TelemetryEvent;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    // When handle() throws, the listener container catches it and delegates to DefaultErrorHandler.
    // DefaultErrorHandler retries the record (FixedBackOff: 2 retries, 1s apart = 3 total attempts).
    // After the last retry it calls DeadLetterPublishingRecoverer, which uses the KafkaTemplate
    // to produce the failed record to telemetry.DLT — that is why this service needs a producer config.
    // Without an error handler the partition blocks forever on a poison record.
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TelemetryEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TelemetryEvent> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        // Destination is explicit: the default naming convention changed across Spring Kafka versions.
        // partition -1 means let the default partitioner decide.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition("telemetry.DLT", -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));

        ConcurrentKafkaListenerContainerFactory<String, TelemetryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
