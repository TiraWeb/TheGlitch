package com.theglitch.glitchitems;

public enum Rarity {
    COMMON(0, "common"),
    UNCOMMON(1, "uncommon"),
    RARE(2, "rare"),
    EPIC(3, "epic"),
    LEGENDARY(4, "legendary");

    private final int tier;
    private final String id;

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
        for (Rarity rarity : values()) {
            if (rarity.id.equalsIgnoreCase(id)) {
                return rarity;
            }
        }
        return null;
    }
}
