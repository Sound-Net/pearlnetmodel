package spherets.ui;

import javafx.scene.control.TextField;

import java.util.Locale;

/** Text field holding a number, with range checking (invalid values are outlined in red). */
class NumberField extends TextField {
    private final double min, max;
    private final boolean optional;

    NumberField(double value, double min, double max) { this(value, min, max, false); }

    NumberField(double value, double min, double max, boolean optional) {
        this.min = min; this.max = max; this.optional = optional;
        setPrefColumnCount(7);
        getStyleClass().add("number-field");
        textProperty().addListener((o, a, b) -> pseudo());
        set(value);
    }

    static String fmt(double v) {
        if (Double.isNaN(v)) return "";
        String s = String.format(Locale.ROOT, "%.6g", v);
        if (s.contains("e")) return s;
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    void set(double v) { setText(fmt(v)); }

    /** NaN if empty (optional) or invalid. */
    double value() {
        String t = getText() == null ? "" : getText().trim().replace(',', '.');
        if (t.isEmpty()) return Double.NaN;
        try { return Double.parseDouble(t); } catch (NumberFormatException e) { return Double.NaN; }
    }

    boolean valid() {
        double v = value();
        if (Double.isNaN(v)) return optional && getText().trim().isEmpty();
        return v >= min && v <= max;
    }

    String rangeText() { return "between " + fmt(min) + " and " + fmt(max); }

    private void pseudo() {
        getStyleClass().remove("invalid");
        if (!valid()) getStyleClass().add("invalid");
    }
}
