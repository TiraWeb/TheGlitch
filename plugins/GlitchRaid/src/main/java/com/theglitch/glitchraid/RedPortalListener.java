package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redirects hub end-portal travel into the raid world.
 * <p>
 * The portal floor needs no End dimension and no world linking: the vanilla
 * END_PORTAL teleport is cancelled and the player is sent to the raid world
 * spawn instead. Entering the world auto-starts/joins the raid via
 * {@link RaidListener#onWorldChange} and pulls the party along.
 * </p>
 */
public final class RedPortalListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RaidManager manager;
    private final RedPortalManager portals;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();

    public RedPortalListener(GlitchRaid plugin, RaidManager manager, RedPortalManager portals) {
        this.plugin = plugin;
        this.manager = manager;
        this.portals = portals;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equalsIgnoreCase(manager.getHubWorld())) return;
        if (!portals.isEnabled()) return;
        // When a region is configured, only it teleports (stray end portals stay dead).
        if (portals.hasRegion() && !portals.contains(event.getFrom())) return;

        long now = System.currentTimeMillis();
        long last = cooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < portals.cooldownSeconds() * 1000L) {
            event.setCancelled(true);
            return;
        }
        cooldown.put(player.getUniqueId(), now);

        World red = Bukkit.getWorld(manager.getAutoStartWorld());
        if (red == null) {
            event.setCancelled(true);
            player.sendMessage(MM.deserialize("<red>The rift is dormant (raid world missing).</red>"));
            return;
        }
        event.setCancelled(true);
        Location dest;
        try {
            dest = red.getSpawnLocation();
        } catch (Exception e) {
            player.sendMessage(MM.deserialize("<red>The rift is dormant (no spawn).</red>"));
            return;
        }
        Location from = player.getLocation().clone();
        FoliaScheduler.teleportEntity(player, plugin, dest);
        player.sendMessage(MM.deserialize("<light_purple><bold>The Glitch takes you.</bold></light_purple>"));
        try {
            dest.getWorld().playSound(dest, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 1.2f);
            dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.2);
            from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 30, 0.5, 1.0, 0.5, 0.2);
        } catch (Exception ignored) {}
    }
}
