package com.fleetpulse.processing.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// The latest known state of a vehicle after a reading, with the rule flags already computed.
// Published for every reading so consumers (the dashboard) never need to know the rules.
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
