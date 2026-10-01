package com.theglitch.glitchbots;

import com.theglitch.common.Bots;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rogue deaths: drop the looted bag plus a share of the gear, announce the kill to
 * real players in that world, and remove the NPC. Kill bounty + Raider Rank credit
 * are handled by GlitchRaid (its EntityDeathEvent bounty handler knows bots).
 */
final class BotListener implements Listener {

    private final GlitchBots plugin;

    BotListener(GlitchBots plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRogueDeath(PlayerDeathEvent event) {
        Player body = event.getEntity();
        if (!Bots.isBot(body)) return;
        NPC npc = plugin.director().registry().getNPC(body);
        if (npc == null) return; // someone else's NPC
        RogueBot bot = plugin.director().bot(npc.getUniqueId());
        if (bot == null) return;

        List<ItemStack> drops = event.getDrops();
        drops.clear();
        for (ItemStack it : bot.bag) {
            if (it != null && !it.getType().isAir()) drops.add(it);
        }
        for (ItemStack it : bot.wornItems()) {
            if (ThreadLocalRandom.current().nextDouble() < plugin.cfg().gearDropChance) drops.add(it);
        }
        event.setDroppedExp(0);
        event.setKeepInventory(false);
        event.deathMessage(null);

        Player killer = body.getKiller();
        if (killer != null && !Bots.isBot(killer)) {
            String msg = "<gray><white>" + killer.getName() + "</white> eliminated Rogue <red>" + bot.handle + "</red>.</gray>";
            for (Player p : Bots.realPlayers(body.getWorld())) p.sendMessage(plugin.mm().deserialize(msg));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.director().remove(bot), 2L);
    }
}
