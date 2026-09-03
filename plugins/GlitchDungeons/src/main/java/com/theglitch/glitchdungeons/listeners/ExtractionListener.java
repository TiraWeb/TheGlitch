package com.theglitch.glitchdungeons.listeners;

import com.theglitch.glitchdungeons.ColorUtil;
import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.DungeonSlot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExtractionListener implements Listener {
    private final GlitchDungeons plugin;
    private final Map<UUID, Integer> extractProgress; // player -> ticks in zone
    private final Map<UUID, Location> lastLocation;   // player -> last known position

    public ExtractionListener(GlitchDungeons plugin) {
        this.plugin = plugin;
        this.extractProgress = new HashMap<>();
        this.lastLocation = new HashMap<>();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        DungeonRun run = plugin.getDungeonManager().getPlayerRun(player.getUniqueId());
        if (run == null) return;
        if (run.getState() != DungeonRun.State.EXTRACTING) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // Check if player moved block-to-block (not just head rotation)
        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) {
            return; // Head rotation only
        }

        DungeonSlot slot = run.getSlot();
        World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
        if (world == null) return;

        // Check if in extraction zone (8x8x4 centered on slot)
        int px = to.getBlockX();
        int pz = to.getBlockZ();
        int py = to.getBlockY();
        int cx = slot.getCenterX();
        int cz = slot.getCenterZ();

        boolean inZone = Math.abs(px - cx) <= 4 &&
                         Math.abs(pz - cz) <= 4 &&
                         py >= world.getHighestBlockYAt(cx, cz);

        if (!inZone) {
            // Reset progress
            if (extractProgress.containsKey(player.getUniqueId())) {
                extractProgress.remove(player.getUniqueId());
                player.sendMessage(ColorUtil.colorize("&cYou moved! Extraction reset."));
            }
            return;
        }

        // Check if player is actually moving or standing still
        Location last = lastLocation.get(player.getUniqueId());
        if (last != null && last.distanceSquared(to) > 0.01) {
            // Player moved, reset
            extractProgress.remove(player.getUniqueId());
            player.sendMessage(ColorUtil.colorize("&cYou moved! Extraction reset."));
        }
        lastLocation.put(player.getUniqueId(), to.clone());
    }

    public void tickExtraction(DungeonRun run) {
        if (run.getState() != DungeonRun.State.EXTRACTING) return;

        int extractionTime = plugin.getDungeonConfig().getExtractionTime();

        for (UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) continue;

            DungeonSlot slot = run.getSlot();
            Location loc = player.getLocation();
            int cx = slot.getCenterX();
            int cz = slot.getCenterZ();
            World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
            if (world == null) {
                plugin.getDungeonManager().failDungeon(run, DungeonRun.FailReason.WIPE);
                return;
            }
            int highestY = world.getHighestBlockYAt(cx, cz);

            boolean inZone = Math.abs(loc.getBlockX() - cx) <= 4 &&
                             Math.abs(loc.getBlockZ() - cz) <= 4 &&
                             loc.getBlockY() >= highestY;

            if (!inZone) continue;

            // Check if standing still
            Location last = lastLocation.get(member);
            if (last != null && last.distanceSquared(loc) < 0.01) {
                int progress = extractProgress.getOrDefault(member, 0) + 1;
                extractProgress.put(member, progress);

                // Update boss bar or action bar
                int percent = (int)((progress / (double)(extractionTime * 20)) * 100);
                player.sendActionBar(ColorUtil.colorize("&aExtracting... " + percent + "%"));

                if (progress >= extractionTime * 20) {
                    // Extraction complete! (cleanupRun clears this run's progress)
                    plugin.getDungeonManager().completeDungeon(run);
                    return;
                }
            } else {
                extractProgress.put(member, 0);
            }
            lastLocation.put(member, loc);
        }
    }

    public void clearRun(DungeonRun run) {
        for (UUID member : run.getParty().getMembers()) {
            extractProgress.remove(member);
            lastLocation.remove(member);
        }
    }

}
