package spherets.plot;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.function.Consumer;

/** Shows one figure (scaled to fit, WYSIWYG with the export size) with copy / export controls. */
public class FigureView extends BorderPane {
    public enum Style {
        MATCH("Match app"), LIGHT("Light"), DARK("Dark");
        final String label;
        Style(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Canvas canvas = new Canvas(), overlay = new Canvas();
    private final Pane holder = new Pane(canvas, overlay);
    private final Label readout = new Label(" ");
    private final Spinner<Double> wSpin, hSpin, fSpin;
    private final CheckBox header = new CheckBox("Header");
    private final ComboBox<Style> style = new ComboBox<>();
    private final ComboBox<Integer> dpi = new ComboBox<>();
    private final BooleanProperty appDark;
    private Figure figure;
    private double scale = 1, offX, offY;
    private File lastDir;
    private Consumer<String> status = s -> {};

    public FigureView(BooleanProperty appDark, double widthCm, double heightCm) {
        this.appDark = appDark == null ? new SimpleBooleanProperty(false) : appDark;
        getStyleClass().add("figure-view");

        wSpin = spinner(4, 60, widthCm, 0.5);
        hSpin = spinner(3, 60, heightCm, 0.5);
        fSpin = spinner(5, 24, 8, 0.5);
        style.getItems().setAll(Style.values());
        style.setValue(Style.MATCH);
        dpi.getItems().setAll(150, 300, 600, 1200);
        dpi.setValue(300);
        header.setSelected(true);

        tip(wSpin, "Figure width in cm. The figure on screen is drawn exactly as it will be copied or exported, scaled to fit the window. "
                + "Typical journal widths: 8.5 cm (one column) or 17.5 cm (two columns).");
        tip(hSpin, "Figure height in cm.");
        tip(fSpin, "Base font size in points at the exported size (8–9 pt suits most journals).");
        tip(header, "Show the line of model parameters (material, click, water) above the figure.");
        tip(style, "Colour scheme for this figure: light (white background, best for papers), dark, or follow the app's theme.");
        tip(dpi, "Resolution for copied and PNG-exported images, in dots per inch. 300 dpi is the usual minimum for print; 600 dpi for line art.");

        Button copy = new Button("Copy");
        copy.setOnAction(e -> copyImage());
        tip(copy, "Copy the figure to the clipboard as an image at the chosen resolution (paste into Word, PowerPoint, Keynote...).");
        MenuButton more = new MenuButton("More");
        MenuItem copySvg = new MenuItem("Copy as SVG text");
        copySvg.setOnAction(e -> copySvg());
        MenuItem copyLight = new MenuItem("Copy light version");
        copyLight.setOnAction(e -> copyImage(PlotTheme.LIGHT));
        MenuItem exp = new MenuItem("Export PNG or SVG…");
        exp.setOnAction(e -> export());
        more.getItems().addAll(copyLight, copySvg, new SeparatorMenuItem(), exp);
        Button export = new Button("Export…");
        export.setOnAction(e -> export());
        tip(export, "Save the figure as a PNG (raster, chosen dpi) or SVG (vector; open in Inkscape / Illustrator, or convert to PDF/EPS).");

        HBox bar = new HBox(6,
                new Label("W"), wSpin, new Label("H"), hSpin, new Label("cm"), sep(),
                new Label("Font"), fSpin, new Label("pt"), sep(), header, sep(),
                new Label("Style"), style, sep(), new Label("dpi"), dpi, spacer(), copy, export, more);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 8, 6, 8));
        bar.getStyleClass().add("figure-toolbar");
        setTop(bar);

        holder.getStyleClass().add("figure-holder");
        holder.setMinSize(100, 100);
        canvas.widthProperty().bind(holder.widthProperty());
        canvas.heightProperty().bind(holder.heightProperty());
        overlay.widthProperty().bind(holder.widthProperty());
        overlay.heightProperty().bind(holder.heightProperty());
        overlay.setMouseTransparent(true);
        canvas.widthProperty().addListener(o -> redraw());
        canvas.heightProperty().addListener(o -> redraw());
        setCenter(holder);

        readout.getStyleClass().add("readout");
        readout.setPadding(new Insets(3, 10, 4, 10));
        readout.setMaxWidth(Double.MAX_VALUE);
        setBottom(readout);

        canvas.setOnMouseMoved(e -> hover(e.getX(), e.getY()));
        canvas.setOnMouseExited(e -> { clearOverlay(); readout.setText(" "); });

        wSpin.valueProperty().addListener(o -> applySize());
        hSpin.valueProperty().addListener(o -> applySize());
        fSpin.valueProperty().addListener(o -> applySize());
        header.selectedProperty().addListener(o -> applySize());
        style.valueProperty().addListener(o -> redraw());
        this.appDark.addListener(o -> redraw());

        ContextMenu cm = new ContextMenu();
        MenuItem c1 = new MenuItem("Copy image");
        c1.setOnAction(e -> copyImage());
        MenuItem c2 = new MenuItem("Copy light version");
        c2.setOnAction(e -> copyImage(PlotTheme.LIGHT));
        MenuItem c3 = new MenuItem("Copy as SVG text");
        c3.setOnAction(e -> copySvg());
        MenuItem c4 = new MenuItem("Export…");
        c4.setOnAction(e -> export());
        cm.getItems().addAll(c1, c2, c3, new SeparatorMenuItem(), c4);
        canvas.setOnContextMenuRequested(e -> cm.show(canvas, e.getScreenX(), e.getScreenY()));
    }

    public void setStatusSink(Consumer<String> s) { status = s; }

    private static Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    private static Separator sep() { return new Separator(javafx.geometry.Orientation.VERTICAL); }
    private static void tip(Control c, String t) { Tooltip tt = new Tooltip(t); tt.setWrapText(true); tt.setMaxWidth(380); c.setTooltip(tt); }

    private static Spinner<Double> spinner(double min, double max, double v, double step) {
        Spinner<Double> s = new Spinner<>(min, max, v, step);
        s.setEditable(true);
        s.setPrefWidth(72);
        s.focusedProperty().addListener((o, a, b) -> { if (!b) s.increment(0); });
        return s;
    }

    public PlotTheme theme() {
        Style s = style.getValue();
        if (s == Style.LIGHT) return PlotTheme.LIGHT;
        if (s == Style.DARK) return PlotTheme.DARK;
        return appDark.get() ? PlotTheme.DARK : PlotTheme.LIGHT;
    }

    public void setFigure(Figure f) {
        figure = f;
        applySize();
    }

    public Figure getFigure() { return figure; }

    private void applySize() {
        if (figure == null) { redraw(); return; }
        figure.widthCm = wSpin.getValue();
        figure.heightCm = hSpin.getValue();
        figure.fontPt = fSpin.getValue();
        figure.showHeader = header.isSelected();
        redraw();
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double cw = canvas.getWidth(), ch = canvas.getHeight();
        g.setTransform(1, 0, 0, 1, 0, 0);
        PlotTheme t = theme();
        g.setFill(t.dark() ? Color.web("#161616") : Color.web("#dcdcdc"));
        g.fillRect(0, 0, cw, ch);
        clearOverlay();
        if (figure == null || cw < 20 || ch < 20) return;
        double W = figure.widthPt(), H = figure.heightPt();
        double m = 12;
        scale = Math.min((cw - 2 * m) / W, (ch - 2 * m) / H);
        offX = (cw - W * scale) / 2;
        offY = (ch - H * scale) / 2;
        g.save();
        g.translate(offX, offY);
        g.scale(scale, scale);
        figure.draw(new FxGfx(g), t);
        g.restore();
    }

    private void clearOverlay() {
        GraphicsContext o = overlay.getGraphicsContext2D();
        o.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
    }

    private void hover(double mx, double my) {
        clearOverlay();
        if (figure == null) return;
        double x = (mx - offX) / scale, y = (my - offY) / scale;
        Panel p = figure.panelAt(x, y);
        if (p == null) { readout.setText(" "); return; }
        String s = p.readout(x, y);
        readout.setText(s == null ? " " : s);
        double[] r = p.plotRect();
        GraphicsContext o = overlay.getGraphicsContext2D();
        PlotTheme t = theme();
        o.setStroke(t.dark() ? Color.rgb(255, 255, 255, 0.55) : Color.rgb(0, 0, 0, 0.45));
        o.setLineWidth(1);
        o.setLineDashes(4, 3);
        double rx = offX + r[0] * scale, ry = offY + r[1] * scale, rw = r[2] * scale, rh = r[3] * scale;
        o.strokeLine(Math.round(mx) + 0.5, ry, Math.round(mx) + 0.5, ry + rh);
        o.strokeLine(rx, Math.round(my) + 0.5, rx + rw, Math.round(my) + 0.5);
    }

    public void copyImage() { copyImage(theme()); }

    public void copyImage(PlotTheme t) {
        if (figure == null) return;
        ClipboardContent cc = new ClipboardContent();
        cc.putImage(FigureExport.render(figure, t, dpi.getValue()));
        Clipboard.getSystemClipboard().setContent(cc);
        status.accept(String.format("Copied %s figure to the clipboard (%.1f × %.1f cm at %d dpi)",
                t.name().toLowerCase(), figure.widthCm, figure.heightCm, dpi.getValue()));
    }

    public void copySvg() {
        if (figure == null) return;
        ClipboardContent cc = new ClipboardContent();
        String svg = figure.toSvg(theme());
        cc.putString(svg);
        DataFormat svgFmt = DataFormat.lookupMimeType("image/svg+xml");
        if (svgFmt == null) svgFmt = new DataFormat("image/svg+xml");
        cc.put(svgFmt, svg);
        Clipboard.getSystemClipboard().setContent(cc);
        status.accept("Copied figure as SVG text");
    }

    public void export() {
        if (figure == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export figure");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG image (" + dpi.getValue() + " dpi)", "*.png"),
                new FileChooser.ExtensionFilter("SVG vector graphic", "*.svg"));
        fc.setInitialFileName(figure.name + ".png");
        if (lastDir != null) fc.setInitialDirectory(lastDir);
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        lastDir = f.getParentFile();
        try {
            String n = f.getName().toLowerCase();
            boolean svg = n.endsWith(".svg") || (!n.endsWith(".png") && fc.getSelectedExtensionFilter() != null
                    && fc.getSelectedExtensionFilter().getDescription().startsWith("SVG"));
            if (svg && !n.endsWith(".svg")) f = new File(f.getPath() + ".svg");
            if (!svg && !n.endsWith(".png")) f = new File(f.getPath() + ".png");
            if (svg) FigureExport.writeSvg(figure, theme(), f.toPath());
            else FigureExport.writePng(figure, theme(), dpi.getValue(), f.toPath());
            status.accept("Saved " + f.getAbsolutePath());
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save the figure:\n" + ex.getMessage()).showAndWait();
        }
    }
}
