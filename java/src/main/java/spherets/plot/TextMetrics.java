package spherets.plot;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.concurrent.ConcurrentHashMap;

/** Measures text with the same font used to draw it. */
final class TextMetrics {
    private TextMetrics() {}
    private static final ConcurrentHashMap<String, Double> CACHE = new ConcurrentHashMap<>();

    static Font font(double size, boolean bold) {
        return Font.font(Gfx.FONT_FAMILY, bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
    }

    static double width(String s, double size, boolean bold) {
        if (s == null || s.isEmpty()) return 0;
        String key = (bold ? "b" : "n") + size + "|" + s;
        return CACHE.computeIfAbsent(key, k -> {
            Text t = new Text(s);
            t.setFont(font(size, bold));
            return t.getLayoutBounds().getWidth();
        });
    }
}
