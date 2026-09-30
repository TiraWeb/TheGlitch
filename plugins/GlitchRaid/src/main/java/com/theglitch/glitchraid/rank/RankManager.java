package com.theglitch.glitchraid.rank;

import com.theglitch.common.FoliaScheduler;
import com.theglitch.glitchraid.GlitchRaid;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Raider Rank: permanent RR ladder (Bronze .. Eternity). RR is earned by
 * extracting (scaled by loot value, plus player kills made that raid) and
 * lost by dying in a raid or combat-logging (scaled by the value dropped).
 * A demotion shield keeps you at your tier's floor once; it rearms on the
 * next extraction. Stored in plugins/GlitchRaid/ranks.yml.
 */
public final class RankManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int HISTORY = 5;

    public static final class Entry {
        volatile String name = "";
        volatile int rr;
        volatile boolean shield = true;
        final Deque<String> history = new ArrayDeque<>();

        public String name() { return name; }
        public int rr() { return rr; }
        public boolean shield() { return shield; }
        public List<String> history() {
            synchronized (history) {
                return new ArrayList<>(history);
            }
        }
    }

    private final GlitchRaid plugin;
    private final File file;
    private final Map<UUID, Entry> data = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingKills = new ConcurrentHashMap<>();
    private final Map<String, Long> lastKill = new ConcurrentHashMap<>();
    private final EnumMap<RankTier, Integer> mins = new EnumMap<>(RankTier.class);
    private volatile boolean dirty;

    private boolean enabled;
    private int extractBase, extractPer100, extractCap, lowValue, lowGain, killBonus;
    private int deathBase, deathPer200, deathCap, killMinVictimValue;
    private long killCooldownMs;
    private double highGain, highLoss, topGain;
    private int broadcastFrom;

    public RankManager(GlitchRaid plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
        reload();
        load();
        FoliaScheduler.runAtFixedRateGlobal(plugin, this::saveIfDirty, 20L * 60, 20L * 60);
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("rank.enabled", true);
        extractBase = c.getInt("rank.extract.base", 10);
        extractPer100 = c.getInt("rank.extract.per-100-value", 1);
        extractCap = c.getInt("rank.extract.cap", 60);
        lowValue = c.getInt("rank.extract.low-value", 50);
        lowGain = c.getInt("rank.extract.low-value-gain", 2);
        killBonus = c.getInt("rank.extract.per-player-kill", 5);
        deathBase = c.getInt("rank.death.base", 8);
        deathPer200 = c.getInt("rank.death.per-200-value", 1);
        deathCap = c.getInt("rank.death.cap", 40);
        killMinVictimValue = c.getInt("rank.kills.min-victim-value", 200);
        killCooldownMs = c.getLong("rank.kills.same-victim-cooldown-minutes", 30) * 60_000L;
        highGain = c.getDouble("rank.curve.grandmaster-gain", 0.8);
        highLoss = c.getDouble("rank.curve.grandmaster-loss", 1.25);
        topGain = c.getDouble("rank.curve.eternity-gain", 0.6);
        broadcastFrom = RankTier.valueOf(c.getString("rank.broadcast-from", "DIAMOND").toUpperCase(java.util.Locale.ROOT)).ordinal();
        for (RankTier t : RankTier.values()) {
            mins.put(t, c.getInt("rank.tiers." + t.name().toLowerCase(java.util.Locale.ROOT), t.defaultMin()));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ---- queries ----

    public Entry entry(UUID id) {
        return data.computeIfAbsent(id, k -> new Entry());
    }

    public int rr(UUID id) {
        Entry e = data.get(id);
        return e == null ? 0 : e.rr;
    }

    public int min(RankTier t) {
        return mins.getOrDefault(t, t.defaultMin());
    }

    public RankTier tierOf(int rr) {
        RankTier best = RankTier.BRONZE;
        for (RankTier t : RankTier.values()) {
            if (rr >= min(t)) best = t;
        }
        return best;
    }

    public RankTier tier(UUID id) {
        return tierOf(rr(id));
    }

    /** Top players by RR, highest first. */
    public List<Map.Entry<UUID, Entry>> top(int n) {
        List<Map.Entry<UUID, Entry>> list = new ArrayList<>(data.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().rr, a.getValue().rr));
        return list.subList(0, Math.min(n, list.size()));
    }

    /** Leaderboard position (1-based) among ranked players. */
    public int position(UUID id) {
        int mine = rr(id);
        int pos = 1;
        for (Entry e : data.values()) {
            if (e.rr > mine) pos++;
        }
        return pos;
    }

    // ---- game hooks ----

    /** A player killed another raider; credited only if the killer later extracts. */
    public void recordPlayerKill(UUID killer, UUID victim, int victimValue) {
        if (!enabled || killer.equals(victim) || victimValue < killMinVictimValue) return;
        String key = killer + ">" + victim;
        long now = System.currentTimeMillis();
        Long last = lastKill.get(key);
        if (last != null && now - last < killCooldownMs) return;
        lastKill.put(key, now);
        pendingKills.merge(killer, 1, Integer::sum);
    }

    public void onExtract(UUID id, int lootValue) {
        if (!enabled) return;
        int kills = pendingKills.getOrDefault(id, 0);
        pendingKills.remove(id);
        int gain = lootValue < lowValue ? lowGain
                : Math.min(extractCap, extractBase + (lootValue / 100) * extractPer100);
        gain += kills * killBonus;
        RankTier t = tier(id);
        if (t == RankTier.ETERNITY) gain = (int) Math.round(gain * topGain);
        else if (t.ordinal() >= RankTier.GRANDMASTER.ordinal()) gain = (int) Math.round(gain * highGain);
        gain = Math.max(1, gain);
        entry(id).shield = true;
        String why = "extracted " + String.format("%,d", lootValue) + " Shards"
                + (kills > 0 ? ", " + kills + " raider kill" + (kills == 1 ? "" : "s") : "");
        change(id, gain, why);
    }

    public void onDeath(UUID id, int lostValue, String reason) {
        if (!enabled) return;
        pendingKills.remove(id);
        int loss = Math.min(deathCap, deathBase + (lostValue / 200) * deathPer200);
        if (tier(id).ordinal() >= RankTier.GRANDMASTER.ordinal()) loss = (int) Math.round(loss * highLoss);
        change(id, -loss, reason);
    }

    /** Admin set. */
    public void set(UUID id, String name, int rr) {
        Entry e = entry(id);
        if (name != null) e.name = name;
        change(id, Math.max(0, rr) - e.rr, "set by admin");
    }

    public void reset(UUID id) {
        data.remove(id);
        pendingKills.remove(id);
        dirty = true;
    }

    public void rememberName(Player p) {
        Entry e = data.get(p.getUniqueId());
        if (e != null && !p.getName().equals(e.name)) {
            e.name = p.getName();
            dirty = true;
        }
    }

    private void change(UUID id, int delta, String why) {
        Entry e = entry(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) e.name = p.getName();
        RankTier before = tierOf(e.rr);
        int next = Math.max(0, e.rr + delta);
        boolean shielded = false;
        if (delta < 0 && tierOf(next).ordinal() < before.ordinal() && e.shield && !"set by admin".equals(why)) {
            next = min(before);
            e.shield = false;
            shielded = true;
        }
        int applied = next - e.rr;
        e.rr = next;
        synchronized (e.history) {
            e.history.addFirst((applied >= 0 ? "<green>+" : "<red>") + applied + "</> <gray>" + why + "</gray>");
            while (e.history.size() > HISTORY) e.history.removeLast();
        }
        dirty = true;
        RankTier after = tierOf(next);
        if (p == null) return;

        String sign = applied >= 0 ? "<green>+" + applied : "<red>" + applied;
        p.sendMessage(MM.deserialize("<dark_gray>[</dark_gray>" + after.styled() + "<dark_gray>]</dark_gray> "
                + sign + " RR</> <gray>(" + why + ") — " + next + " RR</gray>"
                + (shielded ? " <yellow>Demotion shield used — you stay " + before.displayName() + ".</yellow>" : "")));

        if (after.ordinal() > before.ordinal()) {
            // after the extraction title (RaidManager shows it right away)
            FoliaScheduler.runLaterGlobal(plugin, () -> promote(p, after), 60L);
        } else if (after.ordinal() < before.ordinal()) {
            p.sendMessage(MM.deserialize("<gray>You dropped to " + after.styled() + ".</gray>"));
        }
    }

    private void promote(Player p, RankTier t) {
        if (!p.isOnline()) return;
        String glyph = String.valueOf(t.glyph());
        try {
            p.showTitle(Title.title(MM.deserialize(glyph + " " + t.styled()),
                    MM.deserialize("<gray>Raider Rank up!</gray>"),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        } catch (Exception ignored) {
        }
        if (t.ordinal() >= broadcastFrom) {
            Bukkit.broadcast(MM.deserialize("<gold>★</gold> <white>" + p.getName() + "</white> <gray>reached</gray> "
                    + glyph + " " + t.styled() + " <gray>Raider Rank!</gray>"));
        } else {
            p.sendMessage(MM.deserialize("<gray>You reached " + glyph + " " + t.styled() + "!</gray>"));
        }
    }

    // ---- persistence ----

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                Entry e = new Entry();
                e.name = s.getString(k + ".name", "");
                e.rr = s.getInt(k + ".rr", 0);
                e.shield = s.getBoolean(k + ".shield", true);
                for (String h : s.getStringList(k + ".history")) e.history.addLast(h);
                data.put(id, e);
            } catch (IllegalArgumentException ignored) {
            }
        }
        plugin.getLogger().info("Raider Rank: loaded " + data.size() + " players.");
    }

    private String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> me : data.entrySet()) {
            String k = "players." + me.getKey();
            Entry e = me.getValue();
            y.set(k + ".name", e.name);
            y.set(k + ".rr", e.rr);
            y.set(k + ".shield", e.shield);
            y.set(k + ".history", e.history());
        }
        return y.saveToString();
    }

    private void write(String yaml) {
        try {
            plugin.getDataFolder().mkdirs();
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), yaml, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            plugin.getLogger().warning("Raider Rank save failed: " + ex.getMessage());
        }
    }

    private void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        String yaml = serialize();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(yaml));
    }

    /** Synchronous save for shutdown. */
    public void saveNow() {
        dirty = false;
        write(serialize());
    }
}
