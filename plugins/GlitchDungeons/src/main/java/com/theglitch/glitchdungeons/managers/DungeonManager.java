package com.theglitch.glitchdungeons.managers;

import com.theglitch.glitchdungeons.ColorUtil;
import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.config.DungeonConfig;
import com.theglitch.glitchdungeons.models.DungeonRun;
import com.theglitch.glitchdungeons.models.DungeonSlot;
import com.theglitch.glitchdungeons.models.Party;
import com.theglitch.glitchdungeons.tasks.TimerTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DungeonManager {
    private final GlitchDungeons plugin;
    private final Map<Integer, DungeonRun> activeRuns;   // runId -> run
    private final Map<UUID, DungeonRun> playerRuns;      // playerUuid -> run
    private final AtomicInteger nextRunId;

    public DungeonManager(GlitchDungeons plugin) {
        this.plugin = plugin;
        this.activeRuns = new HashMap<>();
        this.playerRuns = new HashMap<>();
        this.nextRunId = new AtomicInteger(1);
    }

    public DungeonSlot findFreeSlot() {
        for (DungeonSlot slot : plugin.getDungeonConfig().getSlots().values()) {
            if (!slot.isOccupied()) return slot;
        }
        return null;
    }

    public boolean canJoin(Player player, int tier) {
        if (!plugin.getDungeonConfig().getDungeons().containsKey(tier)) return false;
        if (!player.hasPermission("glitchdungeons.dungeon.tier" + tier)) return false;
        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), tier)) return false;
        if (findFreeSlot() == null) return false;
        Party party = plugin.getPartyManager().getParty(player.getUniqueId());
        if (party == null) return false;
        if (!party.isLeader(player.getUniqueId())) return false;
        return true;
    }

    public DungeonRun startDungeon(Party party, int tier) {
        DungeonSlot slot = findFreeSlot();
        if (slot == null) return null;
        var tierConfig = plugin.getDungeonConfig().getDungeon(tier);
        if (tierConfig == null) return null;

        int runId = nextRunId.getAndIncrement();
        DungeonRun run = new DungeonRun(runId, party, slot, tier,
            tierConfig.getMaxTime(), tierConfig.getWaveCount());

        slot.setOccupied(true);
        slot.setAssignedParty(party.getLeaderUuid());
        activeRuns.put(runId, run);

        // Mark all members as in dungeon
        for (UUID member : party.getMembers()) {
            playerRuns.put(member, run);
            plugin.getPartyManager().setInDungeon(member, true);
        }

        // Teleport party to staging
        teleportToStaging(party);

        // Set state to ASSIGNING, then PREP after teleport
        run.setState(DungeonRun.State.ASSIGNING);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Run may have failed/cleaned up during the delay window
            if (activeRuns.get(run.getRunId()) != run
                    || run.getState() != DungeonRun.State.ASSIGNING) {
                return;
            }
            teleportToSlot(party, slot);
            run.setState(DungeonRun.State.PREP);
            // Start prep timer
            new TimerTask(plugin, run).startPrep();
        }, 5L);

        return run;
    }

    public void teleportToStaging(Party party) {
        World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
        if (world == null) {
            plugin.getLogger().warning("Staging world '" + plugin.getDungeonConfig().getStagingWorld()
                + "' not found! Cannot teleport party to staging.");
            return;
        }
        DungeonConfig config = plugin.getDungeonConfig();
        Location loc = new Location(world, config.getStagingX(), config.getStagingY(), config.getStagingZ());
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.teleport(loc);
            }
        }
    }

    public void teleportToSlot(Party party, DungeonSlot slot) {
        World world = Bukkit.getWorld(plugin.getDungeonConfig().getStagingWorld());
        if (world == null) {
            plugin.getLogger().warning("Staging world '" + plugin.getDungeonConfig().getStagingWorld()
                + "' not found! Cannot teleport party to slot.");
            return;
        }
        Location loc = new Location(world, slot.getCenterX() + 0.5, world.getHighestBlockYAt(slot.getCenterX(), slot.getCenterZ()) + 2, slot.getCenterZ() + 0.5);
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.teleport(loc);
            }
        }
    }

    public void teleportToHub(Party party) {
        World hubWorld = Bukkit.getWorld(plugin.getDungeonConfig().getHubWorld());
        if (hubWorld == null) {
            plugin.getLogger().warning("Hub world '" + plugin.getDungeonConfig().getHubWorld()
                + "' not found! Cannot teleport party.");
            return;
        }
        DungeonConfig config = plugin.getDungeonConfig();
        Location loc = new Location(hubWorld,
            config.getHubX(), config.getHubY(), config.getHubZ()
        );
        for (UUID member : party.getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.teleport(loc);
            }
        }
    }

    public void teleportToHub(UUID playerUuid) {
        World hubWorld = Bukkit.getWorld(plugin.getDungeonConfig().getHubWorld());
        if (hubWorld == null) {
            plugin.getLogger().warning("Hub world '" + plugin.getDungeonConfig().getHubWorld()
                + "' not found! Cannot teleport player.");
            return;
        }
        DungeonConfig config = plugin.getDungeonConfig();
        Location loc = new Location(hubWorld,
            config.getHubX(), config.getHubY(), config.getHubZ()
        );
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            player.teleport(loc);
        }
    }

    public void removePlayer(UUID playerUuid) {
        playerRuns.remove(playerUuid);
        plugin.getPartyManager().setInDungeon(playerUuid, false);
    }

    public void completeDungeon(DungeonRun run) {
        run.setState(DungeonRun.State.COMPLETED);

        // Give rewards to each player
        for (UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                plugin.getRewardManager().giveRewards(player, run.getTier(),
                    run.getTotalWaves(), run.getParty().getSize());
                player.sendMessage(ColorUtil.colorize("&aDungeon complete! Rewards sent to your stash."));
            }
            // Set cooldown
            plugin.getCooldownManager().setCooldown(member, run.getTier());
        }

        // Teleport to hub
        teleportToHub(run.getParty());

        // Cleanup
        cleanupRun(run);
    }

    public void failDungeon(DungeonRun run, DungeonRun.FailReason reason) {
        run.setState(DungeonRun.State.FAILED);
        run.setFailReason(reason);

        // Teleport to hub (keep inventory per gamerule)
        teleportToHub(run.getParty());

        for (UUID member : run.getParty().getMembers()) {
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                player.sendMessage(ColorUtil.colorize("&cDungeon failed. You were teleported to hub."));
            }
        }

        cleanupRun(run);
    }

    public void playerDied(UUID playerUuid) {
        DungeonRun run = playerRuns.get(playerUuid);
        if (run == null) return;
        run.playerDied(playerUuid);
        if (run.isWiped() && run.getState() == DungeonRun.State.ACTIVE) {
            failDungeon(run, DungeonRun.FailReason.WIPE);
        }
    }

    private void cleanupRun(DungeonRun run) {
        run.getSlot().clear();
        for (UUID member : run.getParty().getMembers()) {
            playerRuns.remove(member);
            plugin.getPartyManager().setInDungeon(member, false);
        }
        activeRuns.remove(run.getRunId());
        plugin.getExtractionListener().clearRun(run);
    }

    public DungeonRun getPlayerRun(UUID playerUuid) {
        return playerRuns.get(playerUuid);
    }

    public DungeonRun getRun(int runId) {
        return activeRuns.get(runId);
    }

    public Map<Integer, DungeonRun> getActiveRuns() {
        return activeRuns;
    }

    public int getFreeSlotCount() {
        int count = 0;
        for (DungeonSlot slot : plugin.getDungeonConfig().getSlots().values()) {
            if (!slot.isOccupied()) count++;
        }
        return count;
    }
}
