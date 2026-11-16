package com.fleetpulse.live.controller;

import com.fleetpulse.live.event.AlertEvent;
import com.fleetpulse.live.stream.AlertStream;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class AlertController {

    private final AlertStream alertStream;

    public AlertController(AlertStream alertStream) {
        this.alertStream = alertStream;
    }

    // TEXT_EVENT_STREAM keeps the HTTP response open; Spring writes each emitted alert as one SSE message.
    @GetMapping(value = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AlertEvent> stream() {
        return alertStream.stream();
    }
}
