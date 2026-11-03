package spherets.model;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Output of one calculation: everything the figures and CSV files need. */
public final class Results {
    public final Settings s;
    public final double[] size;        // mm (radius or diameter)
    public final double[] angles;      // deg
    public final double[][] ts;        // [size][angle] click TS dB
    public final double[] onAxis, mean, lo, hi, cw;
    public final double cwFreqHz;
    public final int iBest, iSmall, iCwBest, iCwSmall;
    // clicks figure
    public final double[] showAngles;
    public final double[][] waveT, waveX;
    public final double[] specF;       // Hz
    public final double[][] specDb;
    public final double[] beamLoss;
    // frequency map
    public final double[] mapF;        // kHz
    public final double[][] mapTs;     // [size][freq]
    public final long computeMillis;

    private Results(Settings s, DoubleConsumer progress) {
        long t0 = System.currentTimeMillis();
        this.s = s;
        size = s.sizesMm();
        angles = s.angles();
        double[] radii = new double[size.length];
        for (int i = 0; i < size.length; i++) radii[i] = s.radiusM(size[i]);
        DoubleConsumer p = progress == null ? v -> {} : progress;

        ts = SphereModel.beamClickTS(radii, angles, s.click(), s.material(), s.water(), s.fmaxHz(), s.dfHz(),
                v -> p.accept(0.8 * v));
        int i0 = 0;
        for (int j = 1; j < angles.length; j++) if (Math.abs(angles[j]) < Math.abs(angles[i0])) i0 = j;
        int n = size.length;
        onAxis = new double[n]; mean = new double[n]; lo = new double[n]; hi = new double[n];
        for (int i = 0; i < n; i++) {
            double[] row = ts[i].clone();
            onAxis[i] = row[i0];
            mean[i] = Arrays.stream(row).average().orElse(Double.NaN);
            Arrays.sort(row);
            switch (s.band()) {
                case IQR: lo[i] = percentile(row, 25); hi[i] = percentile(row, 75); break;
                case P10_90: lo[i] = percentile(row, 10); hi[i] = percentile(row, 90); break;
                default: lo[i] = row[0]; hi[i] = row[row.length - 1];
            }
        }
        cwFreqHz = s.cwFreqHz() > 0 ? s.cwFreqHz() : s.click().centroidHz();
        cw = new double[n];
        for (int i = 0; i < n; i++) cw[i] = SphereModel.targetStrength(cwFreqHz, radii[i], s.material(), s.water());
        int[] m = maxima(size, onAxis, s.smallLimitMm());
        iBest = m[0]; iSmall = m[1];
        m = maxima(size, cw, s.smallLimitMm());
        iCwBest = m[0]; iCwSmall = m[1];
        p.accept(0.85);

        // clicks at selected angles
        showAngles = Arrays.stream(new double[]{0, 5, 10, 20, 45, 90})
                .filter(a -> a >= angles[0] - 1e-9 && a <= angles[angles.length - 1] + 1e-9).toArray();
        waveT = new double[showAngles.length][];
        waveX = new double[showAngles.length][];
        specF = SphereModel.frequencyGrid(s.click(), s.fmaxHz(), 200);
        double[] s2 = s.click().powerSpectrum(specF);
        double s2max = Arrays.stream(s2).max().orElse(1);
        specDb = new double[showAngles.length][];
        for (int k = 0; k < showAngles.length; k++) {
            double[][] w = SphereModel.offAxisWaveform(s.click(), showAngles[k], s.water(), 300e-6);
            waveT[k] = w[0]; waveX[k] = w[1];
            specDb[k] = SphereModel.offAxisSpectrumDb(specF, s2, s2max, showAngles[k], s.click(), s.water());
        }
        beamLoss = SphereModel.beamLossDb(angles, s.click(), s.water(), s.fmaxHz());
        p.accept(0.9);

        // CW size x frequency map, 1 kHz steps
        int nf = (int) Math.floor(Math.min(s.fmaxHz(), 250e3) / 1e3);
        mapF = new double[nf];
        for (int j = 0; j < nf; j++) mapF[j] = j + 1;
        mapTs = new double[n][nf];
        java.util.stream.IntStream.range(0, n).parallel().forEach(i -> {
            for (int j = 0; j < nf; j++)
                mapTs[i][j] = SphereModel.targetStrength(mapF[j] * 1e3, radii[i], s.material(), s.water());
        });
        p.accept(1.0);
        computeMillis = System.currentTimeMillis() - t0;
    }

    public static Results compute(Settings s, DoubleConsumer progress) { return new Results(s, progress); }

    static double percentile(double[] sorted, double q) {
        double idx = q / 100 * (sorted.length - 1);
        int lo = (int) Math.floor(idx), hi = Math.min(lo + 1, sorted.length - 1);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo);
    }

    static int[] maxima(double[] size, double[] t, double small) {
        int i = 0, j = -1;
        for (int k = 0; k < t.length; k++) {
            if (t[k] > t[i]) i = k;
            if (size[k] <= small + 1e-9 && (j < 0 || t[k] > t[j])) j = k;
        }
        return new int[]{i, j < 0 ? i : j};
    }

    public String sizeUnitLabel() { return s.sizeWord() + " [mm]"; }

    /** Plain-text summary, like run_ts.py's console output. */
    public String summary() {
        String w = s.sizeWord().toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        b.append("Species / click: ").append(s.speciesName()).append('\n');
        b.append("  ").append(s.click().describe())
                .append(String.format(Locale.ROOT, "; piston diameter %.1f cm%n", s.click().pistonDiameterM() * 100));
        b.append("Material: ").append(s.material().describe()).append('\n');
        b.append(String.format(Locale.ROOT, "Water: ρ = %.1f kg/m³, c = %.1f m/s%n", s.water().density(), s.water().soundSpeed()));
        b.append(String.format(Locale.ROOT, "Sphere %s %.4g–%.4g mm (step %.4g), off-axis angles %.4g–%.4g° (step %.4g)%n%n",
                w, size[0], size[size.length - 1], s.sizeStepMm(), angles[0], angles[angles.length - 1], s.angleStep()));
        b.append("On-axis click target strength\n");
        b.append(String.format(Locale.ROOT, "  best %s ≤ %.4g mm : %.2f mm, TS = %.2f dB  (off-axis %.1f to %.1f dB)%n",
                w, size[size.length - 1], size[iBest], onAxis[iBest], lo[iBest], hi[iBest]));
        b.append(String.format(Locale.ROOT, "  best %s ≤ %.4g mm : %.2f mm, TS = %.2f dB  (off-axis %.1f to %.1f dB)%n%n",
                w, s.smallLimitMm(), size[iSmall], onAxis[iSmall], lo[iSmall], hi[iSmall]));
        b.append(String.format(Locale.ROOT, "Continuous wave, %.4g kHz%n", cwFreqHz / 1e3));
        b.append(String.format(Locale.ROOT, "  best %s ≤ %.4g mm : %.2f mm, TS = %.2f dB%n", w, size[size.length - 1], size[iCwBest], cw[iCwBest]));
        b.append(String.format(Locale.ROOT, "  best %s ≤ %.4g mm : %.2f mm, TS = %.2f dB%n%n", w, s.smallLimitMm(), size[iCwSmall], cw[iCwSmall]));
        b.append(String.format(Locale.ROOT, "Computed %d sizes × %d angles in %.1f s%n", size.length, angles.length, computeMillis / 1000.0));
        return b.toString();
    }

    /** Same layout as run_ts.py's ts_diameter_angle_*.csv. */
    public void writeGridCsv(Path p) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p))) {
            w.println("# first row: off-axis angle deg; first column: " + s.sizeWord().toLowerCase(Locale.ROOT) + " mm; values: click TS dB");
            StringBuilder h = new StringBuilder("nan");
            for (double a : angles) h.append(',').append(f4(a));
            w.println(h);
            for (int i = 0; i < size.length; i++) {
                StringBuilder r = new StringBuilder(f4(size[i]));
                for (double v : ts[i]) r.append(',').append(f4(v));
                w.println(r);
            }
        }
    }

    /** Same layout as run_ts.py's ts_vs_diameter_*.csv. */
    public void writeCurveCsv(Path p) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(p))) {
            w.printf(Locale.ROOT, "%s_mm,TS_on_axis_dB,TS_mean_over_angles_dB,band_low_dB,band_high_dB,TS_CW_%skHz_dB%n",
                    s.sizeWord().toLowerCase(Locale.ROOT), trim(cwFreqHz / 1e3));
            for (int i = 0; i < size.length; i++)
                w.println(String.join(",", f4(size[i]), f4(onAxis[i]), f4(mean[i]), f4(lo[i]), f4(hi[i]), f4(cw[i])));
        }
    }

    private static String f4(double v) { return String.format(Locale.ROOT, "%.4f", v); }
    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.4f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
