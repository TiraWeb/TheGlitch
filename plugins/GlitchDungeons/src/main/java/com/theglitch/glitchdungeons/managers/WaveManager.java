package com.theglitch.glitchdungeons.managers;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.DungeonSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WaveManager {
    private final GlitchDungeons plugin;

    public WaveManager(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    public void startNextWave(DungeonRun run) {
        int nextWave = run.getCurrentWave() + 1;
        if (nextWave > run.getTotalWaves()) {
            // All waves complete - start extraction
            run.setState(DungeonRun.State.EXTRACTING);
            run.setExtractionStartTime(System.currentTimeMillis());
            broadcastToParty(run, "&aAll waves cleared! Get to the extraction point!");
            return;
        }

        run.setCurrentWave(nextWave);

        // Announce wave
        if (isBossWave(run, nextWave)) {
            broadcastToParty(run, "&4BOSS WAVE! Prepare for the worst!");
        } else {
            broadcastToParty(run, "&6Wave " + nextWave + "/" + run.getTotalWaves() + " incoming!");
        }

        // Spawn mobs for this wave
        spawnWave(run, nextWave);

        // Schedule next wave after delay (or check for wave completion)
        scheduleWaveCheck(run, nextWave);
    }

    private boolean isBossWave(DungeonRun run, int wave) {
        var tierConfig = plugin.getDungeonConfig().getDungeon(run.getTier());
        if (tierConfig == null) return false;
        ConfigurationSection waveSection = tierConfig.getSection().getConfigurationSection("waves." + wave);
        return waveSection != null && waveSection.contains("boss");
    }

    private void spawnWave(DungeonRun run, int wave) {
        var tierConfig = plugin.getDungeonConfig().getDungeon(run.getTier());
        if (tierConfig == null) return;
        ConfigurationSection waveSection = tierConfig.getSection().getConfigurationSection("waves." + wave);
        if (waveSection == null) return;

        DungeonSlot slot = run.getSlot();
        World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
        if (world == null) return;

        // Spawn regular mobs
        ConfigurationSection mobsSection = waveSection.getConfigurationSection("mobs");
        if (mobsSection != null) {
            for (String mobKey : mobsSection.getKeys(false)) {
                ConfigurationSection mobSection = mobsSection.getConfigurationSection(mobKey);
                if (mobSection == null) continue;
                String type = mobSection.getString("type", "GlitchStalker");
                int count = mobSection.getInt("count", 1);
                int radius = mobSection.getInt("radius", 16);
                spawnMobs(world, slot, type, count, radius);
            }
        }

        // Spawn boss
        ConfigurationSection bossSection = waveSection.getConfigurationSection("boss");
        if (bossSection != null) {
            String bossType = bossSection.getString("type", "GlitchCore");
            int bossCount = bossSection.getInt("count", 1);
            spawnMobs(world, slot, bossType, bossCount, 8);
        }
    }

    private void spawnMobs(World world, DungeonSlot slot, String mobType, int count, int radius) {
        // Sanitize mob type to prevent command injection
        String safeType = mobType.replaceAll("[^a-zA-Z0-9_]", "");
        if (safeType.isEmpty()) return;

        for (int i = 0; i < count; i++) {
            double x = slot.getCenterX() + ThreadLocalRandom.current().nextDouble(-radius, radius);
            double z = slot.getCenterZ() + ThreadLocalRandom.current().nextDouble(-radius, radius);
            double y = world.getHighestBlockYAt((int) x, (int) z) + 1;

            try {
                var mm = Bukkit.getPluginManager().getPlugin("MythicMobs");
                if (mm != null && mm.isEnabled()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "mm m spawn " + safeType + " 1 " + world.getName() + " " + x + " " + y + " " + z);
                } else {
                    plugin.getLogger().warning("MythicMobs not available - cannot spawn " + safeType);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to spawn mob " + safeType + ": " + e.getMessage());
            }
        }
    }

    private void scheduleWaveCheck(DungeonRun run, int wave) {
        var tierConfig = plugin.getDungeonConfig().getDungeon(run.getTier());
        if (tierConfig == null) return;
        ConfigurationSection waveSection = tierConfig.getSection().getConfigurationSection("waves." + wave);
        int delay = plugin.getDungeonConfig().getWaveDelay();
        if (waveSection != null) {
            delay = waveSection.getInt("delay", delay);
        }
        final int waveDelay = delay;

        // Check for mob completion periodically
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (run.getState() != DungeonRun.State.ACTIVE) {
                task.cancel();
                return;
            }
            if (isWaveCleared(run)) {
                task.cancel();
                // Start next wave after waveDelay
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (run.getState() == DungeonRun.State.ACTIVE) {
                        startNextWave(run);
                    }
                }, waveDelay * 20L);
            }
        }, 20L, 20L); // Check every second
    }

    private boolean isWaveCleared(DungeonRun run) {
        World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
        if (world == null) return true;
        DungeonSlot slot = run.getSlot();

        // Check nearby entities for any non-player living entities
        // MythicMobs sets "MythicMob" metadata on spawned mobs
        boolean mmAvailable = Bukkit.getPluginManager().getPlugin("MythicMobs") != null;
        int checkRadius = 32;
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(
                new Location(world, slot.getCenterX(), 64, slot.getCenterZ()),
                checkRadius, 32, checkRadius)) {
            if (!(entity instanceof org.bukkit.entity.LivingEntity living)) continue;
            if (entity instanceof Player) continue;
            if (living.isDead()) continue;
            // If MythicMobs is available, check its metadata
            if (mmAvailable && entity.hasMetadata("MythicMob")) return false;
            // Fallback: if any non-player living entity exists in the area, wave isn't clear
            if (!mmAvailable && entity.getLocation().distanceSquared(
                    new Location(world, slot.getCenterX(), entity.getLocation().getY(), slot.getCenterZ())) < checkRadius * checkRadius) {
                return false;
            }
        }
        return true;
    }

    public void broadcastToParty(DungeonRun run, String message) {
        String colored = message.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
        for (UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && player.isOnline()) {
                player.sendMessage(colored);
            }
        }
    }
}
