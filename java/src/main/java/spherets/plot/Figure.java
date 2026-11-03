package spherets.plot;

import java.util.ArrayList;
import java.util.List;

/** A figure made of panels side by side, with an optional parameter header. Units: points. */
public class Figure {
    public static final double PT_PER_CM = 72 / 2.54;

    public final String name;
    public double widthCm, heightCm, fontPt = 8;
    public boolean showHeader = true;
    public String header = "";
    public final List<Panel> panels = new ArrayList<>();
    public final List<Double> ratios = new ArrayList<>();

    public Figure(String name, double widthCm, double heightCm) {
        this.name = name; this.widthCm = widthCm; this.heightCm = heightCm;
    }

    public Figure add(Panel p, double ratio) { panels.add(p); ratios.add(ratio); return this; }

    public double widthPt() { return widthCm * PT_PER_CM; }
    public double heightPt() { return heightCm * PT_PER_CM; }

    public void draw(Gfx g, PlotTheme t) {
        double W = widthPt(), H = heightPt(), fs = fontPt;
        g.fill(t.bg());
        g.fillRect(0, 0, W, H);
        double top = fs * 0.4;
        if (showHeader && header != null && !header.isBlank()) {
            double hf = fs * 0.85;
            List<String> lines = wrap(header, hf, W - 2 * fs);
            g.fill(t.muted());
            for (String l : lines) {
                g.text(l, W / 2, top, hf, false, Gfx.H.CENTER, Gfx.V.TOP, 0);
                top += hf * 1.3;
            }
            top += fs * 0.2;
        }
        double sum = ratios.stream().mapToDouble(Double::doubleValue).sum();
        double gap = fs * 1.5, pad = fs * 0.4;
        double avail = W - 2 * pad - gap * (panels.size() - 1);
        double x = pad;
        for (int i = 0; i < panels.size(); i++) {
            double w = avail * ratios.get(i) / sum;
            panels.get(i).draw(g, t, x, top, w, H - top - pad, fs);
            x += w + gap;
        }
    }

    /** Word-wrap each line of text to the given width. */
    static List<String> wrap(String text, double size, double width) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String w : para.split(" ")) {
                String trial = cur.length() == 0 ? w : cur + " " + w;
                if (cur.length() > 0 && Gfx.textWidth(trial, size, false) > width) {
                    out.add(cur.toString());
                    cur = new StringBuilder(w);
                } else cur = new StringBuilder(trial);
            }
            out.add(cur.toString());
        }
        return out;
    }

    public String readout(double x, double y) {
        for (Panel p : panels) {
            String s = p.readout(x, y);
            if (s != null) return s;
        }
        return null;
    }

    public Panel panelAt(double x, double y) {
        for (Panel p : panels) if (p.inPlot(x, y)) return p;
        return null;
    }

    public String toSvg(PlotTheme t) {
        SvgGfx g = new SvgGfx(widthPt(), heightPt());
        draw(g, t);
        return g.document();
    }
}
