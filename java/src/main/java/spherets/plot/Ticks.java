package spherets.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Nice" axis ticks and number formatting. */
public final class Ticks {
    private Ticks() {}

    public static double niceStep(double range, int target) {
        double raw = range / Math.max(target, 1);
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double r = raw / mag;
        double nice = r < 1.5 ? 1 : r < 2.25 ? 2 : r < 3.5 ? 2.5 : r < 7.5 ? 5 : 10;
        return nice * mag;
    }

    public static List<Double> ticks(double min, double max, int target) {
        List<Double> t = new ArrayList<>();
        if (!(max > min)) { t.add(min); return t; }
        double step = niceStep(max - min, target);
        double start = Math.ceil(min / step - 1e-9) * step;
        for (double v = start; v <= max + step * 1e-9; v += step) t.add(Math.abs(v) < step * 1e-9 ? 0 : v);
        return t;
    }

    public static int decimals(double step) {
        if (step >= 1 && Math.abs(step - Math.rint(step)) < 1e-9) return 0;
        int d = (int) Math.max(0, -Math.floor(Math.log10(step) + 1e-9));
        // 2.5, 0.25 ... need one more
        if (Math.abs(step / Math.pow(10, -d) - Math.rint(step / Math.pow(10, -d))) > 1e-6) d++;
        return d;
    }

    /** Format with a typographic minus sign. */
    public static String fmt(double v, int decimals) {
        String s = String.format(Locale.ROOT, "%." + decimals + "f", v);
        if (s.matches("-0(\\.0*)?")) s = s.substring(1);
        return s.replace('-', '−');
    }

    /** Round range outward to multiples of step. */
    public static double[] expand(double min, double max, int target) {
        if (!(max > min)) { min -= 1; max += 1; }
        double step = niceStep(max - min, target);
        return new double[]{Math.floor(min / step) * step, Math.ceil(max / step) * step};
    }
}
