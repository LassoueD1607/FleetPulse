package com.fleetpulse.live.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record AlertEvent(
        String eventId,
        String vehicleId,
        double speedKmh,
        String alertType,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
