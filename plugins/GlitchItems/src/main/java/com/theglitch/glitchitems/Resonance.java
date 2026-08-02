package com.theglitch.glitchitems;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Resonance {
    AEGIS("Aegis", "<gold>"),
    VEIL("Veil", "<aqua>"),
    BLOOM("Bloom", "<green>"),
    WARD("Ward", "<red>"),
    HOLLOW("Hollow", "<light_purple>");

    private final String label;
    private final String colorTag;

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
        for (Resonance resonance : values()) {
            if (resonance.name().equalsIgnoreCase(id) || resonance.label.equalsIgnoreCase(id)) {
                return resonance;
            }
        }
        return null;
    }
}
