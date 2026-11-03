package spherets.model;

/** Minimal immutable complex number. */
public final class Complex {
    public static final Complex ZERO = new Complex(0, 0);
    public static final Complex ONE = new Complex(1, 0);
    public static final Complex I = new Complex(0, 1);

    public final double re, im;

    public Complex(double re, double im) { this.re = re; this.im = im; }
    public static Complex of(double re) { return new Complex(re, 0); }

    public Complex add(Complex o) { return new Complex(re + o.re, im + o.im); }
    public Complex sub(Complex o) { return new Complex(re - o.re, im - o.im); }
    public Complex mul(Complex o) { return new Complex(re * o.re - im * o.im, re * o.im + im * o.re); }
    public Complex mul(double s) { return new Complex(re * s, im * s); }
    public Complex add(double s) { return new Complex(re + s, im); }
    public Complex neg() { return new Complex(-re, -im); }
    public Complex conj() { return new Complex(re, -im); }

    public Complex div(Complex o) {
        // Smith's algorithm
        if (Math.abs(o.re) >= Math.abs(o.im)) {
            double r = o.im / o.re, den = o.re + o.im * r;
            return new Complex((re + im * r) / den, (im - re * r) / den);
        } else {
            double r = o.re / o.im, den = o.re * r + o.im;
            return new Complex((re * r + im) / den, (im * r - re) / den);
        }
    }
    public Complex div(double s) { return new Complex(re / s, im / s); }
    public Complex inv() { return ONE.div(this); }

    public double abs() { return Math.hypot(re, im); }
    public double abs2() { return re * re + im * im; }
    public boolean isFinite() { return Double.isFinite(re) && Double.isFinite(im); }

    /** Principal square root. */
    public Complex sqrt() {
        if (re == 0 && im == 0) return ZERO;
        double m = abs();
        double a = Math.sqrt(0.5 * (m + Math.abs(re)));
        if (re >= 0) return new Complex(a, im / (2 * a));
        return new Complex(Math.abs(im) / (2 * a), Math.copySign(a, im));
    }

    public Complex sin() {
        return new Complex(Math.sin(re) * Math.cosh(im), Math.cos(re) * Math.sinh(im));
    }
    public Complex cos() {
        return new Complex(Math.cos(re) * Math.cosh(im), -Math.sin(re) * Math.sinh(im));
    }

    @Override public String toString() { return re + (im >= 0 ? "+" : "") + im + "i"; }
}
