package com.fleetpulse.processing.config;

import com.fleetpulse.processing.event.TelemetryEvent;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
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
            KafkaTemplate<String, TelemetryEvent> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition("telemetry.DLT", -1));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));

        ConcurrentKafkaListenerContainerFactory<String, TelemetryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        // application.yml's spring.kafka.listener.ack-mode only configures Spring Boot's own factory.
        // Since we replaced that factory with this bean, the setting is ignored and the mode falls back
        // to BATCH, where handle() cannot receive an Acknowledgment. It must be set here explicitly.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
