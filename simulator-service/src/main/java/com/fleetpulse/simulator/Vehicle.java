package com.fleetpulse.simulator;

import java.time.Instant;
import java.util.Random;

public class Vehicle {

    private static final double CRUISE_SPEED_KMH = 60.0;
    private static final double MAX_SPEED_KMH = 130.0;
    private static final double KM_PER_DEGREE = 111.0;

    // Vehicles stay inside a box around this point (Tunis) and turn around at its edge.
    private static final double CENTER_LAT = 36.80;
    private static final double CENTER_LON = 10.18;
    private static final double BOX_DEGREES = 0.1;

    private final Random random = new Random();
    private final String id;
    private double lat;
    private double lon;
    private double speedKmh;
    private double headingDeg;

    public Vehicle(String id) {
        this.id = id;
        this.lat = CENTER_LAT + (random.nextDouble() - 0.5) * BOX_DEGREES;
        this.lon = CENTER_LON + (random.nextDouble() - 0.5) * BOX_DEGREES;
        this.speedKmh = CRUISE_SPEED_KMH;
        this.headingDeg = random.nextDouble() * 360;
    }

    public TelemetryReading move(double seconds) {
        // Pulled back toward cruising speed plus noise, so speed drifts like a driver's.
        speedKmh += (CRUISE_SPEED_KMH - speedKmh) * 0.1 + random.nextGaussian() * 3;
        // Rare hard acceleration, so some readings cross the 90 km/h limit; the pull above slows it down again.
        if (random.nextInt(20) == 0) {
            speedKmh += 40;
        }
        speedKmh = Math.max(0, Math.min(MAX_SPEED_KMH, speedKmh));

        headingDeg += random.nextGaussian() * 5;

        double distanceKm = speedKmh / 3600 * seconds;
        double headingRad = Math.toRadians(headingDeg);
        lat += distanceKm * Math.cos(headingRad) / KM_PER_DEGREE;
        // A degree of longitude gets shorter away from the equator.
        lon += distanceKm * Math.sin(headingRad) / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));

        if (Math.abs(lat - CENTER_LAT) > BOX_DEGREES || Math.abs(lon - CENTER_LON) > BOX_DEGREES) {
            headingDeg += 180;
        }

        return new TelemetryReading(id, round(lat, 5), round(lon, 5), round(speedKmh, 1), Instant.now());
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
