package spherets.plot;

import javafx.scene.paint.Color;

/** Colours for light (publication) and dark rendering of the figures. */
public record PlotTheme(String name, boolean dark, Color bg, Color fg, Color muted, Color grid, Color primary,
                        Color highlight, Color cwDots, Color[] categorical, double bandAlpha,
                        double infernoLo, double infernoHi) {

    public static final PlotTheme LIGHT = new PlotTheme("Light", false,
            Color.WHITE, Color.web("#1a1a1a"), Color.web("#555555"), Color.web("#e6e6e6"),
            Color.web("#2b6cb0"), Color.web("#d62728"), Color.web("#1a1a1a"),
            new Color[]{Color.web("#2b6cb0"), Color.web("#dd6b20"), Color.web("#2f855a"), Color.web("#805ad5")},
            0.22, 0.05, 0.85);

    public static final PlotTheme DARK = new PlotTheme("Dark", true,
            Color.web("#17191e"), Color.web("#e6e6e6"), Color.web("#a3a7ae"), Color.web("#2e323a"),
            Color.web("#5ea3ff"), Color.web("#ff6b6b"), Color.web("#e6e6e6"),
            new Color[]{Color.web("#5ea3ff"), Color.web("#f6ad55"), Color.web("#68d391"), Color.web("#b794f4")},
            0.30, 0.30, 0.97);

    /** Colour for item i of n drawn from the inferno map (range adapted to the background). */
    public Color sequential(int i, int n) {
        double t = n <= 1 ? infernoLo : infernoLo + (infernoHi - infernoLo) * i / (n - 1.0);
        return Colormap.infernoColor(t);
    }
}
