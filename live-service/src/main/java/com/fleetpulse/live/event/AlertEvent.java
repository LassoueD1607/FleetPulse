package com.fleetpulse.live.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// alertType and severity are plain strings so this service keeps working if processing adds new values.
public record AlertEvent(
        String alertId,
        String vehicleId,
        String alertType,
        String severity,
        String message,
        double speedKmh,
        double lat,
        double lon,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
