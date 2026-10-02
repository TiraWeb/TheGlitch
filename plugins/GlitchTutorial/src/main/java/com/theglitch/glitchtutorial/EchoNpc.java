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
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Echo, the tutorial guide: a Citizens NPC at points.echo (in-memory, never saved; Minecraft
 * default skin, never a real player's). Right-click repeats the current step's lines.
 */
final class EchoNpc implements Listener {

    private final GlitchTutorial plugin;
    private final TutorialManager manager;
    private final NPCRegistry registry;
    private NPC npc;

    EchoNpc(GlitchTutorial plugin, TutorialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        NPCRegistry existing = CitizensAPI.getNamedNPCRegistry("glitchtutorial");
        this.registry = existing != null ? existing : CitizensAPI.createInMemoryNPCRegistry("glitchtutorial");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    void spawn() {
        Location at = manager.point("echo");
        if (at == null) return;
        if (npc == null) {
            npc = registry.createNPC(EntityType.PLAYER, "Echo");
            npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
            npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
            npc.setProtected(true);
            npc.getOrAddTrait(SkinTrait.class).setFetchDefaultSkin(false);
            npc.getOrAddTrait(ScoreboardTrait.class).setColor(ChatColor.AQUA);
            npc.getOrAddTrait(LookClose.class).lookClose(true);
        }
        if (npc.isSpawned()) npc.teleport(at, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        else npc.spawn(at);
    }

    void remove() {
        if (npc != null) {
            try {
                npc.destroy();
            } catch (Exception ignored) {
            }
            npc = null;
        }
    }

    @EventHandler
    public void onClick(NPCRightClickEvent event) {
        if (npc != null && event.getNPC() == npc) manager.replay(event.getClicker());
    }
}
