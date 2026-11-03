package spherets.model;

/**
 * All inputs of one calculation. Sizes are in mm and refer to the radius or the
 * diameter depending on {@link #useRadius}.
 */
public record Settings(String speciesName, Click click, Material material, Water water,
                       boolean useRadius, double sizeMinMm, double sizeMaxMm, double sizeStepMm, double smallLimitMm,
                       double angleMin, double angleMax, double angleStep, Band band,
                       double cwFreqHz, double fmaxHz, double dfHz) {

    public enum Band {
        RANGE("Full range (min–max)", "range over off-axis angles"),
        P10_90("10–90 % of angles", "10–90 % of off-axis angles"),
        IQR("25–75 % of angles", "25–75 % of off-axis angles");
        public final String label, legend;
        Band(String l, String g) { label = l; legend = g; }
        @Override public String toString() { return label; }
    }

    public String sizeWord() { return useRadius ? "Radius" : "Diameter"; }

    public double[] sizesMm() {
        int n = (int) Math.ceil((sizeMaxMm + sizeStepMm / 2 - sizeMinMm) / sizeStepMm);
        double[] d = new double[Math.max(n, 1)];
        for (int i = 0; i < d.length; i++) d[i] = Math.round((sizeMinMm + i * sizeStepMm) * 1e4) / 1e4;
        return d;
    }

    public double[] angles() {
        int n = (int) Math.ceil((angleMax + angleStep / 2 - angleMin) / angleStep);
        double[] a = new double[Math.max(n, 1)];
        for (int i = 0; i < a.length; i++) a[i] = angleMin + i * angleStep;
        return a;
    }

    /** Sphere radius in metres for a size value in mm. */
    public double radiusM(double sizeMm) { return (useRadius ? sizeMm : sizeMm / 2) * 1e-3; }
}
