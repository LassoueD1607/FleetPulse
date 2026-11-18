package com.fleetpulse.processing.rules;

import com.fleetpulse.processing.entity.ProcessedEvent;
import com.fleetpulse.processing.entity.VehicleState;
import com.fleetpulse.processing.event.AlertEvent;
import com.fleetpulse.processing.event.AlertType;
import com.fleetpulse.processing.event.Severity;
import com.fleetpulse.processing.event.TelemetryEvent;
import com.fleetpulse.processing.event.VehicleUpdate;
import com.fleetpulse.processing.repository.ProcessedEventRepository;
import com.fleetpulse.processing.repository.VehicleStateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelemetryProcessor {

    private final ProcessedEventRepository processedEvents;
    private final VehicleStateRepository states;
    private final GeofenceService geofence;
    private final double speedLimitKmh;
    private final double speedingClearKmh;

    public TelemetryProcessor(ProcessedEventRepository processedEvents,
                              VehicleStateRepository states,
                              GeofenceService geofence,
                              @Value("${fleet.rules.speed-limit-kmh}") double speedLimitKmh,
                              @Value("${fleet.rules.speeding-clear-kmh}") double speedingClearKmh) {
        this.processedEvents = processedEvents;
        this.states = states;
        this.geofence = geofence;
        this.speedLimitKmh = speedLimitKmh;
        this.speedingClearKmh = speedingClearKmh;
    }

    // One transaction: the idempotency record and the new vehicle state commit together, so a crash
    // cannot leave an event marked as processed without its state change. A duplicate eventId makes
    // saveAndFlush throw DataIntegrityViolationException here and rolls everything back.
    // Per vehicle there is no race on vehicle_state: the vehicle ID is the Kafka key, so one vehicle's
    // readings always reach the same partition and are processed one at a time, in order.
    @Transactional
    public ProcessingResult process(TelemetryEvent event) {
        processedEvents.saveAndFlush(new ProcessedEvent(event.eventId()));

        VehicleState state = states.findById(event.vehicleId())
                .orElseGet(() -> new VehicleState(event.vehicleId()));
        List<AlertEvent> alerts = new ArrayList<>();

        boolean speeding = state.isSpeeding()
                ? event.speedKmh() > speedingClearKmh
                : event.speedKmh() > speedLimitKmh;
        if (speeding && !state.isSpeeding()) {
            alerts.add(alert(event, AlertType.SPEEDING, Severity.WARNING,
                    String.format(Locale.ROOT, "Speeding at %.1f km/h (limit %.0f)", event.speedKmh(), speedLimitKmh)));
        }
        state.setSpeeding(speeding);

        boolean outside = !geofence.isInsideTunisia(event.lat(), event.lon());
        if (outside && !state.isOutsideTunisia()) {
            alerts.add(alert(event, AlertType.LEFT_TUNISIA, Severity.CRITICAL,
                    String.format(Locale.ROOT, "Left Tunisia at %.4f, %.4f", event.lat(), event.lon())));
        } else if (!outside && state.isOutsideTunisia()) {
            alerts.add(alert(event, AlertType.RETURNED_TO_TUNISIA, Severity.INFO, "Back inside Tunisia"));
        }
        state.setOutsideTunisia(outside);

        states.save(state);
        VehicleUpdate update = new VehicleUpdate(event.vehicleId(), event.lat(), event.lon(), event.speedKmh(),
                speeding, outside, event.timestamp());
        return new ProcessingResult(alerts, update);
    }

    private AlertEvent alert(TelemetryEvent event, AlertType type, Severity severity, String message) {
        return new AlertEvent(UUID.randomUUID().toString(), event.vehicleId(), type, severity, message,
                event.speedKmh(), event.lat(), event.lon(), event.timestamp());
    }
}
