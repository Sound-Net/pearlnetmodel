package spherets.plot;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Raster / vector export of figures. Must be called on the JavaFX thread for raster output. */
public final class FigureExport {
    private FigureExport() {}

    /** Render at the given dots per inch. */
    public static WritableImage render(Figure f, PlotTheme t, double dpi) {
        double s = dpi / 72.0;
        int w = (int) Math.round(f.widthPt() * s), h = (int) Math.round(f.heightPt() * s);
        Canvas c = new Canvas(w, h);
        GraphicsContext gc = c.getGraphicsContext2D();
        gc.scale(s, s);
        f.draw(new FxGfx(gc), t);
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(t.bg());
        return c.snapshot(sp, null);
    }

    public static BufferedImage toBuffered(WritableImage img) {
        int w = (int) img.getWidth(), h = (int) img.getHeight();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader pr = img.getPixelReader();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            pr.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
            bi.setRGB(0, y, w, 1, row, 0, w);
        }
        return bi;
    }

    /** PNG with the resolution stored in the pHYs chunk (so Word / LaTeX get the physical size right). */
    public static void writePng(Figure f, PlotTheme t, double dpi, Path out) throws IOException {
        BufferedImage bi = toBuffered(render(f, t, dpi));
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(bi), param);
        int ppm = (int) Math.round(dpi / 0.0254);
        IIOMetadataNode phys = new IIOMetadataNode("pHYs");
        phys.setAttribute("pixelsPerUnitXAxis", Integer.toString(ppm));
        phys.setAttribute("pixelsPerUnitYAxis", Integer.toString(ppm));
        phys.setAttribute("unitSpecifier", "meter");
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        root.appendChild(phys);
        meta.mergeTree("javax_imageio_png_1.0", root);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(out))) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(bi, null, meta), param);
        } finally {
            writer.dispose();
        }
    }

    public static void writeSvg(Figure f, PlotTheme t, Path out) throws IOException {
        Files.writeString(out, f.toSvg(t), StandardCharsets.UTF_8);
    }

}
