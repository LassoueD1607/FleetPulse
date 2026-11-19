package com.fleetpulse.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Vehicle {

    private record Place(String name, double lat, double lon) {}

    private static final double MAX_SPEED_KMH = 140.0;
    private static final double KM_PER_DEGREE = 111.0;

    private static final Place TUNIS = new Place("Tunis", 36.80, 10.18);
    private static final Place BIZERTE = new Place("Bizerte", 37.27, 9.87);
    private static final Place LE_KEF = new Place("Le Kef", 36.17, 8.71);
    private static final Place SOUSSE = new Place("Sousse", 35.83, 10.64);
    private static final Place KAIROUAN = new Place("Kairouan", 35.68, 10.10);
    private static final Place SFAX = new Place("Sfax", 34.74, 10.76);
    private static final Place GAFSA = new Place("Gafsa", 34.43, 8.78);
    private static final Place TOZEUR = new Place("Tozeur", 33.92, 8.13);
    private static final Place SKHIRA = new Place("Skhira", 34.30, 10.07);
    private static final Place GABES = new Place("Gabes", 33.88, 10.10);
    private static final Place DJERBA = new Place("Djerba", 33.81, 10.86);
    private static final Place TATAOUINE = new Place("Tataouine", 32.93, 10.45);

    private static final Place SOUK_AHRAS = new Place("Souk Ahras (Algeria)", 36.28, 7.95);
    private static final Place TEBESSA = new Place("Tebessa (Algeria)", 35.40, 8.12);
    private static final Place ZUWARA = new Place("Zuwara (Libya)", 32.93, 12.08);

    // Vehicles only drive between neighbouring cities: a straight line between distant cities would
    // cut across the sea or another country and look like leaving Tunisia when it is not.
    private static final Map<Place, List<Place>> ROADS = new HashMap<>();

    // The Tunisian city each foreign town is reached from, and the way back.
    private static final Map<Place, Place> BORDER_CROSSINGS = Map.of(LE_KEF, SOUK_AHRAS, GAFSA, TEBESSA, TATAOUINE, ZUWARA);
    private static final Map<Place, Place> RETURN_ROUTES = Map.of(SOUK_AHRAS, LE_KEF, TEBESSA, GAFSA, ZUWARA, TATAOUINE);

    static {
        road(TUNIS, BIZERTE);
        road(TUNIS, SOUSSE);
        road(TUNIS, KAIROUAN);
        road(TUNIS, LE_KEF);
        road(LE_KEF, KAIROUAN);
        road(SOUSSE, KAIROUAN);
        road(SOUSSE, SFAX);
        road(KAIROUAN, SFAX);
        road(KAIROUAN, GAFSA);
        road(SFAX, GAFSA);
        // Skhira keeps the road on the coast: a straight Sfax-Gabes line would cross the Gulf of Gabes.
        road(SFAX, SKHIRA);
        road(SKHIRA, GABES);
        road(GAFSA, TOZEUR);
        road(GABES, TOZEUR);
        road(GABES, DJERBA);
        road(GABES, TATAOUINE);
        road(DJERBA, TATAOUINE);
    }

    private static final List<Place> CITIES = List.copyOf(ROADS.keySet());
    private static final List<Place> BORDER_CITIES = List.copyOf(BORDER_CROSSINGS.keySet());

    private static void road(Place a, Place b) {
        ROADS.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
        ROADS.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
    }

    private final Random random = new Random();
    private final String id;
    private final Personality personality;
    private double lat;
    private double lon;
    private double speedKmh;
    private Place destination;

    public Vehicle(String id, Personality personality) {
        this.id = id;
        this.personality = personality;
        // A ROGUE vehicle starts in a border city so the demo shows a crossing early.
        List<Place> startOptions = personality == Personality.ROGUE ? BORDER_CITIES : CITIES;
        Place start = startOptions.get(random.nextInt(startOptions.size()));
        this.lat = start.lat();
        this.lon = start.lon();
        this.speedKmh = personality.cruiseKmh();
        this.destination = pickDestination(start);
    }

    // simulatedSeconds is longer than the real tick: at real speeds a trip between two cities would
    // take hours to watch, so each tick covers the distance of several simulated minutes.
    // The reported speed stays realistic; only the distance covered per tick is scaled.
    public TelemetryReading move(double simulatedSeconds) {
        updateSpeed();

        double stepKm = speedKmh / 3600 * simulatedSeconds;
        double dyKm = (destination.lat() - lat) * KM_PER_DEGREE;
        // A degree of longitude gets shorter away from the equator.
        double dxKm = (destination.lon() - lon) * KM_PER_DEGREE * Math.cos(Math.toRadians(lat));
        double remainingKm = Math.hypot(dxKm, dyKm);

        if (stepKm >= remainingKm) {
            lat = destination.lat();
            lon = destination.lon();
            destination = pickDestination(destination);
        } else {
            // Bearing measured from north, with a little wobble so the path is not a ruler-straight line.
            double bearing = Math.atan2(dxKm, dyKm) + random.nextGaussian() * 0.03;
            lat += stepKm * Math.cos(bearing) / KM_PER_DEGREE;
            lon += stepKm * Math.sin(bearing) / (KM_PER_DEGREE * Math.cos(Math.toRadians(lat)));
        }

        return new TelemetryReading(id, round(lat, 5), round(lon, 5), round(speedKmh, 1), Instant.now());
    }

    private void updateSpeed() {
        // Pulled back toward the driver's cruising speed plus noise, so speed drifts like a driver's.
        speedKmh += (personality.cruiseKmh() - speedKmh) * 0.1 + random.nextGaussian() * 3;
        // Occasional hard acceleration; the pull above slows the vehicle down again.
        if (random.nextInt(personality.burstOneIn()) == 0) {
            speedKmh += 25;
        }
        speedKmh = Math.max(0, Math.min(MAX_SPEED_KMH, speedKmh));
    }

    private Place pickDestination(Place current) {
        // Abroad, the only way is back to the city the vehicle crossed from.
        if (RETURN_ROUTES.containsKey(current)) {
            return RETURN_ROUTES.get(current);
        }
        if (personality == Personality.ROGUE && BORDER_CROSSINGS.containsKey(current) && random.nextDouble() < 0.7) {
            return BORDER_CROSSINGS.get(current);
        }
        List<Place> neighbours = ROADS.get(current);
        return neighbours.get(random.nextInt(neighbours.size()));
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}
