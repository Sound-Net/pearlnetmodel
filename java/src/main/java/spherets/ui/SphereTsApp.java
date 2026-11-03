package spherets.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.pixelduke.transit.Style;
import com.pixelduke.transit.TransitStyleClass;
import com.pixelduke.transit.TransitTheme;
import spherets.model.*;
import spherets.plot.FigureView;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

/** Main window: inputs on the left, figures and results on the right. */
public class SphereTsApp extends Application {

    public static void main(String[] args) { launch(args); }

    private static final String VERSION = "1.0.0";
    private final Preferences prefs = Preferences.userNodeForPackage(SphereTsApp.class);
    private final BooleanProperty dark = new SimpleBooleanProperty(true);
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sphere-ts-compute"); t.setDaemon(true); return t;
    });
    private Scene scene;
    private TransitTheme transit;
    private Stage stage;
    private boolean updating;

    // ---- species / click
    private final ComboBox<Species> species = new ComboBox<>();
    private final ComboBox<Click.Type> clickType = new ComboBox<>();
    private final NumberField centroid = new NumberField(130, 1, 1000);
    private final NumberField duration = new NumberField(70, 1, 100000);
    private final NumberField bandwidth = new NumberField(16, 0.1, 1000);
    private final NumberField piston = new NumberField(7.5, 0.1, 100);
    private final Button loadWav = new Button("Load WAV…");
    private final Label wavLabel = new Label("no file loaded");
    private final Label speciesNote = note();
    private WavReader.Wav wav;
    private String wavName;
    private final List<Node> durationRow = new ArrayList<>(), bwRow = new ArrayList<>(), wavRow = new ArrayList<>();

    // ---- material
    private static final Material CUSTOM = new Material("Custom (edited)", 0, 0, 0, 0, "Your own values.");
    private final ComboBox<Material> material = new ComboBox<>();
    private final NumberField density = new NumberField(1180, 1, 30000);
    private final NumberField youngs = new NumberField(4.8, 1e-4, 2000);
    private final NumberField poisson = new NumberField(0.37, -0.99, 0.4999);
    private final NumberField cLong = new NumberField(2682, 1, 20000);
    private final NumberField cShear = new NumberField(1218, 1, 20000);
    private final NumberField loss = new NumberField(0, 0, 1);
    private final Label materialNote = note();

    // ---- size
    private final ToggleButton radiusBtn = new ToggleButton("Radius"), diameterBtn = new ToggleButton("Diameter");
    private final NumberField sizeMin = new NumberField(1.7, 0.01, 1000);
    private final NumberField sizeMax = new NumberField(10, 0.01, 1000);
    private final NumberField sizeStep = new NumberField(0.05, 0.001, 100);
    private final NumberField smallLimit = new NumberField(5, 0.01, 1000);
    private final Label sizeCount = note();
    private final List<Label> sizeLabels = new ArrayList<>();

    // ---- water
    private final NumberField waterC = new NumberField(1500, 1300, 1700);
    private final NumberField waterRho = new NumberField(1000, 900, 1100);
    private final NumberField temp = new NumberField(10, -2, 40);
    private final NumberField sal = new NumberField(35, 0, 45);
    private final NumberField depth = new NumberField(0, 0, 11000);

    // ---- analysis
    private final NumberField angMin = new NumberField(0, 0, 90);
    private final NumberField angMax = new NumberField(90, 0, 90);
    private final NumberField angStep = new NumberField(1, 0.05, 90);
    private final ComboBox<Settings.Band> band = new ComboBox<>();
    private final NumberField cwFreq = new NumberField(Double.NaN, 0.1, 2000, true);
    private final NumberField fmax = new NumberField(250, 10, 2000);
    private final NumberField df = new NumberField(200, 10, 5000);

    // ---- output
    private final Button calc = new Button("Calculate");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("Ready");
    private final FigureView figTs = new FigureView(dark, 18, 7.5);
    private final FigureView figAngle = new FigureView(dark, 12, 8);
    private final FigureView figClicks = new FigureView(dark, 18, 6);
    private final FigureView figMap = new FigureView(dark, 14, 9);
    private final TextArea summary = new TextArea();
    private Results results;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        dark.set(prefs.getBoolean("dark", true));

        BorderPane root = new BorderPane();
        root.setTop(menuBar());
        SplitPane split = new SplitPane(inputs(), outputs());
        split.setDividerPositions(0.29);
        SplitPane.setResizableWithParent(split.getItems().get(0), false);
        root.setCenter(split);

        scene = new Scene(root, 1500, 900);
        root.getStyleClass().add(TransitStyleClass.BACKGROUND);
        transit = new TransitTheme(scene, transitStyle());
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        applyTheme();
        dark.addListener((o, a, b) -> { applyTheme(); prefs.putBoolean("dark", b); });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), this::calculate);

        stage.setTitle("Sphere TS – target strength of spheres for odontocete clicks");
        try { stage.getIcons().add(new Image(getClass().getResourceAsStream("icon.png"))); } catch (Exception ignored) {}
        stage.setScene(scene);
        stage.show();

        species.getSelectionModel().select(0);
        material.getSelectionModel().select(0);
        calculate();
    }

    @Override public void stop() { exec.shutdownNow(); }

    private Style transitStyle() { return dark.get() ? Style.DARK : Style.LIGHT; }

    /** Transit theme (user-agent stylesheet) for a dialog, plus our own additions. */
    private void themeDialog(Dialog<?> d) {
        DialogPane p = d.getDialogPane();
        if (p.getScene() != null) p.getScene().setUserAgentStylesheet(transitStyle().getStyleStylesheetURL());
        else p.getStylesheets().add(transitStyle().getStyleStylesheetURL());
        p.getStylesheets().addAll(scene.getStylesheets());
        p.getStyleClass().add(dark.get() ? "dark" : "light");
    }

    private void applyTheme() {
        transit.setStyle(transitStyle());
        Node r = scene.getRoot();
        r.getStyleClass().removeAll("dark", "light");
        r.getStyleClass().add(dark.get() ? "dark" : "light");
    }

    // ------------------------------------------------------------------ menus
    private MenuBar menuBar() {
        Menu file = new Menu("File");
        MenuItem csv = new MenuItem("Save results as CSV…");
        csv.setOnAction(e -> saveCsv());
        MenuItem all = new MenuItem("Export all figures…");
        all.setOnAction(e -> exportAll());
        MenuItem quit = new MenuItem("Quit");
        quit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.SHORTCUT_DOWN));
        quit.setOnAction(e -> Platform.exit());
        file.getItems().addAll(csv, all, new SeparatorMenuItem(), quit);

        Menu view = new Menu("View");
        CheckMenuItem dm = new CheckMenuItem("Dark mode");
        dm.selectedProperty().bindBidirectional(dark);
        dm.setAccelerator(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        view.getItems().add(dm);

        Menu help = new Menu("Help");
        MenuItem about = new MenuItem("About the model…");
        about.setOnAction(e -> about());
        help.getItems().add(about);
        MenuBar mb = new MenuBar(file, view, help);
        mb.setUseSystemMenuBar(true);
        return mb;
    }

    // ------------------------------------------------------------------ inputs
    private static Label note() {
        Label l = new Label();
        l.setWrapText(true);
        l.getStyleClass().add("note");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static void tip(String text, Control... cs) {
        Tooltip t = new Tooltip(text);
        t.setWrapText(true);
        t.setMaxWidth(380);
        t.setShowDuration(javafx.util.Duration.seconds(30));
        for (Control c : cs) c.setTooltip(t);
    }

    /** Adds "label  field  unit" to the grid; returns the row's nodes. */
    private static List<Node> row(GridPane g, String label, Control field, String unit, String tooltip) {
        int r = g.getRowCount();
        Label l = new Label(label);
        Label u = new Label(unit == null ? "" : unit);
        u.getStyleClass().add("unit");
        tip(tooltip, l, field, u);
        g.add(l, 0, r);
        g.add(field, 1, r);
        g.add(u, 2, r);
        if (field instanceof ComboBox) GridPane.setColumnSpan(field, 2);
        field.setMaxWidth(Double.MAX_VALUE);
        return List.of(l, field, u);
    }

    private static GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(105);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS); c1.setMinWidth(90);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setMinWidth(44);
        g.getColumnConstraints().addAll(c0, c1, c2);
        return g;
    }

    private static TitledPane section(String title, Node content, String tooltip) {
        TitledPane p = new TitledPane(title, content);
        p.setCollapsible(true);
        if (tooltip != null) { Tooltip t = new Tooltip(tooltip); t.setWrapText(true); t.setMaxWidth(380); p.setTooltip(t); }
        return p;
    }

    private static void show(List<Node> nodes, boolean v) { for (Node n : nodes) { n.setVisible(v); n.setManaged(v); } }

    private Node inputs() {
        VBox box = new VBox(8, speciesSection(), materialSection(), sizeSection(), waterSection(), analysisSection());
        box.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        calc.setDefaultButton(true);
        calc.getStyleClass().add("calc-button");
        calc.setMaxWidth(Double.MAX_VALUE);
        calc.setOnAction(e -> calculate());
        tip("Run the calculation with the values above (Ctrl/⌘ + Enter). A typical 167 × 91 size × angle grid takes a few seconds.", calc);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        status.setWrapText(true);
        status.getStyleClass().add("status");
        VBox bottom = new VBox(6, calc, progress, status);
        bottom.setPadding(new Insets(8));
        bottom.getStyleClass().add("calc-box");
        BorderPane bp = new BorderPane(sp);
        bp.setBottom(bottom);
        bp.setMinWidth(330);
        bp.setPrefWidth(430);
        return bp;
    }

    private Node speciesSection() {
        GridPane g = grid();
        species.getItems().setAll(Species.PRESETS);
        species.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Species s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.name());
                setTooltip(empty || s == null ? null : new Tooltip(s.group()));
            }
        });
        row(g, "Species", species, null,
                "Click and beam preset. The first four are the waveforms in simclick.m (piston beam model). The others use the centroid "
                        + "frequencies in Kratzer et al. (2020) Table 2 with a generic click shape and piston size: edit the click below if you have "
                        + "measured values.");
        clickType.getItems().setAll(Click.Type.values());
        row(g, "Click type", clickType, null,
                "Shape of the on-axis click. Hann-windowed tone: sine at the centre frequency under a Hann window of the given duration "
                        + "(simclick types 1 and 4). Single cycle: one unwindowed sine cycle (simclick type 2, broadband dolphin click). "
                        + "Gaussian: Gaussian-envelope tone with the given −3 dB bandwidth. WAV: your own recorded or simulated click.");
        row(g, "Centre frequency", centroid, "kHz",
                "Click centre (peak) frequency. Also used as the default continuous-wave frequency in plot B and the frequency map.");
        durationRow.addAll(row(g, "Duration", duration, "µs", "Length of the Hann window (whole click), in microseconds. "
                + "Harbour porpoise (simclick): 70 µs."));
        bwRow.addAll(row(g, "Bandwidth", bandwidth, "kHz", "−3 dB bandwidth of the Gaussian click. Narrow-band high-frequency (NBHF) "
                + "porpoise clicks: about 16 kHz at 130 kHz. Broadband delphinid clicks: roughly half the centre frequency or more."));
        // duration, bandwidth and WAV file share one grid row (only one is shown at a time)
        int shared = GridPane.getRowIndex(durationRow.get(0));
        for (Node n : bwRow) GridPane.setRowIndex(n, shared);
        HBox wb = new HBox(8, loadWav, wavLabel);
        wb.setAlignment(Pos.CENTER_LEFT);
        loadWav.setOnAction(e -> chooseWav());
        tip("Load a click recording (.wav, mono or first channel; PCM or float). The whole file is used as the click, so trim it to one "
                + "click with a little silence. Its sample rate sets the highest usable frequency.", loadWav);
        Label wl = new Label("Click file");
        g.add(wl, 0, shared);
        g.add(wb, 1, shared, 2, 1);
        wavRow.addAll(List.of(wl, wb));
        row(g, "Piston diameter", piston, "cm",
                "Diameter of the circular piston that models the animal's sound source (simclick.m / pistonimpulse.m). "
                        + "Larger pistons give narrower beams, so off-axis clicks lose their high frequencies faster. "
                        + "Harbour porpoise 7.5 cm, dolphin 6 cm.");
        g.add(speciesNote, 0, g.getRowCount(), 3, 1);

        species.valueProperty().addListener((o, a, s) -> { if (s != null) setSpecies(s); });
        clickType.valueProperty().addListener((o, a, t) -> updateClickRows());
        return section("Species & click", g, null);
    }

    private void setSpecies(Species s) {
        Click c = s.click();
        clickType.setValue(c.type());
        centroid.set(c.centroidHz() / 1e3);
        if (c.type() == Click.Type.HANN_TONE) duration.set(c.durationS() * 1e6);
        if (c.type() == Click.Type.GAUSSIAN) bandwidth.set(c.bandwidthHz() / 1e3);
        piston.set(c.pistonDiameterM() * 100);
        speciesNote.setText(s.note());
        updateClickRows();
    }

    private void updateClickRows() {
        Click.Type t = clickType.getValue();
        show(durationRow, t == Click.Type.HANN_TONE);
        show(bwRow, t == Click.Type.GAUSSIAN);
        show(wavRow, t == Click.Type.WAV);
    }

    private void chooseWav() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load click (.wav)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("WAV files", "*.wav", "*.WAV"));
        String last = prefs.get("wavDir", null);
        if (last != null && new File(last).isDirectory()) fc.setInitialDirectory(new File(last));
        File f = fc.showOpenDialog(stage);
        if (f == null) return;
        prefs.put("wavDir", f.getParent());
        try {
            wav = WavReader.read(f.toPath());
            wavName = f.getName();
            wavLabel.setText(String.format(Locale.ROOT, "%s (%d samples, %.0f kHz)", wavName, wav.samples().length, wav.sampleRate() / 1e3));
            double fc0 = Click.spectralCentroid(wav.samples(), wav.sampleRate());
            centroid.set(Math.round(fc0 / 100) / 10.0);
            clickType.setValue(Click.Type.WAV);
            status("Loaded " + wavName + String.format(Locale.ROOT, "; spectral centroid %.1f kHz", fc0 / 1e3));
        } catch (Exception ex) {
            error("Could not read the WAV file:\n" + ex.getMessage());
        }
    }

    private Node materialSection() {
        GridPane g = grid();
        material.getItems().setAll(Materials.PRESETS);
        material.getItems().add(CUSTOM);
        material.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Material m) { return m == null ? "" : m.name(); }
            @Override public Material fromString(String s) { return null; }
        });
        row(g, "Preset", material, null, "Default material properties. Pick one and then change any value below; "
                + "the preset changes to 'Custom (edited)'.");
        row(g, "Density ρ", density, "kg/m³", "Density of the sphere material.");
        row(g, "Young's modulus E", youngs, "GPa",
                "Stiffness. For plastics the high-frequency (dynamic) value is higher than datasheet (static) values, and the TS peaks "
                        + "move with it: Kratzer et al. (2020) fitted 4.8 GPa for PMMA against a static 3.3 GPa. Changing E or ν updates the sound speeds.");
        row(g, "Poisson's ratio ν", poisson, null,
                "Ratio of transverse to axial strain (0–0.5). Typical plastics 0.35–0.40, metals 0.28–0.34.");
        row(g, "Longitudinal speed c_L", cLong, "m/s",
                "Compressional (P-wave) sound speed in the material. Equivalent to setting E and ν: change the speeds here if you have "
                        + "ultrasonic measurements, and E and ν update.");
        row(g, "Shear speed c_T", cShear, "m/s", "Shear (S-wave) sound speed in the material. Must be less than c_L / √2 for a physical material.");
        row(g, "Loss factor η", loss, null,
                "Material damping as a complex modulus E(1 + iη). 0 = lossless (as in the paper). Real plastics are roughly 0.02–0.05; "
                        + "damping lowers and broadens the resonance peaks.");
        g.add(materialNote, 0, g.getRowCount(), 3, 1);

        material.valueProperty().addListener((o, a, m) -> { if (m != null && m != CUSTOM) setMaterial(m); });
        for (NumberField f : new NumberField[]{density, youngs, poisson})
            f.textProperty().addListener((o, a, b) -> { if (!updating) { markCustom(); syncSpeeds(); } });
        for (NumberField f : new NumberField[]{cLong, cShear})
            f.textProperty().addListener((o, a, b) -> { if (!updating) { markCustom(); syncModuli(); } });
        loss.textProperty().addListener((o, a, b) -> { if (!updating) markCustom(); });
        return section("Sphere material", g, null);
    }

    private void setMaterial(Material m) {
        updating = true;
        density.set(m.density());
        youngs.set(round(m.youngsModulusPa() / 1e9, 5));
        poisson.set(round(m.poissonRatio(), 5));
        cLong.set(Math.round(m.cLong() * 10) / 10.0);
        cShear.set(Math.round(m.cShear() * 10) / 10.0);
        loss.set(m.lossFactor());
        materialNote.setText(m.note());
        updating = false;
    }

    private static double round(double v, int sig) {
        if (v == 0) return 0;
        double s = Math.pow(10, sig - Math.ceil(Math.log10(Math.abs(v))));
        return Math.round(v * s) / s;
    }

    private void markCustom() {
        if (material.getValue() != CUSTOM) {
            updating = true;
            material.setValue(CUSTOM);
            materialNote.setText("Custom values.");
            updating = false;
        }
    }

    private void syncSpeeds() {
        double rho = density.value(), e = youngs.value() * 1e9, nu = poisson.value();
        if (!density.valid() || !youngs.valid() || !poisson.valid()) return;
        Material m = new Material("x", rho, e, nu, 0, "");
        updating = true;
        cLong.set(Math.round(m.cLong() * 10) / 10.0);
        cShear.set(Math.round(m.cShear() * 10) / 10.0);
        updating = false;
    }

    private void syncModuli() {
        double rho = density.value(), cl = cLong.value(), ct = cShear.value();
        if (!density.valid() || !cLong.valid() || !cShear.valid() || ct >= cl / Math.sqrt(2)) return;
        Material m = Material.fromSpeeds("x", rho, cl, ct, 0, "");
        updating = true;
        youngs.set(round(m.youngsModulusPa() / 1e9, 5));
        poisson.set(round(m.poissonRatio(), 5));
        updating = false;
    }

    private Node sizeSection() {
        GridPane g = grid();
        ToggleGroup tg = new ToggleGroup();
        radiusBtn.setToggleGroup(tg);
        diameterBtn.setToggleGroup(tg);
        radiusBtn.setSelected(true);
        HBox seg = new HBox(0, radiusBtn, diameterBtn);
        seg.getStyleClass().add("segmented");
        radiusBtn.setMaxWidth(Double.MAX_VALUE);
        diameterBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(radiusBtn, Priority.ALWAYS);
        HBox.setHgrow(diameterBtn, Priority.ALWAYS);
        tip("Enter and plot sphere sizes as radius or diameter. Switching converts the values. (Kratzer et al. and the Python scripts use diameter.)",
                radiusBtn, diameterBtn);
        Label l = new Label("Size given as");
        g.add(l, 0, 0);
        g.add(seg, 1, 0, 2, 1);
        sizeLabels.add(label(g, row(g, "Minimum radius", sizeMin, "mm", "Smallest sphere in the calculation.")));
        sizeLabels.add(label(g, row(g, "Maximum radius", sizeMax, "mm", "Largest sphere in the calculation. Also the limit for the 'best overall' sphere.")));
        sizeLabels.add(label(g, row(g, "Step", sizeStep, "mm", "Size step. Resonance peaks are narrow, so keep it small (0.05 mm radius / 0.1 mm diameter or less).")));
        sizeLabels.add(label(g, row(g, "Small-sphere limit", smallLimit, "mm",
                "The best sphere at or below this size is also marked (Kratzer et al. reported the best ≤ 20 mm and ≤ 10 mm diameter).")));
        g.add(sizeCount, 0, g.getRowCount(), 3, 1);
        tg.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { (a == null ? radiusBtn : (ToggleButton) a).setSelected(true); return; }
            double k = b == radiusBtn ? 0.5 : 2;
            for (NumberField f : new NumberField[]{sizeMin, sizeMax, sizeStep, smallLimit})
                if (f.valid()) f.set(round(f.value() * k, 6));
            updateSizeLabels();
        });
        for (NumberField f : new NumberField[]{sizeMin, sizeMax, sizeStep}) f.textProperty().addListener(o -> updateSizeLabels());
        updateSizeLabels();
        return section("Sphere size", g, null);
    }

    private static Label label(GridPane g, List<Node> row) { return (Label) row.get(0); }

    private void updateSizeLabels() {
        String w = radiusBtn.isSelected() ? "radius" : "diameter";
        sizeLabels.get(0).setText("Minimum " + w);
        sizeLabels.get(1).setText("Maximum " + w);
        if (sizeMin.valid() && sizeMax.valid() && sizeStep.valid() && sizeMax.value() > sizeMin.value()) {
            int n = (int) Math.ceil((sizeMax.value() + sizeStep.value() / 2 - sizeMin.value()) / sizeStep.value());
            double k = radiusBtn.isSelected() ? 2 : 0.5;
            sizeCount.setText(String.format(Locale.ROOT, "%d sizes (%s %s–%s mm)", n, radiusBtn.isSelected() ? "diameter" : "radius",
                    NumberField.fmt(sizeMin.value() * k), NumberField.fmt(sizeMax.value() * k)));
        } else sizeCount.setText("");
    }

    private Node waterSection() {
        GridPane g = grid();
        row(g, "Sound speed c", waterC, "m/s", "Sound speed in the water. Kratzer et al. (2020) used 1500 m/s.");
        row(g, "Density ρ_w", waterRho, "kg/m³", "Water density. Kratzer et al. (2020) used 1000 kg/m³.");
        Label sep = new Label("Or calculate from:");
        sep.getStyleClass().add("note");
        g.add(sep, 0, g.getRowCount(), 3, 1);
        row(g, "Temperature", temp, "°C", "Water temperature, for the sound speed and density calculation.");
        row(g, "Salinity", sal, "psu", "Salinity (35 for open ocean, ~20 for the Baltic proper, ~8–10 for inner Baltic).");
        row(g, "Depth", depth, "m", "Depth (only changes the sound speed).");
        Button set = new Button("Set c and ρ_w from T, S and depth");
        set.setMaxWidth(Double.MAX_VALUE);
        tip("Sound speed from Mackenzie (1981) and density from the UNESCO equation of state at the surface.", set);
        set.setOnAction(e -> {
            if (!temp.valid() || !sal.valid() || !depth.valid()) { error("Check temperature, salinity and depth."); return; }
            Water w = Water.fromTS(temp.value(), sal.value(), depth.value());
            waterC.set(Math.round(w.soundSpeed() * 10) / 10.0);
            waterRho.set(Math.round(w.density() * 10) / 10.0);
        });
        g.add(set, 0, g.getRowCount(), 3, 1);
        TitledPane p = section("Water", g, null);
        p.setExpanded(false);
        return p;
    }

    private Node analysisSection() {
        GridPane g = grid();
        row(g, "Min angle", angMin, "°", "Smallest off-axis angle of the click (0 = on the beam axis).");
        row(g, "Max angle", angMax, "°", "Largest off-axis angle (up to 90°). The shaded band in plot B covers clicks from this range of angles.");
        row(g, "Angle step", angStep, "°", "Angle resolution of the heatmap.");
        band.getItems().setAll(Settings.Band.values());
        band.setValue(Settings.Band.RANGE);
        row(g, "Off-axis band", band, null, "What the shaded band in plot B shows: the full min–max range of TS over all angles, "
                + "or the 10–90 % / 25–75 % spread (less affected by a few extreme angles).");
        row(g, "CW frequency", cwFreq, "kHz", "Frequency for the continuous-wave TS curve (black dots in plot B). Leave empty to use the "
                + "click centre frequency, as in Kratzer et al. (2020).");
        row(g, "Max frequency", fmax, "kHz", "Highest frequency included in the click spectrum (limited to half the sample rate).");
        row(g, "Frequency step", df, "Hz", "Frequency resolution of the click spectrum. 200 Hz resolves the resonances of spheres up to a few cm.");
        TitledPane p = section("Analysis", g, null);
        p.setExpanded(false);
        return p;
    }

    // ------------------------------------------------------------------ outputs
    private Node outputs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add(TransitStyleClass.UNDERLINE_TAB_PANE);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (FigureView v : List.of(figTs, figAngle, figClicks, figMap)) v.setStatusSink(this::status);
        Tab t1 = new Tab("Target strength", figTs);
        t1.setTooltip(new Tooltip("A: click TS by sphere size and off-axis angle. B: TS vs size for the on-axis click, the off-axis band, and the continuous-wave TS."));
        Tab t2 = new Tab("Best spheres vs angle", figAngle);
        t2.setTooltip(new Tooltip("TS of the best sphere overall and the best small sphere as a function of the click's off-axis angle."));
        Tab t3 = new Tab("Clicks & beam", figClicks);
        t3.setTooltip(new Tooltip("The click waveform and spectrum at several off-axis angles, and the energy beam profile of the piston."));
        Tab t4 = new Tab("Frequency map", figMap);
        t4.setTooltip(new Tooltip("Continuous-wave TS by sphere size and frequency (the single-frequency analysis of Kratzer et al. 2020)."));
        summary.setEditable(false);
        summary.getStyleClass().add("summary");
        Button saveCsv = new Button("Save CSV files…");
        saveCsv.setOnAction(e -> saveCsv());
        tip("Write the TS grid (size × angle) and the TS-vs-size curves as CSV files, in the same layout as the Python scripts.", saveCsv);
        Button copyTxt = new Button("Copy summary");
        copyTxt.setOnAction(e -> {
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(summary.getText());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            status("Summary copied");
        });
        HBox hb = new HBox(8, saveCsv, copyTxt);
        hb.setPadding(new Insets(8));
        BorderPane rp = new BorderPane(summary);
        rp.setTop(hb);
        Tab t5 = new Tab("Results", rp);
        tabs.getTabs().addAll(t1, t2, t3, t4, t5);
        return tabs;
    }

    // ------------------------------------------------------------------ calculation
    private Settings readSettings() {
        List<String> errs = new ArrayList<>();
        check(errs, "Centre frequency", centroid);
        check(errs, "Piston diameter", piston);
        Click.Type t = clickType.getValue();
        if (t == Click.Type.HANN_TONE) check(errs, "Duration", duration);
        if (t == Click.Type.GAUSSIAN) check(errs, "Bandwidth", bandwidth);
        if (t == Click.Type.WAV && wav == null) errs.add("Load a WAV file for the click (or choose another click type).");
        check(errs, "Density", density);
        check(errs, "Young's modulus", youngs);
        check(errs, "Poisson's ratio", poisson);
        check(errs, "Loss factor", loss);
        check(errs, "Minimum size", sizeMin);
        check(errs, "Maximum size", sizeMax);
        check(errs, "Size step", sizeStep);
        check(errs, "Small-sphere limit", smallLimit);
        check(errs, "Water sound speed", waterC);
        check(errs, "Water density", waterRho);
        check(errs, "Min angle", angMin);
        check(errs, "Max angle", angMax);
        check(errs, "Angle step", angStep);
        check(errs, "CW frequency", cwFreq);
        check(errs, "Max frequency", fmax);
        check(errs, "Frequency step", df);
        if (errs.isEmpty()) {
            if (sizeMax.value() <= sizeMin.value()) errs.add("Maximum size must be larger than the minimum size.");
            if (angMax.value() < angMin.value()) errs.add("Max angle must not be smaller than min angle.");
            double n = (sizeMax.value() - sizeMin.value()) / sizeStep.value();
            if (n > 5000) errs.add("Too many sizes (" + (int) n + "); increase the size step.");
        }
        if (!errs.isEmpty()) { error(String.join("\n", errs)); return null; }

        double pd = piston.value() / 100, f0 = centroid.value() * 1e3;
        Click click;
        switch (t) {
            case HANN_TONE: click = Click.hann(f0, duration.value() * 1e-6, pd); break;
            case SINGLE_CYCLE: click = Click.singleCycle(f0, pd); break;
            case GAUSSIAN: click = Click.gaussian(f0, bandwidth.value() * 1e3, pd); break;
            default: click = Click.wav(wav.samples(), wav.sampleRate(), wavName, f0, pd);
        }
        Material sel = material.getValue();
        String mname = sel == null || sel == CUSTOM ? "Custom material" : sel.name();
        Material m = new Material(mname, density.value(), youngs.value() * 1e9, poisson.value(), loss.value(), "");
        String sp = species.getValue() == null ? "Custom click" : species.getValue().name();
        if (t == Click.Type.WAV) sp = "Recorded click";
        return new Settings(sp, click, m, new Water(waterRho.value(), waterC.value()), radiusBtn.isSelected(),
                sizeMin.value(), sizeMax.value(), sizeStep.value(), smallLimit.value(),
                angMin.value(), angMax.value(), angStep.value(), band.getValue(),
                Double.isNaN(cwFreq.value()) ? 0 : cwFreq.value() * 1e3, fmax.value() * 1e3, df.value());
    }

    private static void check(List<String> errs, String name, NumberField f) {
        if (!f.valid()) errs.add(name + " must be a number " + f.rangeText() + ".");
    }

    private void calculate() {
        if (calc.isDisabled()) return;
        Settings s = readSettings();
        if (s == null) return;
        Task<Results> task = new Task<>() {
            @Override protected Results call() { return Results.compute(s, p -> updateProgress(p, 1)); }
        };
        progress.progressProperty().bind(task.progressProperty());
        progress.setVisible(true);
        calc.setDisable(true);
        status("Calculating…");
        task.setOnSucceeded(e -> {
            done();
            results = task.getValue();
            figTs.setFigure(Figures.targetStrength(results));
            figAngle.setFigure(Figures.tsVsAngle(results));
            figClicks.setFigure(Figures.clicks(results));
            figMap.setFigure(Figures.frequencyMap(results));
            summary.setText(results.summary());
            devShots();
            String w = s.sizeWord().toLowerCase(Locale.ROOT);
            status(String.format(Locale.ROOT, "Done in %.1f s. Best on-axis sphere: %s %.2f mm (%.1f dB); best ≤ %s mm: %.2f mm (%.1f dB).",
                    results.computeMillis / 1000.0, w, results.size[results.iBest], results.onAxis[results.iBest],
                    NumberField.fmt(s.smallLimitMm()), results.size[results.iSmall], results.onAxis[results.iSmall]));
        });
        task.setOnFailed(e -> {
            done();
            Throwable ex = task.getException();
            status("Calculation failed");
            error("Calculation failed:\n" + ex);
        });
        exec.submit(task);
    }

    /** Developer hook: -Dspherets.shots=DIR writes every figure (light + dark, PNG + SVG) and a window capture. */
    private void devShots() {
        String dir = System.getProperty("spherets.shots");
        if (dir == null) return;
        try {
            Path d = Path.of(dir);
            java.nio.file.Files.createDirectories(d);
            for (FigureView v : List.of(figTs, figAngle, figClicks, figMap)) {
                for (spherets.plot.PlotTheme t : List.of(spherets.plot.PlotTheme.LIGHT, spherets.plot.PlotTheme.DARK)) {
                    String n = v.getFigure().name + "_" + t.name().toLowerCase(Locale.ROOT);
                    spherets.plot.FigureExport.writePng(v.getFigure(), t, 200, d.resolve(n + ".png"));
                    spherets.plot.FigureExport.writeSvg(v.getFigure(), t, d.resolve(n + ".svg"));
                }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void done() {
        progress.progressProperty().unbind();
        progress.setVisible(false);
        calc.setDisable(false);
    }

    private void status(String s) {
        status.getStyleClass().remove("error");
        status.setText(s);
    }

    private void error(String msg) {
        status.setText(msg);
        if (!status.getStyleClass().contains("error")) status.getStyleClass().add("error");
        Alert a = new Alert(Alert.AlertType.WARNING, msg);
        a.setHeaderText(null);
        a.initOwner(stage);
        themeDialog(a);
        a.showAndWait();
    }

    private File chooseDir(String title) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle(title);
        String last = prefs.get("outDir", null);
        if (last != null && new File(last).isDirectory()) dc.setInitialDirectory(new File(last));
        File d = dc.showDialog(stage);
        if (d != null) prefs.put("outDir", d.getPath());
        return d;
    }

    private void saveCsv() {
        if (results == null) return;
        File d = chooseDir("Folder for the CSV files");
        if (d == null) return;
        String tag = figTs.getFigure().name.replaceFirst("^figure_", "");
        try {
            Path a = d.toPath().resolve("ts_" + results.s.sizeWord().toLowerCase(Locale.ROOT) + "_angle_" + tag + ".csv");
            Path b = d.toPath().resolve("ts_vs_" + results.s.sizeWord().toLowerCase(Locale.ROOT) + "_" + tag + ".csv");
            results.writeGridCsv(a);
            results.writeCurveCsv(b);
            status("Saved " + a.getFileName() + " and " + b.getFileName() + " in " + d);
        } catch (Exception ex) {
            error("Could not save: " + ex.getMessage());
        }
    }

    private void exportAll() {
        if (results == null) return;
        File d = chooseDir("Folder for the figures");
        if (d == null) return;
        try {
            for (FigureView v : List.of(figTs, figAngle, figClicks, figMap)) {
                spherets.plot.FigureExport.writePng(v.getFigure(), v.theme(), 300, d.toPath().resolve(v.getFigure().name + ".png"));
                spherets.plot.FigureExport.writeSvg(v.getFigure(), v.theme(), d.toPath().resolve(v.getFigure().name + ".svg"));
            }
            status("Saved 4 figures (PNG 300 dpi + SVG) in " + d);
        } catch (Exception ex) {
            error("Could not save: " + ex.getMessage());
        }
    }

    private void about() {
        String text = "Sphere TS " + VERSION + "\n\n"
                + "Target strength of solid elastic spheres in water, from the exact normal-mode solution (Faran 1951, "
                + "J. Acoust. Soc. Am. 23:405; Hickling 1962, JASA 34:1582). This is the same physics that Kratzer et al. (2020, "
                + "Front. Mar. Sci. 7:539) solved with COMSOL; their Table 2 optimal sizes are reproduced within 0.3 mm (0.4 dB rms).\n\n"
                + "Clicks: the animal is a circular piston (simclick.m / pistonimpulse.m), so a click from off-axis angle θ is the "
                + "on-axis click filtered by D(f, θ) = 2 J1(ka sinθ) / (ka sinθ).\n\n"
                + "Click target strength = echo energy / incident click energy:\n"
                + "TS(d, θ) = 10 log10( Σ |S(f) D(f, θ)|² |f_bs(f, d)|² / Σ |S(f) D(f, θ)|² ).\n\n"
                + "Assumptions: solid, homogeneous, isotropic sphere in the free field (no holes or attachments); far-field backscatter; "
                + "lossless unless a loss factor is set.";
        Alert a = new Alert(Alert.AlertType.INFORMATION, text);
        a.setTitle("About Sphere TS");
        a.setHeaderText("Sphere TS");
        a.initOwner(stage);
        a.getDialogPane().setPrefWidth(560);
        themeDialog(a);
        a.showAndWait();
    }
}
