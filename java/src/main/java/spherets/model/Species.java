package spherets.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Click/beam presets for each species. */
public record Species(String name, String group, Click click, String note) {

    @Override public String toString() { return name; }

    public static final List<Species> PRESETS;

    static {
        List<Species> l = new ArrayList<>();
        l.add(new Species("Harbour porpoise (simclick type 1)", "simclick.m",
                Click.hann(130e3, 70e-6, 0.075),
                "simclick.m sigType 1: Hann-windowed 130 kHz sine, 70 µs; 7.5 cm piston."));
        l.add(new Species("Dolphin (simclick type 2)", "simclick.m",
                Click.singleCycle(60e3, 0.06),
                "simclick.m sigType 2: one cycle of 60 kHz, no window; 6 cm piston."));
        l.add(new Species("Beluga (simclick type 4)", "simclick.m",
                Click.hann(50e3, 4 / 50e3, 0.069),
                "simclick.m sigType 4: Hann-windowed 4 cycles of 50 kHz; 6.9 cm piston."));
        l.add(new Species("Harbour porpoise (Gaussian NBHF)", "simclick.m",
                Click.gaussian(130e3, 16e3, 0.075),
                "Gaussian 130 kHz click, 16 kHz −3 dB bandwidth (Villadsgaard et al. 2007); 7.5 cm piston."));

        // Kratzer et al. 2020 Table 2: centroid frequencies. The paper used single frequencies,
        // so the click shape and piston size here are generic assumptions: NBHF species get a
        // narrow-band click (Q = 8, 7.5 cm piston); broadband species a Q = 2 click (6 cm piston).
        Object[][] t2 = {
                {"Lissodelphis borealis", 18.2, false}, {"Delphinus delphis", 112.0, false},
                {"Phocoena phocoena", 130.0, true}, {"Lagenorhynchus obliquidens", 94.6, false},
                {"Phocoenoides dalli", 133.0, true}, {"Neophocaena phocaenoides", 125.0, true},
                {"Stenella coeruleoalba", 40.0, false}, {"Tursiops truncatus ponticus", 80.0, false},
                {"Lagenorhynchus obscurus", 73.8, false}, {"Phocoena sinus", 132.0, true},
                {"Platanista gangetica", 64.4, false}, {"Inia geoffrensis geoffrensis", 101.2, false},
                {"Cephalorhynchus hectori", 124.0, true}};
        for (Object[] r : t2) {
            String sp = (String) r[0];
            double f = (Double) r[1] * 1e3;
            boolean nbhf = (Boolean) r[2];
            Click c = nbhf ? Click.gaussian(f, f / 8, 0.075) : Click.gaussian(f, f / 2, 0.06);
            l.add(new Species(sp + String.format(" (%.4g kHz)", f / 1e3), "Kratzer et al. 2020 Table 2", c,
                    String.format("Centroid frequency %.4g kHz from Kratzer et al. 2020 Table 2. Click shape and piston size are generic "
                            + "assumptions (%s) - edit them if you have measured values.",
                            f / 1e3, nbhf ? "narrow-band high-frequency click, Q = 8, 7.5 cm piston"
                                    : "broadband click, Q = 2, 6 cm piston")));
        }
        PRESETS = Collections.unmodifiableList(l);
    }
}
