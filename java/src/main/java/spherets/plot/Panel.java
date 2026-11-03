package spherets.plot;

import javafx.scene.paint.Color;

/** One set of axes inside a figure. Coordinates are in points. */
public abstract class Panel {
    public String letter;   // bold panel label, e.g. "A"
    public String title;    // optional small title
    /** Plot area from the last draw (pt). */
    protected double px, py, pw, ph;

    /** Draw into the box (x, y, w, h); fs = base font size in pt. */
    public abstract void draw(Gfx g, PlotTheme t, double x, double y, double w, double h, double fs);

    /** Text describing the data under the point (pt), or null. */
    public abstract String readout(double x, double y);

    public boolean inPlot(double x, double y) { return x >= px && x <= px + pw && y >= py && y <= py + ph; }
    public double[] plotRect() { return new double[]{px, py, pw, ph}; }

    protected void drawHeading(Gfx g, PlotTheme t, double fs) {
        double y = py - fs * 0.55;
        double x = px;
        if (letter != null && !letter.isEmpty()) {
            g.fill(t.fg());
            g.text(letter, px, y, fs * 1.15, true, Gfx.H.LEFT, Gfx.V.BOTTOM, 0);
            x += Gfx.textWidth(letter, fs * 1.15, true) + fs * 0.7;
        }
        if (title != null && !title.isEmpty()) {
            g.fill(t.fg());
            g.text(title, letter == null ? px + pw / 2 : x, y, fs, false,
                    letter == null ? Gfx.H.CENTER : Gfx.H.LEFT, Gfx.V.BOTTOM, 0);
        }
    }

    protected static double tickLen(double fs) { return fs * 0.4; }

    /** Axis frame + ticks + tick labels + axis labels. */
    protected void drawAxes(Gfx g, PlotTheme t, double fs, double xmin, double xmax, double ymin, double ymax,
                            java.util.List<Double> xt, int xd, java.util.List<Double> yt, int yd,
                            String xLabel, String yLabel, double yLabelX, boolean grid) {
        double lw = Math.max(0.5, fs * 0.08);
        if (grid) {
            g.stroke(t.grid(), lw);
            for (double v : xt) { double X = X(v, xmin, xmax); g.line(X, py, X, py + ph); }
            for (double v : yt) { double Y = Y(v, ymin, ymax); g.line(px, Y, px + pw, Y); }
        }
        g.stroke(t.fg(), lw);
        g.strokeRect(px, py, pw, ph);
        double tl = tickLen(fs);
        g.fill(t.fg());
        for (double v : xt) {
            double X = X(v, xmin, xmax);
            g.line(X, py + ph, X, py + ph + tl);
            g.text(Ticks.fmt(v, xd), X, py + ph + tl + fs * 0.25, fs, false, Gfx.H.CENTER, Gfx.V.TOP, 0);
        }
        for (double v : yt) {
            double Y = Y(v, ymin, ymax);
            g.line(px - tl, Y, px, Y);
            g.text(Ticks.fmt(v, yd), px - tl - fs * 0.3, Y, fs, false, Gfx.H.RIGHT, Gfx.V.CENTER, 0);
        }
        g.text(xLabel, px + pw / 2, py + ph + tl + fs * 1.6, fs * 1.05, false, Gfx.H.CENTER, Gfx.V.TOP, 0);
        g.text(yLabel, yLabelX, py + ph / 2, fs * 1.05, false, Gfx.H.CENTER, Gfx.V.TOP, -90);
    }

    protected double X(double v, double min, double max) { return px + (v - min) / (max - min) * pw; }
    protected double Y(double v, double min, double max) { return py + ph - (v - min) / (max - min) * ph; }

    protected static double maxLabelWidth(java.util.List<Double> ticks, int d, double fs) {
        double w = 0;
        for (double v : ticks) w = Math.max(w, Gfx.textWidth(Ticks.fmt(v, d), fs, false));
        return w;
    }

    protected static Color withAlpha(Color c, double a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
}
