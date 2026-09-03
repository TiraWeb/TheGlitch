package com.theglitch.glitchitems;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Resonance {
    AEGIS("Aegis", "<gold>"),
    VEIL("Veil", "<aqua>"),
    BLOOM("Bloom", "<green>"),
    WARD("Ward", "<red>"),
    HOLLOW("Hollow", "<light_purple>");

    private final String label;
    private final String colorTag;

    /** Lower-cased enum name and label -> resonance, so {@link #fromId(String)} avoids scans. */
    private static final Map<String, Resonance> BY_ID = new HashMap<>();

    static {
        for (Resonance resonance : values()) {
            BY_ID.put(resonance.name().toLowerCase(Locale.ROOT), resonance);
            BY_ID.putIfAbsent(resonance.label.toLowerCase(Locale.ROOT), resonance);
        }
    }

    Resonance(String label, String colorTag) {
        this.label = label;
        this.colorTag = colorTag;
    }

    public String getLabel() {
        return label;
    }

    public String getColorTag() {
        return colorTag;
    }

    public static Resonance fromId(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
