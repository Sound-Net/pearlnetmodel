package spherets.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads the first channel of a PCM (8/16/24/32-bit) or IEEE-float (32/64-bit) WAV file. */
public final class WavReader {
    private WavReader() {}

    public record Wav(double[] samples, double sampleRate) {}

    public static Wav read(Path p) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < 12 || b.getInt(0) != 0x46464952 || b.getInt(8) != 0x45564157)
            throw new IOException("Not a RIFF/WAVE file");
        int pos = 12, fmt = -1, ch = 0, bits = 0, sr = 0;
        while (pos + 8 <= b.limit()) {
            int id = b.getInt(pos), size = b.getInt(pos + 4);
            int body = pos + 8;
            if (id == 0x20746d66) { // "fmt "
                fmt = b.getShort(body) & 0xffff;
                ch = b.getShort(body + 2) & 0xffff;
                sr = b.getInt(body + 4);
                bits = b.getShort(body + 14) & 0xffff;
                if (fmt == 0xFFFE && size >= 26) fmt = b.getShort(body + 24) & 0xffff; // extensible
            } else if (id == 0x61746164) { // "data"
                if (fmt < 0) throw new IOException("WAV data before fmt chunk");
                int bps = bits / 8, frame = bps * ch;
                int len = Math.min(size, b.limit() - body);
                int n = len / frame;
                double[] x = new double[n];
                double max = 0;
                for (int i = 0; i < n; i++) {
                    int o = body + i * frame;
                    double v;
                    if (fmt == 3) v = bits == 32 ? b.getFloat(o) : b.getDouble(o);
                    else if (bits == 8) v = (b.get(o) & 0xff) - 128;
                    else if (bits == 16) v = b.getShort(o);
                    else if (bits == 24) v = ((b.get(o) & 0xff) | ((b.get(o + 1) & 0xff) << 8) | (b.get(o + 2) << 16));
                    else if (bits == 32) v = b.getInt(o);
                    else throw new IOException("Unsupported bit depth " + bits);
                    x[i] = v;
                    max = Math.max(max, Math.abs(v));
                }
                if (max > 0) for (int i = 0; i < n; i++) x[i] /= max;
                return new Wav(x, sr);
            }
            pos = body + size + (size & 1);
        }
        throw new IOException("No data chunk in WAV file");
    }
}
