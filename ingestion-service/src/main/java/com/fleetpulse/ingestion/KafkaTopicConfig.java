package com.fleetpulse.ingestion;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String TELEMETRY_TOPIC = "telemetry";

    @Bean
    NewTopic telemetryTopic() {
        return TopicBuilder.name(TELEMETRY_TOPIC).partitions(3).replicas(1).build();
    }

    // Same partition count as the source: the dead-letter recoverer publishes to the same partition number.
    @Bean
    NewTopic telemetryDeadLetterTopic() {
        return TopicBuilder.name(TELEMETRY_TOPIC + ".DLT").partitions(3).replicas(1).build();
    }
}
