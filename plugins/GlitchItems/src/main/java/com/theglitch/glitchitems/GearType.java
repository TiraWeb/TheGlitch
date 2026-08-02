package com.theglitch.glitchitems;

import java.util.concurrent.ThreadLocalRandom;

public enum GearType {
    BLADE("Blade"),
    GREATBLADE("Greatblade"),
    ARCANE_STAFF("Arcane Staff"),
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
        return this == BLADE || this == GREATBLADE || this == ARCANE_STAFF;
    }

    public String getMaterialKey() {
        return name().toLowerCase();
    }

    public static GearType randomWeapon() {
        GearType[] weapons = {BLADE, GREATBLADE, ARCANE_STAFF};
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
