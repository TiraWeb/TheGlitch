package com.theglitch.glitchquests;

import com.theglitch.glitchquests.QuestDef.Period;
import com.theglitch.glitchquests.QuestDef.QuestType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Owns config, per-player data, quest rotation, progress and claims. */
public final class QuestManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchQuests plugin;
    private final File dataDir;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    private ZoneId zone = ZoneId.systemDefault();
    private int dailyCount = 5;
    private int weeklyCount = 3;
    private List<Reward> streakRewards = List.of();
    private Reward weeklyReward = Reward.NONE;
    private Reward monthlyReward = Reward.NONE;
    private Reward dailyBonus = Reward.NONE;
    private final Map<String, QuestDef> dailyPool = new LinkedHashMap<>();
    private final Map<String, QuestDef> weeklyPool = new LinkedHashMap<>();

    // rotation cache, recomputed when the period key changes
    private String activeDailyKey = "";
    private List<QuestDef> activeDaily = List.of();
    private String activeWeeklyKey = "";
    private List<QuestDef> activeWeekly = List.of();

    public QuestManager(GlitchQuests plugin) {
        this.plugin = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "players");
        dataDir.mkdirs();
        reload();
    }

    // ------------------------------------------------------------------ config

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        String tz = c.getString("timezone", "system");
        try {
            zone = "system".equalsIgnoreCase(tz) ? ZoneId.systemDefault() : ZoneId.of(tz);
        } catch (Exception e) {
            plugin.getLogger().warning("Bad timezone '" + tz + "', using system default");
            zone = ZoneId.systemDefault();
        }
        dailyCount = Math.max(1, Math.min(7, c.getInt("daily-quest-count", 5)));
        weeklyCount = Math.max(1, Math.min(7, c.getInt("weekly-quest-count", 3)));

        List<Reward> streak = new ArrayList<>();
        for (Map<?, ?> m : c.getMapList("login-streak")) streak.add(Reward.parseMap(m));
        if (streak.isEmpty()) streak.add(new Reward(100, Map.of(), List.of()));
        streakRewards = streak;
        weeklyReward = Reward.parse(c.getConfigurationSection("weekly-reward"));
        monthlyReward = Reward.parse(c.getConfigurationSection("monthly-reward"));
        dailyBonus = Reward.parse(c.getConfigurationSection("daily-bonus"));

        dailyPool.clear();
        weeklyPool.clear();
        loadPool(c.getConfigurationSection("quests.daily"), Period.DAILY, dailyPool);
        loadPool(c.getConfigurationSection("quests.weekly"), Period.WEEKLY, weeklyPool);
        activeDailyKey = "";
        activeWeeklyKey = "";
        plugin.getLogger().info("Loaded " + dailyPool.size() + " daily / " + weeklyPool.size()
                + " weekly quests; " + dailyCount + "+" + weeklyCount + " active, zone " + zone);
    }

    private void loadPool(ConfigurationSection sec, Period period, Map<String, QuestDef> into) {
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection q = sec.getConfigurationSection(id);
            if (q == null) continue;
            QuestType type;
            try {
                type = QuestType.valueOf(q.getString("type", "").toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Quest '" + id + "' has unknown type " + q.getString("type") + " — skipped");
                continue;
            }
            Material icon = Material.matchMaterial(q.getString("icon", "PAPER"));
            into.put(id, new QuestDef(id, period, q.getString("name", id), q.getString("description", ""),
                    type, Math.max(1, q.getInt("amount", 1)), icon == null ? Material.PAPER : icon,
                    Reward.parse(q.getConfigurationSection("reward"))));
        }
    }

    // ------------------------------------------------------------------ periods

    public ZonedDateTime now() { return ZonedDateTime.now(zone); }
    public String dayKey() { return now().toLocalDate().toString(); }
    public String weekKey() {
        LocalDate d = now().toLocalDate();
        return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }
    public String monthKey() { return YearMonth.from(now()).toString(); }

    public Duration untilNextDay() {
        ZonedDateTime n = now();
        return Duration.between(n, n.toLocalDate().plusDays(1).atStartOfDay(zone));
    }
    public Duration untilNextWeek() {
        ZonedDateTime n = now();
        LocalDate next = n.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return Duration.between(n, next.atStartOfDay(zone));
    }
    public Duration untilNextMonth() {
        ZonedDateTime n = now();
        return Duration.between(n, n.toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(zone));
    }

    public List<QuestDef> activeDaily() {
        String key = dayKey();
        if (!key.equals(activeDailyKey)) {
            activeDaily = pick(dailyPool, dailyCount, "daily:" + key);
            activeDailyKey = key;
        }
        return activeDaily;
    }

    public List<QuestDef> activeWeekly() {
        String key = weekKey();
        if (!key.equals(activeWeeklyKey)) {
            activeWeekly = pick(weeklyPool, weeklyCount, "weekly:" + key);
            activeWeeklyKey = key;
        }
        return activeWeekly;
    }

    private static List<QuestDef> pick(Map<String, QuestDef> pool, int count, String seed) {
        List<QuestDef> all = new ArrayList<>(pool.values());
        Collections.shuffle(all, new Random(seed.hashCode() * 31L + 7));
        return List.copyOf(all.subList(0, Math.min(count, all.size())));
    }

    // ------------------------------------------------------------------ data

    public PlayerData data(Player p) {
        PlayerData d = players.computeIfAbsent(p.getUniqueId(), this::loadFile);
        rollover(d);
        return d;
    }

    /** Resets quest progress when the day/week changed since it was recorded. */
    private void rollover(PlayerData d) {
        String day = dayKey();
        if (!day.equals(d.dailyKey)) {
            d.dailyKey = day;
            d.dailyProgress.clear();
            d.dailyClaimed.clear();
            d.dailyBonusClaimed = false;
            d.lootedToday.clear();
            d.dirty = true;
        }
        String week = weekKey();
        if (!week.equals(d.weeklyKey)) {
            d.weeklyKey = week;
            d.weeklyProgress.clear();
            d.weeklyClaimed.clear();
            d.dirty = true;
        }
    }

    private PlayerData loadFile(UUID id) {
        File f = new File(dataDir, id + ".yml");
        return f.exists() ? PlayerData.load(YamlConfiguration.loadConfiguration(f)) : new PlayerData();
    }

    public void unload(UUID id) {
        PlayerData d = players.remove(id);
        if (d != null && d.dirty) saveAsync(id, d);
    }

    public void saveDirty() {
        players.forEach((id, d) -> { if (d.dirty) saveAsync(id, d); });
    }

    public void saveAllSync() {
        players.forEach((id, d) -> { if (d.dirty) write(id, d.toYaml()); d.dirty = false; });
    }

    private void saveAsync(UUID id, PlayerData d) {
        String snapshot = d.toYaml().saveToString();   // snapshot on main thread
        d.dirty = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.loadFromString(snapshot);
                write(id, y);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save quest data for " + id, e);
            }
        });
    }

    private synchronized void write(UUID id, YamlConfiguration y) {
        File target = new File(dataDir, id + ".yml");
        File tmp = new File(dataDir, id + ".yml.tmp");
        try {
            y.save(tmp);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                plugin.getLogger().log(Level.WARNING, "Failed to save quest data for " + id, e2);
            }
        }
    }

    public void reset(UUID id) {
        players.put(id, new PlayerData());
        players.get(id).dirty = true;
        new File(dataDir, id + ".yml").delete();
    }

    // ------------------------------------------------------------------ progress

    public void progress(Player p, QuestType type, int amount) {
        if (amount <= 0) return;
        PlayerData d = data(p);
        for (QuestDef q : activeDaily()) {
            if (q.type() == type) bump(p, d, q, d.dailyProgress, amount);
        }
        for (QuestDef q : activeWeekly()) {
            if (q.type() == type) bump(p, d, q, d.weeklyProgress, amount);
        }
    }

    private void bump(Player p, PlayerData d, QuestDef q, Map<String, Integer> progress, int amount) {
        int before = progress.getOrDefault(q.id(), 0);
        if (before >= q.amount()) return;
        int after = Math.min(q.amount(), before + amount);
        progress.put(q.id(), after);
        d.dirty = true;
        if (after >= q.amount()) {
            announce(p, q);
            if (q.period() == Period.DAILY) progress(p, QuestType.COMPLETE_DAILY, 1);
        }
    }

    private void announce(Player p, QuestDef q) {
        String label = q.period() == Period.DAILY ? "Daily" : "Weekly";
        p.showTitle(Title.title(
                MM.deserialize("<gradient:#C084FC:#F0ABFC><bold>QUEST COMPLETE</bold></gradient>"),
                MM.deserialize("<gray>" + label + ": <white>" + q.name() + "</white></gray>"),
                Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(400))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        p.sendMessage(MM.deserialize("<dark_purple>✦</dark_purple> <gray>" + label + " quest <white>" + q.name()
                + "</white> complete! <click:run_command:/quests><hover:show_text:'<gray>Open the quest board'>"
                + "<light_purple><u>Claim it in /quests</u></light_purple></hover></click></gray>"));
    }

    public int progressOf(PlayerData d, QuestDef q) {
        return (q.period() == Period.DAILY ? d.dailyProgress : d.weeklyProgress).getOrDefault(q.id(), 0);
    }

    public boolean isClaimed(PlayerData d, QuestDef q) {
        return (q.period() == Period.DAILY ? d.dailyClaimed : d.weeklyClaimed).contains(q.id());
    }

    // ------------------------------------------------------------------ claims

    public enum ClaimResult { OK, NOT_READY, ALREADY }

    public ClaimResult claimQuest(Player p, QuestDef q) {
        PlayerData d = data(p);
        if (isClaimed(d, q)) return ClaimResult.ALREADY;
        if (progressOf(d, q) < q.amount()) return ClaimResult.NOT_READY;
        (q.period() == Period.DAILY ? d.dailyClaimed : d.weeklyClaimed).add(q.id());
        d.dirty = true;
        q.reward().give(p, plugin.getEconomy());
        return ClaimResult.OK;
    }

    public boolean allDailiesDone(PlayerData d) {
        for (QuestDef q : activeDaily()) if (progressOf(d, q) < q.amount()) return false;
        return true;
    }

    public ClaimResult claimDailyBonus(Player p) {
        PlayerData d = data(p);
        if (d.dailyBonusClaimed) return ClaimResult.ALREADY;
        if (!allDailiesDone(d)) return ClaimResult.NOT_READY;
        d.dailyBonusClaimed = true;
        d.dirty = true;
        dailyBonus.give(p, plugin.getEconomy());
        return ClaimResult.OK;
    }

    public boolean canClaimStreak(PlayerData d) { return !dayKey().equals(d.lastDailyClaim); }
    public boolean canClaimWeekly(PlayerData d) { return !weekKey().equals(d.lastWeeklyClaim); }
    public boolean canClaimMonthly(PlayerData d) { return !monthKey().equals(d.lastMonthlyClaim); }

    /** Streak day (1-based) the next claim would pay out as. */
    public int nextStreakDay(PlayerData d) {
        return continuesStreak(d) ? d.streak + 1 : 1;
    }

    /** Streak length currently shown to the player (0 once it has lapsed). */
    public int currentStreak(PlayerData d) {
        if (dayKey().equals(d.lastDailyClaim) || continuesStreak(d)) return d.streak;
        return 0;
    }

    private boolean continuesStreak(PlayerData d) {
        return now().toLocalDate().minusDays(1).toString().equals(d.lastDailyClaim);
    }

    public Reward streakReward(int day) {
        return streakRewards.get((day - 1) % streakRewards.size());
    }

    public int streakCycle() { return streakRewards.size(); }

    public ClaimResult claimStreak(Player p) {
        PlayerData d = data(p);
        if (!canClaimStreak(d)) return ClaimResult.ALREADY;
        int day = nextStreakDay(d);
        d.streak = day;
        d.lastDailyClaim = dayKey();
        d.dirty = true;
        streakReward(day).give(p, plugin.getEconomy());
        return ClaimResult.OK;
    }

    public ClaimResult claimWeekly(Player p) {
        PlayerData d = data(p);
        if (!canClaimWeekly(d)) return ClaimResult.ALREADY;
        d.lastWeeklyClaim = weekKey();
        d.dirty = true;
        weeklyReward.give(p, plugin.getEconomy());
        return ClaimResult.OK;
    }

    public ClaimResult claimMonthly(Player p) {
        PlayerData d = data(p);
        if (!canClaimMonthly(d)) return ClaimResult.ALREADY;
        d.lastMonthlyClaim = monthKey();
        d.dirty = true;
        monthlyReward.give(p, plugin.getEconomy());
        return ClaimResult.OK;
    }

    public Reward weeklyReward() { return weeklyReward; }
    public Reward monthlyReward() { return monthlyReward; }
    public Reward dailyBonus() { return dailyBonus; }

    public QuestDef findActive(String id) {
        for (QuestDef q : activeDaily()) if (q.id().equals(id)) return q;
        for (QuestDef q : activeWeekly()) if (q.id().equals(id)) return q;
        return null;
    }

    /** Admin: set progress directly (bypasses completion announce). */
    public boolean setProgress(Player p, String questId, int amount) {
        QuestDef q = findActive(questId);
        if (q == null) return false;
        PlayerData d = data(p);
        (q.period() == Period.DAILY ? d.dailyProgress : d.weeklyProgress).put(q.id(), Math.min(amount, q.amount()));
        d.dirty = true;
        return true;
    }
}
