# Sphere target strength for porpoise and dolphin clicks

Python scripts that calculate the echo target strength (TS) of small solid spheres
(acrylic/PMMA, polycarbonate or any material you like) in seawater. They use the exact
analytical solution for sound scattering from an elastic sphere (Faran 1951; Hickling 1962),
so they replace the COMSOL simulations in Kratzer et al. (2020) for the sphere case. A full
200 × 167 frequency–diameter map takes about 5 seconds.

## Install

    pip install -r requirements.txt

## Use

    python run_ts.py                                      # PMMA spheres, harbour porpoise click, 0-90 deg
    python run_ts.py --material polycarbonate --click dolphin
    python run_ts.py --click fakeporp4J2.wav              # any recorded/synthetic click (wav)
    python run_ts.py --band iqr                           # shade 25-75 % of angles instead of full range
    python run_ts.py --freq-map                           # also make the diameter x frequency map
    python run_ts.py --single 8.5                         # one sphere: TS vs click angle
    python run_ts.py --density 1200 --youngs 2.9 --poisson 0.39   # your own material
    python run_ts.py --temp 8 --sal 20                    # other water conditions
    python run_ts.py -h                                   # all options
    python validate.py                                    # checks vs the paper, a WC sphere and pistonimpulse.m

Each run writes to `output/`:

* `figure_<material>_<click>.png`: (A) heatmap (inferno) of click target strength by sphere
  diameter and off-axis angle of the click; (B) TS vs diameter for the on-axis click (line)
  with a translucent band for the off-axis clicks (0-90 deg), plus the continuous-wave TS at the
  click centre frequency (black dots, as in the paper; change with `--cw-freq`). Click maxima red, CW maxima open circles.
* `clicks_*.png`: the click waveform and spectrum at 0, 5, 10, 20, 45 and 90 deg, and the energy beam profile.
* `ts_vs_angle_*.png`: TS of the best spheres as a function of click angle.
* `ts_diameter_angle_*.csv`, `ts_vs_diameter_*.csv`: the numbers behind the plots.

## Clicks and beam model (from simclick.m / pistonimpulse.m)

| key | waveform | piston diameter |
|---|---|---|
| `porpoise` | simclick type 1: Hann-windowed 130 kHz sine, 70 µs | 7.5 cm |
| `dolphin` | simclick type 2: one cycle of 60 kHz, no window | 6 cm |
| `beluga` | simclick type 4: Hann-windowed 4 cycles of 50 kHz | 6.9 cm |
| `porpoise_gauss` | Gaussian 130 kHz, 16 kHz bandwidth | 7.5 cm |
| `something.wav` | your own click file | 7.5 cm (change with `--piston`, in cm) |

A click from off-axis angle θ is the on-axis click filtered by the piston's far-field
response, D(f,θ) = 2·J1(ka·sinθ)/(ka·sinθ). This is the frequency-domain form of the
Stepanishen impulse response that pistonimpulse.m convolves with; `validate.py` shows the two
agree within 0.3 dB at 2 MHz. (At pistonimpulse.m's default 500 kHz sample rate the impulse
response is only a few samples long, and for the porpoise it is off by up to about 4 dB at 10°.)

The click target strength is echo energy / incident click energy:
TS(d,θ) = 10·log10( Σ|S(f)D(f,θ)|²·|f_bs(f,d)|² / Σ|S(f)D(f,θ)|² ). It is a ratio, so the
beam's overall loss of level off-axis cancels: only the change in the click's spectrum matters.

## Built-in materials (edit `MATERIALS` in `sphere_ts.py` to add more)

| key | density | E | ν | note |
|---|---|---|---|---|
| `pmma` | 1180 | 4.8 GPa | 0.37 | Paper's high-frequency value. ν not given in the paper; 0.37 fits Table 2 best. |
| `pmma_static` | 1180 | 3.3 GPa | 0.37 | Literature static value |
| `pmma_speeds` | 1190 | c_L 2690, c_T 1340 m/s | | Ultrasonic sound speeds |
| `polycarbonate` | 1200 | 2.4 GPa | 0.37 | Datasheet static value |
| `polycarbonate_speeds` | 1200 | c_L 2270, c_T 940 m/s | | Ultrasonic speeds (implies dynamic E ≈ 2.9 GPa) |
| `tungsten_carbide`, `steel` | | | | Reference spheres |

As the paper found for PMMA, the high-frequency (dynamic) stiffness of plastics is higher than
datasheet values, and TS peaks move with it. **The polycarbonate values have not been checked
against measurements**, so measure the sound speeds of your actual stock if you can (or fit E to
a tank measurement, as Kratzer et al. did) and pass them with `--c-long/--c-shear`.

## Validation (`python validate.py`)

* A 38.1 mm tungsten carbide calibration sphere at 38 kHz gives −42.39 dB (accepted value −42.4 dB).
* Kratzer et al. Table 2 (13 species, best spheres ≤ 20 mm and ≤ 10 mm): optimal diameters match
  within 0.0–0.3 mm. TS differs by 0.41 dB rms (mean −0.02 dB, worst 1.9 dB, on the steep flank
  of a resonance at 64 kHz).

## Assumptions

* Solid, homogeneous, isotropic sphere in free field (no holes, cuts or net attachment).
* Clicks are energy-averaged; the paper's single-frequency TS is `sphere_ts.target_strength(f, d)` (and `--freq-map`).
* Lossless material by default (as in the paper). Real plastics have some internal damping, which
  lowers and broadens the resonance peaks. Add it with `--loss` (loss factor, e.g. 0.02–0.05 for PMMA).
* TS = 20·log10|f_bs| re 1 m, far-field backscatter.
