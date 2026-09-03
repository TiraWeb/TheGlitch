package com.theglitch.glitchitems;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Rarity {
    COMMON(0, "common"),
    UNCOMMON(1, "uncommon"),
    RARE(2, "rare"),
    EPIC(3, "epic"),
    LEGENDARY(4, "legendary");

    private final int tier;
    private final String id;

    /** Lower-cased id -> rarity, so {@link #fromId(String)} avoids a linear scan per call. */
    private static final Map<String, Rarity> BY_ID = new HashMap<>();

    static {
        for (Rarity rarity : values()) {
            BY_ID.put(rarity.id.toLowerCase(Locale.ROOT), rarity);
        }
    }

    Rarity(int tier, String id) {
        this.tier = tier;
        this.id = id;
    }

    public int getTier() {
        return tier;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    public static Rarity fromId(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }
}
