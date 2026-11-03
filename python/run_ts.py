#!/usr/bin/env python3
"""
run_ts.py - target strength of small spheres for on- and off-axis echolocation clicks.

The click and its beam follow simclick.m / pistonimpulse.m: the animal is a
circular piston, so a click arriving from off-axis angle theta (0-90 deg) is
the on-axis click low-pass filtered by the piston's beam pattern. For every
sphere diameter and every angle the script computes the click target
strength (echo energy / incident click energy).

Examples
--------
  python run_ts.py                                   # PMMA, harbour porpoise click
  python run_ts.py --material polycarbonate --click dolphin
  python run_ts.py --click fakeporp4J2.wav           # use a recorded/synthetic click wav
  python run_ts.py --band iqr                        # shade the 25-75 % range of angles instead of min-max
  python run_ts.py --freq-map                        # also make the old diameter x frequency map
  python run_ts.py --single 8.5                      # TS of one sphere vs angle
  python run_ts.py --density 1200 --youngs 2.9 --poisson 0.39   # your own material

Outputs go to ./output/ (PNG figures + CSV files).
"""
import argparse
import os
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

import sphere_ts as st


def parse():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--material", default="pmma", help=f"built-in material: {', '.join(st.MATERIALS)}")
    p.add_argument("--density", type=float, help="custom material density kg/m3")
    p.add_argument("--youngs", type=float, help="custom Young's modulus in GPa")
    p.add_argument("--poisson", type=float, help="custom Poisson ratio")
    p.add_argument("--c-long", type=float, help="custom longitudinal speed m/s (instead of E/nu)")
    p.add_argument("--c-shear", type=float, help="custom shear speed m/s (instead of E/nu)")
    p.add_argument("--loss", type=float, default=None, help="material loss factor (default 0)")
    p.add_argument("--water-c", type=float, default=1500.0, help="water sound speed m/s (default 1500)")
    p.add_argument("--water-rho", type=float, default=1000.0, help="water density kg/m3 (default 1000)")
    p.add_argument("--temp", type=float, help="water temperature C (overrides --water-c/--water-rho)")
    p.add_argument("--sal", type=float, default=35.0, help="salinity psu (used with --temp)")
    p.add_argument("--click", default="porpoise",
                   help=f"built-in click ({', '.join(st.CLICKS)}) or path to a .wav click")
    p.add_argument("--piston", type=float, help="override piston (aperture) DIAMETER in cm")
    p.add_argument("--amin", type=float, default=0.0, help="min off-axis angle deg")
    p.add_argument("--amax", type=float, default=90.0, help="max off-axis angle deg")
    p.add_argument("--astep", type=float, default=1.0, help="angle step deg")
    p.add_argument("--band", default="range", choices=["range", "iqr", "p10_90"],
                   help="shaded off-axis area: full min-max range (default), 25-75 %% or 10-90 %% of angles")
    p.add_argument("--dmin", type=float, default=3.4, help="min diameter mm")
    p.add_argument("--dmax", type=float, default=20.0, help="max diameter mm")
    p.add_argument("--dstep", type=float, default=0.1, help="diameter step mm")
    p.add_argument("--dsmall", type=float, default=10.0, help="'small sphere' limit mm for 2nd maximum")
    p.add_argument("--fmax", type=float, default=250.0, help="max frequency in click spectrum kHz")
    p.add_argument("--cw-freq", type=float, help="continuous-wave frequency for plot B in kHz (default: click centre frequency)")
    p.add_argument("--freq-map", action="store_true", help="also plot the diameter x frequency map")
    p.add_argument("--single", type=float, help="just print TS vs angle for one diameter (mm) and exit")
    p.add_argument("--out", default="output", help="output folder")
    return p.parse_args()


def build_material(a):
    if a.density is None and a.youngs is None and a.c_long is None:
        m = st.get_material(a.material)
        if a.loss is not None:
            m = st.Material(**{**m.__dict__, "loss_factor": a.loss})
        return m
    base = st.get_material(a.material)
    rho = a.density or base.density
    if a.c_long and a.c_shear:
        return st.Material("custom", rho, c_long=a.c_long, c_shear=a.c_shear, loss_factor=a.loss or 0)
    E = (a.youngs * 1e9) if a.youngs else base.youngs_modulus_pa
    nu = a.poisson if a.poisson is not None else (base.poisson_ratio or 0.37)
    return st.Material("custom", rho, E, nu, loss_factor=a.loss or 0)


def band_limits(ts, how):
    if how == "iqr":
        return np.percentile(ts, 25, axis=1), np.percentile(ts, 75, axis=1), "25–75 % of off-axis angles"
    if how == "p10_90":
        return np.percentile(ts, 10, axis=1), np.percentile(ts, 90, axis=1), "10–90 % of off-axis angles"
    return ts.min(axis=1), ts.max(axis=1), "range over off-axis angles"


def maxima(d, ts, dsmall):
    i = int(np.argmax(ts))
    j = int(np.argmax(np.where(d <= dsmall + 1e-9, ts, -np.inf)))
    return i, j


def main():
    a = parse()
    mat = build_material(a)
    water = st.Water.from_ts(a.temp, a.sal) if a.temp is not None else st.Water(a.water_rho, a.water_c)
    click = st.get_click(a.click)
    if a.piston:
        click = st.Click(**{**click.__dict__, "piston_radius": a.piston / 100 / 2})
    fmax = a.fmax * 1e3
    angles = np.arange(a.amin, a.amax + a.astep / 2, a.astep)

    print(mat.describe())
    print(f"Water: rho={water.density:.1f} kg/m3, c={water.sound_speed:.1f} m/s")
    print(f"Click: {click.name}; piston diameter {click.piston_radius*200:.1f} cm; "
          f"angles {angles[0]:g}-{angles[-1]:g} deg")

    if a.single:
        ts = st.beam_click_target_strength(a.single * 1e-3, angles, click, mat, water, fmax_hz=fmax)[0]
        for ang, t in zip(angles, ts):
            print(f"  d = {a.single:g} mm, angle {ang:5.1f} deg : click TS = {t:.2f} dB")
        return

    os.makedirs(a.out, exist_ok=True)
    ctag = os.path.splitext(os.path.basename(a.click))[0] if a.click.lower().endswith(".wav") else a.click
    tag = f"{a.material if mat.name != 'custom' else 'custom'}_{ctag}"
    d = np.round(np.arange(a.dmin, a.dmax + a.dstep / 2, a.dstep), 4)

    print(f"Computing {len(d)} diameters x {len(angles)} angles ...")
    TS = st.beam_click_target_strength(d * 1e-3, angles, click, mat, water, fmax_hz=fmax)
    on = TS[:, np.argmin(np.abs(angles))]
    lo, hi, band_lbl = band_limits(TS, a.band)
    f_cw = (a.cw_freq or click.centroid_hz / 1e3) * 1e3
    ts_cw = st.target_strength(f_cw, d * 1e-3, mat, water)

    np.savetxt(os.path.join(a.out, f"ts_diameter_angle_{tag}.csv"),
               np.vstack([np.r_[np.nan, angles], np.column_stack([d, TS])]), delimiter=",", fmt="%.4f",
               header="first row: off-axis angle deg; first column: diameter mm; values: click TS dB")
    np.savetxt(os.path.join(a.out, f"ts_vs_diameter_{tag}.csv"),
               np.column_stack([d, on, TS.mean(axis=1), lo, hi, ts_cw]), delimiter=",", fmt="%.4f",
               header=f"diameter_mm,TS_on_axis_dB,TS_mean_over_angles_dB,band_low_dB,band_high_dB,TS_CW_{f_cw/1e3:g}kHz_dB",
               comments="")

    i, j = maxima(d, on, a.dsmall)
    print(f"\nContinuous wave {f_cw/1e3:g} kHz: best <= {a.dmax:g} mm {d[np.argmax(ts_cw)]:.1f} mm "
          f"({ts_cw.max():.2f} dB); best <= {a.dsmall:g} mm "
          f"{d[maxima(d, ts_cw, a.dsmall)[1]]:.1f} mm ({ts_cw[maxima(d, ts_cw, a.dsmall)[1]]:.2f} dB)")
    print(f"\nOn-axis click:")
    print(f"  best <= {a.dmax:g} mm : {d[i]:.1f} mm, TS = {on[i]:.2f} dB  (off-axis {lo[i]:.1f} to {hi[i]:.1f} dB)")
    print(f"  best <= {a.dsmall:g} mm : {d[j]:.1f} mm, TS = {on[j]:.2f} dB  (off-axis {lo[j]:.1f} to {hi[j]:.1f} dB)")

    # ---------------- Figure 1: A heatmap (angle x diameter), B TS vs diameter
    fig, (axA, axB) = plt.subplots(1, 2, figsize=(13, 5.2), gridspec_kw=dict(width_ratios=[1, 1.15]))
    vmax = np.ceil(TS.max() / 5) * 5
    vmin = max(np.floor(TS.min() / 5) * 5, vmax - 70)
    im = axA.pcolormesh(angles, d, TS, cmap="inferno", vmin=vmin, vmax=vmax, shading="nearest", rasterized=True)
    cb = fig.colorbar(im, ax=axA, extend="min" if TS.min() < vmin else "neither")
    cb.set_label("Click target strength [dB]")
    axA.set_xlabel("Off-axis angle of click [°]")
    axA.set_ylabel("Diameter [mm]")
    axA.set_title("A", loc="left", fontweight="bold")

    blue = "#2b6cb0"
    axB.fill_between(d, lo, hi, color=blue, alpha=0.22, lw=0, label=f"off-axis clicks ({band_lbl}, {angles[0]:g}–{angles[-1]:g}°)")
    axB.plot(d, ts_cw, ".", color="k", ms=3, label=f"continuous wave, {f_cw/1e3:g} kHz")
    ic, jc = maxima(d, ts_cw, a.dsmall)
    axB.plot(d[[ic, jc]], ts_cw[[ic, jc]], "o", mfc="none", mec="k", ms=8, zorder=5,
             label="CW maxima (≤%g mm, ≤%g mm)" % (a.dmax, a.dsmall))
    axB.plot(d, on, "-", color=blue, lw=1.8, label="on-axis click")
    axB.plot(d[[i, j]], on[[i, j]], "o", color="#d62728", ms=8, zorder=5,
             label="on-axis maxima (≤%g mm, ≤%g mm)" % (a.dmax, a.dsmall))
    for k in (i, j):
        axB.annotate(f"{d[k]:.1f} mm\n{on[k]:.1f} dB", (d[k], on[k]), textcoords="offset points",
                     xytext=(0, -14), ha="center", va="top", fontsize=8, color="#d62728", bbox=dict(fc="white", ec="none", alpha=0.75, pad=1))
    for k in (ic, jc):
        right = d[k] > d[0] + 0.8 * (d[-1] - d[0])
        axB.annotate(f"{d[k]:.1f} mm\n{ts_cw[k]:.1f} dB", (d[k], ts_cw[k]), textcoords="offset points",
                     xytext=(-9 if right else 9, 0), ha="right" if right else "left", va="center", fontsize=8, bbox=dict(fc="white", ec="none", alpha=0.75, pad=1))
    axB.set_xlabel("Diameter [mm]")
    axB.set_ylabel("Click target strength [dB]")
    axB.set_ylim(min(-90, np.floor(min(lo.min(), ts_cw.min()) / 10) * 10),
                 max(-25, np.ceil(max(hi.max(), ts_cw.max()) / 5) * 5 + 8))
    axB.grid(True, color="0.9")
    axB.legend(loc="lower right", fontsize=8, frameon=False)
    axB.set_title("B", loc="left", fontweight="bold")
    fig.suptitle(f"{mat.describe()}\n{click.name}, piston Ø {click.piston_radius*200:.1f} cm; "
                 f"water c = {water.sound_speed:.0f} m/s, ρ = {water.density:.0f} kg/m³", fontsize=9)
    fig.tight_layout()
    fn = os.path.join(a.out, f"figure_{tag}.png")
    fig.savefig(fn, dpi=150)
    plt.close(fig)
    print(f"\nSaved {fn}")

    # ---------------- Figure 2: the clicks at different angles + beam loss
    show = [x for x in (0, 5, 10, 20, 45, 90) if angles[0] <= x <= angles[-1]]
    cols = plt.cm.inferno(np.linspace(0.05, 0.85, len(show)))
    fig, (ax1, ax2, ax3) = plt.subplots(1, 3, figsize=(15, 4))
    fr = st.click_frequency_grid(click, fmax, 200.0)
    S = st.get_click(click).spectrum(fr)
    ref = np.max(np.abs(S))
    for ang, cl in zip(show, cols):
        t, x = st.offaxis_waveform(click, ang, water)
        ax1.plot(t * 1e6, x, color=cl, lw=1, label=f"{ang}°")
        D = st.piston_directivity(fr, ang, click.piston_radius, water.sound_speed)
        ax2.plot(fr / 1e3, 20 * np.log10(np.abs(S * D) / ref + 1e-12), color=cl, lw=1.2, label=f"{ang}°")
    s0, fs0 = click.waveform()
    ax1.set_xlim(-20, len(s0) / fs0 * 1e6 + 60)
    ax1.set_xlabel("Time [µs]"); ax1.set_ylabel("Pressure (on-axis peak = 1)")
    ax1.set_title("Click at different off-axis angles"); ax1.legend(fontsize=8, frameon=False)
    ax2.set_ylim(-60, 3); ax2.set_xlabel("Frequency [kHz]"); ax2.set_ylabel("Spectrum [dB re on-axis peak]")
    ax2.grid(True, color="0.9"); ax2.set_title("Click spectra")
    bl = st.beam_loss_db(angles, click, water)
    ax3.plot(angles, bl, color="k")
    ax3.set_xlabel("Off-axis angle [°]"); ax3.set_ylabel("Energy beam loss [dB]")
    ax3.grid(True, color="0.9"); ax3.set_title("Beam profile (total energy)")
    fig.tight_layout()
    fn2 = os.path.join(a.out, f"clicks_{tag}.png")
    fig.savefig(fn2, dpi=150)
    plt.close(fig)
    print(f"Saved {fn2}")

    # ---------------- Figure 3: TS vs angle for the best spheres
    fig, ax = plt.subplots(figsize=(6.5, 4))
    for k, lbl in ((i, f"{d[i]:.1f} mm"), (j, f"{d[j]:.1f} mm")):
        ax.plot(angles, TS[k], lw=1.6, label=lbl)
    ax.set_xlabel("Off-axis angle of click [°]"); ax.set_ylabel("Click target strength [dB]")
    ax.grid(True, color="0.9"); ax.legend(frameon=False, title="Sphere diameter")
    ax.set_title(f"Best spheres: TS vs click angle ({mat.name})", fontsize=10)
    fig.tight_layout()
    fn3 = os.path.join(a.out, f"ts_vs_angle_{tag}.png")
    fig.savefig(fn3, dpi=150)
    plt.close(fig)
    print(f"Saved {fn3}")

    # ---------------- optional: diameter x frequency map (inferno)
    if a.freq_map:
        fk = np.arange(1, 201)
        TSf = st.target_strength(fk[None, :] * 1e3, d[:, None] * 1e-3, mat, water)
        fig, ax = plt.subplots(figsize=(6.5, 5))
        im = ax.pcolormesh(fk, d, TSf, cmap="inferno", vmin=-100, vmax=0, shading="nearest", rasterized=True)
        ax.axvline(click.centroid_hz / 1e3, color="w", ls=":", lw=1)
        fig.colorbar(im, ax=ax, extend="min").set_label("Target strength [dB]")
        ax.set_xlabel("Frequency [kHz]"); ax.set_ylabel("Diameter [mm]")
        fig.tight_layout()
        fn4 = os.path.join(a.out, f"freqmap_{tag}.png")
        fig.savefig(fn4, dpi=150)
        plt.close(fig)
        print(f"Saved {fn4}")
    print(f"CSV files in {a.out}/")


if __name__ == "__main__":
    main()
