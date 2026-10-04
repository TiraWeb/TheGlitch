package com.theglitch.glitchbots;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Cached config.yml values (read once per reload, never per tick). */
final class BotConfig {

    final boolean enabled;
    final List<String> worlds;
    final int targetPerWorld;
    final int ringMin, ringMax, minPlayerDistance, maxSpawnPerTick, tickSeconds, despawnGraceSeconds;
    final String nameFormat;
    final org.bukkit.ChatColor nameColor;
    final List<String> names;
    final Map<String, Integer> rarityWeights = new LinkedHashMap<>();
    final double armorChance, rangedChance;
    final double health, range, chaseRange;
    final int attackRateTicks;
    private final Map<String, Double> accuracy = new LinkedHashMap<>();
    final double fleeHealth;
    final int fleeSeconds;
    final int maxCrates;
    final double crateSearchRadius;
    final int extractWhenRemaining;
    final double gearDropChance;
    // spread over the map (parked rogues materialise near raiders)
    final double nearShare;
    final int nearMin, nearMax, materializeRadius, parkRadius, maxNearPlayer, maxChasers;
    private final Map<String, Double> damage = new LinkedHashMap<>();
    // hidden personality: friendly / betrayer / hostile
    final double friendlyShare, betrayerShare, betrayHealth, backTurnedChance;
    final int betrayAfterMin, betrayAfterMax, giftAfterSeconds, tagAlongSeconds;
    final List<String> giftItems;

    BotConfig(FileConfiguration c) {
        enabled = c.getBoolean("enabled", true);
        worlds = c.getStringList("worlds");
        targetPerWorld = Math.max(0, c.getInt("target-per-world", 10));
        ringMin = Math.max(8, c.getInt("spawn-ring.min", 40));
        ringMax = Math.max(ringMin + 4, c.getInt("spawn-ring.max", 96));
        minPlayerDistance = Math.max(0, c.getInt("min-player-distance", 24));
        maxSpawnPerTick = Math.max(1, c.getInt("max-spawn-per-tick", 1));
        tickSeconds = Math.max(1, c.getInt("tick-seconds", 5));
        despawnGraceSeconds = Math.max(0, c.getInt("despawn-grace-seconds", 30));
        // Strip any legacy/MiniMessage formatting: a '§' in an NPC player's name breaks its death packet
        String fmt = c.getString("name-format", "Rogue <name>").replace("<name>", "\u0000");
        fmt = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(fmt))
                .replaceAll("(?i)[\u00a7&][0-9a-fk-or]", "");
        nameFormat = fmt.replace("\u0000", "<name>");
        org.bukkit.ChatColor color;
        try {
            color = org.bukkit.ChatColor.valueOf(c.getString("name-color", "RED").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            color = org.bukkit.ChatColor.RED;
        }
        nameColor = color;
        List<String> n = c.getStringList("names");
        names = n.isEmpty() ? List.of("Vex", "Kael", "Riven", "Sable", "Dusk") : n;
        ConfigurationSection tiers = c.getConfigurationSection("gear-tiers");
        if (tiers != null) {
            for (String k : tiers.getKeys(false)) rarityWeights.put(k.toUpperCase(Locale.ROOT), Math.max(0, tiers.getInt(k)));
        }
        if (rarityWeights.values().stream().mapToInt(Integer::intValue).sum() <= 0) rarityWeights.put("COMMON", 1);
        armorChance = c.getDouble("armor-chance", 0.7);
        rangedChance = c.getDouble("ranged-chance", 0.25);
        health = c.getDouble("combat.health", 20.0);
        range = c.getDouble("combat.range", 24.0);
        chaseRange = c.getDouble("combat.chase-range", 40.0);
        attackRateTicks = Math.max(4, c.getInt("combat.attack-rate-ticks", 14));
        ConfigurationSection acc = c.getConfigurationSection("combat.accuracy");
        if (acc != null) {
            for (String k : acc.getKeys(false)) accuracy.put(k.toUpperCase(Locale.ROOT), acc.getDouble(k));
        }
        fleeHealth = c.getDouble("combat.flee-health", 0.3);
        fleeSeconds = Math.max(1, c.getInt("combat.flee-seconds", 7));
        maxCrates = Math.max(1, c.getInt("loot.max-crates", 4));
        crateSearchRadius = Math.max(8, c.getDouble("loot.crate-search-radius", 80));
        extractWhenRemaining = Math.max(0, c.getInt("loot.extract-when-remaining", 240));
        gearDropChance = c.getDouble("drops.gear-chance", 0.35);
        nearShare = Math.max(0, Math.min(1, c.getDouble("spread.near-share", 0.3)));
        nearMin = Math.max(32, c.getInt("spread.near-ring.min", 140));
        nearMax = Math.max(nearMin + 8, c.getInt("spread.near-ring.max", 320));
        materializeRadius = Math.max(32, c.getInt("spread.materialize-radius", 100));
        parkRadius = Math.max(materializeRadius + 16, c.getInt("spread.park-radius", 150));
        maxNearPlayer = Math.max(1, c.getInt("spread.max-near-player", 2));
        maxChasers = Math.max(1, c.getInt("spread.max-chasers-per-player", 2));
        friendlyShare = Math.max(0, Math.min(1, c.getDouble("disposition.friendly-share", 0.5)));
        betrayerShare = Math.max(0, Math.min(1, c.getDouble("disposition.betrayer-share", 0.4)));
        List<Integer> ba = c.getIntegerList("disposition.betray-after-seconds");
        betrayAfterMin = Math.max(3, ba.size() > 0 ? ba.get(0) : 8);
        betrayAfterMax = Math.max(betrayAfterMin, ba.size() > 1 ? ba.get(1) : 25);
        betrayHealth = c.getDouble("disposition.betray-when-health-below", 0.4);
        backTurnedChance = c.getDouble("disposition.back-turned-chance", 0.25);
        giftAfterSeconds = Math.max(3, c.getInt("disposition.gift-after-seconds", 15));
        tagAlongSeconds = Math.max(10, c.getInt("disposition.tag-along-seconds", 45));
        giftItems = c.getStringList("disposition.gift-items");
        ConfigurationSection dmg = c.getConfigurationSection("combat.damage");
        if (dmg != null) {
            for (String k : dmg.getKeys(false)) damage.put(k.toUpperCase(Locale.ROOT), dmg.getDouble(k));
        }
    }

    double accuracy(String rarity) {
        return accuracy.getOrDefault(rarity, 2.0);
    }

    /** Rolls a new rogue's hidden personality. */
    RogueBot.Disposition rollDisposition() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() < friendlyShare) return RogueBot.Disposition.FRIENDLY;
        return r.nextDouble() < betrayerShare ? RogueBot.Disposition.BETRAYER : RogueBot.Disposition.HOSTILE;
    }

    /** Fixed damage per hit (half-hearts, before the victim's armor) by gear rarity. */
    double damage(String rarity) {
        return damage.getOrDefault(rarity, 3.0);
    }

    String rollRarity() {
        int total = rarityWeights.values().stream().mapToInt(Integer::intValue).sum();
        int r = ThreadLocalRandom.current().nextInt(total);
        for (Map.Entry<String, Integer> e : rarityWeights.entrySet()) {
            r -= e.getValue();
            if (r < 0) return e.getKey();
        }
        return "COMMON";
    }
}
