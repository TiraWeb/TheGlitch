package com.theglitch.glitchraid.rank;

import org.bukkit.Material;

/**
 * Raider Rank tiers. {@code glyph} is the nametag/tab icon (Nexo glyphs
 * rank_t_* in theglitch.yml, art from scripts/gen-rank-icons.py);
 * {@code minRr} defaults are overridable via rank.tiers.<name> in config.yml.
 */
public enum RankTier {
    BRONZE("Bronze", 0, '', "#C47A40", Material.COPPER_INGOT),
    SILVER("Silver", 100, '', "#C6CEDA", Material.IRON_INGOT),
    GOLD("Gold", 250, '', "#F5C43C", Material.GOLD_INGOT),
    PLATINUM("Platinum", 450, '', "#A0E1E1", Material.PRISMARINE_CRYSTALS),
    DIAMOND("Diamond", 700, '', "#5ACDFA", Material.DIAMOND),
    GRANDMASTER("Grandmaster", 1000, '', "#EB4650", Material.NETHERITE_INGOT),
    CELESTIAL("Celestial", 1400, '', "#B996FF", Material.NETHER_STAR),
    ETERNITY("Eternity", 1900, '', "#FF6EC8", Material.END_CRYSTAL);

    private final String displayName;
    private final int defaultMin;
    private final char glyph;
    private final String color;
    private final Material icon;

    RankTier(String displayName, int defaultMin, char glyph, String color, Material icon) {
        this.displayName = displayName;
        this.defaultMin = defaultMin;
        this.glyph = glyph;
        this.color = color;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultMin() {
        return defaultMin;
    }

    public char glyph() {
        return glyph;
    }

    /** Hex colour for MiniMessage, e.g. {@code <#F5C43C>}. */
    public String color() {
        return color;
    }

    public Material icon() {
        return icon;
    }

    /** Coloured tier name in MiniMessage. */
    public String styled() {
        return "<" + color + ">" + displayName + "</" + color + ">";
    }

    public RankTier next() {
        RankTier[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }
}
