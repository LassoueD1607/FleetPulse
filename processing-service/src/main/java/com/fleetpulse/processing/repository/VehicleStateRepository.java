package com.fleetpulse.processing.repository;

import com.fleetpulse.processing.entity.VehicleState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleStateRepository extends JpaRepository<VehicleState, String> {
}
