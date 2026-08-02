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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GearManager {

    private final GlitchItems plugin;
    private final NamespacedKey gearKey;

    public GearManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.gearKey = new NamespacedKey(plugin, GearRolls.SERIAL_KEY);
    }

    public int[] statRange(Rarity rarity, String stat) {
        ConfigurationSection ranges = plugin.getConfig().getConfigurationSection("stat-ranges." + stat);
        if (ranges == null) return new int[]{0, 0};
        List<Integer> range = ranges.getIntegerList(rarity.getId());
        if (range.size() < 2) return new int[]{0, 0};
        return new int[]{range.get(0), range.get(1)};
    }

    public int identifyFee(Rarity rarity) {
        return plugin.getConfig().getInt("identify-fees." + rarity.getId(), 0);
    }

    public int sellValue(Rarity rarity) {
        return plugin.getConfig().getInt("sell-values." + rarity.getId(), 0);
    }

    public int resonanceBoost(Rarity rarity) {
        return plugin.getConfig().getInt("resonance-boost." + rarity.getId(), 0);
    }

    public int weaponResonanceBase() {
        return plugin.getConfig().getInt("resonance.weapon-damage-vs-matching", 25);
    }

    public Material materialFor(GearType type, Rarity rarity) {
        List<String> mats = plugin.getConfig().getStringList("materials." + type.getMaterialKey());
        if (mats.isEmpty()) return Material.STICK;
        String name = mats.get(Math.min(rarity.getTier(), mats.size() - 1));
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.STICK;
        }
    }

    public ItemStack generateGear(GearType type, Rarity rarity) {
        return generateGear(type, rarity, null);
    }

    public ItemStack generateGear(GearType type, Rarity rarity, Resonance forcedResonance) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        GearRolls rolls = new GearRolls();
        rolls.rarity = rarity;
        rolls.type = type;
        rolls.resonance = forcedResonance != null
                ? forcedResonance
                : Resonance.values()[rand.nextInt(Resonance.values().length)];
        rolls.boost = resonanceBoost(rarity);

        int[] starRange = statRange(rarity, "stars");
        rolls.starsPrimary = rand.nextInt(starRange[0], starRange[1] + 1);
        rolls.starsSpeed = rand.nextInt(starRange[0], starRange[1] + 1);
        rolls.starsHp = rand.nextInt(starRange[0], starRange[1] + 1);

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

    private String rollWeaponAttribute(Rarity rarity, ThreadLocalRandom rand) {
        if (rarity.getTier() < Rarity.RARE.getTier()) return "";
        int lifesteal = plugin.getConfig().getInt("attributes.weapon.lifesteal." + rarity.getId(), 0);
        int fire = plugin.getConfig().getInt("attributes.weapon.fire-aspect." + rarity.getId(), 0);
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
        int reduction = plugin.getConfig().getInt("attributes.armor.damage-reduction." + rarity.getId(), 0);
        return reduction > 0 ? "damage-reduction:" + reduction : "";
    }

    private ItemStack buildItem(GearRolls rolls) {
        Material material = materialFor(rolls.type, rolls.rarity);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String color = rarityColor(rolls.rarity);
        String name = color + "<bold>[" + rolls.rarity.getDisplayName() + "]</bold> <white>" + rolls.type.getLabel() + "</white>";
        meta.customName(MiniMessage.miniMessage().deserialize(name));

        List<Component> lore = new ArrayList<>();

        if (rolls.type.isWeapon()) {
            lore.add(MiniMessage.miniMessage().deserialize(
                    "<gray>Damage: <white>+" + rolls.damage + "%</white> " + stars(rolls.starsPrimary)));
        } else {
            lore.add(MiniMessage.miniMessage().deserialize(
                    "<gray>Armor: <white>+" + rolls.armor + "</white> " + stars(rolls.starsPrimary)));
        }
        if (rolls.speed > 0) {
            lore.add(MiniMessage.miniMessage().deserialize(
                    "<gray>Speed: <white>+" + rolls.speed + "%</white> " + stars(rolls.starsSpeed)));
        }
        if (rolls.maxhp > 0) {
            lore.add(MiniMessage.miniMessage().deserialize(
                    "<gray>Max HP: <white>+" + rolls.maxhp + "</white> " + stars(rolls.starsHp)));
        }
        lore.add(MiniMessage.miniMessage().deserialize(
                rolls.resonance.getColorTag() + "Resonance: " + rolls.resonance.getLabel() + "</" + tagSuffix(rolls.resonance.getColorTag()) + ">"));

        if (!rolls.attributes.isEmpty()) {
            for (String attr : rolls.attributes.split(";")) {
                String line = attributeLore(attr);
                if (line != null) {
                    lore.add(MiniMessage.miniMessage().deserialize("<aqua>" + line + "</aqua>"));
                }
            }
        }
        if (rolls.type.isWeapon()) {
            lore.add(MiniMessage.miniMessage().deserialize(
                    "<dark_gray>+" + (weaponResonanceBase() + rolls.boost) + "% dmg vs " + rolls.resonance.getLabel() + " mobs</dark_gray>"));
        }
        lore.add(Component.empty());
        lore.add(MiniMessage.miniMessage().deserialize(
                "<gray>Sell price: <aqua>" + sellValue(rolls.rarity) + " Shards</aqua></gray>"));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (rolls.speed > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED,
                    new AttributeModifier(uniqueKey("speed"), 0.1 * rolls.speed / 100.0, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (rolls.maxhp > 0) {
            meta.addAttributeModifier(Attribute.GENERIC_MAX_HEALTH,
                    new AttributeModifier(uniqueKey("maxhp"), rolls.maxhp, AttributeModifier.Operation.ADD_NUMBER));
        }

        meta.getPersistentDataContainer().set(gearKey, PersistentDataType.STRING, rolls.serialize());
        item.setItemMeta(meta);
        return item;
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

    private String stars(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("<gold>★</gold>");
        }
        return sb.toString();
    }

    private String rarityColor(Rarity rarity) {
        return plugin.getConfig().getString("rarity-colors." + rarity.getId(), "<white>");
    }

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
