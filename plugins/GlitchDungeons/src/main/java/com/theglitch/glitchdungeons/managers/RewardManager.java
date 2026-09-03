package com.theglitch.glitchdungeons.managers;

import com.theglitch.glitchdungeons.GlitchDungeons;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RewardManager {
    private final GlitchDungeons plugin;

    public RewardManager(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    public int calculateShards(int tier, int totalWaves, int partySize) {
        var config = plugin.getDungeonConfig().getDungeon(tier);
        if (config == null) return 0;
        int baseShards = config.getSection().getInt("rewards.base-shards", 50);
        int perWaveBonus = config.getSection().getInt("rewards.per-wave-bonus", 10);
        double tierMultiplier = config.getSection().getDouble("rewards.tier-multiplier", 1.0);
        double partyBonus = getPartySizeBonus(partySize);
        return (int) ((baseShards + perWaveBonus * totalWaves) * tierMultiplier * partyBonus);
    }

    public double getLootChance(int tier) {
        var config = plugin.getDungeonConfig().getDungeon(tier);
        if (config == null) return 0.0;
        return config.getSection().getDouble("rewards.loot-chance", 0.3);
    }

    private double getPartySizeBonus(int partySize) {
        return switch (partySize) {
            case 1 -> 1.0;
            case 2 -> 1.1;
            case 3 -> 1.2;
            case 4 -> 1.3;
            default -> 1.0;
        };
    }

    public void giveRewards(Player player, int tier, int totalWaves, int partySize) {
        int shards = calculateShards(tier, totalWaves, partySize);
        if (shards <= 0) return;

        // Use Vault economy (Coins plugin hooks into Vault)
        try {
            org.bukkit.Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "eco give " + player.getName() + " " + shards);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to give " + shards + " shards to " + player.getName() + ": " + e.getMessage());
        }
    }
}
