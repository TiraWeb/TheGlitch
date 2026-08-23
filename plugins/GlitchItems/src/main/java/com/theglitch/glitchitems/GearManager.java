package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GearManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchItems plugin;
    private final NamespacedKey gearKey;

    // Cached config — refreshed in reload()
    private final Map<String, Map<Rarity, int[]>> statRanges = new HashMap<>();
    private final Map<Rarity, Integer> identifyFees = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> sellValues = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> resonanceBoosts = new EnumMap<>(Rarity.class);
    private final Map<Rarity, String> rarityColors = new EnumMap<>(Rarity.class);
    private final Map<GearType, List<Material>> materialsCache = new EnumMap<>(GearType.class);
    private final Map<Rarity, Integer> weaponLifesteal = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> weaponFireAspect = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> armorDamageReduction = new EnumMap<>(Rarity.class);
    private int weaponResonanceBase = 25;
    private int armorReductionPerPiece = 10;
    private int armorReductionCap = 40;
    private int armorPointsReductionPerPoint = 2;
    private int armorPointsCap = 25;
    private int armorAttributeReductionCap = 30;

    public GearManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.gearKey = new NamespacedKey(plugin, GearRolls.SERIAL_KEY);
        reload();
    }

    public void reload() {
        statRanges.clear();
        identifyFees.clear();
        sellValues.clear();
        resonanceBoosts.clear();
        rarityColors.clear();
        materialsCache.clear();
        weaponLifesteal.clear();
        weaponFireAspect.clear();
        armorDamageReduction.clear();

        ConfigurationSection sr = plugin.getConfig().getConfigurationSection("stat-ranges");
        if (sr != null) {
            for (String stat : sr.getKeys(false)) {
                ConfigurationSection sec = sr.getConfigurationSection(stat);
                if (sec == null) continue;
                Map<Rarity, int[]> perRarity = new EnumMap<>(Rarity.class);
                for (Rarity r : Rarity.values()) {
                    List<Integer> range = sec.getIntegerList(r.getId());
                    if (range.size() >= 2) perRarity.put(r, new int[]{range.get(0), range.get(1)});
                    else perRarity.put(r, new int[]{0, 0});
                }
                statRanges.put(stat, perRarity);
            }
        }
        for (Rarity r : Rarity.values()) {
            identifyFees.put(r, plugin.getConfig().getInt("identify-fees." + r.getId(), 0));
            sellValues.put(r, plugin.getConfig().getInt("sell-values." + r.getId(), 0));
            resonanceBoosts.put(r, plugin.getConfig().getInt("resonance-boost." + r.getId(), 0));
            String col = plugin.getConfig().getString("rarity-colors." + r.getId(), "<white>");
            rarityColors.put(r, col);
        }
        for (GearType type : GearType.values()) {
            List<String> mats = plugin.getConfig().getStringList("materials." + type.getMaterialKey());
            List<Material> resolved = new ArrayList<>();
            for (String name : mats) {
                try {
                    resolved.add(Material.valueOf(name));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown material '" + name + "' for materials." + type.getMaterialKey());
                    resolved.add(Material.STICK);
                }
            }
            if (resolved.isEmpty()) resolved.add(Material.STICK);
            materialsCache.put(type, List.copyOf(resolved));
        }
        ConfigurationSection weaponSec = plugin.getConfig().getConfigurationSection("attributes.weapon");
        if (weaponSec != null) {
            ConfigurationSection ls = weaponSec.getConfigurationSection("lifesteal");
            if (ls != null) for (Rarity r : Rarity.values()) weaponLifesteal.put(r, ls.getInt(r.getId(), 0));
            ConfigurationSection fa = weaponSec.getConfigurationSection("fire-aspect");
            if (fa != null) for (Rarity r : Rarity.values()) weaponFireAspect.put(r, fa.getInt(r.getId(), 0));
        }
        ConfigurationSection armorSec = plugin.getConfig().getConfigurationSection("attributes.armor");
        if (armorSec != null) {
            ConfigurationSection dr = armorSec.getConfigurationSection("damage-reduction");
            if (dr != null) for (Rarity r : Rarity.values()) armorDamageReduction.put(r, dr.getInt(r.getId(), 0));
        }
        weaponResonanceBase = plugin.getConfig().getInt("resonance.weapon-damage-vs-matching", 25);
        armorReductionPerPiece = plugin.getConfig().getInt("resonance.armor-reduction-per-piece", 10);
        armorReductionCap = plugin.getConfig().getInt("resonance.armor-reduction-cap", 40);
        armorPointsReductionPerPoint = plugin.getConfig().getInt("resonance.armor-points-reduction-per-point", 2);
        armorPointsCap = plugin.getConfig().getInt("resonance.armor-points-cap", 25);
        armorAttributeReductionCap = plugin.getConfig().getInt("resonance.armor-attribute-reduction-cap", 30);
    }

    public int[] statRange(Rarity rarity, String stat) {
        Map<Rarity, int[]> perRarity = statRanges.get(stat);
        if (perRarity == null) return new int[]{0, 0};
        int[] range = perRarity.get(rarity);
        return range != null ? range : new int[]{0, 0};
    }

    public int identifyFee(Rarity rarity) {
        return identifyFees.getOrDefault(rarity, 0);
    }

    public int sellValue(Rarity rarity) {
        if (rarity == null) return 0;
        return sellValues.getOrDefault(rarity, 0);
    }

    public int resonanceBoost(Rarity rarity) {
        return resonanceBoosts.getOrDefault(rarity, 0);
    }

    public int weaponResonanceBase() {
        return weaponResonanceBase;
    }

    public int getArmorReductionPerPiece() { return armorReductionPerPiece; }
    public int getArmorReductionCap() { return armorReductionCap; }
    public int getArmorPointsReductionPerPoint() { return armorPointsReductionPerPoint; }
    public int getArmorPointsCap() { return armorPointsCap; }
    public int getArmorAttributeReductionCap() { return armorAttributeReductionCap; }

    public Material materialFor(GearType type, Rarity rarity) {
        List<Material> mats = materialsCache.get(type);
        if (mats == null || mats.isEmpty()) return Material.STICK;
        return mats.get(Math.min(rarity.getTier(), mats.size() - 1));
    }

    public ItemStack generateGear(GearType type, Rarity rarity) {
        return generateGear(type, rarity, null, 0);
    }

    public ItemStack generateGear(GearType type, Rarity rarity, Resonance forcedResonance) {
        return generateGear(type, rarity, forcedResonance, 0);
    }

    public ItemStack generateGodroll(GearType type) {
        Rarity rarity = Rarity.LEGENDARY;
        GearRolls rolls = new GearRolls();
        rolls.rarity = rarity;
        rolls.type = type;
        rolls.resonance = Resonance.values()[ThreadLocalRandom.current().nextInt(Resonance.values().length)];
        rolls.boost = resonanceBoost(rarity);

        rolls.starsPrimary = statRange(rarity, "stars")[1];
        rolls.starsSpeed = statRange(rarity, "stars")[1];
        rolls.starsHp = statRange(rarity, "stars")[1];

        if (type.isWeapon()) {
            rolls.damage = statRange(rarity, "damage")[1];
            int lifesteal = weaponLifesteal.getOrDefault(Rarity.LEGENDARY, 8);
            int fire = weaponFireAspect.getOrDefault(Rarity.LEGENDARY, 2);
            rolls.attributes = "lifesteal:" + lifesteal + ";fire-aspect:" + fire;
        } else {
            rolls.armor = statRange(rarity, "armor")[1];
            int reduction = armorDamageReduction.getOrDefault(Rarity.LEGENDARY, 12);
            rolls.attributes = "damage-reduction:" + reduction;
        }
        rolls.speed = statRange(rarity, "speed")[1];
        rolls.maxhp = statRange(rarity, "maxhp")[1];

        return buildItem(rolls);
    }

    public ItemStack generateGear(GearType type, Rarity rarity, Resonance forcedResonance, int luck) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        GearRolls rolls = new GearRolls();
        rolls.rarity = rarity;
        rolls.type = type;
        rolls.resonance = forcedResonance != null
                ? forcedResonance
                : Resonance.values()[rand.nextInt(Resonance.values().length)];
        rolls.boost = resonanceBoost(rarity);

        int[] starRange = statRange(rarity, "stars");
        rolls.starsPrimary = rollStars(starRange, luck, rand);
        rolls.starsSpeed = rollStars(starRange, luck, rand);
        rolls.starsHp = rollStars(starRange, luck, rand);

        if (type.isWeapon()) {
            int[] dmgRange = statRange(rarity, "damage");
            rolls.damage = rand.nextInt(dmgRange[0], dmgRange[1] + 1);
        } else {
            int[] armorRange = statRange(rarity, "armor");
            rolls.armor = rand.nextInt(armorRange[0], armorRange[1] + 1);
        }
        int[] speedRange = statRange(rarity, "speed");
        rolls.speed = rand.nextInt(speedRange[0], speedRange[1] + 1);
        int[] hpRange = statRange(rarity, "maxhp");
        rolls.maxhp = rand.nextInt(hpRange[0], hpRange[1] + 1);

        rolls.attributes = type.isWeapon()
                ? rollWeaponAttribute(rarity, rand)
                : rollArmorAttribute(rarity, rand);

        return buildItem(rolls);
    }

    private int rollStars(int[] range, int luck, ThreadLocalRandom rand) {
        int stars = rand.nextInt(range[0], range[1] + 1);
        if (luck > 0 && rand.nextInt(100) < luck) {
            stars++;
        }
        return Math.min(stars, 5);
    }

    private String rollWeaponAttribute(Rarity rarity, ThreadLocalRandom rand) {
        if (rarity.getTier() < Rarity.RARE.getTier()) return "";
        int lifesteal = weaponLifesteal.getOrDefault(rarity, 0);
        int fire = weaponFireAspect.getOrDefault(rarity, 0);
        if (rarity == Rarity.LEGENDARY) {
            return "lifesteal:" + lifesteal + ";fire-aspect:" + fire;
        }
        if (lifesteal > 0 && fire > 0) {
            return rand.nextBoolean() ? "lifesteal:" + lifesteal : "fire-aspect:" + fire;
        }
        if (lifesteal > 0) return "lifesteal:" + lifesteal;
        if (fire > 0) return "fire-aspect:" + fire;
        return "";
    }

    private String rollArmorAttribute(Rarity rarity, ThreadLocalRandom rand) {
        if (rarity.getTier() < Rarity.RARE.getTier()) return "";
        int reduction = armorDamageReduction.getOrDefault(rarity, 0);
        return reduction > 0 ? "damage-reduction:" + reduction : "";
    }

    private ItemStack buildItem(GearRolls rolls) {
        Material material = materialFor(rolls.type, rolls.rarity);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String color = rarityColors.getOrDefault(rolls.rarity, "<white>");
        meta.customName(MM.deserialize(color + "<bold>" + rolls.type.getLabel() + "</bold>"));

        ThreadLocalRandom flavorRand = ThreadLocalRandom.current();
        List<Component> lore = new ArrayList<>();

        // --- header: rule + rarity identity line (Wynncraft-style detail page)
        lore.add(MM.deserialize(GlitchUI.DIVIDER));
        lore.add(MM.deserialize(color + "<bold>" + rolls.rarity.getDisplayName()
                + "</bold></" + tagSuffix(color) + "> <dark_gray>" + archetypeLabel(rolls.type) + "</dark_gray>"));

        // --- stat block with star pips
        if (rolls.type.isWeapon()) {
            lore.add(MM.deserialize(statLine("Damage", "+" + rolls.damage + "%", rolls.starsPrimary)));
        } else {
            lore.add(MM.deserialize(statLine("Armor", "+" + rolls.armor, rolls.starsPrimary)));
        }
        if (rolls.speed > 0) {
            lore.add(MM.deserialize(statLine("Speed", "+" + rolls.speed + "%", rolls.starsSpeed)));
        }
        if (rolls.maxhp > 0) {
            lore.add(MM.deserialize(statLine("Max HP", "+" + rolls.maxhp, rolls.starsHp)));
        }

        // --- resonance block
        String resColor = rolls.resonance.getColorTag();
        String resClose = tagSuffix(resColor);
        lore.add(MM.deserialize(GlitchUI.resIcon(rolls.resonance) + " "
                + resColor + "<bold>" + rolls.resonance.getLabel() + "</bold></" + resClose + ">"
                + " <gray>Resonance</gray>"));
        if (rolls.type.isWeapon()) {
            lore.add(MM.deserialize("<dark_gray>» +" + (weaponResonanceBase + rolls.boost)
                    + "% dmg vs " + resColor + rolls.resonance.getLabel() + "</" + resClose + "> mobs</dark_gray>"));
        } else {
            lore.add(MM.deserialize("<dark_gray>» Resists " + resColor
                    + rolls.resonance.getLabel() + "</" + resClose + "> damage</dark_gray>"));
        }

        // --- special attributes
        if (!rolls.attributes.isEmpty()) {
            for (String attr : rolls.attributes.split(";")) {
                String line = attributeLore(attr);
                if (line != null) {
                    lore.add(MM.deserialize("<dark_gray>» </dark_gray><aqua>" + line + "</aqua>"));
                }
            }
        }

        boolean godroll = rolls.rarity == Rarity.LEGENDARY
                && rolls.starsPrimary >= 5 && rolls.starsSpeed >= 5 && rolls.starsHp >= 5;
        if (godroll) {
            lore.add(MM.deserialize("<gold><bold>Perfectly resonant.</bold></gold>"));
        }

        // --- flavor footer
        lore.add(Component.empty());
        String[] pool = rolls.type.isWeapon() ? WEAPON_FLAVOR : ARMOR_FLAVOR;
        lore.add(MM.deserialize("<dark_gray><italic>" + pool[flavorRand.nextInt(pool.length)] + "</italic></dark_gray>"));
        lore.add(Component.empty());
        lore.add(MM.deserialize(GlitchUI.SHARD
                + " <gray>Sell price: <aqua>" + sellValue(rolls.rarity) + " Shards</aqua></gray>"));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (rolls.speed > 0) {
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(uniqueKey("speed"), 0.1 * rolls.speed / 100.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (rolls.maxhp > 0) {
            meta.addAttributeModifier(Attribute.MAX_HEALTH,
                    new AttributeModifier(uniqueKey("maxhp"), rolls.maxhp, AttributeModifier.Operation.ADD_NUMBER));
        }

        meta.getPersistentDataContainer().set(gearKey, PersistentDataType.STRING, rolls.serialize());
        item.setItemMeta(meta);
        return item;
    }

    private String archetypeLabel(GearType type) {
        return switch (type) {
            case BLADE -> "Melee Weapon";
            case GREATBLADE -> "Heavy Weapon";
            case ARCANE_STAFF -> "Arcane Focus";
            default -> "Armor · " + type.getLabel();
        };
    }

    private String statLine(String label, String value, int stars) {
        return "<gray>» <white>" + value + "</white> " + label + "  " + GlitchUI.pips(stars) + "</gray>";
    }

    private String attributeLore(String attr) {
        if (attr == null || !attr.contains(":")) return null;
        String[] parts = attr.split(":");
        String value = parts[1];
        switch (parts[0]) {
            case "lifesteal":
                return "Lifesteal " + value + "%";
            case "fire-aspect":
                return "Fire Aspect " + value;
            case "damage-reduction":
                return "Damage taken -" + value + "%";
            default:
                return null;
        }
    }

    private static final String[] WEAPON_FLAVOR = {
            "Forgotten steel that still remembers the shape of hands.",
            "It hums faintly when the rift draws near.",
            "Its edge scatters light into wrong colors.",
            "Warm to the touch, like something breathing."
    };

    private static final String[] ARMOR_FLAVOR = {
            "Woven from threads that survived the anomaly.",
            "The lining is cold; the outside refuses to burn.",
            "Someone etched a warning here once. It faded.",
            "It fits better than it should."
    };

    private String tagSuffix(String tag) {
        String t = tag.replace("<", "").replace(">", "");
        int slash = t.indexOf('/');
        if (slash > 0) return t.substring(0, slash);
        return t;
    }

    private NamespacedKey uniqueKey(String prefix) {
        return new NamespacedKey(plugin, prefix + "_" + UUID.randomUUID());
    }

    public GearRolls parse(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String data = item.getItemMeta().getPersistentDataContainer().get(gearKey, PersistentDataType.STRING);
        return GearRolls.deserialize(data);
    }

    public Map<String, Integer> parseAttributes(GearRolls rolls) {
        return parseAttributes(rolls.attributes);
    }

    public static Map<String, Integer> parseAttributes(String attributes) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (attributes == null || attributes.isEmpty()) return result;
        for (String attr : attributes.split(";")) {
            String[] parts = attr.split(":");
            if (parts.length == 2) {
                try {
                    result.put(parts[0], Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }
}
