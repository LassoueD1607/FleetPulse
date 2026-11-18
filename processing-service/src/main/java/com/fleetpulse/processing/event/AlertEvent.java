package com.fleetpulse.processing.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// alertId is unique per alert: one reading can raise several alerts, so the reading's eventId is not enough.
public record AlertEvent(
        String alertId,
        String vehicleId,
        AlertType alertType,
        Severity severity,
        String message,
        double speedKmh,
        double lat,
        double lon,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
