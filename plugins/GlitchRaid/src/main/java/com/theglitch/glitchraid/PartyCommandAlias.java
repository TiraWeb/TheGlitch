package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * One party for raids and dungeons: /party is the raid party. MythicDungeons
 * registers its own /party (and /dparty, /recruit) for a separate party system;
 * those are rerouted here so players can't end up in two different parties.
 */
public final class PartyCommandAlias implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> PARTY = Set.of("party", "dparty", "mythicdungeons:party", "mythicdungeons:dparty");
    private static final Set<String> RECRUIT = Set.of("recruit", "mythicdungeons:recruit");
    private static final Set<String> LEAVE = Set.of("leave", "mythicdungeons:leave");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg.length() < 2) return;
        String[] parts = msg.substring(1).trim().split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        // /leave in a raid or a dungeon goes through /abandon's confirm (MD's own /leave has none,
        // and outside MD it would mean nothing in a Red Zone)
        if (LEAVE.contains(label)) {
            String w = event.getPlayer().getWorld().getName();
            GlitchRaid raid = GlitchRaid.getInstance();
            if (raid != null && (raid.getRaidManager().isRedWorld(w) || com.theglitch.glitchraid.gui.DungeonSelectGUI.isDungeonWorld(w))) {
                event.setMessage("/abandon");
            }
            return;
        }
        if (RECRUIT.contains(label)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(MM.deserialize("<gray>Build your team with <yellow>/party invite <player></yellow> — the same party goes into raids and dungeons.</gray>"));
            return;
        }
        if (!PARTY.contains(label)) return;
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "list";
        String rest = parts.length > 2 ? " " + String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length)) : "";
        switch (sub) {
            case "disband" -> sub = "leave";
            case "join" -> sub = "accept";
            case "info", "members" -> sub = "list";
            case "create" -> {
                event.setCancelled(true);
                event.getPlayer().sendMessage(MM.deserialize("<gray>Parties are created automatically — just <yellow>/party invite <player></yellow>.</gray>"));
                return;
            }
            default -> { }
        }
        event.setMessage("/raid " + sub + rest);
    }
}
