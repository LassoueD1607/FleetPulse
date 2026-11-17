package com.fleetpulse.simulator;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

// eventId is left out on purpose: ingestion assigns it.
public record TelemetryReading(
        String vehicleId,
        double lat,
        double lon,
        double speedKmh,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
