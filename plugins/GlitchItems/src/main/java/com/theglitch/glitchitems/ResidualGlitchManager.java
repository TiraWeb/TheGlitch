package com.theglitch.glitchitems;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ResidualGlitchManager {

    private final GlitchItems plugin;
    private final NamespacedKey stacksKey;
    private final NamespacedKey lastKey;
    private final NamespacedKey eliteKey;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public ResidualGlitchManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.stacksKey = new NamespacedKey(plugin, "glitch_stacks");
        this.lastKey = new NamespacedKey(plugin, "glitch_last");
        this.eliteKey = new NamespacedKey(plugin, "glitch_elite_last");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isEnabledWorld(player.getWorld().getName())) {
                hide(player);
                continue;
            }
            long now = System.currentTimeMillis();
            long last = getLast(player);
            if (last == 0L) {
                setLast(player, now);
            } else {
                int due = (int) ((now - last) / (intervalMinutes() * 60_000L));
                if (due > 0) {
                    int oldStacks = getStacks(player);
                    int stacks = Math.min(oldStacks + due, maxStacks());
                    setStacks(player, stacks);
                    setLast(player, now);
                    if (oldStacks < eliteHuntStacks() && stacks >= eliteHuntStacks()) {
                        player.sendMessage(MiniMessage.miniMessage().deserialize(
                                "<dark_red>You have " + stacks + " stacks of Residual Glitch — something elite is hunting you.</dark_red>"));
                    }
                }
            }
            maybeSpawnElite(player);
            show(player);
        }
    }

    private void show(Player player) {
        int stacks = getStacks(player);
        String template = plugin.getConfig().getString("residual-glitch.bossbar-title",
                "<red>Residual Glitch: <white>{stacks}/{max}</white> <dark_gray>(<gray>+{dmg}% dmg taken, +{payout}% payout</gray>)</dark_gray>");
        String text = template
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{max}", String.valueOf(maxStacks()))
                .replace("{dmg}", String.valueOf(stacks * damageTakenPerStack()))
                .replace("{payout}", String.valueOf((int) (stacks * payoutPerStack() * 100)));
        Component title = MiniMessage.miniMessage().deserialize(text);

        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, 0.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            player.showBossBar(bar);
            bars.put(player.getUniqueId(), bar);
        }
        bar.name(title);
        bar.progress((float) Math.min(1.0, (double) stacks / maxStacks()));
        bar.color(stacks >= eliteHuntStacks() ? BossBar.Color.PURPLE : BossBar.Color.RED);

        if (plugin.getConfig().getBoolean("residual-glitch.show-xp-bar", false)) {
            player.setLevel(stacks);
            player.setExp((float) stacks / maxStacks());
        }
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        if (player.getLevel() != 0 || player.getExp() > 0.0f) {
            player.setLevel(0);
            player.setExp(0.0f);
        }
    }

    public int getStacks(Player player) {
        return player.getPersistentDataContainer().getOrDefault(stacksKey, PersistentDataType.INTEGER, 0);
    }

    public void setStacks(Player player, int stacks) {
        player.getPersistentDataContainer().set(stacksKey, PersistentDataType.INTEGER, stacks);
    }

    private long getLast(Player player) {
        return player.getPersistentDataContainer().getOrDefault(lastKey, PersistentDataType.LONG, 0L);
    }

    private void setLast(Player player, long time) {
        player.getPersistentDataContainer().set(lastKey, PersistentDataType.LONG, time);
    }

    public void clear(Player player) {
        setStacks(player, 0);
        setLast(player, 0L);
        player.getPersistentDataContainer().remove(eliteKey);
        hide(player);
    }

    public double getPayoutMultiplier(Player player) {
        return 1.0 + getStacks(player) * payoutPerStack();
    }

    public double getDamageTakenMultiplier(Player player) {
        return 1.0 + getStacks(player) * damageTakenPerStack() / 100.0;
    }

    public int lootLuckBonus(Player player) {
        return getStacks(player) * lootLuckPerStack();
    }

    /**
     * Elite hunt consumer (design ITEM_SYSTEM.md §6): once a player holds
     * elite-hunt stacks or more, an elite mob spawns near them immediately,
     * then again every spawn-interval-minutes while they stay at that level.
     */
    private void maybeSpawnElite(Player player) {
        if (getStacks(player) < eliteHuntStacks()) return;

        long last = player.getPersistentDataContainer()
                .getOrDefault(eliteKey, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        long interval = eliteSpawnIntervalMinutes() * 60_000L;
        if (last != 0L && now - last < interval) return;

        player.getPersistentDataContainer().set(eliteKey, PersistentDataType.LONG, now);
        spawnElite(player);
    }

    private void spawnElite(Player player) {
        String mob = plugin.getConfig().getString("elite-hunt.mob", "GlitchSentinel");
        int radius = plugin.getConfig().getInt("elite-hunt.spawn-radius", 12);
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Location base = player.getLocation();
        int x = base.getBlockX() + rand.nextInt(-radius, radius + 1);
        int z = base.getBlockZ() + rand.nextInt(-radius, radius + 1);

        String cmd = "mm spawn " + mob + " " + base.getWorld().getName()
                + " " + x + " " + base.getBlockY() + " " + z;
        boolean dispatched = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
        if (!dispatched) {
            plugin.getLogger().warning("Elite hunt: could not dispatch '" + cmd + "' — MythicMobs loaded?");
            return;
        }
        if (plugin.getConfig().getBoolean("elite-hunt.announce", true)) {
            String msg = plugin.getConfig().getString(
                    "elite-hunt.message",
                    "<dark_red><bold>An elite hunts you.</bold></dark_red> <gray>Something powerful is closing in.</gray>");
            player.sendMessage(MiniMessage.miniMessage().deserialize(msg));
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
            org.bukkit.entity.Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bars.clear();
    }

    private boolean isEnabledWorld(String world) {
        return enabledWorlds().contains(world);
    }

    private List<String> enabledWorlds() {
        return plugin.getConfig().getStringList("residual-glitch.enabled-worlds");
    }

    private int intervalMinutes() {
        return plugin.getConfig().getInt("residual-glitch.stack-interval-minutes", 5);
    }

    private int maxStacks() {
        return plugin.getConfig().getInt("residual-glitch.max-stacks", 8);
    }

    private int damageTakenPerStack() {
        return plugin.getConfig().getInt("residual-glitch.damage-taken-per-stack", 5);
    }

    private int lootLuckPerStack() {
        return plugin.getConfig().getInt("residual-glitch.loot-luck-per-stack", 5);
    }

    private int eliteHuntStacks() {
        return plugin.getConfig().getInt("residual-glitch.elite-hunt-stacks", 5);
    }

    private int eliteSpawnIntervalMinutes() {
        return plugin.getConfig().getInt("elite-hunt.spawn-interval-minutes", 10);
    }

    private double payoutPerStack() {
        return plugin.getConfig().getDouble("residual-glitch.payout-per-stack", 0.10);
    }
}
