package com.fleetpulse.live.controller;

import com.fleetpulse.live.event.AlertEvent;
import com.fleetpulse.live.state.VehicleStatus;
import com.fleetpulse.live.state.VehicleTracker;
import com.fleetpulse.live.stream.AlertStream;
import java.time.Duration;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class FleetController {

    private final VehicleTracker tracker;
    private final AlertStream alertStream;

    public FleetController(VehicleTracker tracker, AlertStream alertStream) {
        this.tracker = tracker;
        this.alertStream = alertStream;
    }

    // First page load: where every vehicle is right now.
    @GetMapping("/vehicles")
    public List<VehicleStatus> vehicles() {
        return tracker.snapshot();
    }

    // First page load: the latest alerts, newest first.
    @GetMapping("/alerts")
    public List<AlertEvent> alerts() {
        return alertStream.recent();
    }

    // One connection carries both kinds of event, told apart by the SSE event name.
    // TEXT_EVENT_STREAM keeps the HTTP response open; Spring writes each element as one SSE message.
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream() {
        Flux<ServerSentEvent<Object>> vehicleEvents = tracker.updates().map(v -> event("vehicle", v));
        Flux<ServerSentEvent<Object>> alertEvents = alertStream.stream().map(a -> event("alert", a));
        // A comment line every 15 s: proxies close connections that stay silent for too long.
        Flux<ServerSentEvent<Object>> keepAlive = Flux.interval(Duration.ofSeconds(15))
                .map(tick -> ServerSentEvent.<Object>builder().comment("keep-alive").build());
        return Flux.merge(vehicleEvents, alertEvents, keepAlive);
    }

    private static ServerSentEvent<Object> event(String name, Object data) {
        return ServerSentEvent.<Object>builder(data).event(name).build();
    }
}
