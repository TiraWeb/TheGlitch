package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles death recap, quit handling, and simple loot accounting.
 */
public final class RaidListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RaidManager manager;

    public RaidListener(GlitchRaid plugin, RaidManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!manager.isInRaid(player.getUniqueId())) {
            return;
        }
        manager.incrementDeaths(player.getUniqueId());
        RaidSession session = manager.getSession(player.getUniqueId());
        int deaths = session != null ? session.getDeaths() : 1;

        String recapRaw = plugin.getConfig().getString("messages.death-recap",
                "<red>Death recap: <white>You died! <gray>(Death #<deaths> this raid)</gray></white></red>");
        player.sendMessage(MM.deserialize(recapRaw.replace("<deaths>", String.valueOf(deaths))));

        // Broadcast to party members
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
        // If leader quits, end entire raid
        if (session.getLeader().equals(player.getUniqueId())) {
            manager.endRaid(player.getUniqueId(), RaidEndReason.LEADER_QUIT);
        } else {
            // Non-leader quit: remove only that member
            manager.removeMember(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (!manager.isInRaid(killer.getUniqueId())) {
            return;
        }
        // Simple loot accounting stub: +10 per hostile kill
        // Could be enhanced with entity-type based values
        int value = 10;
        String typeName = event.getEntity().getType().name();
        // Slight bonus for bosses/minibosses by name
        if (typeName.contains("BOSS") || typeName.contains("ELDER") || typeName.contains("WARDEN") || typeName.contains("ENDER_DRAGON")) {
            value = 50;
        } else if (event.getEntity() instanceof org.bukkit.entity.Monster) {
            value = 10;
        }

        manager.addLoot(killer.getUniqueId(), value);

        String lootRaw = plugin.getConfig().getString("messages.loot-added", "<gold>+<amount> loot value</gold>");
        killer.sendActionBar(MM.deserialize(lootRaw.replace("<amount>", String.valueOf(value))));
    }
}
