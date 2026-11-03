package spherets.ui;

import javafx.scene.paint.Color;
import spherets.model.Results;
import spherets.model.Settings;
import spherets.plot.*;

import java.util.Locale;

/** Builds the figures from a set of results (same content as run_ts.py). */
final class Figures {
    private Figures() {}

    static String header(Results r) {
        Settings s = r.s;
        return s.material().describe() + "\n" + s.speciesName() + ": " + s.click().describe()
                + String.format(Locale.ROOT, ", piston Ø %.1f cm; water c = %.0f m/s, ρ = %.0f kg/m³",
                s.click().pistonDiameterM() * 100, s.water().soundSpeed(), s.water().density());
    }

    private static String g(double v) {
        String s = String.format(Locale.ROOT, "%.4g", v);
        if (s.contains(".") && !s.contains("e")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private static String tag(Results r) {
        String m = r.s.material().name().split("[ (]")[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        String c = r.s.speciesName().split("[(]")[0].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return m + "_" + c;
    }

    static Figure targetStrength(Results r) {
        Settings s = r.s;
        String sz = r.sizeUnitLabel(), word = s.sizeWord().toLowerCase(Locale.ROOT);
        HeatmapPanel a = new HeatmapPanel(r.angles, r.size, r.ts);
        a.letter = "A";
        a.xLabel = "Off-axis angle of click [°]";
        a.yLabel = sz;
        a.cLabel = "Click target strength [dB]";
        a.xName = "angle"; a.xUnit = "°"; a.yName = word; a.yUnit = "mm"; a.zName = "TS"; a.zUnit = "dB";

        XYPanel b = new XYPanel();
        b.letter = "B";
        b.xLabel = sz;
        b.yLabel = "Click target strength [dB]";
        b.xName = word; b.xUnit = "mm"; b.yUnit = "dB";
        b.legend = XYPanel.Legend.LOWER_RIGHT;
        double[] x = r.size;
        String band = String.format("off-axis clicks (%s, %s–%s°)", s.band().legend, g(r.angles[0]), g(r.angles[r.angles.length - 1]));
        b.add(new XYPanel.Band(x, r.lo, r.hi, PlotTheme::primary, band));
        b.add(new XYPanel.Dots(x, r.cw, PlotTheme::cwDots, 0.9, true, "continuous wave, " + g(r.cwFreqHz / 1e3) + " kHz"));
        String lim = String.format("(≤ %s mm, ≤ %s mm)", g(r.size[r.size.length - 1]), g(s.smallLimitMm()));
        b.add(new XYPanel.Dots(new double[]{x[r.iCwBest], x[r.iCwSmall]}, new double[]{r.cw[r.iCwBest], r.cw[r.iCwSmall]},
                PlotTheme::fg, 3.4, false, "CW maxima " + lim));
        XYPanel.Line on = new XYPanel.Line(x, r.onAxis, PlotTheme::primary, 1.6, "on-axis click");
        b.add(on);
        b.add(new XYPanel.Dots(new double[]{x[r.iBest], x[r.iSmall]}, new double[]{r.onAxis[r.iBest], r.onAxis[r.iSmall]},
                PlotTheme::highlight, 3.2, true, "on-axis maxima " + lim));
        int[] ks = r.iBest == r.iSmall ? new int[]{r.iBest} : new int[]{r.iBest, r.iSmall};
        for (int k : ks)
            b.add(new XYPanel.Annotation(x[k], r.onAxis[k], String.format(Locale.ROOT, "%.1f mm\n%s dB", x[k], Ticks.fmt(r.onAxis[k], 1)),
                    PlotTheme::highlight, 0, 2.3, Gfx.H.CENTER));
        int[] kc = r.iCwBest == r.iCwSmall ? new int[]{r.iCwBest} : new int[]{r.iCwBest, r.iCwSmall};
        for (int k : kc)
            b.add(new XYPanel.Annotation(x[k], r.cw[k], String.format(Locale.ROOT, "%.1f mm\n%s dB", x[k], Ticks.fmt(r.cw[k], 1)),
                    PlotTheme::fg, -0.9, -0.4, Gfx.H.RIGHT));

        Figure f = new Figure("figure_" + tag(r), 18, 7.5);
        f.header = header(r);
        f.add(a, 1).add(b, 1.15);
        return f;
    }

    static Figure clicks(Results r) {
        Settings s = r.s;
        int n = r.showAngles.length;
        XYPanel w = new XYPanel();
        w.title = "Click at different off-axis angles";
        w.xLabel = "Time [µs]"; w.yLabel = "Pressure (on-axis peak = 1)";
        w.xName = "t"; w.xUnit = "µs";
        w.legend = XYPanel.Legend.UPPER_RIGHT;
        w.legendTitle = "Angle";
        double dur = s.click().waveform().length / s.click().sampleRate() * 1e6;
        w.xMin = -20; w.xMax = dur + 60;
        XYPanel sp = new XYPanel();
        sp.title = "Click spectra";
        sp.xLabel = "Frequency [kHz]"; sp.yLabel = "Spectrum [dB re on-axis peak]";
        sp.xName = "f"; sp.xUnit = "kHz"; sp.yUnit = "dB";
        sp.yMin = -60; sp.yMax = 3;
        double[] fk = new double[r.specF.length];
        for (int i = 0; i < fk.length; i++) fk[i] = r.specF[i] / 1e3;
        double peak = 0;
        for (double[] x : r.waveX) for (double v : x) peak = Math.max(peak, Math.abs(v));
        for (int k = 0; k < n; k++) {
            final int kk = k;
            double[] t = new double[r.waveT[k].length], x = new double[t.length];
            for (int i = 0; i < t.length; i++) { t[i] = r.waveT[k][i] * 1e6; x[i] = r.waveX[k][i] / peak; }
            String lab = g(r.showAngles[k]) + "°";
            w.add(new XYPanel.Line(t, x, th -> th.sequential(kk, n), 1.0, lab));
            sp.add(new XYPanel.Line(fk, r.specDb[k], th -> th.sequential(kk, n), 1.2, lab));
        }
        XYPanel bl = new XYPanel();
        bl.title = "Beam profile (total energy)";
        bl.xLabel = "Off-axis angle [°]"; bl.yLabel = "Energy beam loss [dB]";
        bl.xName = "angle"; bl.xUnit = "°"; bl.yUnit = "dB";
        bl.add(new XYPanel.Line(r.angles, r.beamLoss, PlotTheme::fg, 1.4, "beam loss"));
        Figure f = new Figure("clicks_" + tag(r), 18, 6);
        f.header = header(r);
        f.add(w, 1).add(sp, 1).add(bl, 1);
        return f;
    }

    static Figure tsVsAngle(Results r) {
        XYPanel p = new XYPanel();
        p.xLabel = "Off-axis angle of click [°]";
        p.yLabel = "Click target strength [dB]";
        p.xName = "angle"; p.xUnit = "°"; p.yUnit = "dB";
        p.legend = XYPanel.Legend.LOWER_LEFT;
        p.legendTitle = "Sphere " + r.s.sizeWord().toLowerCase(Locale.ROOT);
        int[] ks = r.iBest == r.iSmall ? new int[]{r.iBest} : new int[]{r.iBest, r.iSmall};
        for (int c = 0; c < ks.length; c++) {
            final int cc = c;
            p.add(new XYPanel.Line(r.angles, r.ts[ks[c]], th -> th.categorical()[cc], 1.6,
                    String.format(Locale.ROOT, "%.1f mm", r.size[ks[c]])));
        }
        Figure f = new Figure("ts_vs_angle_" + tag(r), 12, 8);
        f.header = header(r);
        f.add(p, 1);
        return f;
    }

    static Figure frequencyMap(Results r) {
        HeatmapPanel h = new HeatmapPanel(r.mapF, r.size, r.mapTs);
        h.xLabel = "Frequency [kHz]";
        h.yLabel = r.sizeUnitLabel();
        h.cLabel = "Continuous-wave target strength [dB]";
        h.xName = "f"; h.xUnit = "kHz"; h.yName = r.s.sizeWord().toLowerCase(Locale.ROOT); h.yUnit = "mm";
        h.zName = "TS"; h.zUnit = "dB";
        h.vlines.add(new double[]{r.s.click().centroidHz() / 1e3});
        h.vlineColor = t -> Color.WHITE;
        Figure f = new Figure("freqmap_" + tag(r), 14, 9);
        f.header = header(r) + "\nDotted line: click centre frequency " + g(r.s.click().centroidHz() / 1e3) + " kHz";
        f.add(h, 1);
        return f;
    }
}
