package com.theglitch.glitchquests;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Mutable per-player state. Access only from the main thread. */
public final class PlayerData {

    // login streak / periodic rewards
    String lastDailyClaim = "";     // ISO date of last streak claim
    int streak = 0;
    String lastWeeklyClaim = "";    // week key
    String lastMonthlyClaim = "";   // month key

    // quests
    String dailyKey = "";
    final Map<String, Integer> dailyProgress = new HashMap<>();
    final Set<String> dailyClaimed = new HashSet<>();
    boolean dailyBonusClaimed = false;

    String weeklyKey = "";
    final Map<String, Integer> weeklyProgress = new HashMap<>();
    final Set<String> weeklyClaimed = new HashSet<>();

    // not persisted: containers already counted today, travel sampling
    final Set<String> lootedToday = new HashSet<>();
    long lastTravelCm = -1;

    boolean dirty = false;

    static PlayerData load(YamlConfiguration y) {
        PlayerData d = new PlayerData();
        d.lastDailyClaim = y.getString("streak.last-claim", "");
        d.streak = y.getInt("streak.count", 0);
        d.lastWeeklyClaim = y.getString("weekly.last-claim", "");
        d.lastMonthlyClaim = y.getString("monthly.last-claim", "");
        d.dailyKey = y.getString("daily-quests.key", "");
        readMap(y.getConfigurationSection("daily-quests.progress"), d.dailyProgress);
        d.dailyClaimed.addAll(y.getStringList("daily-quests.claimed"));
        d.dailyBonusClaimed = y.getBoolean("daily-quests.bonus-claimed", false);
        d.weeklyKey = y.getString("weekly-quests.key", "");
        readMap(y.getConfigurationSection("weekly-quests.progress"), d.weeklyProgress);
        d.weeklyClaimed.addAll(y.getStringList("weekly-quests.claimed"));
        return d;
    }

    YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("streak.last-claim", lastDailyClaim);
        y.set("streak.count", streak);
        y.set("weekly.last-claim", lastWeeklyClaim);
        y.set("monthly.last-claim", lastMonthlyClaim);
        y.set("daily-quests.key", dailyKey);
        y.createSection("daily-quests.progress", dailyProgress);
        y.set("daily-quests.claimed", dailyClaimed.stream().sorted().toList());
        y.set("daily-quests.bonus-claimed", dailyBonusClaimed);
        y.set("weekly-quests.key", weeklyKey);
        y.createSection("weekly-quests.progress", weeklyProgress);
        y.set("weekly-quests.claimed", weeklyClaimed.stream().sorted().toList());
        return y;
    }

    private static void readMap(ConfigurationSection sec, Map<String, Integer> into) {
        if (sec == null) return;
        for (String k : sec.getKeys(false)) into.put(k, sec.getInt(k));
    }
}
