package spherets.model;

/** The surrounding water: density (kg/m^3) and sound speed (m/s). */
public record Water(double density, double soundSpeed) {
    /** Kratzer et al. 2020 values. */
    public static Water standard() { return new Water(1000, 1500); }

    /** Seawater from temperature (C), salinity (psu) and depth (m): Mackenzie (1981) speed, UNESCO density at the surface. */
    public static Water fromTS(double t, double s, double depth) {
        double c = 1448.96 + 4.591 * t - 5.304e-2 * t * t + 2.374e-4 * t * t * t
                + 1.340 * (s - 35) + 1.630e-2 * depth + 1.675e-7 * depth * depth
                - 1.025e-2 * t * (s - 35) - 7.139e-13 * t * depth * depth * depth;
        double rhoW = 999.842594 + 6.793952e-2 * t - 9.095290e-3 * t * t
                + 1.001685e-4 * Math.pow(t, 3) - 1.120083e-6 * Math.pow(t, 4) + 6.536332e-9 * Math.pow(t, 5);
        double rho = rhoW + s * (0.824493 - 4.0899e-3 * t + 7.6438e-5 * t * t - 8.2467e-7 * Math.pow(t, 3) + 5.3875e-9 * Math.pow(t, 4))
                + Math.pow(s, 1.5) * (-5.72466e-3 + 1.0227e-4 * t - 1.6546e-6 * t * t) + 4.8314e-4 * s * s;
        return new Water(rho, c);
    }
}
