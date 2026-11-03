package spherets.model;

/**
 * An echolocation click: a waveform type plus the diameter of the piston that
 * models the animal's sound source (beam). Waveforms follow simclick.m.
 */
public record Click(Type type, double centroidHz, double durationS, double bandwidthHz,
                    double pistonDiameterM, double[] wavSamples, double wavSampleRate, String wavName) {

    public enum Type {
        HANN_TONE("Hann-windowed tone (simclick types 1 and 4)"),
        SINGLE_CYCLE("Single sine cycle, no window (simclick type 2)"),
        GAUSSIAN("Gaussian-envelope tone"),
        WAV("Recorded click (.wav file)");

        public final String label;
        Type(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    /** Sample rate used for synthetic clicks. */
    public static final double SYNTH_SR = 2e6;

    public static Click hann(double f0, double durS, double pistonDiamM) {
        return new Click(Type.HANN_TONE, f0, durS, 0, pistonDiamM, null, 0, null);
    }
    public static Click singleCycle(double f0, double pistonDiamM) {
        return new Click(Type.SINGLE_CYCLE, f0, 1 / f0, 0, pistonDiamM, null, 0, null);
    }
    public static Click gaussian(double f0, double bw, double pistonDiamM) {
        return new Click(Type.GAUSSIAN, f0, 0, bw, pistonDiamM, null, 0, null);
    }
    public static Click wav(double[] samples, double sr, String name, double f0, double pistonDiamM) {
        return new Click(Type.WAV, f0, samples.length / sr, 0, pistonDiamM, samples, sr, name);
    }

    public double pistonRadius() { return pistonDiameterM / 2; }

    public double sampleRate() { return type == Type.WAV ? wavSampleRate : SYNTH_SR; }

    /** On-axis waveform, starting at t = 0, sampled at {@link #sampleRate()}. */
    public double[] waveform() {
        double sr = sampleRate();
        switch (type) {
            case HANN_TONE: {
                int nB = (int) Math.floor(durationS * sr) + 1;
                double[] s = new double[nB];
                for (int n = 0; n < nB; n++) {
                    double w = nB > 1 ? 0.5 - 0.5 * Math.cos(2 * Math.PI * n / (nB - 1)) : 1;
                    s[n] = w * Math.sin(2 * Math.PI * n / sr * centroidHz);
                }
                return s;
            }
            case SINGLE_CYCLE: {
                int nB = (int) Math.floor(1 / centroidHz * sr) + 1;
                double[] s = new double[nB];
                for (int n = 0; n < nB; n++) s[n] = Math.sin(2 * Math.PI * n / sr * centroidHz);
                return s;
            }
            case GAUSSIAN: {
                double sf = bandwidthHz / (2 * Math.sqrt(2 * Math.log(2)));
                double st = 1 / (2 * Math.PI * Math.sqrt(2) * sf);
                int n = (int) Math.ceil(10 * st * sr);
                double[] s = new double[n];
                for (int i = 0; i < n; i++) {
                    double t = -5 * st + i / sr;
                    s[i] = Math.exp(-0.5 * (t / st) * (t / st)) * Math.cos(2 * Math.PI * centroidHz * t);
                }
                return s;
            }
            case WAV:
                return wavSamples.clone();
            default:
                throw new IllegalStateException();
        }
    }

    /** |S(f)|^2 of the on-axis click by direct DTFT on an arbitrary frequency grid. */
    public double[] powerSpectrum(double[] f) {
        double[] s = waveform();
        double sr = sampleRate();
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) {
            double w = -2 * Math.PI * f[i] / sr;
            // recursive phasor rotation (fast + accurate enough for <10^4 samples)
            double cr = Math.cos(w), ci = Math.sin(w), pr = 1, pi = 0, re = 0, im = 0;
            for (int n = 0; n < s.length; n++) {
                re += s[n] * pr; im += s[n] * pi;
                double t = pr * cr - pi * ci; pi = pr * ci + pi * cr; pr = t;
            }
            out[i] = re * re + im * im;
        }
        return out;
    }

    /** Spectral centroid (Hz) of the energy spectrum up to fmax - used for WAV clicks. */
    public static double spectralCentroid(double[] samples, double sr) {
        Click c = new Click(Type.WAV, 0, 0, 0, 0.075, samples, sr, "");
        double df = 200, fmax = sr / 2;
        int n = (int) ((fmax - 1e3) / df) + 1;
        double[] f = new double[n];
        for (int i = 0; i < n; i++) f[i] = 1e3 + i * df;
        double[] p = c.powerSpectrum(f);
        double a = 0, b = 0;
        for (int i = 0; i < n; i++) { a += f[i] * p[i]; b += p[i]; }
        return a / b;
    }

    public String describe() {
        switch (type) {
            case HANN_TONE: return String.format("Hann-windowed %.1f kHz tone, %.0f µs", centroidHz / 1e3, durationS * 1e6);
            case SINGLE_CYCLE: return String.format("single cycle of %.1f kHz", centroidHz / 1e3);
            case GAUSSIAN: return String.format("Gaussian %.1f kHz tone, %.1f kHz −3 dB bandwidth", centroidHz / 1e3, bandwidthHz / 1e3);
            case WAV: return String.format("recorded click %s (%.0f kHz sample rate)", wavName, wavSampleRate / 1e3);
            default: return "";
        }
    }
}
