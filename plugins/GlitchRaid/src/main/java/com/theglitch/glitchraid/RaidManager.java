package com.theglitch.glitchraid;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active raid sessions, bossbars, and timers.
 * Each player UUID maps to a shared RaidSession instance (party support).
 */
public final class RaidManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;

    private final Map<UUID, RaidSession> activeRaids = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> timers = new ConcurrentHashMap<>();

    // Cached config values
    private volatile int durationSeconds = 1800;
    private volatile int summaryDelayTicks = 40;
    private volatile int partyMaxSize = 4;
    private volatile double payoutMultiplier = 1.0;

    public RaidManager(GlitchRaid plugin) {
        this.plugin = plugin;
        cacheConfig();
    }

    public void cacheConfig() {
        try {
            int duration = plugin.getConfig().getInt("raid.duration-seconds", 1800);
            if (duration < 10 || duration > 86400) {
                plugin.getLogger().warning("Invalid raid.duration-seconds " + duration + " — clamped to 1800.");
                duration = Math.max(10, Math.min(duration, 86400));
            }
            durationSeconds = duration;

            int delay = plugin.getConfig().getInt("raid.summary-delay-ticks", 40);
            if (delay < 0 || delay > 600) {
                plugin.getLogger().warning("Invalid raid.summary-delay-ticks " + delay + " — clamped to 40.");
                delay = Math.max(0, Math.min(delay, 600));
            }
            summaryDelayTicks = delay;

            int maxSize = plugin.getConfig().getInt("raid.party-max-size", 4);
            if (maxSize < 1 || maxSize > 8) {
                plugin.getLogger().warning("Invalid raid.party-max-size " + maxSize + " — clamped to 4.");
                maxSize = Math.max(1, Math.min(maxSize, 8));
            }
            partyMaxSize = maxSize;

            double payout = plugin.getConfig().getDouble("raid.payout-multiplier", 1.0);
            if (payout < 0.0 || payout > 100.0) {
                plugin.getLogger().warning("Invalid raid.payout-multiplier " + payout + " — clamped to 1.0.");
                payout = Math.max(0.0, Math.min(payout, 100.0));
            }
            payoutMultiplier = payout;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cache GlitchRaid config: " + e.getMessage());
        }
    }

    public void reload() {
        cacheConfig();
        // Refresh bossbar names with new time-left format? Existing bars will update on next tick.
        plugin.getLogger().info("RaidManager reloaded (duration=" + durationSeconds + "s, payout=" + payoutMultiplier + ", partyMax=" + partyMaxSize + ").");
    }

    public boolean isInRaid(UUID uuid) {
        return activeRaids.containsKey(uuid);
    }

    public RaidSession getSession(UUID uuid) {
        return activeRaids.get(uuid);
    }

    public Collection<RaidSession> getAllSessions() {
        // Deduplicate: multiple player UUIDs may point to same session instance
        return new HashSet<>(activeRaids.values());
    }

    public int getActiveCount() {
        return getAllSessions().size();
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public int getSummaryDelayTicks() {
        return summaryDelayTicks;
    }

    public int getPartyMaxSize() {
        return partyMaxSize;
    }

    public double getPayoutMultiplier() {
        return payoutMultiplier;
    }

    /**
     * Starts a new raid for the given leader.
     *
     * @return false if already in a raid
     */
    public boolean startRaid(Player leader) {
        UUID uuid = leader.getUniqueId();
        if (isInRaid(uuid)) {
            return false;
        }
        long now = System.currentTimeMillis();
        long end = now + (durationSeconds * 1000L);
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        members.add(uuid);
        RaidSession session = new RaidSession(uuid, members, now, end);

        // Map leader to session
        activeRaids.put(uuid, session);

        // BossBar: initial
        String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
        String formatted = formatTime(durationSeconds);
        Component initialName = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
        BossBar bar = BossBar.bossBar(initialName, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        bossBars.put(uuid, bar);
        leader.showBossBar(bar);

        String startedRaw = plugin.getConfig().getString("messages.raid-started", "<green><bold>Raid started!</bold> <gray>Good luck</gray>");
        leader.sendMessage(MM.deserialize(startedRaw));

        // Schedule per-raid tick every second
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(uuid), 20L, 20L);
        timers.put(uuid, task);

        plugin.getLogger().info("Raid started for " + leader.getName() + " (duration=" + durationSeconds + "s, partyMax=" + partyMaxSize + ")");
        return true;
    }

    /**
     * Ends the raid for the given player (leader or member).
     * Removes all members of that raid.
     */
    public void endRaid(UUID playerUuid, RaidEndReason reason) {
        RaidSession session = activeRaids.get(playerUuid);
        if (session == null) {
            return;
        }
        UUID leaderId = session.getLeader();

        // Snapshot members to avoid concurrent modification
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());

        // Cancel timers and hide bossbars for all members (and leader)
        for (UUID memberId : membersSnapshot) {
            activeRaids.remove(memberId);
            BossBar bar = bossBars.remove(memberId);
            if (bar != null) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
            BukkitTask task = timers.remove(memberId);
            if (task != null) {
                task.cancel();
            }
        }
        // Also ensure leader's bar/task are removed (in case leader not in snapshot due to party logic)
        BossBar leaderBar = bossBars.remove(leaderId);
        if (leaderBar != null) {
            for (UUID memberId : membersSnapshot) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null) {
                    p.hideBossBar(leaderBar);
                }
            }
            Player leaderPlayer = Bukkit.getPlayer(leaderId);
            if (leaderPlayer != null && !membersSnapshot.contains(leaderId)) {
                leaderPlayer.hideBossBar(leaderBar);
            }
        }
        BukkitTask leaderTask = timers.remove(leaderId);
        if (leaderTask != null) {
            leaderTask.cancel();
        }

        int baseLoot = session.getLootValue();
        int finalPayout = (int) Math.round(baseLoot * payoutMultiplier);

        String endedRaw = plugin.getConfig().getString("messages.raid-ended", "<red>Raid ended <gray>(<reason>)</gray></red>");
        Component endedComp = MM.deserialize(endedRaw.replace("<reason>", reason.name().toLowerCase()));

        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(endedComp);
            }
        }

        // Schedule summary screen
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendSummary(session, reason, finalPayout), summaryDelayTicks);

        String leaderName = Bukkit.getOfflinePlayer(leaderId).getName();
        if (leaderName == null) leaderName = leaderId.toString();
        plugin.getLogger().info("Raid ended for " + leaderName + " reason=" + reason + " loot=" + baseLoot + " payout=" + finalPayout + " deaths=" + session.getDeaths() + " members=" + membersSnapshot.size());
    }

    /**
     * Handles a non-leader quit: remove single player from raid without ending entire raid.
     * If leader quits, this is not used — use endRaid with LEADER_QUIT instead.
     */
    public void removeMember(UUID uuid) {
        RaidSession session = activeRaids.remove(uuid);
        if (session == null) {
            return;
        }
        session.getMembers().remove(uuid);
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
        BukkitTask task = timers.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        // If no members left, clean up leader's structures (edge case)
        if (session.getMembers().isEmpty()) {
            UUID leaderId = session.getLeader();
            activeRaids.remove(leaderId);
            BossBar leaderBar = bossBars.remove(leaderId);
            if (leaderBar != null) {
                Player lp = Bukkit.getPlayer(leaderId);
                if (lp != null) lp.hideBossBar(leaderBar);
            }
            BukkitTask leaderTask = timers.remove(leaderId);
            if (leaderTask != null) leaderTask.cancel();
        }
        plugin.getLogger().info("Player " + uuid + " removed from raid (quit). Remaining members: " + session.getMembers().size());
    }

    public void addLoot(UUID uuid, int amount) {
        RaidSession session = activeRaids.get(uuid);
        if (session == null) return;
        session.addLoot(amount);
    }

    public void incrementDeaths(UUID uuid) {
        RaidSession session = activeRaids.get(uuid);
        if (session == null) return;
        session.incrementDeaths();
    }

    /**
     * Tick handler for a specific raid (identified by leader UUID).
     * Updates bossbar and auto-ends on expiry.
     */
    public void tick(UUID leaderId) {
        RaidSession session = activeRaids.get(leaderId);
        if (session == null) {
            // Cleanup orphaned bar/task
            BossBar bar = bossBars.remove(leaderId);
            if (bar != null) {
                Player p = Bukkit.getPlayer(leaderId);
                if (p != null) p.hideBossBar(bar);
            }
            BukkitTask task = timers.remove(leaderId);
            if (task != null) task.cancel();
            return;
        }

        long now = System.currentTimeMillis();
        long remainingMs = session.getEndTime() - now;
        int remainingSeconds = (int) Math.max(0, remainingMs / 1000);
        float progress = durationSeconds > 0 ? (float) remainingSeconds / (float) durationSeconds : 0f;
        progress = Math.max(0f, Math.min(1f, progress));

        BossBar bar = bossBars.get(leaderId);
        if (bar != null) {
            String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
            String formatted = formatTime(remainingSeconds);
            Component name = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
            bar.name(name);
            bar.progress(progress);
            if (remainingSeconds <= 60) {
                bar.color(BossBar.Color.RED);
            } else if (remainingSeconds <= 300) {
                bar.color(BossBar.Color.YELLOW);
            } else {
                bar.color(BossBar.Color.GREEN);
            }
            // Ensure all party members see the bar (MVP: only leader has bar, but party would need it)
            for (UUID memberId : session.getMembers()) {
                if (memberId.equals(leaderId)) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.showBossBar(bar);
                }
            }
        }

        if (remainingMs <= 0) {
            endRaid(leaderId, RaidEndReason.TIMEOUT);
        }
    }

    private void sendSummary(RaidSession session, RaidEndReason reason, int payout) {
        String titleRaw = plugin.getConfig().getString("messages.raid-summary-title", "<gold><bold>Raid Summary</bold></gold>");
        Component title = MM.deserialize(titleRaw);
        long durationMs = System.currentTimeMillis() - session.getStartTime();
        // Clamp duration to configured duration if system time skewed
        int durationSec = (int) Math.min(durationMs / 1000, durationSeconds);
        String durationStr = formatTime(durationSec);

        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            p.sendMessage(Component.empty());
            p.sendMessage(title);
            p.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
            p.sendMessage(MM.deserialize("<gray>Reason: <white>" + reason.name() + "</white>"));
            p.sendMessage(MM.deserialize("<gray>Loot value: <gold>" + session.getLootValue() + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
            p.sendMessage(MM.deserialize("<gray>Deaths: <red>" + session.getDeaths() + "</red>"));
            // Death recap line
            if (session.getDeaths() > 0) {
                p.sendMessage(MM.deserialize("<gray>Death recap: <red>" + session.getDeaths() + " death(s) this raid</red>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Death recap: <green>Flawless — no deaths!</green>"));
            }
            p.sendMessage(Component.empty());

            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
            Component subtitle = MM.deserialize("<gray>" + reason.name().toLowerCase() + " • Loot " + payout + " • Deaths " + session.getDeaths());
            p.showTitle(Title.title(title, subtitle, times));
        }
    }

    public String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void shutdown() {
        // Cancel all timers and hide all bossbars
        for (Map.Entry<UUID, BukkitTask> entry : timers.entrySet()) {
            try {
                entry.getValue().cancel();
            } catch (Exception ignored) {
            }
        }
        timers.clear();
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            BossBar bar = entry.getValue();
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                try {
                    p.hideBossBar(bar);
                } catch (Exception ignored) {
                }
            }
            // Also hide from all members of that raid (party case)
            RaidSession s = activeRaids.get(entry.getKey());
            if (s != null) {
                for (UUID memberId : s.getMembers()) {
                    if (memberId.equals(entry.getKey())) continue;
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) {
                        try {
                            member.hideBossBar(bar);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        bossBars.clear();
        activeRaids.clear();
    }
}
