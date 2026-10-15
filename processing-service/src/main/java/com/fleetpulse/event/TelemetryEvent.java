package com.fleetpulse.event;

import java.time.Instant;

public record TelemetryEvent(
        String eventId,
        String vehicleId,
        double lat,
        double lon,
        double speedKmh,
        Instant timestamp) {
}
