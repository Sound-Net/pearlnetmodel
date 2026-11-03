package spherets.plot;

import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.TextAlignment;

/** Gfx on a JavaFX canvas; the caller sets the pt -> pixel transform. */
public final class FxGfx implements Gfx {
    private final GraphicsContext g;
    private Color strokeColor = Color.BLACK, fillColor = Color.BLACK;
    private double lineWidth = 1;
    private double[] dash = new double[0];

    public FxGfx(GraphicsContext g) {
        this.g = g;
        g.setLineCap(StrokeLineCap.BUTT);
        g.setLineJoin(StrokeLineJoin.ROUND);
    }

    private void applyStroke() {
        g.setStroke(strokeColor);
        g.setLineWidth(lineWidth);
        g.setLineDashes(dash.length == 0 ? null : dash);
    }

    @Override public void stroke(Color c, double width, double... d) { strokeColor = c; lineWidth = width; dash = d; }
    @Override public void fill(Color c) { fillColor = c; }

    @Override public void line(double x1, double y1, double x2, double y2) { applyStroke(); g.strokeLine(x1, y1, x2, y2); }

    @Override public void polyline(double[] x, double[] y, int n) { applyStroke(); g.strokePolyline(x, y, n); }

    @Override public void fillPolygon(double[] x, double[] y, int n) { g.setFill(fillColor); g.fillPolygon(x, y, n); }

    @Override public void fillRect(double x, double y, double w, double h) { g.setFill(fillColor); g.fillRect(x, y, w, h); }

    @Override public void strokeRect(double x, double y, double w, double h) { applyStroke(); g.strokeRect(x, y, w, h); }

    @Override public void fillCircle(double cx, double cy, double r) { g.setFill(fillColor); g.fillOval(cx - r, cy - r, 2 * r, 2 * r); }

    @Override public void strokeCircle(double cx, double cy, double r) { applyStroke(); g.strokeOval(cx - r, cy - r, 2 * r, 2 * r); }

    @Override
    public void text(String s, double x, double y, double size, boolean bold, H h, V v, double rot) {
        g.save();
        g.setFill(fillColor);
        g.setFont(TextMetrics.font(size, bold));
        g.setTextBaseline(VPos.BASELINE);
        g.setTextAlign(h == H.LEFT ? TextAlignment.LEFT : h == H.RIGHT ? TextAlignment.RIGHT : TextAlignment.CENTER);
        g.translate(x, y);
        if (rot != 0) g.rotate(rot);
        g.fillText(s, 0, Gfx.baselineOffset(v, size));
        g.restore();
    }

    @Override
    public void image(int[] argb, int iw, int ih, double x, double y, double w, double h) {
        WritableImage img = new WritableImage(iw, ih);
        img.getPixelWriter().setPixels(0, 0, iw, ih, PixelFormat.getIntArgbInstance(), argb, 0, iw);
        boolean sm = g.isImageSmoothing();
        g.setImageSmoothing(false);
        g.drawImage(img, x, y, w, h);
        g.setImageSmoothing(sm);
    }

    @Override public void clip(double x, double y, double w, double h) {
        g.save(); g.beginPath(); g.rect(x, y, w, h); g.closePath(); g.clip();
    }
    @Override public void unclip() { g.restore(); }
}
