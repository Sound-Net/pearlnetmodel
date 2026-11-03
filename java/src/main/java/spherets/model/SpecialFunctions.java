package spherets.model;

/** Spherical Bessel functions (complex argument) and the cylindrical J1. */
public final class SpecialFunctions {
    private SpecialFunctions() {}

    /**
     * Spherical Bessel functions of the first kind j_0..j_nmax for complex z,
     * by Miller's backward recurrence (stable for all orders), normalised with
     * j_0 or j_1, whichever is larger.
     */
    public static Complex[] sphJ(int nmax, Complex z) {
        Complex[] out = new Complex[nmax + 1];
        double az = z.abs();
        if (az < 1e-12) {
            out[0] = Complex.ONE;
            for (int n = 1; n <= nmax; n++) out[n] = Complex.ZERO;
            return out;
        }
        int start = Math.max(nmax, (int) Math.ceil(az)) + 20 + (int) Math.ceil(Math.sqrt(40 * Math.max(az, 1)));
        Complex zi = z.inv();
        // f_{n-1} = (2n+1)/z f_n - f_{n+1}
        double fr1 = 0, fi1 = 0;         // f_{n+1}
        double fr = 1e-300, fi = 0;      // f_n  (n = start)
        double[] sr = new double[nmax + 1], si = new double[nmax + 1];
        for (int n = start; n >= 1; n--) {
            double c = 2 * n + 1;
            double ar = c * (zi.re * fr - zi.im * fi), ai = c * (zi.re * fi + zi.im * fr);
            double nr = ar - fr1, ni = ai - fi1;   // f_{n-1}
            fr1 = fr; fi1 = fi; fr = nr; fi = ni;
            if (n - 1 <= nmax) { sr[n - 1] = fr; si[n - 1] = fi; }
            if (n <= nmax) { sr[n] = fr1; si[n] = fi1; }
            double m = Math.abs(fr) + Math.abs(fi);
            if (m > 1e250) {           // rescale to avoid overflow
                double s = 1e-250;
                fr *= s; fi *= s; fr1 *= s; fi1 *= s;
                for (int k = Math.max(n - 1, 0); k <= nmax; k++) { sr[k] *= s; si[k] *= s; }
            }
        }
        // true j0, j1
        Complex sinz = z.sin(), cosz = z.cos();
        Complex j0, j1;
        if (az < 1e-4) {
            Complex z2 = z.mul(z);
            j0 = Complex.ONE.sub(z2.div(6.0));
            j1 = z.div(3.0).mul(Complex.ONE.sub(z2.div(10.0)));
        } else {
            j0 = sinz.mul(zi);
            j1 = sinz.mul(zi).sub(cosz).mul(zi);
        }
        Complex scale;
        if (j0.abs() >= j1.abs() || nmax < 1) {
            scale = j0.div(new Complex(sr[0], si[0]));
        } else {
            scale = j1.div(new Complex(sr[1], si[1]));
        }
        for (int n = 0; n <= nmax; n++) out[n] = new Complex(sr[n], si[n]).mul(scale);
        return out;
    }

    /** Spherical Bessel functions of the second kind y_0..y_nmax for real x > 0 (forward recurrence). */
    public static double[] sphY(int nmax, double x) {
        double[] y = new double[nmax + 1];
        y[0] = -Math.cos(x) / x;
        if (nmax >= 1) y[1] = -Math.cos(x) / (x * x) - Math.sin(x) / x;
        for (int n = 1; n < nmax; n++) y[n + 1] = (2 * n + 1) / x * y[n] - y[n - 1];
        return y;
    }

    /** Derivative of spherical Bessel functions from the array f_0..f_{N}: f_n' = f_{n-1} - (n+1)/z f_n. */
    public static Complex[] derivative(Complex[] f, Complex z, int nmax) {
        Complex[] d = new Complex[nmax + 1];
        Complex zi = z.inv();
        d[0] = f[1].neg();
        for (int n = 1; n <= nmax; n++) d[n] = f[n - 1].sub(f[n].mul(zi).mul(n + 1));
        return d;
    }

    public static double[] derivative(double[] f, double x, int nmax) {
        double[] d = new double[nmax + 1];
        d[0] = -f[1];
        for (int n = 1; n <= nmax; n++) d[n] = f[n - 1] - (n + 1) / x * f[n];
        return d;
    }

    /** Cylindrical Bessel J1(x), real x (Numerical Recipes rational approximations, |err| < 1e-8). */
    public static double besselJ1(double x) {
        double ax = Math.abs(x);
        if (ax < 8.0) {
            double y = x * x;
            double a1 = x * (72362614232.0 + y * (-7895059235.0 + y * (242396853.1
                    + y * (-2972611.439 + y * (15704.48260 + y * (-30.16036606))))));
            double a2 = 144725228442.0 + y * (2300535178.0 + y * (18583304.74
                    + y * (99447.43394 + y * (376.9991397 + y))));
            return a1 / a2;
        }
        double z = 8.0 / ax, y = z * z, xx = ax - 2.356194491;
        double a1 = 1.0 + y * (0.183105e-2 + y * (-0.3516396496e-4 + y * (0.2457520174e-5 + y * (-0.240337019e-6))));
        double a2 = 0.04687499995 + y * (-0.2002690873e-3 + y * (0.8449199096e-5 + y * (-0.88228987e-6 + y * 0.105787412e-6)));
        double ans = Math.sqrt(0.636619772 / ax) * (Math.cos(xx) * a1 - z * Math.sin(xx) * a2);
        return x < 0 ? -ans : ans;
    }
}
