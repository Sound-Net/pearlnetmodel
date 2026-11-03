package spherets.model;

/**
 * Isotropic elastic solid, stored as density + Young's modulus + Poisson's ratio.
 * Sound speeds are derived (and can be converted back with {@link #fromSpeeds}).
 * A loss factor eta adds damping as a complex modulus E* = E (1 + i eta), which is
 * the same as scaling both speeds by sqrt(1 + i eta).
 */
public record Material(String name, double density, double youngsModulusPa, double poissonRatio,
                       double lossFactor, String note) {

    public static Material fromSpeeds(String name, double density, double cLong, double cShear,
                                      double lossFactor, String note) {
        double cl2 = cLong * cLong, ct2 = cShear * cShear;
        double nu = (cl2 - 2 * ct2) / (2 * (cl2 - ct2));
        double e = density * ct2 * (3 * cl2 - 4 * ct2) / (cl2 - ct2);
        return new Material(name, density, e, nu, lossFactor, note);
    }

    /** Real longitudinal speed (m/s), lossless. */
    public double cLong() {
        double e = youngsModulusPa, nu = poissonRatio;
        return Math.sqrt(e * (1 - nu) / ((1 + nu) * (1 - 2 * nu)) / density);
    }

    /** Real shear speed (m/s), lossless. */
    public double cShear() {
        return Math.sqrt(youngsModulusPa / (2 * (1 + poissonRatio)) / density);
    }

    /** Complex Lame parameters {lambda, mu} including the loss factor. */
    public Complex[] lame() {
        Complex e = new Complex(youngsModulusPa, youngsModulusPa * lossFactor);
        double nu = poissonRatio;
        Complex mu = e.div(2 * (1 + nu));
        Complex lam = e.mul(nu / ((1 + nu) * (1 - 2 * nu)));
        return new Complex[]{lam, mu};
    }

    /** Complex {c_L, c_T}. */
    public Complex[] speeds() {
        Complex[] lm = lame();
        Complex cl = lm[0].add(lm[1].mul(2)).div(density).sqrt();
        Complex ct = lm[1].div(density).sqrt();
        return new Complex[]{cl, ct};
    }

    public Material withName(String n) { return new Material(n, density, youngsModulusPa, poissonRatio, lossFactor, note); }

    public String describe() {
        String s = String.format("%s: ρ = %.0f kg/m³, E = %.3g GPa, ν = %.3f (c_L = %.0f m/s, c_T = %.0f m/s)",
                name, density, youngsModulusPa / 1e9, poissonRatio, cLong(), cShear());
        if (lossFactor > 0) s += String.format(", loss factor %.3g", lossFactor);
        return s;
    }
}
