package com.theglitch.glitchtutorial;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.ScoreboardTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;

/**
 * Echo, the tutorial guide: one Citizens NPC at points.echo in each tutorial world (the
 * template and every player's instance). In-memory, never saved; Minecraft default skin,
 * never a real player's. Right-click repeats the current step's lines.
 */
final class EchoNpc implements Listener {

    private final TutorialManager manager;
    private final NPCRegistry registry;
    private final Map<String, NPC> byWorld = new HashMap<>();

    EchoNpc(GlitchTutorial plugin, TutorialManager manager) {
        this.manager = manager;
        NPCRegistry existing = CitizensAPI.getNamedNPCRegistry("glitchtutorial");
        this.registry = existing != null ? existing : CitizensAPI.createInMemoryNPCRegistry("glitchtutorial");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Spawns (or moves) Echo in this tutorial world. */
    void spawn(World w) {
        Location at = manager.point(w, "echo");
        if (at == null) return;
        NPC npc = byWorld.get(w.getName());
        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, "Echo");
            npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
            npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
            npc.setProtected(true);
            npc.getOrAddTrait(SkinTrait.class).setFetchDefaultSkin(false);
            npc.getOrAddTrait(ScoreboardTrait.class).setColor(ChatColor.AQUA);
            npc.getOrAddTrait(LookClose.class).lookClose(true);
            byWorld.put(w.getName(), npc);
        }
        if (npc.isSpawned()) npc.teleport(at, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        else npc.spawn(at);
    }

    /** Re-places Echo in every loaded tutorial world (after setpoint / reload). */
    void respawnAll() {
        for (World w : Bukkit.getWorlds()) {
            if (manager.instances().isTutorialWorld(w.getName())) spawn(w);
        }
    }

    /** Removes Echo from a world that is about to be unloaded. */
    void remove(World w) {
        NPC npc = byWorld.remove(w.getName());
        if (npc != null) {
            try {
                npc.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    void removeAll() {
        for (NPC npc : byWorld.values()) {
            try {
                npc.destroy();
            } catch (Exception ignored) {
            }
        }
        byWorld.clear();
    }

    @EventHandler
    public void onClick(NPCRightClickEvent event) {
        if (byWorld.containsValue(event.getNPC())) manager.replay(event.getClicker());
    }
}
