# pearlnetmodel

Target strength (TS) of small solid spheres (PMMA, polycarbonate, metals or your own material) for harbour porpoise,
dolphin and other odontocete echolocation clicks, on and off the beam axis.

The model uses the exact elastic-sphere scattering solution (Faran 1951; Hickling 1962), the physics that
Kratzer et al. (2020, *Front. Mar. Sci.* 7:539) solved with COMSOL. Clicks and beams follow `simclick.m` /
`pistonimpulse.m`: the animal is a circular piston, so a click from off-axis angle θ is the on-axis click filtered by
the piston's directivity. Click TS is echo energy / incident click energy.

| Folder | What it is |
|---|---|
| [`java/`](java) | **Sphere TS**, a JavaFX desktop app (Maven): species, material and sphere-size inputs with tooltips, publication-quality figures with copy/export and light/dark rendering, dark/light app theme, and Windows/macOS installers via jpackage. |
| [`python/`](python) | The original Python scripts (`sphere_ts.py` model, `run_ts.py` command line, `validate.py` checks). |

The two give the same results (the Java tests compare against the Python outputs to within 0.0001 dB).

## Quick start

    # Java app (JDK 17+, Maven 3.8+)
    cd java
    mvn javafx:run                 # run
    mvn -Pinstaller package        # build a .dmg (macOS) or .msi (Windows, needs WiX 3)

    # Python scripts
    cd python
    pip install -r requirements.txt
    python run_ts.py --material polycarbonate --click dolphin
    python validate.py

GitHub Actions (`.github/workflows/installers.yml`) builds the Windows and macOS installers when you push a tag such
as `v1.0.0` or run the workflow manually.

See [`java/README.md`](java/README.md) and [`python/README.md`](python/README.md) for details.
