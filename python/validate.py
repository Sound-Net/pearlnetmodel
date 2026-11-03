#!/usr/bin/env python3
"""validate.py - checks the elastic-sphere model against known values.

1. 38.1 mm tungsten-carbide calibration sphere at 38 kHz (standard
   echosounder calibration target, accepted TS = -42.4 dB).
2. Kratzer et al. (2020) Table 2: optimal PMMA sphere sizes and TS at each
   species' centroid frequency (COMSOL results, E = 4.8 GPa, 1180 kg/m3,
   water 1500 m/s and 1000 kg/m3).
"""
import numpy as np
import sphere_ts as st
from table2 import TABLE2

print("1) Tungsten carbide 38.1 mm calibration sphere, 38 kHz")
ts = st.target_strength(38e3, 0.0381, "tungsten_carbide", st.Water(1026, 1490))
print(f"   model {ts:.2f} dB   reference -42.4 dB\n")

print("2) Kratzer et al. 2020 Table 2 (PMMA, E=4.8 GPa) - COMSOL vs this model")
print(f"   {st.MATERIALS['pmma'].describe()}")
d = np.round(np.arange(3.4, 20.001, 0.1), 1)
hdr = f"{'species':30s}{'kHz':>6s} | {'paper <=20':>14s} {'model <=20':>14s} | {'paper <=10':>14s} {'model <=10':>14s}"
print(hdr); print("-" * len(hdr))
err = []
for sp, fk, d20, t20, d10, t10 in TABLE2:
    t = st.target_strength(round(fk) * 1e3, d * 1e-3, "pmma")
    i = np.argmax(t); j = np.argmax(np.where(d <= 10, t, -np.inf))
    err += [t[i] - t20, t[j] - t10]
    print(f"{sp:30s}{fk:6.1f} | {d20:5.1f} ({t20:6.2f}) {d[i]:5.1f} ({t[i]:6.2f}) |"
          f" {d10:5.1f} ({t10:6.2f}) {d[j]:5.1f} ({t[j]:6.2f})")
err = np.array(err)
print(f"\nTS difference model - paper: mean {err.mean():+.2f} dB, rms {np.sqrt((err**2).mean()):.2f} dB, "
      f"max {np.abs(err).max():.2f} dB")

print("\n3) Piston beam model: frequency-domain directivity vs time-domain impulse response")
print("   (pistonimpulse.m method, 2 MHz sample rate). Energy beam loss in dB:")
for name in ("porpoise", "dolphin"):
    ck = st.get_click(name)
    s, fs = ck.waveform(2e6)
    angs = [5, 10, 20, 45, 90]
    td = [10 * np.log10((np.convolve(s, st.piston_impulse_response(ck.piston_radius, a_, fs)) ** 2).sum()
                        / (s ** 2).sum()) for a_ in angs]
    fd = st.beam_loss_db(angs, ck, fmax_hz=fs / 2)
    print(f"   {name:9s} angles {angs}")
    print(f"     impulse response: {np.round(td, 2)}")
    print(f"     directivity     : {np.round(fd, 2)}")
