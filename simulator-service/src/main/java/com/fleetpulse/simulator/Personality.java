package com.fleetpulse.simulator;

// cruiseKmh: the speed the driver drifts back to. burstOneIn: a hard acceleration happens on average
// once every N ticks.
public enum Personality {
    CALM(80, 150),
    AGGRESSIVE(100, 40),
    // Drives like a calm driver, but half of its trips head abroad, over the Algerian or Libyan border.
    ROGUE(90, 80);

    private final double cruiseKmh;
    private final int burstOneIn;

    Personality(double cruiseKmh, int burstOneIn) {
        this.cruiseKmh = cruiseKmh;
        this.burstOneIn = burstOneIn;
    }

    public double cruiseKmh() {
        return cruiseKmh;
    }

    public int burstOneIn() {
        return burstOneIn;
    }
}
