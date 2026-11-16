package com.fleetpulse.processing.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record TelemetryEvent(
        String eventId,
        String vehicleId,
        double lat,
        double lon,
        double speedKmh,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
