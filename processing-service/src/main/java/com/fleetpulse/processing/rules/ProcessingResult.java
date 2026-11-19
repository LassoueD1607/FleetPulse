package com.fleetpulse.processing.rules;

import com.fleetpulse.processing.event.AlertEvent;
import com.fleetpulse.processing.event.VehicleUpdate;
import java.util.List;

public record ProcessingResult(List<AlertEvent> alerts, VehicleUpdate update) {}
