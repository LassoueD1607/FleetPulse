package com.fleetpulse.live.stream;

import com.fleetpulse.live.event.AlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class AlertStream {

    private static final Logger log = LoggerFactory.getLogger(AlertStream.class);

    // Bridge between the blocking Kafka callback and the reactive world: the listener pushes into
    // the sink, each connected browser subscribes to its Flux. directBestEffort never buffers:
    // with no subscriber the alert is dropped, and a slow subscriber is skipped instead of
    // blocking the Kafka thread or the other browsers. That is the right behaviour for a live view.
    private final Sinks.Many<AlertEvent> sink = Sinks.many().multicast().directBestEffort();

    // Own consumer group, separate from "processing": it receives every alert and its offsets
    // do not interfere with the processing-service.
    @KafkaListener(topics = "alerts", groupId = "live")
    public void onAlert(AlertEvent alert) {
        log.info("Alert {} for vehicle {} — {}km/h", alert.alertType(), alert.vehicleId(), alert.speedKmh());
        sink.tryEmitNext(alert);
    }

    public Flux<AlertEvent> stream() {
        return sink.asFlux();
    }
}
