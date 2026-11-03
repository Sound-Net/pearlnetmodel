package spherets.model;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the Java port against the Python sphere_ts.py outputs and published reference values. */
class ValidationTest {

    static List<double[]> csv(String name) throws Exception {
        List<double[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ValidationTest.class.getResourceAsStream("/" + name)))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.startsWith("diameter")) continue;
                String[] p = line.split(",");
                double[] v = new double[p.length];
                for (int i = 0; i < p.length; i++) v[i] = p[i].equals("nan") ? Double.NaN : Double.parseDouble(p[i]);
                rows.add(v);
            }
        }
        return rows;
    }

    static Settings settings(Click click, Material m) {
        return new Settings("test", click, m, Water.standard(), false, 3.4, 20, 0.1, 10,
                0, 90, 1, Settings.Band.RANGE, 0, 250e3, 200);
    }

    @Test
    void tungstenCarbideCalibrationSphere() {
        Material wc = Materials.byPrefix("Tungsten");
        double ts = SphereModel.targetStrength(38e3, 0.0381 / 2, wc, new Water(1026, 1490));
        System.out.printf("WC 38.1 mm @ 38 kHz: %.2f dB (reference -42.4)%n", ts);
        assertEquals(-42.39, ts, 0.02);
    }

    @Test
    void kratzerTable2() {
        Object[][] t2 = {
                {18.2, 20.0, -61.68, 10.0, -75.65}, {112.0, 18.6, -31.36, 9.9, -37.43}, {130.0, 18.5, -32.08, 8.5, -38.78},
                {94.6, 18.5, -31.08, 8.1, -37.78}, {133.0, 18.1, -32.22, 8.3, -38.97}, {125.0, 19.2, -32.03, 8.9, -38.47},
                {40.0, 19.1, -30.26, 10.0, -67.60}, {80.0, 18.0, -33.38, 9.6, -36.28}, {73.8, 19.5, -32.73, 10.0, -38.04},
                {132.0, 18.2, -32.54, 8.4, -38.86}, {64.4, 17.0, -32.72, 10.0, -53.14}, {101.2, 17.2, -31.85, 7.5, -38.38},
                {124.0, 19.4, -31.61, 8.9, -38.41}};
        Material pmma = Materials.PRESETS.get(0);
        double sse = 0; int n = 0;
        for (Object[] r : t2) {
            double f = Math.round((Double) r[0]) * 1e3;
            double best = -999, bestD = 0, bestS = -999, bestDS = 0;
            for (int k = 0; k <= 166; k++) {
                double d = Math.round((3.4 + 0.1 * k) * 10) / 10.0;
                double ts = SphereModel.targetStrength(f, d / 2 * 1e-3, pmma, Water.standard());
                if (ts > best) { best = ts; bestD = d; }
                if (d <= 10 && ts > bestS) { bestS = ts; bestDS = d; }
            }
            assertEquals((Double) r[1], bestD, 0.31);
            assertEquals((Double) r[3], bestDS, 0.31);
            sse += Math.pow(best - (Double) r[2], 2) + Math.pow(bestS - (Double) r[4], 2); n += 2;
        }
        double rms = Math.sqrt(sse / n);
        System.out.printf("Table 2 TS rms difference: %.2f dB%n", rms);
        assertTrue(rms < 0.5);
    }

    void compareCurve(String file, Click click, Material m) throws Exception {
        Results r = Results.compute(settings(click, m), null);
        List<double[]> ref = csv(file);
        assertEquals(ref.size(), r.size.length);
        double maxOn = 0, maxBand = 0, maxCw = 0;
        for (int i = 0; i < ref.size(); i++) {
            double[] q = ref.get(i);
            assertEquals(q[0], r.size[i], 1e-9);
            maxOn = Math.max(maxOn, Math.abs(q[1] - r.onAxis[i]));
            maxBand = Math.max(maxBand, Math.max(Math.abs(q[3] - r.lo[i]), Math.abs(q[4] - r.hi[i])));
            maxCw = Math.max(maxCw, Math.abs(q[5] - r.cw[i]));
        }
        System.out.printf("%s: max |Java - Python| on-axis %.4f dB, band %.4f dB, CW %.4f dB%n", file, maxOn, maxBand, maxCw);
        assertTrue(maxOn < 0.01 && maxBand < 0.01 && maxCw < 0.01);
    }

    @Test void pmmaPorpoise() throws Exception {
        compareCurve("ts_vs_diameter_pmma_porpoise.csv", Species.PRESETS.get(0).click(), Materials.PRESETS.get(0));
    }
    @Test void pmmaDolphin() throws Exception {
        compareCurve("ts_vs_diameter_pmma_dolphin.csv", Species.PRESETS.get(1).click(), Materials.PRESETS.get(0));
    }
    @Test void polycarbonatePorpoise() throws Exception {
        compareCurve("ts_vs_diameter_polycarbonate_porpoise.csv", Species.PRESETS.get(0).click(), Materials.byPrefix("Polycarbonate (E"));
    }
    @Test void polycarbonateDolphin() throws Exception {
        compareCurve("ts_vs_diameter_polycarbonate_dolphin.csv", Species.PRESETS.get(1).click(), Materials.byPrefix("Polycarbonate (E"));
    }
    @Test void wavClick() throws Exception {
        java.nio.file.Path p = java.nio.file.Path.of(getClass().getResource("/fakeporp4J2.wav").toURI());
        WavReader.Wav w = WavReader.read(p);
        compareCurve("ts_vs_diameter_pmma_fakeporp4J2.csv", Click.wav(w.samples(), w.sampleRate(), "fakeporp4J2.wav", 130e3, 0.075),
                Materials.PRESETS.get(0));
    }

    @Test void fullGridPmmaPorpoise() throws Exception {
        Results r = Results.compute(settings(Species.PRESETS.get(0).click(), Materials.PRESETS.get(0)), null);
        List<double[]> ref = csv("ts_diameter_angle_pmma_porpoise.csv");
        double max = 0;
        for (int i = 1; i < ref.size(); i++)
            for (int j = 1; j < ref.get(i).length; j++)
                max = Math.max(max, Math.abs(ref.get(i)[j] - r.ts[i - 1][j - 1]));
        System.out.printf("Full grid PMMA porpoise: max |Java - Python| = %.4f dB (%d ms)%n", max, r.computeMillis);
        assertTrue(max < 0.01);
    }

    @Test void lossyMaterialGaussianClick() {
        Material m = new Material("x", 1180, 4.8e9, 0.37, 0.04, "");
        double[] d = {3.4, 5.0, 8.5, 12.0, 18.5};
        double[] radii = new double[d.length];
        for (int i = 0; i < d.length; i++) radii[i] = d[i] / 2 * 1e-3;
        double[] ref = {-77.66198, -77.68503, -77.73152, -55.41124, -52.9583, -56.21724, -37.27553, -41.51533, -36.68474,
                -30.8344, -26.33032, -35.57152, -38.29185, -38.16054, -38.33333};
        double[] refCw = {-77.50453, -59.66888, -33.81904, -45.75564, -34.15755};
        double[][] ts = SphereModel.beamClickTS(radii, new double[]{0, 30, 90}, Click.gaussian(130e3, 16e3, 0.075), m,
                Water.standard(), 250e3, 200, null);
        for (int i = 0; i < d.length; i++) {
            for (int j = 0; j < 3; j++) assertEquals(ref[i * 3 + j], ts[i][j], 1e-3);
            assertEquals(refCw[i], SphereModel.targetStrength(130e3, radii[i], m, Water.standard()), 1e-3);
        }
    }
}
