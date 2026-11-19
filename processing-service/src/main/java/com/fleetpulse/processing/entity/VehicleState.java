package com.fleetpulse.processing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// What the rules knew about a vehicle after its last reading. Comparing it with the new reading
// is how we alert on a change of state (started speeding, left Tunisia) instead of on every reading.
// Kept in the database, not in memory, so a restart or rebalance does not repeat alerts.
@Entity
@Table(name = "vehicle_state")
public class VehicleState {

    @Id
    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(nullable = false)
    private boolean speeding;

    @Column(name = "outside_tunisia", nullable = false)
    private boolean outsideTunisia;

    protected VehicleState() {}

    public VehicleState(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public boolean isSpeeding() {
        return speeding;
    }

    public void setSpeeding(boolean speeding) {
        this.speeding = speeding;
    }

    public boolean isOutsideTunisia() {
        return outsideTunisia;
    }

    public void setOutsideTunisia(boolean outsideTunisia) {
        this.outsideTunisia = outsideTunisia;
    }
}
