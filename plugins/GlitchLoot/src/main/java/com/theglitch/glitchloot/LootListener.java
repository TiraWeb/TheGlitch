package com.theglitch.glitchloot;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls contextual bonus loot on monster deaths (ROADMAP 5.9.7).
 * Every path is guarded — a failure here must never break vanilla death drops.
 */
public final class LootListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Base chance percent before the adaptive dry-streak bonus. */
    private static final double BASE_CHANCE_PERCENT = 8.0;

    private static final int RARE_WEIGHT = 70;
    private static final int EPIC_WEIGHT = 25;
    private static final int LEGENDARY_WEIGHT = 5;

    private final GlitchLoot plugin;
    private final LootEngine engine;

    public LootListener(GlitchLoot plugin, LootEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        try {
            handle(event);
        } catch (Exception e) {
            plugin.getLogger().warning("GlitchLoot roll failed (death drops unaffected): " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            engine.handleQuit(event.getPlayer().getUniqueId());
        } catch (Exception ignored) {
        }
    }

    private void handle(EntityDeathEvent event) throws Exception {
        if (!(event.getEntity() instanceof Monster victim)) {
            return;
        }
        if (!engine.isEnabledWorld(victim.getWorld())) {
            return;
        }
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        long now = System.currentTimeMillis();
        double bonus = engine.isAdaptiveEnabled() ? engine.bonusPercent(killer, now) : 0;
        double chance = BASE_CHANCE_PERCENT + bonus;
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble(100.0) >= chance) {
            engine.recordDryRoll(killer, now);
            return;
        }

        if (engine.isAntiFunnelEnabled() && engine.withinAntiFunnel(killer, now)) {
            killer.sendActionBar(plugin.getMessages().comp(
                    "cooldown-message", "<gray>Loot surge cooling down here.</gray>"));
            engine.recordDryRoll(killer, now);
            return;
        }

        String rarity = rollRarity(rand);
        if (engine.powerRemaining() < engine.costOf(rarity)) {
            killer.sendMessage(plugin.getMessages().comp(
                    "power-capped", "<red>The Glitch is exhausted here — try again later.</red>"));
            engine.recordDryRoll(killer, now);
            return;
        }

        ItemStack item = buildItem(rarity);
        Location loc = victim.getLocation();
        victim.getWorld().dropItemNaturally(loc, item);

        engine.recordLoot(killer, rarity, now);

        killer.sendActionBar(plugin.getMessages().comp(
                "bonus-applied", "<dark_purple><bold>GLITCH</bold> <gray>surge!</gray>"));
    }

    private String rollRarity(ThreadLocalRandom rand) {
        int total = RARE_WEIGHT + EPIC_WEIGHT + LEGENDARY_WEIGHT;
        int pick = rand.nextInt(total);
        if ((pick -= RARE_WEIGHT) < 0) {
            return "rare";
        }
        if ((pick -= EPIC_WEIGHT) < 0) {
            return "epic";
        }
        return "legendary";
    }

    private ItemStack buildItem(String rarity) throws Exception {
        Material material;
        String nameRaw;
        switch (rarity.toLowerCase(Locale.ROOT)) {
            case "epic" -> {
                material = Material.AMETHYST_SHARD;
                nameRaw = "<light_purple><bold>Glitch-touched Amethyst Shard</bold></light_purple>";
            }
            case "legendary" -> {
                material = Material.DIAMOND;
                nameRaw = "<aqua><bold>Glitch-touched Diamond</bold></aqua>";
            }
            default -> {
                material = Material.EMERALD;
                nameRaw = "<green><bold>Glitch-touched Emerald</bold></green>";
            }
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MM.deserialize(nameRaw));
            item.setItemMeta(meta);
        }
        return item;
    }
}
