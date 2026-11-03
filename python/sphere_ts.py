"""
sphere_ts.py - Target strength of solid elastic spheres in water.

Exact analytical (partial-wave / normal-mode) solution for plane-wave
scattering from a solid, homogeneous, isotropic elastic sphere immersed in
a fluid (Faran 1951, J. Acoust. Soc. Am. 23, 405; Hickling 1962, JASA 34,
1582). This is the same physics COMSOL solves numerically for the sphere
case in Kratzer et al. (2020), Front. Mar. Sci. 7:539.

Main functions
--------------
backscatter_amplitude(f_hz, diameter_m, material, water)   -> complex f_bs [m]
target_strength(f_hz, diameter_m, material, water)          -> TS [dB re 1 m^2]
click_target_strength(diameter_m, click, material, water)   -> spectrum-weighted TS

Materials are described by density + Young's modulus + Poisson's ratio,
or alternatively by density + longitudinal and shear sound speeds.
"""
from __future__ import annotations

from dataclasses import dataclass, field
import numpy as np
from scipy.special import spherical_jn, spherical_yn


# ---------------------------------------------------------------------------
# Material and medium definitions
# ---------------------------------------------------------------------------
@dataclass
class Material:
    """Isotropic elastic solid.

    Give either (youngs_modulus_pa, poisson_ratio) or (c_long, c_shear).
    loss_factor (optional) adds material damping as a complex modulus
    E* = E (1 + i*eta); 0 = lossless, which is what the paper assumes.
    """
    name: str
    density: float                      # kg/m^3
    youngs_modulus_pa: float | None = None
    poisson_ratio: float | None = None
    c_long: float | None = None         # m/s
    c_shear: float | None = None        # m/s
    loss_factor: float = 0.0
    note: str = ""

    def speeds(self) -> tuple[complex, complex]:
        """Return (complex) longitudinal and shear wave speeds in m/s."""
        if self.c_long is not None and self.c_shear is not None:
            cl, ct = complex(self.c_long), complex(self.c_shear)
            if self.loss_factor:
                cl *= np.sqrt(1 + 1j * self.loss_factor)
                ct *= np.sqrt(1 + 1j * self.loss_factor)
            return cl, ct
        if self.youngs_modulus_pa is None or self.poisson_ratio is None:
            raise ValueError(f"Material '{self.name}' needs (E, nu) or (c_long, c_shear)")
        E = self.youngs_modulus_pa * (1 + 1j * self.loss_factor)
        nu = self.poisson_ratio
        mu = E / (2 * (1 + nu))
        lam = E * nu / ((1 + nu) * (1 - 2 * nu))
        return np.sqrt((lam + 2 * mu) / self.density), np.sqrt(mu / self.density)

    def describe(self) -> str:
        cl, ct = self.speeds()
        s = f"{self.name}: rho={self.density:g} kg/m3"
        if self.youngs_modulus_pa is not None and self.c_long is None:
            s += f", E={self.youngs_modulus_pa/1e9:g} GPa, nu={self.poisson_ratio:g}"
        s += f"  (c_L={cl.real:.0f} m/s, c_T={ct.real:.0f} m/s)"
        if self.loss_factor:
            s += f", loss factor={self.loss_factor:g}"
        return s


@dataclass
class Water:
    density: float = 1000.0     # kg/m^3  (paper: 1000)
    sound_speed: float = 1500.0  # m/s     (paper: 1500)

    @staticmethod
    def from_ts(temperature_c: float, salinity_psu: float, depth_m: float = 0.0) -> "Water":
        """Seawater from temperature/salinity: Mackenzie (1981) sound speed,
        simple linear-ish density (UNESCO-like, good to ~0.1%)."""
        T, S, D = temperature_c, salinity_psu, depth_m
        c = (1448.96 + 4.591 * T - 5.304e-2 * T**2 + 2.374e-4 * T**3
             + 1.340 * (S - 35) + 1.630e-2 * D + 1.675e-7 * D**2
             - 1.025e-2 * T * (S - 35) - 7.139e-13 * T * D**3)
        rho_w = (999.842594 + 6.793952e-2 * T - 9.095290e-3 * T**2
                 + 1.001685e-4 * T**3 - 1.120083e-6 * T**4 + 6.536332e-9 * T**5)
        rho = rho_w + S * (0.824493 - 4.0899e-3 * T + 7.6438e-5 * T**2
                           - 8.2467e-7 * T**3 + 5.3875e-9 * T**4) \
            + S**1.5 * (-5.72466e-3 + 1.0227e-4 * T - 1.6546e-6 * T**2) + 4.8314e-4 * S**2
        return Water(density=float(rho), sound_speed=float(c))


# Built-in materials. Edit / add your own here or pass a Material object.
MATERIALS: dict[str, Material] = {
    # Kratzer et al. 2020 used E = 4.8 GPa ("adapted to high frequencies",
    # from their tank measurements) and 1180 kg/m3. Poisson's ratio is not
    # stated in the paper; 0.37 (typical PMMA range 0.35-0.40) gives the best
    # match to the paper's Table 2 (see validate.py).
    "pmma": Material("PMMA / acrylic (E=4.8 GPa, high-freq.)", 1180.0, 4.8e9, 0.37,
                     note="Kratzer et al. 2020 high-frequency value"),
    "pmma_static": Material("PMMA / acrylic (E=3.3 GPa, static)", 1180.0, 3.3e9, 0.37,
                            note="Literature static value (Abts 2016)"),
    # Ultrasonic sound speeds for PMMA (e.g. Selfridge 1985 / Kaye & Laby):
    "pmma_speeds": Material("PMMA / acrylic (ultrasonic speeds)", 1190.0,
                            c_long=2690.0, c_shear=1340.0,
                            note="Measured ultrasonic sound speeds"),
    # Polycarbonate: datasheet static E ~2.3-2.4 GPa, nu ~0.37-0.39.
    # Ultrasonic (dynamic) speeds are c_L~2270, c_T~900-950 m/s which imply a
    # dynamic E ~2.9 GPa - like PMMA, the high-frequency value is higher.
    "polycarbonate": Material("Polycarbonate (E=2.4 GPa, static)", 1200.0, 2.4e9, 0.37,
                              note="Typical datasheet values"),
    "polycarbonate_speeds": Material("Polycarbonate (ultrasonic speeds)", 1200.0,
                                     c_long=2270.0, c_shear=940.0,
                                     note="Ultrasonic sound speeds (dynamic)"),
    # Reference / validation spheres
    "tungsten_carbide": Material("Tungsten carbide (6% Co)", 14900.0,
                                 c_long=6853.0, c_shear=4171.0),
    "steel": Material("Stainless steel", 7850.0, 200e9, 0.29),
}


def get_material(m: str | Material) -> Material:
    if isinstance(m, Material):
        return m
    try:
        return MATERIALS[m.lower()]
    except KeyError:
        raise KeyError(f"Unknown material '{m}'. Built-in: {', '.join(MATERIALS)}")


# ---------------------------------------------------------------------------
# Core scattering solution
# ---------------------------------------------------------------------------
def _jn(n, x, d=False):
    return spherical_jn(n, x, derivative=d)


def _hn(n, x, d=False):
    return spherical_jn(n, x, derivative=d) + 1j * spherical_yn(n, x, derivative=d)


def backscatter_amplitude(f_hz, diameter_m, material="pmma", water: Water | None = None,
                          n_extra: int = 12):
    """Complex backscattering amplitude f_bs (metres) of a solid elastic sphere.

    f_hz and diameter_m may be scalars or arrays (they are broadcast together).
    TS = 20*log10(|f_bs|).

    Boundary conditions at r = a (time convention exp(-i w t)):
      1) normal displacement continuous,
      2) normal stress = -pressure,
      3) tangential stress = 0.
    For each mode n these give a 3x3 linear system for the scattered-wave
    coefficient A_n and the two internal (longitudinal, shear) coefficients.
    """
    mat = get_material(material)
    water = water or Water()
    f, d = np.broadcast_arrays(np.asarray(f_hz, float), np.asarray(diameter_m, float))
    shape = f.shape
    f = f.ravel()
    a = d.ravel() / 2.0

    rho, c = water.density, water.sound_speed
    rho1 = mat.density
    cl, ct = mat.speeds()
    mu = rho1 * ct**2
    lam = rho1 * cl**2 - 2 * mu

    w = 2 * np.pi * f
    k, kl, kt = w / c, w / cl, w / ct
    x, xl, xt = k * a, kl * a, kt * a

    nmax = int(np.ceil(np.max(np.abs(np.r_[x, xl.real, xt.real])))) + n_extra
    fbs = np.zeros(f.shape, complex)

    for n in range(nmax + 1):
        nn = n * (n + 1)
        jx, djx = _jn(n, x), _jn(n, x, True)
        hx, dhx = _hn(n, x), _hn(n, x, True)
        jl, djl = _jn(n, xl), _jn(n, xl, True)
        jt, djt = _jn(n, xt), _jn(n, xt, True)
        # second derivative from the spherical Bessel equation
        ddjl = -(2 / xl) * djl - (1 - nn / xl**2) * jl

        # --- solid field at r=a, per unit B (longitudinal) and C (shear) ---
        # u_r = B kl j'(xl) + C n(n+1) j(xt)/a
        ur_B = kl * djl
        ur_C = nn * jt / a
        # u_theta coefficient (multiplies dP_n/dtheta)
        ut_B = jl / a
        ut_C = (jt + xt * djt) / a
        # d u_r / dr
        durdr_B = kl**2 * ddjl
        durdr_C = nn * (kt * djt / a - jt / a**2)
        # d u_theta / dr
        dutdr_B = kl * djl / a - jl / a**2
        dutdr_C = (nn / a**2 - kt**2) * jt - ut_C / a
        # stresses
        srr_B = -lam * kl**2 * jl + 2 * mu * durdr_B
        srr_C = 2 * mu * durdr_C
        srt_B = mu * (ur_B / a + dutdr_B - ut_B / a)
        srt_C = mu * (ur_C / a + dutdr_C - ut_C / a)

        # --- fluid: p = j_n + A h_n ; u_r = (1/(rho w^2)) dp/dr ---
        g = k / (rho * w**2)
        if n == 0:
            M = np.empty(f.shape + (2, 2), complex)
            rhs = np.empty(f.shape + (2,), complex)
            M[:, 0, 0], M[:, 0, 1] = g * dhx, -ur_B
            M[:, 1, 0], M[:, 1, 1] = hx, srr_B
            rhs[:, 0], rhs[:, 1] = -g * djx, -jx
        else:
            M = np.empty(f.shape + (3, 3), complex)
            rhs = np.zeros(f.shape + (3,), complex)
            M[:, 0, 0], M[:, 0, 1], M[:, 0, 2] = g * dhx, -ur_B, -ur_C
            M[:, 1, 0], M[:, 1, 1], M[:, 1, 2] = hx, srr_B, srr_C
            M[:, 2, 0], M[:, 2, 1], M[:, 2, 2] = 0.0, srt_B, srt_C
            rhs[:, 0], rhs[:, 1] = -g * djx, -jx
        # column scaling for numerical conditioning
        scale = np.max(np.abs(M), axis=1, keepdims=True)
        scale[scale == 0] = 1.0
        sol = np.linalg.solve(M / scale, rhs[..., None])[..., 0]
        A = sol[:, 0] / scale[:, 0, 0]
        A = np.where(np.isfinite(A), A, 0.0)
        fbs += (2 * n + 1) * (-1) ** n * A

    fbs *= -1j / k
    return fbs.reshape(shape)


def target_strength(f_hz, diameter_m, material="pmma", water: Water | None = None):
    """Backscatter target strength (dB re 1 m^2) at single frequencies."""
    return 20 * np.log10(np.abs(backscatter_amplitude(f_hz, diameter_m, material, water)))


# ---------------------------------------------------------------------------
# Echolocation clicks and the piston beam model
# ---------------------------------------------------------------------------
# The clicks and beam model follow simclick.m / pistonimpulse.m
# (sim_piston_model): the animal's sound source is a circular piston, and the
# click seen at off-axis angle theta is the on-axis click filtered by the
# piston's far-field response. pistonimpulse.m does this in the time domain
# (convolution with the Stepanishen impulse response, normalised to unit
# sum); the identical result in the frequency domain is the classic
#     D(f, theta) = 2 J1(k a sin(theta)) / (k a sin(theta)),  k = 2 pi f / c
# which is what is used here (validate.py checks the two agree).
from scipy.special import j1 as _bessel_j1
from scipy.signal.windows import hann as _hann


def piston_directivity(f_hz, theta_deg, radius_m, c=1500.0):
    """Far-field pressure directivity of a baffled circular piston (on-axis = 1)."""
    f, th = np.broadcast_arrays(np.asarray(f_hz, float), np.asarray(theta_deg, float))
    x = 2 * np.pi * f / c * radius_m * np.sin(np.radians(th))
    out = np.ones(x.shape)
    nz = np.abs(x) > 1e-9
    out[nz] = 2 * _bessel_j1(x[nz]) / x[nz]
    return out


def piston_impulse_response(radius_m, theta_deg, sr, c=1500.0, R=1000.0):
    """Port of the impulse-response loop in pistonimpulse.m (unit-sum normalised)."""
    ar = np.radians(theta_deg)
    z, r, a = R * np.cos(ar), R * np.sin(ar), radius_m
    if ar == 0 or r < a:
        return np.array([1.0])
    t0 = np.sqrt(R**2 + a**2) / c
    tmin = np.sqrt(z**2 + (r - a) ** 2) / c - t0
    tmax = np.sqrt(z**2 + (r + a) ** 2) / c - t0
    nP = int(np.floor(tmax * sr) - np.ceil(tmin * sr)) + 1
    t = t0 + np.ceil(tmin * sr) / sr + np.arange(nP + 4) / sr
    tb = (c * t) ** 2 - z**2 + r**2 - a**2
    bb = 2 * r * np.sqrt(np.maximum((c * t) ** 2 - z**2, 0))
    with np.errstate(divide="ignore", invalid="ignore"):
        q = tb / bb
    h = np.where(np.abs(q) <= 1, c / np.pi * np.arccos(np.clip(q, -1, 1)), 0.0)
    return h / h.sum()


@dataclass
class Click:
    """An echolocation click: a waveform plus the piston radius of its beam.

    kind = "simclick": waveform from simclick.m sigType 1 (porpoise) / 2 (dolphin)
    kind = "wav"     : waveform read from a wav file (e.g. fakeporp4J2.wav)
    kind = "gauss"   : Gaussian-envelope tone, centroid_hz and bw3db_hz
    """
    name: str
    kind: str
    piston_radius: float               # m (simclick.m: a = diameter / 2)
    centroid_hz: float = 130e3          # nominal frequency (for single-f analysis)
    sig_type: int = 1
    duration_s: float = 70e-6
    bw3db_hz: float = 16e3
    wav_path: str = ""
    note: str = ""

    def waveform(self, sr=2e6):
        """Return (samples, sample_rate). Samples start at t = 0."""
        if self.kind == "simclick":
            if self.sig_type == 1:      # harbour porpoise: Hann-windowed 130 kHz, 70 us
                l = self.duration_s
                nB = int(np.floor(l * sr)) + 1
                tP = np.arange(nB) / sr
                return _hann(nB, sym=True) * np.sin(2 * np.pi * tP * self.centroid_hz), sr
            if self.sig_type == 2:      # dolphin: one cycle of 60 kHz, no window
                l = 1 / self.centroid_hz
                nB = int(np.floor(l * sr)) + 1
                tP = np.arange(nB) / sr
                return np.sin(2 * np.pi * tP * self.centroid_hz), sr
            if self.sig_type == 4:      # beluga: Hann-windowed 4 cycles of 50 kHz
                l = 4 / self.centroid_hz
                nB = int(np.floor(l * sr)) + 1
                tP = np.arange(nB) / sr
                return _hann(nB, sym=True) * np.sin(2 * np.pi * tP * self.centroid_hz), sr
            raise ValueError(f"simclick sig_type {self.sig_type} not implemented")
        if self.kind == "wav":
            from scipy.io import wavfile
            fs, x = wavfile.read(self.wav_path)
            x = x.astype(float)
            if x.ndim > 1:
                x = x[:, 0]
            return x / np.max(np.abs(x)), float(fs)
        if self.kind == "gauss":
            sf = self.bw3db_hz / (2 * np.sqrt(2 * np.log(2)))
            st_ = 1 / (2 * np.pi * np.sqrt(2) * sf)
            t = np.arange(-5 * st_, 5 * st_, 1 / sr)
            return np.exp(-0.5 * (t / st_) ** 2) * np.cos(2 * np.pi * self.centroid_hz * t), sr
        raise ValueError(self.kind)

    def spectrum(self, f_hz, sr=2e6):
        """Complex spectrum S(f) of the on-axis click (direct DTFT, any f grid)."""
        s, fs = self.waveform(sr)
        n = np.arange(len(s))
        return np.exp(-2j * np.pi * np.outer(f_hz, n) / fs) @ s

    def max_freq(self, sr=2e6):
        s, fs = self.waveform(sr)
        return fs / 2


CLICKS: dict[str, Click] = {
    "porpoise": Click("Harbour porpoise click (simclick type 1)", "simclick", 0.075 / 2, 130e3, 1,
                      note="Hann-windowed 130 kHz, 70 us; 7.5 cm piston"),
    "dolphin": Click("Dolphin click (simclick type 2)", "simclick", 0.06 / 2, 60e3, 2,
                     note="single 60 kHz cycle; 6 cm piston"),
    "beluga": Click("Beluga click (simclick type 4)", "simclick", 0.138 / 2 / 2, 50e3, 4,
                    note="4 cycles 50 kHz Hann; 13.8 cm/2 piston as in simclick.m"),
    "porpoise_gauss": Click("Porpoise NBHF click (Gaussian, 16 kHz BW)", "gauss", 0.075 / 2, 130e3,
                            bw3db_hz=16e3, note="Villadsgaard et al. 2007 bandwidth"),
}


def get_click(c: str | Click) -> Click:
    if isinstance(c, Click):
        return c
    if str(c).lower().endswith(".wav"):
        return Click(f"Recorded click ({c})", "wav", 0.075 / 2, 130e3, wav_path=str(c))
    try:
        return CLICKS[c.lower()]
    except KeyError:
        raise KeyError(f"Unknown click '{c}'. Built-in: {', '.join(CLICKS)} or a .wav path")


def click_frequency_grid(click, fmax_hz=250e3, df_hz=200.0, sr=2e6):
    ck = get_click(click)
    fmax = min(fmax_hz, ck.max_freq(sr))
    return np.arange(1e3, fmax + df_hz / 2, df_hz)


def beam_click_target_strength(diameter_m, angles_deg, click="porpoise", material="pmma",
                               water: Water | None = None, fmax_hz=250e3, df_hz=200.0,
                               sr=2e6, return_details=False):
    """Click (energy) target strength for clicks arriving from off-axis angles.

        W(f, theta)  = |S(f)|^2 |D(f, theta)|^2         (off-axis click energy spectrum)
        TS(d, theta) = 10 log10( sum_f W |f_bs(f, d)|^2  /  sum_f W )

    i.e. echo energy / incident click energy, as an energy detector would see
    it. Returns an array [n_diameters, n_angles] in dB.
    """
    ck = get_click(click)
    water = water or Water()
    d = np.atleast_1d(np.asarray(diameter_m, float))
    th = np.atleast_1d(np.asarray(angles_deg, float))
    fr = click_frequency_grid(ck, fmax_hz, df_hz, sr)
    S2 = np.abs(ck.spectrum(fr, sr)) ** 2
    D2 = piston_directivity(fr[None, :], th[:, None], ck.piston_radius, water.sound_speed) ** 2
    W = S2[None, :] * D2                                   # [n_ang, n_f]
    sig = np.vstack([np.abs(backscatter_amplitude(fr, di, material, water)) ** 2 for di in d])
    ts = 10 * np.log10((sig @ W.T) / W.sum(axis=1)[None, :])
    if return_details:
        return ts, dict(f=fr, W=W, sigma_bs=sig)
    return ts


def click_target_strength(diameter_m, click="porpoise", material="pmma", water=None, **kw):
    """On-axis click (energy) target strength."""
    out = beam_click_target_strength(diameter_m, [0.0], click, material, water, **kw)[:, 0]
    return out if np.ndim(diameter_m) else float(out[0])


def beam_loss_db(angles_deg, click="porpoise", water=None, sr=2e6, fmax_hz=250e3):
    """Energy beam loss vs angle (same as 'Total Energy' in pistonimpulse.m)."""
    ck = get_click(click)
    water = water or Water()
    fr = click_frequency_grid(ck, fmax_hz, 100.0, sr)
    S2 = np.abs(ck.spectrum(fr, sr)) ** 2
    D2 = piston_directivity(fr[None, :], np.atleast_1d(angles_deg)[:, None],
                            ck.piston_radius, water.sound_speed) ** 2
    return 10 * np.log10((S2 * D2).sum(axis=1) / S2.sum())


def offaxis_waveform(click="porpoise", angle_deg=0.0, water=None, sr=2e6, pad_s=300e-6):
    """Time-domain click seen at an off-axis angle (frequency-domain filtering)."""
    ck = get_click(click)
    water = water or Water()
    s, fs = ck.waveform(sr)
    npad = int(pad_s * fs)
    x = np.r_[np.zeros(npad // 4), s, np.zeros(npad)]
    N = len(x)
    fr = np.fft.rfftfreq(N, 1 / fs)
    X = np.fft.rfft(x) * piston_directivity(fr, angle_deg, ck.piston_radius, water.sound_speed)
    t = (np.arange(N) - npad // 4) / fs
    return t, np.fft.irfft(X, N)


def echo_waveform(diameter_m, click="porpoise", material="pmma", water=None, angle_deg=0.0,
                  sr=2e6, pad_s=600e-6):
    """Incident click (at angle) and its echo from the sphere, referenced to 1 m."""
    ck = get_click(click)
    water = water or Water()
    t, x = offaxis_waveform(ck, angle_deg, water, sr, pad_s)
    fs = 1 / (t[1] - t[0])
    N = len(x)
    fr = np.fft.rfftfreq(N, 1 / fs)
    X = np.fft.rfft(x)
    H = np.zeros_like(X)
    m = (fr > 1e3) & (fr <= min(250e3, fs / 2))
    # physics uses exp(-i w t); numpy's FFT kernel corresponds to exp(+i w t) -> conjugate
    H[m] = np.conj(backscatter_amplitude(fr[m], diameter_m, material, water))
    return t, x, np.fft.irfft(X * H, N)
