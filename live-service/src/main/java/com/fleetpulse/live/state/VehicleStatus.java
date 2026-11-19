package com.fleetpulse.live.state;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// What the dashboard shows for one vehicle. status is OK, SPEEDING, OUTSIDE_TUNISIA or OFFLINE.
public record VehicleStatus(
        String vehicleId,
        double lat,
        double lon,
        double speedKmh,
        String status,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant lastSeen
) {}
