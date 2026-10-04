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

    /** Rogues path over ledges a player would avoid — no fall damage, so they don't die walking. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRogueFall(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!Bots.isBot(event.getEntity())) return;
        NPC npc = plugin.director().registry().getNPC(event.getEntity());
        if (npc != null && plugin.director().bot(npc.getUniqueId()) != null) event.setCancelled(true);
    }

    /** A raider hitting a peaceful rogue: friendly ones fight back from then on, betrayers drop the act. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRogueHurt(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!Bots.isBot(event.getEntity())) return;
        org.bukkit.entity.Entity d = event.getDamager();
        if (d instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) d = shooter;
        if (!(d instanceof Player hitter) || Bots.isBot(hitter)) return;
        NPC npc = plugin.director().registry().getNPC(event.getEntity());
        RogueBot bot = npc == null ? null : plugin.director().bot(npc.getUniqueId());
        if (bot != null) bot.onHurtBy(hitter);
    }

    /** Raiders can talk to rogues: chat near one (or say its name) and it answers in character. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (plugin.director().inWorld(player.getWorld().getName()).isEmpty()) return; // any world with rogues (incl. the tutorial)
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.chat().onPlayerChat(player, text);
        });
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
        if (bot.tutorialTarget != null) drops.clear(); // training rogue: GlitchTutorial hands out the (tagged) loot
        else for (ItemStack it : bot.wornItems()) {
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
