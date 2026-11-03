package spherets.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

/**
 * Exact (normal-mode) solution for plane-wave scattering from a solid elastic sphere
 * in a fluid (Faran 1951, JASA 23:405; Hickling 1962, JASA 34:1582), and the click /
 * piston-beam averaging used by sphere_ts.py.
 */
public final class SphereModel {
    private SphereModel() {}

    /**
     * Complex backscattering amplitude f_bs (m) at frequency f (Hz) for a sphere of radius a (m).
     * TS = 20 log10 |f_bs|. Time convention exp(-i w t).
     */
    public static Complex backscatterAmplitude(double f, double a, Material mat, Water water) {
        return backscatterAmplitude(f, a, mat.density(), mat.lame(), mat.speeds(), water);
    }

    static Complex backscatterAmplitude(double f, double a, double rho1, Complex[] lameMu, Complex[] speeds, Water water) {
        double rho = water.density(), c = water.soundSpeed();
        Complex lam = lameMu[0], mu = lameMu[1];
        Complex cl = speeds[0], ct = speeds[1];
        double w = 2 * Math.PI * f;
        double k = w / c;
        Complex kl = Complex.of(w).div(cl), kt = Complex.of(w).div(ct);
        double x = k * a;
        Complex xl = kl.mul(a), xt = kt.mul(a);
        int nmax = (int) Math.ceil(Math.max(x, Math.max(xl.re, xt.re))) + 12;

        Complex[] jxA = SpecialFunctions.sphJ(nmax + 1, Complex.of(x));
        double[] jx = new double[nmax + 2];
        for (int n = 0; n <= nmax + 1; n++) jx[n] = jxA[n].re;
        double[] djx = SpecialFunctions.derivative(jx, x, nmax);
        double[] yx = SpecialFunctions.sphY(nmax + 1, x);
        double[] dyx = SpecialFunctions.derivative(yx, x, nmax);
        Complex[] jl = SpecialFunctions.sphJ(nmax + 1, xl);
        Complex[] djl = SpecialFunctions.derivative(jl, xl, nmax);
        Complex[] jt = SpecialFunctions.sphJ(nmax + 1, xt);
        Complex[] djt = SpecialFunctions.derivative(jt, xt, nmax);

        double g = k / (rho * w * w);
        Complex xlInv = xl.inv();
        Complex kl2 = kl.mul(kl), kt2 = kt.mul(kt);
        double a2 = a * a;
        Complex twoMu = mu.mul(2);
        Complex fbs = Complex.ZERO;
        Complex[][] m = new Complex[3][3];
        Complex[] rhs = new Complex[3];

        for (int n = 0; n <= nmax; n++) {
            double nn = n * (n + 1.0);
            Complex hx = new Complex(jx[n], yx[n]);
            Complex dhx = new Complex(djx[n], dyx[n]);
            Complex ddjl = djl[n].mul(xlInv).mul(-2).sub(Complex.ONE.sub(xlInv.mul(xlInv).mul(nn)).mul(jl[n]));

            Complex urB = kl.mul(djl[n]);
            Complex urC = jt[n].mul(nn / a);
            Complex utB = jl[n].div(a);
            Complex utC = jt[n].add(xt.mul(djt[n])).div(a);
            Complex durdrB = kl2.mul(ddjl);
            Complex durdrC = kt.mul(djt[n]).div(a).sub(jt[n].div(a2)).mul(nn);
            Complex dutdrB = kl.mul(djl[n]).div(a).sub(jl[n].div(a2));
            Complex dutdrC = Complex.of(nn / a2).sub(kt2).mul(jt[n]).sub(utC.div(a));
            Complex srrB = lam.mul(kl2).mul(jl[n]).neg().add(twoMu.mul(durdrB));
            Complex srrC = twoMu.mul(durdrC);
            Complex srtB = mu.mul(urB.div(a).add(dutdrB).sub(utB.div(a)));
            Complex srtC = mu.mul(urC.div(a).add(dutdrC).sub(utC.div(a)));

            Complex A;
            if (n == 0) {
                m[0][0] = dhx.mul(g); m[0][1] = urB.neg();
                m[1][0] = hx;         m[1][1] = srrB;
                rhs[0] = Complex.of(-g * djx[n]); rhs[1] = Complex.of(-jx[n]);
                A = solveFirst(m, rhs, 2);
            } else {
                m[0][0] = dhx.mul(g); m[0][1] = urB.neg(); m[0][2] = urC.neg();
                m[1][0] = hx;         m[1][1] = srrB;      m[1][2] = srrC;
                m[2][0] = Complex.ZERO; m[2][1] = srtB;    m[2][2] = srtC;
                rhs[0] = Complex.of(-g * djx[n]); rhs[1] = Complex.of(-jx[n]); rhs[2] = Complex.ZERO;
                A = solveFirst(m, rhs, 3);
            }
            if (!A.isFinite()) A = Complex.ZERO;
            double sgn = (n % 2 == 0) ? 1 : -1;
            fbs = fbs.add(A.mul((2 * n + 1) * sgn));
        }
        // fbs *= -i / k
        return new Complex(fbs.im / k, -fbs.re / k);
    }

    /** Solve m x = rhs (size n) with column scaling + partial pivoting; return x[0]. */
    private static Complex solveFirst(Complex[][] m0, Complex[] r0, int n) {
        double[] scale = new double[n];
        Complex[][] m = new Complex[n][n];
        Complex[] r = new Complex[n];
        for (int j = 0; j < n; j++) {
            double s = 0;
            for (int i = 0; i < n; i++) s = Math.max(s, m0[i][j].abs());
            scale[j] = s == 0 ? 1 : s;
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) m[i][j] = m0[i][j].div(scale[j]);
            r[i] = r0[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            double best = m[col][col].abs();
            for (int i = col + 1; i < n; i++) if (m[i][col].abs() > best) { best = m[i][col].abs(); piv = i; }
            if (piv != col) {
                Complex[] t = m[col]; m[col] = m[piv]; m[piv] = t;
                Complex tr = r[col]; r[col] = r[piv]; r[piv] = tr;
            }
            Complex p = m[col][col];
            for (int i = col + 1; i < n; i++) {
                Complex fct = m[i][col].div(p);
                for (int j = col; j < n; j++) m[i][j] = m[i][j].sub(fct.mul(m[col][j]));
                r[i] = r[i].sub(fct.mul(r[col]));
            }
        }
        Complex[] x = new Complex[n];
        for (int i = n - 1; i >= 0; i--) {
            Complex s = r[i];
            for (int j = i + 1; j < n; j++) s = s.sub(m[i][j].mul(x[j]));
            x[i] = s.div(m[i][i]);
        }
        return x[0].div(scale[0]);
    }

    /** Continuous-wave backscatter target strength (dB re 1 m^2). */
    public static double targetStrength(double f, double a, Material mat, Water water) {
        return 20 * Math.log10(backscatterAmplitude(f, a, mat, water).abs());
    }

    /** sigma_bs = |f_bs|^2 on a frequency grid (one radius). */
    public static double[] sigmaBs(double[] f, double a, Material mat, Water water) {
        Complex[] lm = mat.lame(), sp = mat.speeds();
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) out[i] = backscatterAmplitude(f[i], a, mat.density(), lm, sp, water).abs2();
        return out;
    }

    /** Far-field pressure directivity of a baffled circular piston (on-axis = 1). */
    public static double pistonDirectivity(double f, double thetaDeg, double radius, double c) {
        double x = 2 * Math.PI * f / c * radius * Math.sin(Math.toRadians(thetaDeg));
        if (Math.abs(x) <= 1e-9) return 1;
        return 2 * SpecialFunctions.besselJ1(x) / x;
    }

    /** Frequency grid used for click averaging: 1 kHz to min(fmax, fs/2) in steps of df. */
    public static double[] frequencyGrid(Click click, double fmaxHz, double dfHz) {
        double fmax = Math.min(fmaxHz, click.sampleRate() / 2);
        int n = (int) Math.ceil((fmax + dfHz / 2 - 1e3) / dfHz);
        double[] f = new double[n];
        for (int i = 0; i < n; i++) f[i] = 1e3 + i * dfHz;
        return f;
    }

    /**
     * Click (energy) target strength for clicks from off-axis angles:
     * TS(a, theta) = 10 log10( sum_f W |f_bs|^2 / sum_f W ),  W = |S(f)|^2 |D(f, theta)|^2.
     * Returns [nRadii][nAngles] in dB.
     */
    public static double[][] beamClickTS(double[] radii, double[] anglesDeg, Click click, Material mat, Water water,
                                         double fmaxHz, double dfHz, DoubleConsumer progress) {
        double[] f = frequencyGrid(click, fmaxHz, dfHz);
        double[] s2 = click.powerSpectrum(f);
        int na = anglesDeg.length, nf = f.length;
        double[][] wgt = new double[na][nf];
        double[] wsum = new double[na];
        for (int j = 0; j < na; j++) {
            for (int i = 0; i < nf; i++) {
                double d = pistonDirectivity(f[i], anglesDeg[j], click.pistonRadius(), water.soundSpeed());
                wgt[j][i] = s2[i] * d * d;
                wsum[j] += wgt[j][i];
            }
        }
        double[][] ts = new double[radii.length][na];
        AtomicInteger done = new AtomicInteger();
        IntStream.range(0, radii.length).parallel().forEach(r -> {
            double[] sig = sigmaBs(f, radii[r], mat, water);
            for (int j = 0; j < na; j++) {
                double acc = 0;
                for (int i = 0; i < nf; i++) acc += wgt[j][i] * sig[i];
                ts[r][j] = 10 * Math.log10(acc / wsum[j]);
            }
            if (progress != null) progress.accept(done.incrementAndGet() / (double) radii.length);
        });
        return ts;
    }

    /** Energy beam loss (dB) vs angle, as 'Total Energy' in pistonimpulse.m. */
    public static double[] beamLossDb(double[] anglesDeg, Click click, Water water, double fmaxHz) {
        double[] f = frequencyGrid(click, fmaxHz, 100);
        double[] s2 = click.powerSpectrum(f);
        double tot = 0;
        for (double v : s2) tot += v;
        double[] out = new double[anglesDeg.length];
        for (int j = 0; j < anglesDeg.length; j++) {
            double acc = 0;
            for (int i = 0; i < f.length; i++) {
                double d = pistonDirectivity(f[i], anglesDeg[j], click.pistonRadius(), water.soundSpeed());
                acc += s2[i] * d * d;
            }
            out[j] = 10 * Math.log10(acc / tot);
        }
        return out;
    }

    /** Off-axis click spectrum in dB re the on-axis peak, on grid f. */
    public static double[] offAxisSpectrumDb(double[] f, double[] s2onAxis, double s2max, double angleDeg,
                                             Click click, Water water) {
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            double d = pistonDirectivity(f[i], angleDeg, click.pistonRadius(), water.soundSpeed());
            out[i] = 10 * Math.log10(s2onAxis[i] * d * d / s2max + 1e-24);
        }
        return out;
    }

    /**
     * Time-domain click seen at an off-axis angle (frequency-domain filtering with the piston directivity).
     * Returns {t (s), x}.
     */
    public static double[][] offAxisWaveform(Click click, double angleDeg, Water water, double padS) {
        double[] s = click.waveform();
        double fs = click.sampleRate();
        int npad = (int) (padS * fs);
        int pre = npad / 4;
        int len = pre + s.length + npad;
        int nfft = Integer.highestOneBit(Math.max(len - 1, 1)) << 1;
        double[] re = new double[nfft], im = new double[nfft];
        System.arraycopy(s, 0, re, pre, s.length);
        FFT.transform(re, im, false);
        for (int k = 0; k < nfft; k++) {
            int kk = k <= nfft / 2 ? k : nfft - k;
            double fr = kk * fs / nfft;
            double d = pistonDirectivity(fr, angleDeg, click.pistonRadius(), water.soundSpeed());
            re[k] *= d; im[k] *= d;
        }
        FFT.transform(re, im, true);
        double[] t = new double[len], x = new double[len];
        for (int i = 0; i < len; i++) { t[i] = (i - pre) / fs; x[i] = re[i]; }
        return new double[][]{t, x};
    }
}
