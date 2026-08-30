package com.theglitch.glitchloot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core adaptive loot logic (ROADMAP 5.9.7):
 * - dry-streak adaptive bonus (with staleness window)
 * - hourly server-wide item power budget
 * - per-player anti-funnel cooldowns
 */
public final class LootEngine {

    private final GlitchLoot plugin;

    // Player state
    private final Map<UUID, Integer> dryStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastLootTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> powerSpent = new ConcurrentHashMap<>();
    /** Last dry-roll time — used to expire stale streaks after window-seconds. */
    private final Map<UUID, Long> lastRollTime = new ConcurrentHashMap<>();

    private BukkitTask powerResetTask;

    // Cached config values
    private volatile Set<String> enabledWorlds = Set.of("glitch_red", "glitch_pve");
    private volatile boolean adaptiveEnabled = true;
    private volatile int windowSeconds = 600;
    private volatile int bonusPercentPerRoll = 2;
    private volatile int maxBonusPercent = 25;
    private volatile int luckDecayOnLoot = 50;
    private volatile boolean antiFunnelEnabled = true;
    private volatile long cooldownSeconds = 120;
    private volatile boolean powerBudgetEnabled = true;
    private volatile int maxPowerPerHour = 400;
    private volatile Map<String, Integer> costs = Map.of("rare", 20, "epic", 60, "legendary", 150);

    public LootEngine(GlitchLoot plugin) {
        this.plugin = plugin;
        reload();
        startHourlyReset();
    }

    /** Re-caches all config values. Safe to call at runtime (reload command). */
    public void reload() {
        try {
            Set<String> worlds = Set.copyOf(plugin.getConfig().getStringList("loot.enabled-worlds"));
            enabledWorlds = worlds.isEmpty() ? Set.of("glitch_red", "glitch_pve") : worlds;

            adaptiveEnabled = plugin.getConfig().getBoolean("adaptive.enabled", true);
            windowSeconds = Math.max(0, plugin.getConfig().getInt("adaptive.window-seconds", 600));
            bonusPercentPerRoll = Math.max(0, plugin.getConfig().getInt("adaptive.dry-streak-bonus-percent-per-roll", 2));
            maxBonusPercent = Math.max(0, plugin.getConfig().getInt("adaptive.max-bonus-percent", 25));
            luckDecayOnLoot = Math.max(0, Math.min(100, plugin.getConfig().getInt("adaptive.luck-decay-on-loot", 50)));

            antiFunnelEnabled = plugin.getConfig().getBoolean("anti-funnel.enabled", true);
            cooldownSeconds = Math.max(0, plugin.getConfig().getInt("anti-funnel.per-player-rarity-cooldown-seconds", 120));

            powerBudgetEnabled = plugin.getConfig().getBoolean("power-budget.enabled", true);
            maxPowerPerHour = Math.max(0, plugin.getConfig().getInt("power-budget.max-power-per-hour", 400));
            Map<String, Integer> loadedCosts = new HashMap<>();
            loadedCosts.put("rare", Math.max(0, plugin.getConfig().getInt("power-budget.rare-cost", 20)));
            loadedCosts.put("epic", Math.max(0, plugin.getConfig().getInt("power-budget.epic-cost", 60)));
            loadedCosts.put("legendary", Math.max(0, plugin.getConfig().getInt("power-budget.legendary-cost", 150)));
            costs = loadedCosts;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cache GlitchLoot config: " + e.getMessage());
        }
        plugin.getLogger().info("LootEngine reloaded (worlds=" + enabledWorlds + ", bonus/roll=" + bonusPercentPerRoll
                + "%, maxBonus=" + maxBonusPercent + "%, decay=" + luckDecayOnLoot
                + "%, budget/h=" + maxPowerPerHour + ", cooldown=" + cooldownSeconds + "s).");
    }

    private void startHourlyReset() {
        if (powerResetTask != null) {
            powerResetTask.cancel();
        }
        powerResetTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!powerSpent.isEmpty()) {
                plugin.getLogger().info("Hourly power budget reset (spent " + powerSpentThisHour() + "/" + maxPowerPerHour + ").");
            }
            powerSpent.clear();
        }, 20L * 3600, 20L * 3600);
    }

    /**
     * Current adaptive bonus percent for the player:
     * min(dryStreak * perRoll, maxBonus). Stale streaks (outside the window) count as 0.
     */
    public int bonusPercent(Player p) {
        UUID id = p.getUniqueId();
        int streak = currentStreak(id);
        return Math.min(streak * bonusPercentPerRoll, maxBonusPercent);
    }

    /** Records a failed roll — increments the dry streak (respecting the staleness window). */
    public void recordDryRoll(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastRollTime.get(id);
        // windowSeconds <= 0 means "no window" — the streak never goes stale
        // (mirrors currentStreak) so the adaptive bonus is not capped every roll
        boolean stale = last == null || (windowSeconds > 0 && now - last > windowSeconds * 1000L);
        int next = stale ? 1 : currentStreak(id) + 1;
        dryStreak.put(id, next);
        lastRollTime.put(id, now);
    }

    /**
     * Records a successful loot event.
     *
     * @return false if the power budget is enabled and exhausted for this hour
     *         (nothing was spent and no cooldown applied); true when loot is allowed.
     */
    public boolean recordLoot(Player p, String rarityId) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (powerBudgetEnabled) {
            int cost = costOf(rarityId);
            if (powerRemaining() < cost) {
                return false;
            }
            powerSpent.merge(id, cost, Integer::sum);
        }

        int streak = dryStreak.getOrDefault(id, 0);
        double factor = Math.max(0, 100 - luckDecayOnLoot) / 100.0;
        int decayed = (int) Math.floor(streak * factor);
        dryStreak.put(id, Math.max(0, decayed));
        lastLootTime.put(id, now);
        lastRollTime.remove(id);
        return true;
    }

    /** True while the player is inside their anti-funnel cooldown window. */
    public boolean withinAntiFunnel(Player p) {
        if (cooldownSeconds <= 0) {
            return false;
        }
        Long last = lastLootTime.get(p.getUniqueId());
        return last != null && System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }

    /** Remaining unspent power for the current hourly window. */
    public int powerRemaining() {
        return Math.max(0, maxPowerPerHour - powerSpentThisHour());
    }

    /** Power cost of a rarity id ("rare"/"epic"/"legendary"); unknown ids are treated as unaffordable. */
    public int costOf(String rarityId) {
        if (rarityId == null) {
            return Integer.MAX_VALUE;
        }
        Integer cost = costs.get(rarityId.toLowerCase(Locale.ROOT));
        return cost != null ? cost : Integer.MAX_VALUE;
    }

    public boolean isEnabledWorld(World world) {
        return world != null && enabledWorlds.contains(world.getName());
    }

    private int currentStreak(UUID id) {
        if (windowSeconds > 0) {
            Long last = lastRollTime.get(id);
            if (last != null && System.currentTimeMillis() - last > windowSeconds * 1000L) {
                return 0;
            }
        }
        return dryStreak.getOrDefault(id, 0);
    }

    private int powerSpentThisHour() {
        int total = 0;
        for (int value : powerSpent.values()) {
            total += value;
        }
        return total;
    }

    public boolean isAdaptiveEnabled() {
        return adaptiveEnabled;
    }

    public boolean isAntiFunnelEnabled() {
        return antiFunnelEnabled;
    }

    public boolean isPowerBudgetEnabled() {
        return powerBudgetEnabled;
    }

    public int getDryStreak(UUID id) {
        return currentStreak(id);
    }

    public int getMaxPowerPerHour() {
        return maxPowerPerHour;
    }

    public int getMaxBonusPercent() {
        return maxBonusPercent;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public void shutdown() {
        if (powerResetTask != null) {
            powerResetTask.cancel();
            powerResetTask = null;
        }
        dryStreak.clear();
        lastLootTime.clear();
        powerSpent.clear();
        lastRollTime.clear();
    }
}
