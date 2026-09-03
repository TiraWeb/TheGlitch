package com.theglitch.glitchitems;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ResidualGlitchManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchItems plugin;
    private final NamespacedKey stacksKey;
    private final NamespacedKey lastKey;
    private final NamespacedKey eliteKey;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastShownStacks = new ConcurrentHashMap<>();
    private final Map<UUID, SavedXp> savedXp = new ConcurrentHashMap<>();

    private record SavedXp(int level, float exp) {}

    // Cached config
    private volatile Set<String> enabledWorlds = Set.of("glitch_red");
    private volatile int intervalMinutes = 5;
    private volatile int maxStacks = 8;
    private volatile int damageTakenPerStack = 5;
    private volatile int lootLuckPerStack = 5;
    private volatile int eliteHuntStacks = 5;
    private volatile long eliteSpawnIntervalMs = 10 * 60_000L;
    private volatile double payoutPerStack = 0.10;
    private volatile String bossbarTemplate = "<red>Residual Glitch: <white>{stacks}/{max}</white> <dark_gray>(<gray>+{dmg}% dmg taken, +{payout}% payout</gray>)</dark_gray>";
    private volatile boolean showXpBar = false;
    private volatile String eliteMob = "GlitchSentinel";
    private volatile int eliteSpawnRadius = 12;
    private volatile boolean eliteAnnounce = true;
    private volatile Component eliteMessageComponent = MM.deserialize("<dark_red><bold>An elite hunts you.</bold></dark_red> <gray>Something powerful is closing in.</gray>");
    private volatile int rarityUpgradePercentPerStack = 2;

    public ResidualGlitchManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.stacksKey = new NamespacedKey(plugin, "glitch_stacks");
        this.lastKey = new NamespacedKey(plugin, "glitch_last");
        this.eliteKey = new NamespacedKey(plugin, "glitch_elite_last");
        reload();
    }

    public void reload() {
        enabledWorlds = Set.copyOf(plugin.getConfig().getStringList("residual-glitch.enabled-worlds"));
        if (enabledWorlds.isEmpty()) enabledWorlds = Set.of("glitch_red");
        intervalMinutes = Math.max(1, plugin.getConfig().getInt("residual-glitch.stack-interval-minutes", 5));
        maxStacks = Math.max(1, plugin.getConfig().getInt("residual-glitch.max-stacks", 8));
        damageTakenPerStack = plugin.getConfig().getInt("residual-glitch.damage-taken-per-stack", 5);
        lootLuckPerStack = plugin.getConfig().getInt("residual-glitch.loot-luck-per-stack", 5);
        eliteHuntStacks = plugin.getConfig().getInt("residual-glitch.elite-hunt-stacks", 5);
        payoutPerStack = plugin.getConfig().getDouble("residual-glitch.payout-per-stack", 0.10);
        bossbarTemplate = plugin.getConfig().getString("residual-glitch.bossbar-title", bossbarTemplate);
        showXpBar = plugin.getConfig().getBoolean("residual-glitch.show-xp-bar", false);
        eliteMob = plugin.getConfig().getString("elite-hunt.mob", "GlitchSentinel");
        eliteSpawnIntervalMs = Math.max(1, plugin.getConfig().getInt("elite-hunt.spawn-interval-minutes", 10)) * 60_000L;
        eliteSpawnRadius = plugin.getConfig().getInt("elite-hunt.spawn-radius", 12);
        eliteAnnounce = plugin.getConfig().getBoolean("elite-hunt.announce", true);
        String msg = plugin.getConfig().getString("elite-hunt.message",
                "<dark_red><bold>An elite hunts you.</bold></dark_red> <gray>Something powerful is closing in.</gray>");
        try {
            eliteMessageComponent = MM.deserialize(msg);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid elite-hunt.message MiniMessage: " + e.getMessage());
        }
        rarityUpgradePercentPerStack = plugin.getConfig().getInt("residual-glitch.rarity-upgrade-percent-per-stack", 2);
        lastShownStacks.clear();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                restoreXp(event.getPlayer());
            }
        }, plugin);
    }

    private void tick() {
        bars.keySet().removeIf(id -> plugin.getServer().getPlayer(id) == null);
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
                long intervalMs = intervalMinutes * 60_000L;
                int due = (int) ((now - last) / intervalMs);
                if (due > 0) {
                    int oldStacks = getStacks(player);
                    int stacks = Math.min(oldStacks + due, maxStacks);
                    setStacks(player, stacks);
                    setLast(player, last + due * intervalMs);
                    if (oldStacks < eliteHuntStacks && stacks >= eliteHuntStacks) {
                        player.sendMessage(MM.deserialize(
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
        Integer last = lastShownStacks.get(player.getUniqueId());
        BossBar existing = bars.get(player.getUniqueId());
        // Dirty-check: skip deserialize + bossbar update if stacks unchanged and bar exists
        if (last != null && last == stacks && existing != null) {
            return;
        }
        String text = bossbarTemplate
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{max}", String.valueOf(maxStacks))
                .replace("{dmg}", String.valueOf(stacks * damageTakenPerStack))
                .replace("{payout}", String.valueOf((int) (stacks * payoutPerStack * 100)));
        Component title;
        try {
            title = MM.deserialize(text);
        } catch (Exception e) {
            title = Component.text("Residual Glitch: " + stacks + "/" + maxStacks);
        }

        BossBar bar = existing;
        BossBar.Overlay overlay = stacks == maxStacks && maxStacks == 10 ? BossBar.Overlay.NOTCHED_10
                : (maxStacks == 8 ? BossBar.Overlay.NOTCHED_10 : BossBar.Overlay.PROGRESS);
        // 8 stacks maps cleanly onto 10 notches; 10 stacks is 1:1. Sub 8 still benefits from segmented look (rare HUD).
        // Progress still drives fill; notches are visual segmentation.
        if (bar == null) {
            bar = BossBar.bossBar(title, 0.0f, BossBar.Color.RED, overlay);
            // Max stacks → subtle dread: darken sky + fog (GlitchHUD config mirrors this, but direct here is reliable)
            if (stacks >= maxStacks) {
                try { bar.addFlag(BossBar.Flag.DARKEN_SCREEN); } catch (Exception ignored) {}
            }
            player.showBossBar(bar);
            bars.put(player.getUniqueId(), bar);
        } else {
            bar.name(title);
            try { bar.overlay(overlay); } catch (Exception ignored) {}
            // Toggle DARKEN_SCREEN only at cap so it doesn't linger
            try {
                if (stacks >= maxStacks) bar.addFlag(BossBar.Flag.DARKEN_SCREEN);
                else bar.removeFlag(BossBar.Flag.DARKEN_SCREEN);
            } catch (Exception ignored) {}
        }
        bar.progress((float) Math.min(1.0, (double) stacks / maxStacks));
        bar.color(stacks >= eliteHuntStacks ? BossBar.Color.PURPLE : BossBar.Color.RED);
        lastShownStacks.put(player.getUniqueId(), stacks);

        if (showXpBar) {
            savedXp.putIfAbsent(player.getUniqueId(), new SavedXp(player.getLevel(), player.getExp()));
            player.setLevel(stacks);
            player.setExp((float) stacks / maxStacks);
        } else {
            restoreXp(player);
        }
    }

    private void restoreXp(Player player) {
        SavedXp saved = savedXp.remove(player.getUniqueId());
        if (saved != null) {
            player.setLevel(saved.level());
            player.setExp(saved.exp());
        }
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        lastShownStacks.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
        restoreXp(player);
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
        return 1.0 + getStacks(player) * payoutPerStack;
    }

    public double getDamageTakenMultiplier(Player player) {
        return 1.0 + getStacks(player) * damageTakenPerStack / 100.0;
    }

    public int lootLuckBonus(Player player) {
        return getStacks(player) * lootLuckPerStack;
    }

    // Getters for other plugins / expansions (cached, no config lookup)
    public int getMaxStacks() { return maxStacks; }
    public int getDamageTakenPerStack() { return damageTakenPerStack; }
    public int getLootLuckPerStack() { return lootLuckPerStack; }
    public double getPayoutPerStack() { return payoutPerStack; }
    public int getRarityUpgradePercentPerStack() { return rarityUpgradePercentPerStack; }
    public boolean isEnabledWorld(String world) { return enabledWorlds.contains(world); }

    private void maybeSpawnElite(Player player) {
        if (getStacks(player) < eliteHuntStacks) return;

        long last = player.getPersistentDataContainer()
                .getOrDefault(eliteKey, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        if (last != 0L && now - last < eliteSpawnIntervalMs) return;

        player.getPersistentDataContainer().set(eliteKey, PersistentDataType.LONG, now);
        spawnElite(player);
    }

    private void spawnElite(Player player) {
        String mob = eliteMob;
        int radius = eliteSpawnRadius;
        Location base = player.getLocation();
        int x = base.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
        int z = base.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);

        String cmd = "mm spawn " + mob + " " + base.getWorld().getName()
                + " " + x + " " + base.getBlockY() + " " + z;
        boolean dispatched = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
        if (!dispatched) {
            plugin.getLogger().warning("Elite hunt: could not dispatch '" + cmd + "' — MythicMobs loaded?");
            return;
        }
        if (eliteAnnounce) {
            player.sendMessage(eliteMessageComponent);
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
        lastShownStacks.clear();
        for (UUID id : savedXp.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) restoreXp(player);
        }
    }
}
