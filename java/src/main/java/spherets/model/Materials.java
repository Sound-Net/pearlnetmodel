package spherets.model;

import java.util.List;

/** Built-in material presets (same values as the Python sphere_ts.py, plus a few metals). */
public final class Materials {
    private Materials() {}

    public static final List<Material> PRESETS = List.of(
            new Material("PMMA / acrylic (E = 4.8 GPa, high-frequency)", 1180, 4.8e9, 0.37, 0,
                    "Kratzer et al. 2020 high-frequency value. ν is not given in the paper; 0.37 fits their Table 2 best."),
            new Material("PMMA / acrylic (E = 3.3 GPa, static)", 1180, 3.3e9, 0.37, 0,
                    "Literature static (datasheet) value."),
            Material.fromSpeeds("PMMA / acrylic (ultrasonic speeds)", 1190, 2690, 1340, 0,
                    "Measured ultrasonic sound speeds c_L = 2690, c_T = 1340 m/s."),
            new Material("Polycarbonate (E = 2.4 GPa, static)", 1200, 2.4e9, 0.37, 0,
                    "Typical datasheet values. Not checked against tank measurements."),
            Material.fromSpeeds("Polycarbonate (ultrasonic speeds)", 1200, 2270, 940, 0,
                    "Ultrasonic sound speeds c_L = 2270, c_T = 940 m/s (implies dynamic E ≈ 2.9 GPa). Not checked against tank measurements."),
            Material.fromSpeeds("Tungsten carbide (6% Co)", 14900, 6853, 4171, 0,
                    "Standard echosounder calibration sphere material."),
            Material.fromSpeeds("Copper (calibration grade)", 8947, 4760, 2288.5, 0,
                    "Standard echosounder calibration sphere material."),
            new Material("Stainless steel", 7850, 200e9, 0.29, 0, "Reference sphere."),
            Material.fromSpeeds("Aluminium", 2700, 6320, 3130, 0, "Typical ultrasonic sound speeds.")
    );

    public static Material byPrefix(String prefix) {
        return PRESETS.stream().filter(m -> m.name().toLowerCase().startsWith(prefix.toLowerCase()))
                .findFirst().orElseThrow();
    }
}
