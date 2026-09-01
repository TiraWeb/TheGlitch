package com.theglitch.glitchitems;

/**
 * Parsed stat rolls stored on a gear item (persisted in PDC).
 * Serialized as: rarity|type|resonance|attributes|damage|armor|speed|maxhp|boost|level
 * where attributes = "lifesteal:8;fire-aspect:2" (semicolon-separated, may be empty)
 * and level = upgrade level 0..max (armor-only; 9-field legacy items deserialize as 0).
 */
public final class GearRolls {

    public static final String SERIAL_KEY = "gear";

    public Rarity rarity;
    public GearType type;
    public Resonance resonance;
    public String attributes = "";
    public int damage;
    public int armor;
    public int speed;
    public int maxhp;
    public int boost;
    public int level;
    public int starsPrimary;
    public int starsSpeed;
    public int starsHp;

    public String serialize() {
        return rarity.getId() + "|" + type.name() + "|" + resonance.name() + "|"
                + attributes + "|"
                + damage + "|" + armor + "|" + speed + "|" + maxhp + "|" + boost
                + "|" + level;
    }

    public static GearRolls deserialize(String data) {
        if (data == null) return null;
        String[] parts = data.split("\\|");
        if (parts.length < 9) return null;
        try {
            GearRolls rolls = new GearRolls();
            rolls.rarity = Rarity.fromId(parts[0]);
            rolls.type = GearType.valueOf(parts[1]);
            rolls.resonance = Resonance.valueOf(parts[2]);
            rolls.attributes = parts[3];
            rolls.damage = Integer.parseInt(parts[4]);
            rolls.armor = Integer.parseInt(parts[5]);
            rolls.speed = Integer.parseInt(parts[6]);
            rolls.maxhp = Integer.parseInt(parts[7]);
            rolls.boost = Integer.parseInt(parts[8]);
            rolls.level = 0;
            if (parts.length >= 10) {
                try {
                    rolls.level = Integer.parseInt(parts[9]);
                } catch (NumberFormatException ignored) {
                    rolls.level = 0;
                }
            }
            // Rarity.fromId returns null for unknown ids (it does not throw) —
            // treat that as corrupt data so callers get null instead of a
            // GearRolls whose rarity access NPEs.
            if (rolls.rarity == null) return null;
            return rolls;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
