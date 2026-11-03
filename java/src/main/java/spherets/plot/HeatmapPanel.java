package spherets.plot;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Image map z[iy][ix] on uniform x / y grids, with an inferno colour bar. */
public class HeatmapPanel extends Panel {
    public final double[] xc, yc;
    public final double[][] z;
    public double vmin, vmax;
    public String xLabel = "", yLabel = "", cLabel = "";
    public String xName = "x", xUnit = "", yName = "y", yUnit = "", zName = "", zUnit = "";
    public final List<double[]> vlines = new ArrayList<>();      // {x}
    public Function<PlotTheme, Color> vlineColor = t -> Color.WHITE;
    private double x0, x1, y0, y1;

    public HeatmapPanel(double[] xc, double[] yc, double[][] z) {
        this.xc = xc; this.yc = yc; this.z = z;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (double[] r : z) for (double v : r) if (Double.isFinite(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        vmax = Math.ceil(hi / 5) * 5;
        vmin = Math.max(Math.floor(lo / 5) * 5, vmax - 70);
    }

    private static double half(double[] c) { return c.length > 1 ? (c[1] - c[0]) / 2 : 0.5; }

    private boolean extendMin() {
        for (double[] r : z) for (double v : r) if (v < vmin) return true;
        return false;
    }

    @Override
    public void draw(Gfx g, PlotTheme t, double x, double y, double w, double h, double fs) {
        x0 = xc[0] - half(xc); x1 = xc[xc.length - 1] + half(xc);
        y0 = yc[0] - half(yc); y1 = yc[yc.length - 1] + half(yc);
        int ny = (int) Math.max(3, Math.min(8, h / (fs * 3.2)));
        List<Double> yt = Ticks.ticks(y0, y1, ny);
        int yd = Ticks.decimals(Ticks.niceStep(y1 - y0, ny));
        List<Double> ct = Ticks.ticks(vmin, vmax, ny);
        int cd = Ticks.decimals(Ticks.niceStep(vmax - vmin, ny));

        double left = tickLen(fs) + fs * 0.3 + maxLabelWidth(yt, yd, fs) + fs * 0.5 + fs * 1.3;
        double cbw = fs * 1.1, cbgap = fs * 1.2;
        double cbLabels = tickLen(fs) + fs * 0.3 + maxLabelWidth(ct, cd, fs) + fs * 0.5 + fs * 1.3;
        double right = cbgap + cbw + cbLabels;
        double top = fs * 1.9, bottom = tickLen(fs) + fs * 1.6 + fs * 1.4;
        px = x + left; py = y + top; pw = Math.max(10, w - left - right); ph = Math.max(10, h - top - bottom);
        int nx = (int) Math.max(3, Math.min(10, pw / (fs * 5.5)));
        List<Double> xt = Ticks.ticks(x0, x1, nx);
        int xd = Ticks.decimals(Ticks.niceStep(x1 - x0, nx));

        // raster: row 0 = top = largest y
        int iw = xc.length, ih = yc.length;
        int[] argb = new int[iw * ih];
        for (int iy = 0; iy < ih; iy++)
            for (int ix = 0; ix < iw; ix++)
                argb[(ih - 1 - iy) * iw + ix] = Colormap.inferno((z[iy][ix] - vmin) / (vmax - vmin));
        g.image(argb, iw, ih, px, py, pw, ph);

        g.clip(px, py, pw, ph);
        for (double[] v : vlines) {
            double X = X(v[0], x0, x1);
            g.stroke(vlineColor.apply(t), fs / 8, fs * 0.15, fs * 0.3);
            g.line(X, py, X, py + ph);
        }
        g.unclip();
        drawAxes(g, t, fs, x0, x1, y0, y1, xt, xd, yt, yd, xLabel, yLabel, x + fs * 0.1, false);
        drawHeading(g, t, fs);

        // colour bar
        double cx = px + pw + cbgap;
        int n = 256;
        int[] cb = new int[n];
        for (int i = 0; i < n; i++) cb[n - 1 - i] = Colormap.inferno(i / (n - 1.0));
        boolean ext = extendMin();
        double tri = ext ? cbw * 1.0 : 0;
        double cby = py, cbh = ph - tri;
        g.image(cb, 1, n, cx, cby, cbw, cbh);
        double lw = Math.max(0.5, fs * 0.08);
        g.stroke(t.fg(), lw);
        if (ext) {
            g.fill(Colormap.infernoColor(0));
            double[] tx = {cx, cx + cbw, cx + cbw / 2}, ty = {cby + cbh, cby + cbh, cby + ph};
            g.fillPolygon(tx, ty, 3);
            g.polyline(new double[]{cx, cx + cbw / 2, cx + cbw}, new double[]{cby + cbh, cby + ph, cby + cbh}, 3);
            g.line(cx, cby, cx, cby + cbh); g.line(cx + cbw, cby, cx + cbw, cby + cbh); g.line(cx, cby, cx + cbw, cby);
        } else g.strokeRect(cx, cby, cbw, cbh);
        g.fill(t.fg());
        double tl = tickLen(fs);
        double maxw = 0;
        for (double v : ct) {
            double Y = cby + cbh - (v - vmin) / (vmax - vmin) * cbh;
            g.line(cx + cbw, Y, cx + cbw + tl, Y);
            String s = Ticks.fmt(v, cd);
            maxw = Math.max(maxw, Gfx.textWidth(s, fs, false));
            g.text(s, cx + cbw + tl + fs * 0.3, Y, fs, false, Gfx.H.LEFT, Gfx.V.CENTER, 0);
        }
        g.text(cLabel, cx + cbw + tl + fs * 0.3 + maxw + fs * 0.5, cby + cbh / 2, fs * 1.05, false, Gfx.H.CENTER, Gfx.V.BOTTOM, 90);
    }

    @Override
    public String readout(double x, double y) {
        if (!inPlot(x, y)) return null;
        double xv = x0 + (x - px) / pw * (x1 - x0), yv = y1 - (y - py) / ph * (y1 - y0);
        int ix = nearest(xc, xv), iy = nearest(yc, yv);
        return xName + " " + Ticks.fmt(xc[ix], 2) + " " + xUnit + "     " + yName + " " + Ticks.fmt(yc[iy], 2) + " " + yUnit
                + "     " + zName + " " + Ticks.fmt(z[iy][ix], 2) + " " + zUnit;
    }

    private static int nearest(double[] c, double v) {
        int b = 0;
        for (int i = 1; i < c.length; i++) if (Math.abs(c[i] - v) < Math.abs(c[b] - v)) b = i;
        return b;
    }
}
