package spherets.plot;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Line / scatter / band axes. */
public class XYPanel extends Panel {
    public enum Legend { NONE, LOWER_RIGHT, UPPER_RIGHT, LOWER_LEFT, UPPER_LEFT }

    public String xLabel = "", yLabel = "";
    public String xName = "x", xUnit = "", yUnit = "";
    public double xMin = Double.NaN, xMax = Double.NaN, yMin = Double.NaN, yMax = Double.NaN;
    public boolean grid = true;
    public Legend legend = Legend.NONE;
    public String legendTitle;
    public final List<Layer> layers = new ArrayList<>();
    private double cxMin, cxMax, cyMin, cyMax;

    public abstract static class Layer {
        public String label;
        public Function<PlotTheme, Color> color;
        double[] x;
        abstract double[] yRange();
        abstract void draw(XYPanel p, Gfx g, PlotTheme t, double fs);
        abstract void swatch(Gfx g, PlotTheme t, double x, double y, double w, double fs);
        Double valueAt(double xv) { return null; }
    }

    public static class Line extends Layer {
        final double[] y; final double width; final double[] dash;
        public Line(double[] x, double[] y, Function<PlotTheme, Color> c, double width, String label, double... dash) {
            this.x = x; this.y = y; this.color = c; this.width = width; this.label = label; this.dash = dash;
        }
        double[] yRange() { return range(y); }
        void draw(XYPanel p, Gfx g, PlotTheme t, double fs) {
            double[] X = new double[x.length], Y = new double[x.length];
            int n = 0;
            g.stroke(color.apply(t), width * fs / 8, scaled(dash, fs));
            for (int i = 0; i < x.length; i++) {
                if (Double.isFinite(y[i])) { X[n] = p.X(x[i], p.cxMin, p.cxMax); Y[n] = p.Y(y[i], p.cyMin, p.cyMax); n++; }
                else if (n > 0) { g.polyline(X, Y, n); n = 0; }
            }
            if (n > 1) g.polyline(X, Y, n);
        }
        void swatch(Gfx g, PlotTheme t, double x0, double y0, double w, double fs) {
            g.stroke(color.apply(t), width * fs / 8, scaled(dash, fs)); g.line(x0, y0, x0 + w, y0);
        }
        Double valueAt(double xv) { return interp(x, y, xv); }
    }

    public static class Dots extends Layer {
        final double[] y; final double r; final boolean filled; final double ring;
        public Dots(double[] x, double[] y, Function<PlotTheme, Color> c, double r, boolean filled, String label) {
            this.x = x; this.y = y; this.color = c; this.r = r; this.filled = filled; this.label = label; this.ring = 0.9;
        }
        double[] yRange() { return range(y); }
        void draw(XYPanel p, Gfx g, PlotTheme t, double fs) {
            double rr = r * fs / 8;
            for (int i = 0; i < x.length; i++) {
                if (!Double.isFinite(y[i])) continue;
                double X = p.X(x[i], p.cxMin, p.cxMax), Y = p.Y(y[i], p.cyMin, p.cyMax);
                if (filled) {
                    if (rr > 1.5) { g.fill(t.bg()); g.fillCircle(X, Y, rr + 0.8 * fs / 8); }
                    g.fill(color.apply(t)); g.fillCircle(X, Y, rr);
                } else { g.stroke(color.apply(t), ring * fs / 8); g.strokeCircle(X, Y, rr); }
            }
        }
        void swatch(Gfx g, PlotTheme t, double x0, double y0, double w, double fs) {
            double rr = Math.max(r * fs / 8, fs * 0.12);
            if (filled) { g.fill(color.apply(t)); g.fillCircle(x0 + w / 2, y0, rr); }
            else { g.stroke(color.apply(t), ring * fs / 8); g.strokeCircle(x0 + w / 2, y0, rr); }
        }
    }

    public static class Band extends Layer {
        final double[] lo, hi;
        public Band(double[] x, double[] lo, double[] hi, Function<PlotTheme, Color> c, String label) {
            this.x = x; this.lo = lo; this.hi = hi; this.color = c; this.label = label;
        }
        double[] yRange() { double[] a = range(lo), b = range(hi); return new double[]{Math.min(a[0], b[0]), Math.max(a[1], b[1])}; }
        void draw(XYPanel p, Gfx g, PlotTheme t, double fs) {
            int n = x.length;
            double[] X = new double[2 * n], Y = new double[2 * n];
            for (int i = 0; i < n; i++) {
                X[i] = p.X(x[i], p.cxMin, p.cxMax); Y[i] = p.Y(hi[i], p.cyMin, p.cyMax);
                X[2 * n - 1 - i] = X[i]; Y[2 * n - 1 - i] = p.Y(lo[i], p.cyMin, p.cyMax);
            }
            g.fill(withAlpha(color.apply(t), t.bandAlpha()));
            g.fillPolygon(X, Y, 2 * n);
        }
        void swatch(Gfx g, PlotTheme t, double x0, double y0, double w, double fs) {
            g.fill(withAlpha(color.apply(t), t.bandAlpha())); g.fillRect(x0, y0 - fs * 0.3, w, fs * 0.6);
        }
    }

    public static class Annotation extends Layer {
        final double ax, ay; final String text; final double dx, dy; final Gfx.H h;
        public Annotation(double x, double y, String text, Function<PlotTheme, Color> c, double dxEm, double dyEm, Gfx.H h) {
            this.ax = x; this.ay = y; this.text = text; this.color = c; this.dx = dxEm; this.dy = dyEm; this.h = h;
            this.x = new double[]{x};
        }
        double[] yRange() { return new double[]{ay, ay}; }
        void draw(XYPanel p, Gfx g, PlotTheme t, double fs) {
            double X = p.X(ax, p.cxMin, p.cxMax) + dx * fs, Y = p.Y(ay, p.cyMin, p.cyMax) + dy * fs;
            String[] lines = text.split("\n");
            double fsA = fs * 0.9, lh = fsA * 1.15;
            double w = 0;
            for (String l : lines) w = Math.max(w, Gfx.textWidth(l, fsA, false));
            double x0 = h == Gfx.H.LEFT ? X : h == Gfx.H.RIGHT ? X - w : X - w / 2;
            double y0 = Y - lines.length * lh / 2;
            g.fill(withAlpha(t.bg(), 0.8));
            g.fillRect(x0 - fsA * 0.2, y0 - fsA * 0.1, w + fsA * 0.4, lines.length * lh + fsA * 0.2);
            g.fill(color.apply(t));
            for (int i = 0; i < lines.length; i++)
                g.text(lines[i], x0, y0 + i * lh, fsA, false, Gfx.H.LEFT, Gfx.V.TOP, 0);
        }
        void swatch(Gfx g, PlotTheme t, double x0, double y0, double w, double fs) {}
    }

    public static class VLine extends Layer {
        final double xv; final double[] dash;
        public VLine(double x, Function<PlotTheme, Color> c, String label, double... dash) {
            this.xv = x; this.color = c; this.label = label; this.dash = dash; this.x = new double[]{x};
        }
        double[] yRange() { return new double[]{Double.NaN, Double.NaN}; }
        void draw(XYPanel p, Gfx g, PlotTheme t, double fs) {
            double X = p.X(xv, p.cxMin, p.cxMax);
            g.stroke(color.apply(t), fs / 8, scaled(dash, fs)); g.line(X, p.py, X, p.py + p.ph);
        }
        void swatch(Gfx g, PlotTheme t, double x0, double y0, double w, double fs) {
            g.stroke(color.apply(t), fs / 8, scaled(dash, fs)); g.line(x0, y0, x0 + w, y0);
        }
    }

    static double[] scaled(double[] dash, double fs) {
        double[] d = new double[dash.length];
        for (int i = 0; i < d.length; i++) d[i] = dash[i] * fs / 8;
        return d;
    }

    static double[] range(double[] v) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double a : v) if (Double.isFinite(a)) { lo = Math.min(lo, a); hi = Math.max(hi, a); }
        return new double[]{lo, hi};
    }

    static Double interp(double[] x, double[] y, double xv) {
        if (x.length == 0) return null;
        int best = 0;
        for (int i = 1; i < x.length; i++) if (Math.abs(x[i] - xv) < Math.abs(x[best] - xv)) best = i;
        return Double.isFinite(y[best]) ? y[best] : null;
    }

    public XYPanel add(Layer l) { layers.add(l); return this; }

    @Override
    public void draw(Gfx g, PlotTheme t, double x, double y, double w, double h, double fs) {
        // data ranges
        double x0 = xMin, x1 = xMax, y0 = yMin, y1 = yMax;
        if (Double.isNaN(x0) || Double.isNaN(x1)) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (Layer l : layers) for (double v : l.x) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
            if (Double.isNaN(x0)) x0 = lo;
            if (Double.isNaN(x1)) x1 = hi;
        }
        if (Double.isNaN(y0) || Double.isNaN(y1)) {
            double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (Layer l : layers) {
                if (l instanceof Annotation) continue;
                double[] r = l.yRange();
                if (Double.isFinite(r[0])) lo = Math.min(lo, r[0]);
                if (Double.isFinite(r[1])) hi = Math.max(hi, r[1]);
            }
            double pad = (hi - lo) * 0.06;
            double[] e = Ticks.expand(lo - pad, hi + pad, 6);
            if (Double.isNaN(y0)) y0 = e[0];
            if (Double.isNaN(y1)) y1 = e[1];
        }
        if (!(x1 > x0)) { x0 -= 1; x1 += 1; }
        if (!(y1 > y0)) { y0 -= 1; y1 += 1; }
        cxMin = x0; cxMax = x1; cyMin = y0; cyMax = y1;

        // layout
        int ny = (int) Math.max(3, Math.min(8, h / (fs * 3.2)));
        List<Double> yt = Ticks.ticks(y0, y1, ny);
        int yd = Ticks.decimals(Ticks.niceStep(y1 - y0, ny));
        double left = tickLen(fs) + fs * 0.3 + maxLabelWidth(yt, yd, fs) + fs * 0.5 + fs * 1.3;
        double top = fs * 1.9, bottom = tickLen(fs) + fs * 1.6 + fs * 1.4, right = fs * 1.2;
        px = x + left; py = y + top; pw = Math.max(10, w - left - right); ph = Math.max(10, h - top - bottom);
        int nx = (int) Math.max(3, Math.min(10, pw / (fs * 5.5)));
        List<Double> xt = Ticks.ticks(x0, x1, nx);
        int xd = Ticks.decimals(Ticks.niceStep(x1 - x0, nx));

        g.fill(t.bg());
        if (grid) {
            double lw = Math.max(0.5, fs * 0.08);
            g.stroke(t.grid(), lw);
            for (double v : xt) { double X = X(v, x0, x1); g.line(X, py, X, py + ph); }
            for (double v : yt) { double Y = Y(v, y0, y1); g.line(px, Y, px + pw, Y); }
        }
        g.clip(px, py, pw, ph);
        for (Layer l : layers) if (!(l instanceof Annotation)) l.draw(this, g, t, fs);
        g.unclip();
        for (Layer l : layers) if (l instanceof Annotation) l.draw(this, g, t, fs);
        drawAxes(g, t, fs, x0, x1, y0, y1, xt, xd, yt, yd, xLabel, yLabel, x + fs * 0.1, false);
        drawHeading(g, t, fs);
        drawLegend(g, t, fs);
    }

    private void drawLegend(Gfx g, PlotTheme t, double fs) {
        if (legend == Legend.NONE) return;
        List<Layer> items = new ArrayList<>();
        for (Layer l : layers) if (l.label != null && !l.label.isEmpty() && !(l instanceof Annotation)) items.add(l);
        if (items.isEmpty()) return;
        double lf = fs * 0.88, lh = lf * 1.45, sw = lf * 1.8, gap = lf * 0.5;
        double w = 0;
        for (Layer l : items) w = Math.max(w, Gfx.textWidth(l.label, lf, false));
        double tw = legendTitle == null ? 0 : Gfx.textWidth(legendTitle, lf, false);
        double bw = Math.max(sw + gap + w, tw) + lf * 0.8;
        double bh = (items.size() + (legendTitle == null ? 0 : 1)) * lh + lf * 0.5;
        double m = fs * 0.5;
        double bx = (legend == Legend.LOWER_LEFT || legend == Legend.UPPER_LEFT) ? px + m : px + pw - bw - m;
        double by = (legend == Legend.UPPER_LEFT || legend == Legend.UPPER_RIGHT) ? py + m : py + ph - bh - m;
        g.fill(withAlpha(t.bg(), 0.85));
        g.fillRect(bx, by, bw, bh);
        double yy = by + lf * 0.25 + lh / 2;
        double xx = bx + lf * 0.4;
        if (legendTitle != null) {
            g.fill(t.fg());
            g.text(legendTitle, bx + bw / 2, yy, lf, false, Gfx.H.CENTER, Gfx.V.CENTER, 0);
            yy += lh;
        }
        for (Layer l : items) {
            l.swatch(g, t, xx, yy, sw, lf);
            g.fill(t.fg());
            g.text(l.label, xx + sw + gap, yy, lf, false, Gfx.H.LEFT, Gfx.V.CENTER, 0);
            yy += lh;
        }
    }

    @Override
    public String readout(double x, double y) {
        if (!inPlot(x, y)) return null;
        double xv = cxMin + (x - px) / pw * (cxMax - cxMin);
        StringBuilder b = new StringBuilder((xName + " " + Ticks.fmt(xv, 2) + " " + xUnit).trim());
        for (Layer l : layers) {
            if (l.label == null || !(l instanceof Line)) continue;
            Double v = l.valueAt(xv);
            if (v != null) b.append("     ").append(l.label).append(": ").append(Ticks.fmt(v, 2)).append(' ').append(yUnit);
        }
        return b.toString().replace('-', '−');
    }
}
