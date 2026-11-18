package com.fleetpulse.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic telemetryTopic() {
        return TopicBuilder.name("telemetry")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic telemetryDltTopic() {
        return TopicBuilder.name("telemetry.DLT")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic alertsTopic() {
        return TopicBuilder.name("alerts")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic vehicleUpdatesTopic() {
        return TopicBuilder.name("vehicle-updates")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
