package com.theglitch.glitchitems;

import java.util.concurrent.ThreadLocalRandom;

public enum GearType {
    BLADE("Blade"),
    GREATBLADE("Greatblade"),
    ARCANE_STAFF("Arcane Staff"),
    MISERS_MAW("Miser's Maw"),
    VEIL_TETHER("Veil Tether"),
    DREAM_EATER("The Glitch That Dreams"),
    HELMET("Helmet"),
    CHESTPLATE("Chestplate"),
    LEGGINGS("Leggings"),
    BOOTS("Boots");

    private final String label;

    GearType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isWeapon() {
        return this == BLADE || this == GREATBLADE || this == ARCANE_STAFF
                || this == MISERS_MAW || this == VEIL_TETHER || this == DREAM_EATER;
    }

    public String getMaterialKey() {
        return name().toLowerCase();
    }

    public static GearType randomWeapon() {
        // DREAM_EATER is chase-only (workbench ritual + admin) — never rolls from rifts.
        GearType[] weapons = {BLADE, GREATBLADE, ARCANE_STAFF, MISERS_MAW, VEIL_TETHER};
        return weapons[ThreadLocalRandom.current().nextInt(weapons.length)];
    }

    public static GearType randomArmor() {
        GearType[] armor = {HELMET, CHESTPLATE, LEGGINGS, BOOTS};
        return armor[ThreadLocalRandom.current().nextInt(armor.length)];
    }

    public static GearType fromId(String id) {
        if (id == null) return null;
        switch (id.toLowerCase()) {
            case "blade": return BLADE;
            case "greatblade": case "axe": return GREATBLADE;
            case "arcane_staff": case "staff": case "wand": return ARCANE_STAFF;
            case "misers_maw": case "misers": case "maw": case "miser": case "greed": return MISERS_MAW;
            case "veil_tether": case "tether": case "lure": case "pull": return VEIL_TETHER;
            case "dream_eater": case "dream": case "dreameater": case "glitch_that_dreams":
            case "hollow_maw": case "hollow_king": return DREAM_EATER;
            case "helmet": return HELMET;
            case "chestplate": return CHESTPLATE;
            case "leggings": return LEGGINGS;
            case "boots": return BOOTS;
            case "weapon": return randomWeapon();
            case "armor": return randomArmor();
            default: return null;
        }
    }
}
