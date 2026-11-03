package spherets.plot;

import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

/** Gfx that writes an SVG document (vector output for publications). */
public final class SvgGfx implements Gfx {
    private final StringBuilder sb = new StringBuilder();
    private final double width, height;
    private Color strokeColor = Color.BLACK, fillColor = Color.BLACK;
    private double lineWidth = 1;
    private double[] dash = new double[0];
    private int clipId = 0;

    public SvgGfx(double widthPt, double heightPt) { width = widthPt; height = heightPt; }

    private static String n(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s.equals("-0") ? "0" : s;
    }
    private static String col(Color c) {
        return String.format("#%02x%02x%02x", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
    private String strokeAttrs() {
        StringBuilder b = new StringBuilder(" fill=\"none\" stroke=\"").append(col(strokeColor)).append('"');
        if (strokeColor.getOpacity() < 1) b.append(" stroke-opacity=\"").append(n(strokeColor.getOpacity())).append('"');
        b.append(" stroke-width=\"").append(n(lineWidth)).append('"');
        if (dash.length > 0) {
            b.append(" stroke-dasharray=\"");
            for (int i = 0; i < dash.length; i++) b.append(i > 0 ? "," : "").append(n(dash[i]));
            b.append('"');
        }
        return b.append(" stroke-linejoin=\"round\"").toString();
    }
    private String fillAttrs() {
        String s = " fill=\"" + col(fillColor) + "\"";
        if (fillColor.getOpacity() < 1) s += " fill-opacity=\"" + n(fillColor.getOpacity()) + "\"";
        return s;
    }
    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override public void stroke(Color c, double w, double... d) { strokeColor = c; lineWidth = w; dash = d; }
    @Override public void fill(Color c) { fillColor = c; }

    @Override public void line(double x1, double y1, double x2, double y2) {
        sb.append("<line x1=\"").append(n(x1)).append("\" y1=\"").append(n(y1)).append("\" x2=\"").append(n(x2))
                .append("\" y2=\"").append(n(y2)).append('"').append(strokeAttrs()).append("/>\n");
    }

    private String points(double[] x, double[] y, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(i > 0 ? " " : "").append(n(x[i])).append(',').append(n(y[i]));
        return b.toString();
    }

    @Override public void polyline(double[] x, double[] y, int n) {
        sb.append("<polyline points=\"").append(points(x, y, n)).append('"').append(strokeAttrs()).append("/>\n");
    }
    @Override public void fillPolygon(double[] x, double[] y, int n) {
        sb.append("<polygon points=\"").append(points(x, y, n)).append('"').append(fillAttrs()).append("/>\n");
    }
    @Override public void fillRect(double x, double y, double w, double h) {
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y)).append("\" width=\"").append(n(w))
                .append("\" height=\"").append(n(h)).append('"').append(fillAttrs()).append("/>\n");
    }
    @Override public void strokeRect(double x, double y, double w, double h) {
        sb.append("<rect x=\"").append(n(x)).append("\" y=\"").append(n(y)).append("\" width=\"").append(n(w))
                .append("\" height=\"").append(n(h)).append('"').append(strokeAttrs()).append("/>\n");
    }
    @Override public void fillCircle(double cx, double cy, double r) {
        sb.append("<circle cx=\"").append(n(cx)).append("\" cy=\"").append(n(cy)).append("\" r=\"").append(n(r))
                .append('"').append(fillAttrs()).append("/>\n");
    }
    @Override public void strokeCircle(double cx, double cy, double r) {
        sb.append("<circle cx=\"").append(n(cx)).append("\" cy=\"").append(n(cy)).append("\" r=\"").append(n(r))
                .append('"').append(strokeAttrs()).append("/>\n");
    }

    @Override public void text(String s, double x, double y, double size, boolean bold, H h, V v, double rot) {
        String anchor = h == H.LEFT ? "start" : h == H.RIGHT ? "end" : "middle";
        sb.append("<text x=\"").append(n(x)).append("\" y=\"").append(n(y + (rot == 0 ? Gfx.baselineOffset(v, size) : 0)))
                .append("\" font-family=\"Arial, Helvetica, sans-serif\" font-size=\"").append(n(size)).append('"');
        if (bold) sb.append(" font-weight=\"bold\"");
        sb.append(" text-anchor=\"").append(anchor).append('"').append(fillAttrs());
        if (rot != 0) {
            // rotate about the anchor, then shift to the baseline in the rotated frame
            sb.append(" transform=\"rotate(").append(n(rot)).append(' ').append(n(x)).append(' ').append(n(y))
                    .append(") translate(0 ").append(n(Gfx.baselineOffset(v, size))).append(")\"");
        }
        sb.append('>').append(esc(s)).append("</text>\n");
    }

    @Override public void image(int[] argb, int iw, int ih, double x, double y, double w, double h) {
        BufferedImage bi = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        bi.setRGB(0, 0, iw, ih, argb, 0, iw);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try { ImageIO.write(bi, "png", bos); } catch (IOException e) { throw new RuntimeException(e); }
        sb.append("<image x=\"").append(n(x)).append("\" y=\"").append(n(y)).append("\" width=\"").append(n(w))
                .append("\" height=\"").append(n(h)).append("\" preserveAspectRatio=\"none\" style=\"image-rendering:pixelated;image-rendering:crisp-edges\"")
                .append(" xlink:href=\"data:image/png;base64,").append(Base64.getEncoder().encodeToString(bos.toByteArray()))
                .append("\"/>\n");
    }

    @Override public void clip(double x, double y, double w, double h) {
        int id = ++clipId;
        sb.append("<clipPath id=\"c").append(id).append("\"><rect x=\"").append(n(x)).append("\" y=\"").append(n(y))
                .append("\" width=\"").append(n(w)).append("\" height=\"").append(n(h)).append("\"/></clipPath>\n");
        sb.append("<g clip-path=\"url(#c").append(id).append(")\">\n");
    }
    @Override public void unclip() { sb.append("</g>\n"); }

    public String document() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" version=\"1.1\""
                + " width=\"" + n(width) + "pt\" height=\"" + n(height) + "pt\" viewBox=\"0 0 " + n(width) + " " + n(height) + "\">\n"
                + sb + "</svg>\n";
    }
}
