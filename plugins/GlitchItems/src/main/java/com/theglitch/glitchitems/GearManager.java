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
import java.util.LinkedHashMap;
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
    private final Map<Rarity, Integer> starSellBonus = new EnumMap<>(Rarity.class);
    private final Map<Rarity, Integer> resonanceBoosts = new EnumMap<>(Rarity.class);
    private final Map<Rarity, String> rarityColors = new EnumMap<>(Rarity.class);
    private final Map<GearType, List<Material>> materialsCache = new EnumMap<>(GearType.class);
    // Generic attribute pools: attr name -> value per rarity (loaded from attributes.weapon / attributes.armor)
    private final Map<String, Map<Rarity, Integer>> weaponAttrPool = new LinkedHashMap<>();
    private final Map<String, Map<Rarity, Integer>> armorAttrPool = new LinkedHashMap<>();
    private double[] arcaneStaffAttackBonus = new double[0];
    private double[] greatbladeKnockbackBonus = new double[0];
    private int voidInfusionMaxBoost = 4;
    private int weaponResonanceBase = 25;
    private int armorReductionPerPiece = 10;
    private int armorReductionCap = 40;
    private int armorPointsReductionPerPoint = 2;
    private int armorPointsCap = 25;
    private int armorAttributeReductionCap = 30;

    // Armor upgrade config (cached in reload())
    private int armorUpgradeMaxLevel = 5;
    private int armorPointsPerLevel = 1;
    private final Map<Rarity, Integer> armorUpgradeShardCosts = new EnumMap<>(Rarity.class);
    private double[] armorUpgradeLevelMultiplier = new double[0];
    private final List<Map<String, Integer>> armorUpgradeMaterials = new ArrayList<>();
    private final Map<GearType, Map<String, Double>> pieceIdentity = new EnumMap<>(GearType.class);

    public GearManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.gearKey = new NamespacedKey(plugin, GearRolls.SERIAL_KEY);
        reload();
    }

    public void reload() {
        statRanges.clear();
        identifyFees.clear();
        sellValues.clear();
        starSellBonus.clear();
        resonanceBoosts.clear();
        rarityColors.clear();
        materialsCache.clear();
        weaponAttrPool.clear();
        armorAttrPool.clear();
        armorUpgradeShardCosts.clear();
        armorUpgradeMaterials.clear();
        pieceIdentity.clear();

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
            starSellBonus.put(r, plugin.getConfig().getInt("star-sell-bonus." + r.getId(), 0));
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
        loadAttrPool("attributes.weapon", weaponAttrPool);
        loadAttrPool("attributes.armor", armorAttrPool);
        arcaneStaffAttackBonus = toDoubleArray(plugin.getConfig().getDoubleList("archetype.arcane-staff-attack-bonus"));
        greatbladeKnockbackBonus = toDoubleArray(plugin.getConfig().getDoubleList("archetype.greatblade-knockback-bonus"));
        voidInfusionMaxBoost = plugin.getConfig().getInt("void-infusion.max-boost", 4);
        weaponResonanceBase = plugin.getConfig().getInt("resonance.weapon-damage-vs-matching", 25);
        armorReductionPerPiece = plugin.getConfig().getInt("resonance.armor-reduction-per-piece", 10);
        armorReductionCap = plugin.getConfig().getInt("resonance.armor-reduction-cap", 40);
        armorPointsReductionPerPoint = plugin.getConfig().getInt("resonance.armor-points-reduction-per-point", 2);
        armorPointsCap = plugin.getConfig().getInt("resonance.armor-points-cap", 25);
        armorAttributeReductionCap = plugin.getConfig().getInt("resonance.armor-attribute-reduction-cap", 30);

        // Armor upgrade config
        armorUpgradeMaxLevel = plugin.getConfig().getInt("armor-upgrade.max-level", 5);
        armorPointsPerLevel = plugin.getConfig().getInt("armor-upgrade.armor-points-per-level", 1);
        for (Rarity r : Rarity.values()) {
            armorUpgradeShardCosts.put(r, plugin.getConfig().getInt("armor-upgrade.shard-costs." + r.getId(), 0));
        }
        armorUpgradeLevelMultiplier = toDoubleArray(plugin.getConfig().getDoubleList("armor-upgrade.shard-level-multiplier"));

        ConfigurationSection matsSec = plugin.getConfig().getConfigurationSection("armor-upgrade.materials-per-level");
        if (matsSec != null) {
            for (String key : matsSec.getKeys(false)) {
                int levelNum;
                try {
                    levelNum = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                int idx = levelNum - 1;
                if (idx < 0) continue;
                while (armorUpgradeMaterials.size() <= idx) armorUpgradeMaterials.add(new HashMap<>());
                Map<String, Integer> map = new HashMap<>();
                ConfigurationSection levelSec = matsSec.getConfigurationSection(key);
                if (levelSec != null) {
                    for (String id : levelSec.getKeys(false)) {
                        map.put(id, levelSec.getInt(id));
                    }
                }
                armorUpgradeMaterials.set(idx, map);
            }
        }

        // Per-slot identity multipliers
        ConfigurationSection piSec = plugin.getConfig().getConfigurationSection("piece-identity");
        if (piSec != null) {
            for (String key : piSec.getKeys(false)) {
                GearType type = GearType.fromId(key);
                if (type == null) continue;
                Map<String, Double> stats = new HashMap<>();
                ConfigurationSection statSec = piSec.getConfigurationSection(key);
                if (statSec != null) {
                    for (String stat : statSec.getKeys(false)) {
                        stats.put(stat, statSec.getDouble(stat, 1.0));
                    }
                }
                pieceIdentity.put(type, stats);
            }
        }
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

    /**
     * Roll-based sell value: rarity base + total star pips x star bonus
     * (docs/ITEM_BALANCE.md §3). Godrolls are worth materially more than bricks.
     */
    public int sellValue(GearRolls rolls) {
        if (rolls == null || rolls.rarity == null) return 0;
        int base = sellValues.getOrDefault(rolls.rarity, 0);
        int perStar = starSellBonus.getOrDefault(rolls.rarity, 0);
        int stars = Math.max(0, rolls.starsPrimary) + Math.max(0, rolls.starsSpeed) + Math.max(0, rolls.starsHp);
        return base + stars * perStar;
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

    // Armor upgrade accessors (used by ArmorCommand + buildItem lore)
    public int armorUpgradeMaxLevel() { return armorUpgradeMaxLevel; }
    public int armorPointsPerLevel() { return armorPointsPerLevel; }

    public int shardCostFor(Rarity rarity, int currentLevel) {
        int base = armorUpgradeShardCosts.getOrDefault(rarity, 0);
        if (currentLevel < 0 || currentLevel >= armorUpgradeLevelMultiplier.length) return base;
        return Math.max(0, (int) Math.round(base * armorUpgradeLevelMultiplier[currentLevel]));
    }

    public Map<String, Integer> materialsForLevel(int nextLevel) {
        int idx = nextLevel - 1;
        if (idx < 0 || idx >= armorUpgradeMaterials.size()) return Map.of();
        Map<String, Integer> map = armorUpgradeMaterials.get(idx);
        return map == null ? Map.of() : map;
    }

    public double pieceIdentityMultiplier(GearType type, String stat) {
        Map<String, Double> stats = pieceIdentity.get(type);
        if (stats == null) return 1.0;
        Double val = stats.get(stat);
        return val == null ? 1.0 : val;
    }

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
            String attrs = pickAttributes(weaponAttrPool, Rarity.LEGENDARY, 2, null);
            rolls.attributes = attrs.isEmpty() ? defaultLegendaryWeaponAttributes() : attrs;
        } else {
            rolls.armor = statRange(rarity, "armor")[1];
            String attrs = pickAttributes(armorAttrPool, Rarity.LEGENDARY, 1, null);
            rolls.attributes = attrs.isEmpty() ? "damage-reduction:" + attrValue(armorAttrPool, "damage-reduction", Rarity.LEGENDARY, 12) : attrs;
        }
        rolls.speed = statRange(rarity, "speed")[1];
        rolls.maxhp = statRange(rarity, "maxhp")[1];

        // Per-slot identity multipliers (armor pieces; weapons have no entry → 1.0)
        rolls.armor = (int) Math.max(0, Math.round(rolls.armor * pieceIdentityMultiplier(type, "armor")));
        rolls.speed = (int) Math.max(0, Math.round(rolls.speed * pieceIdentityMultiplier(type, "speed")));
        rolls.maxhp = (int) Math.max(0, Math.round(rolls.maxhp * pieceIdentityMultiplier(type, "maxhp")));

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

        // Per-slot identity multipliers (armor pieces; weapons have no entry → 1.0)
        rolls.armor = (int) Math.max(0, Math.round(rolls.armor * pieceIdentityMultiplier(type, "armor")));
        rolls.speed = (int) Math.max(0, Math.round(rolls.speed * pieceIdentityMultiplier(type, "speed")));
        rolls.maxhp = (int) Math.max(0, Math.round(rolls.maxhp * pieceIdentityMultiplier(type, "maxhp")));

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

    /**
     * Weapons: Rare/Epic roll ONE attribute from the pool, Legendary rolls TWO
     * distinct attributes (docs/ITEM_BALANCE.md §4).
     */
    private String rollWeaponAttribute(Rarity rarity, ThreadLocalRandom rand) {
        if (rarity.getTier() < Rarity.RARE.getTier()) return "";
        int count = rarity == Rarity.LEGENDARY ? 2 : 1;
        String picked = pickAttributes(weaponAttrPool, rarity, count, rand);
        if (!picked.isEmpty()) return picked;
        // Pool empty / misconfigured — fall back to the classic pair.
        int lifesteal = attrValue(weaponAttrPool, "lifesteal", rarity, 0);
        int fire = attrValue(weaponAttrPool, "fire-aspect", rarity, 0);
        if (rarity == Rarity.LEGENDARY && lifesteal > 0 && fire > 0) {
            return "lifesteal:" + lifesteal + ";fire-aspect:" + fire;
        }
        if (lifesteal > 0) return "lifesteal:" + lifesteal;
        if (fire > 0) return "fire-aspect:" + fire;
        return "";
    }

    /** Armor: exactly ONE attribute per piece (design ITEM_SYSTEM §2) — which one varies. */
    private String rollArmorAttribute(Rarity rarity, ThreadLocalRandom rand) {
        if (rarity.getTier() < Rarity.RARE.getTier()) return "";
        return pickAttributes(armorAttrPool, rarity, 1, rand);
    }

    /** Picks `count` distinct attributes from the pool with a value > 0 at this rarity. */
    private String pickAttributes(Map<String, Map<Rarity, Integer>> pool, Rarity rarity, int count, ThreadLocalRandom rand) {
        List<String> candidates = new ArrayList<>();
        for (Map.Entry<String, Map<Rarity, Integer>> entry : pool.entrySet()) {
            Integer value = entry.getValue().get(rarity);
            if (value != null && value > 0) candidates.add(entry.getKey());
        }
        if (candidates.isEmpty()) return "";
        ThreadLocalRandom r = rand != null ? rand : ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count && !candidates.isEmpty(); i++) {
            String attr = candidates.remove(r.nextInt(candidates.size()));
            if (sb.length() > 0) sb.append(';');
            sb.append(attr).append(':').append(pool.get(attr).get(rarity));
        }
        return sb.toString();
    }

    private String defaultLegendaryWeaponAttributes() {
        int lifesteal = attrValue(weaponAttrPool, "lifesteal", Rarity.LEGENDARY, 8);
        int fire = attrValue(weaponAttrPool, "fire-aspect", Rarity.LEGENDARY, 2);
        return "lifesteal:" + lifesteal + ";fire-aspect:" + fire;
    }

    private int attrValue(Map<String, Map<Rarity, Integer>> pool, String attr, Rarity rarity, int def) {
        Map<Rarity, Integer> perRarity = pool.get(attr);
        Integer value = perRarity == null ? null : perRarity.get(rarity);
        return value != null ? value : def;
    }

    private void loadAttrPool(String path, Map<String, Map<Rarity, Integer>> target) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(path);
        if (sec == null) return;
        for (String attr : sec.getKeys(false)) {
            ConfigurationSection perRaritySec = sec.getConfigurationSection(attr);
            if (perRaritySec == null) continue;
            Map<Rarity, Integer> values = new EnumMap<>(Rarity.class);
            for (Rarity r : Rarity.values()) {
                values.put(r, perRaritySec.getInt(r.getId(), 0));
            }
            target.put(attr, values);
        }
    }

    private double[] toDoubleArray(List<Double> list) {
        if (list == null || list.isEmpty()) return new double[0];
        double[] out = new double[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }

    /**
     * Void Infusion: applies to held Epic+ gear — +1 Resonance boost (capped)
     * and +1 star on each pip. Returns the rebuilt stack, or null if the item
     * is not eligible (wrong type, or boost already at cap).
     */
    public ItemStack applyVoidInfusion(ItemStack gear) {
        GearRolls rolls = parse(gear);
        if (rolls == null || rolls.rarity == null) return null;
        if (rolls.rarity.getTier() < Rarity.EPIC.getTier()) return null;
        if (rolls.boost >= voidInfusionMaxBoost) return null;
        rolls.boost = Math.min(voidInfusionMaxBoost, rolls.boost + 1);
        rolls.starsPrimary = Math.min(5, rolls.starsPrimary + 1);
        rolls.starsSpeed = Math.min(5, rolls.starsSpeed + 1);
        rolls.starsHp = Math.min(5, rolls.starsHp + 1);
        return buildItem(rolls);
    }

    /**
     * Upgrades an armor piece by one level (+armor per level). Returns the
     * rebuilt stack, or null if the item is not armor or is already maxed.
     */
    public ItemStack applyUpgrade(ItemStack gear) {
        GearRolls rolls = parse(gear);
        if (rolls == null || rolls.rarity == null) return null;
        if (rolls.type.isWeapon()) return null;
        if (rolls.level >= armorUpgradeMaxLevel) return null;
        rolls.level++;
        rolls.armor += armorPointsPerLevel;
        return buildItem(rolls);
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

        // --- upgrade level line (armor always; weapons only if > 0)
        if (!rolls.type.isWeapon() || rolls.level > 0) {
            lore.add(MM.deserialize("<gray>» Upgrade <white>+" + rolls.level + "</white>/" + armorUpgradeMaxLevel + "</gray>"));
        }

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

        // --- archetype identity (flat modifiers per docs/ITEM_BALANCE.md §4)
        int tier = Math.min(rolls.rarity.getTier(), 4);
        if (rolls.type == GearType.ARCANE_STAFF && tier < arcaneStaffAttackBonus.length
                && arcaneStaffAttackBonus[tier] > 0) {
            lore.add(MM.deserialize("<dark_gray>» <aqua>Arcane core</aqua> <gray>+" + trimNum(arcaneStaffAttackBonus[tier])
                    + " attack damage</gray></dark_gray>"));
        }
        if (rolls.type == GearType.GREATBLADE && tier < greatbladeKnockbackBonus.length
                && greatbladeKnockbackBonus[tier] > 0) {
            lore.add(MM.deserialize("<dark_gray>» <aqua>Heavy head</aqua> <gray>+"
                    + trimNum(greatbladeKnockbackBonus[tier] * 10) + "% knockback</gray></dark_gray>"));
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
                + " <gray>Sell price: <aqua>" + sellValue(rolls) + " Shards</aqua></gray>"));

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
        if (rolls.type == GearType.ARCANE_STAFF && tier < arcaneStaffAttackBonus.length
                && arcaneStaffAttackBonus[tier] > 0) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(uniqueKey("arcanecore"), arcaneStaffAttackBonus[tier], AttributeModifier.Operation.ADD_NUMBER));
        }
        if (rolls.type == GearType.GREATBLADE && tier < greatbladeKnockbackBonus.length
                && greatbladeKnockbackBonus[tier] > 0) {
            meta.addAttributeModifier(Attribute.ATTACK_KNOCKBACK,
                    new AttributeModifier(uniqueKey("heavyhead"), greatbladeKnockbackBonus[tier], AttributeModifier.Operation.ADD_NUMBER));
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
            case "execute":
                return "Execute +" + value + "% vs low HP";
            case "frost-touch":
                return "Frost Touch " + value + " (slow on hit)";
            case "thorns":
                return "Thorns " + value + "%";
            case "glitch-ward":
                return "Glitch Ward +" + value + "% resonance resist";
            default:
                return null;
        }
    }

    private String trimNum(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
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
