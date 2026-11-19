package com.fleetpulse.live.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public record VehicleUpdate(
        String vehicleId,
        double lat,
        double lon,
        double speedKmh,
        boolean speeding,
        boolean outsideTunisia,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
