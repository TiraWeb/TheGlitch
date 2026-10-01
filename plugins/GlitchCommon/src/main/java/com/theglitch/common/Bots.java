package com.theglitch.common;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Rogue Raiders (GlitchBots) are Citizens player NPCs — real {@link Player}
 * objects to Bukkit, so join/death/world-change/damage events fire for them and
 * {@code world.getPlayers()} includes them. Every player-facing system must skip
 * them: no raid sessions, rank, quests, class passives, stash or player counts.
 * Citizens (and most NPC plugins) mark their entities with the "NPC" metadata.
 */
public final class Bots {

    private Bots() {
    }

    public static boolean isBot(Entity entity) {
        return entity != null && entity.hasMetadata("NPC");
    }

    /** Real (non-NPC) players in a world. */
    public static List<Player> realPlayers(World world) {
        List<Player> out = new ArrayList<>();
        if (world == null) return out;
        for (Player p : world.getPlayers()) {
            if (!isBot(p)) out.add(p);
        }
        return out;
    }

    /** Real (non-NPC) online players. */
    public static List<Player> realOnline() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isBot(p)) out.add(p);
        }
        return out;
    }
}
