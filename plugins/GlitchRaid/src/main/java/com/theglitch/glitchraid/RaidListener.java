package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles death recap, quit handling, loot accounting, auto-start on glitch_red entry,
 * extraction detection, and timeout victim respawn.
 */
public final class RaidListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidListener(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---- Auto-start ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String to = player.getWorld().getName();
        String from = event.getFrom().getName();
        String raidWorld = manager.getAutoStartWorld();
        String hubWorld = manager.getHubWorld();

        // Entering the raid world -> auto start if not already in raid
        if (to.equalsIgnoreCase(raidWorld) && !manager.isInRaid(player.getUniqueId())) {
            boolean started = manager.startRaid(player, true);
            if (started) {
                plugin.getLogger().info("Auto-started raid for " + player.getName() + " (entered " + to + ")");
            }
        }

        // Leaving raid world to hub -> treat as extraction if in raid (and not a recent death respawn)
        if (from.equalsIgnoreCase(raidWorld) && to.equalsIgnoreCase(hubWorld) && manager.isInRaid(player.getUniqueId())) {
            if (manager.isRecentlyDead(player.getUniqueId(), 10000L)) {
                plugin.getLogger().info("Raid world->hub for " + player.getName() + " ignored (recent death, not extraction)");
                return;
            }
            // Give GlitchStash a moment to have saved the stash on KothWinEvent; delay raid end slightly
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (manager.isInRaid(player.getUniqueId())) {
                    manager.handleExtraction(player);
                    plugin.getLogger().info("Raid extraction detected for " + player.getName() + " (" + from + " -> " + to + ")");
                }
            }, 10L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay one second so world is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            String world = player.getWorld().getName();
            if (world.equalsIgnoreCase(manager.getAutoStartWorld()) && !manager.isInRaid(player.getUniqueId())) {
                boolean started = manager.startRaid(player, true);
                if (started) {
                    plugin.getLogger().info("Auto-started raid for " + player.getName() + " (join in " + world + ")");
                }
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (manager.isTimeoutVictim(player.getUniqueId())) {
            // Force respawn to hub spawn
            try {
                org.bukkit.World hub = Bukkit.getWorld(manager.getHubWorld());
                if (hub != null) {
                    event.setRespawnLocation(hub.getSpawnLocation());
                }
                plugin.getLogger().info("Timeout victim " + player.getName() + " respawn redirected to hub");
            } catch (Exception ignored) {
            }
            // Clear flag after a tick
            Bukkit.getScheduler().runTaskLater(plugin, () -> manager.clearTimeoutVictim(player.getUniqueId()), 20L);
            // Ensure bossbar hidden and message
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    String killedRaw = plugin.getConfig().getString("messages.raid-timeout-killed",
                            "<dark_red><bold>The Glitch consumed you.</bold></dark_red>");
                    try { player.sendMessage(MM.deserialize(killedRaw)); } catch (Exception ignored) {}
                }
            }, 10L);
        } else {
            // Normal respawn: if they respawn in the raid world without a raid (e.g., fresh), auto-start
            org.bukkit.Location respawn = event.getRespawnLocation();
            if (respawn != null && respawn.getWorld() != null) {
                String respawnWorld = respawn.getWorld().getName();
                if (respawnWorld.equalsIgnoreCase(manager.getAutoStartWorld()) && !manager.isInRaid(player.getUniqueId())) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (player.getWorld().getName().equalsIgnoreCase(manager.getAutoStartWorld()) && !manager.isInRaid(player.getUniqueId())) {
                            boolean started = manager.startRaid(player, true);
                            if (started) plugin.getLogger().info("Auto-started raid on respawn for " + player.getName());
                        }
                    }, 20L);
                }
            }
        }
    }

    // ---- Death / quit ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isInRaid(player.getUniqueId())) {
            return;
        }
        manager.recordDeath(player.getUniqueId());
        // Timeout victims already incremented in handleTimeout — don't double count if this death is the timeout kill
        boolean isTimeout = manager.isTimeoutVictim(player.getUniqueId());
        if (!isTimeout) {
            manager.incrementDeaths(player.getUniqueId());
        }
        RaidSession session = manager.getSession(player.getUniqueId());
        int deaths = session != null ? session.getDeaths() : 1;

        String recapRaw = plugin.getConfig().getString("messages.death-recap",
                "<red>Death recap: <white>You died! <gray>(Death #<deaths> this raid)</gray></white></red>");
        player.sendMessage(MM.deserialize(recapRaw.replace("<deaths>", String.valueOf(deaths))));

        if (session != null) {
            String partyRaw = plugin.getConfig().getString("messages.party-member-died",
                    "<red>Party member <white><player></white> died! <gray>(Deaths: <deaths>)</gray></red>");
            for (java.util.UUID memberId : session.getMembers()) {
                if (memberId.equals(player.getUniqueId())) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(MM.deserialize(partyRaw
                            .replace("<player>", player.getName())
                            .replace("<deaths>", String.valueOf(deaths))));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!manager.isInRaid(player.getUniqueId())) {
            return;
        }
        RaidSession session = manager.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.getLeader().equals(player.getUniqueId())) {
            manager.endRaid(player.getUniqueId(), RaidEndReason.LEADER_QUIT);
        } else {
            manager.removeMember(player.getUniqueId());
        }
    }

    // ---- Loot accounting ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (!manager.isInRaid(killer.getUniqueId())) {
            return;
        }
        int value = 10;
        String typeName = event.getEntity().getType().name();
        if (typeName.contains("BOSS") || typeName.contains("ELDER") || typeName.contains("WARDEN") || typeName.contains("ENDER_DRAGON")) {
            value = 50;
        } else if (event.getEntity() instanceof org.bukkit.entity.Monster) {
            value = 10;
        }
        manager.addLoot(killer.getUniqueId(), value);
        String lootRaw = plugin.getConfig().getString("messages.loot-added", "<gold>+<amount> loot value</gold>");
        killer.sendActionBar(MM.deserialize(lootRaw.replace("<amount>", String.valueOf(value))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.isInRaid(player.getUniqueId())) return;
        // Only count if the item has sell value (avoid counting junk like dirt)
        org.bukkit.inventory.ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        // Use the sell-price path so only meaningful loot ticks the counter
        java.util.List<org.bukkit.inventory.ItemStack> single = java.util.List.of(stack);
        // Check quickly if it would have value before calling heavy reflection path
        manager.addLootFromItems(player, single);
    }
}
