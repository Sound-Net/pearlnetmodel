package spherets.plot;

import javafx.scene.paint.Color;

/** Drawing surface in points (1/72 inch). Implemented for the JavaFX canvas and SVG. */
public interface Gfx {
    String FONT_FAMILY = "Arial";
    /** Arial ascent / descent / cap height as a fraction of the font size. */
    double ASCENT = 0.905, DESCENT = 0.212, CAP = 0.716;

    enum H { LEFT, CENTER, RIGHT }
    enum V { TOP, CENTER, BASELINE, BOTTOM }

    void stroke(Color c, double width, double... dash);
    void fill(Color c);

    void line(double x1, double y1, double x2, double y2);
    void polyline(double[] x, double[] y, int n);
    void fillPolygon(double[] x, double[] y, int n);
    void fillRect(double x, double y, double w, double h);
    void strokeRect(double x, double y, double w, double h);
    void fillCircle(double cx, double cy, double r);
    void strokeCircle(double cx, double cy, double r);
    /** Text using the current fill colour. */
    void text(String s, double x, double y, double size, boolean bold, H h, V v, double rotationDeg);
    /** ARGB raster, row 0 at the top, drawn without smoothing into (x, y, w, h). */
    void image(int[] argb, int iw, int ih, double x, double y, double w, double h);
    void clip(double x, double y, double w, double h);
    void unclip();

    static double textWidth(String s, double size, boolean bold) {
        return TextMetrics.width(s, size, bold);
    }

    /** Offset from the anchor y to the baseline. */
    static double baselineOffset(V v, double size) {
        switch (v) {
            case TOP: return ASCENT * size;
            case CENTER: return CAP * size / 2;
            case BOTTOM: return -DESCENT * size;
            default: return 0;
        }
    }
}
