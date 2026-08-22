package com.theglitch.glitchraid;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
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
    private final Set<UUID> timeoutVictims = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDeathMillis = new ConcurrentHashMap<>();

    // Cached config values
    private volatile int durationSeconds = 1800;
    private volatile int summaryDelayTicks = 40;
    private volatile int partyMaxSize = 4;
    private volatile double payoutMultiplier = 1.0;
    private volatile String hubWorld = "hub";
    private volatile String autoStartWorld = "glitch_red";

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

            String hub = plugin.getConfig().getString("raid.hub-world", "hub");
            if (hub != null && !hub.isBlank()) hubWorld = hub;
            String auto = plugin.getConfig().getString("raid.auto-start-world", "glitch_red");
            if (auto != null && !auto.isBlank()) autoStartWorld = auto;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to cache GlitchRaid config: " + e.getMessage());
        }
    }

    public void reload() {
        cacheConfig();
        plugin.getLogger().info("RaidManager reloaded (duration=" + durationSeconds + "s, payout=" + payoutMultiplier + ", partyMax=" + partyMaxSize + ", hub=" + hubWorld + ", autoWorld=" + autoStartWorld + ").");
    }

    public boolean isInRaid(UUID uuid) {
        return activeRaids.containsKey(uuid);
    }

    public RaidSession getSession(UUID uuid) {
        return activeRaids.get(uuid);
    }

    public Collection<RaidSession> getAllSessions() {
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

    public String getHubWorld() {
        return hubWorld;
    }

    public String getAutoStartWorld() {
        return autoStartWorld;
    }

    public boolean isTimeoutVictim(UUID uuid) {
        return timeoutVictims.contains(uuid);
    }

    public void clearTimeoutVictim(UUID uuid) {
        timeoutVictims.remove(uuid);
    }

    public void recordDeath(UUID uuid) {
        lastDeathMillis.put(uuid, System.currentTimeMillis());
    }

    public boolean isRecentlyDead(UUID uuid, long withinMs) {
        Long t = lastDeathMillis.get(uuid);
        return t != null && (System.currentTimeMillis() - t) < withinMs;
    }

    /**
     * Starts a new raid for the given leader.
     *
     * @return false if already in a raid
     */
    public boolean startRaid(Player leader) {
        return startRaid(leader, false);
    }

    public boolean startRaid(Player leader, boolean auto) {
        UUID uuid = leader.getUniqueId();
        if (isInRaid(uuid)) {
            return false;
        }
        long now = System.currentTimeMillis();
        long end = now + (durationSeconds * 1000L);
        Set<UUID> members = ConcurrentHashMap.newKeySet();
        members.add(uuid);
        RaidSession session = new RaidSession(uuid, members, now, end);

        activeRaids.put(uuid, session);

        String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
        String formatted = formatTime(durationSeconds);
        Component initialName = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
        BossBar bar = BossBar.bossBar(initialName, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        bossBars.put(uuid, bar);
        leader.showBossBar(bar);

        String key = auto ? "messages.raid-auto-started" : "messages.raid-started";
        String fallback = auto ? "<green><bold>Raid started!</bold> <gray>You entered the Glitch — 30:00 to extract!</gray>" : "<green><bold>Raid started!</bold> <gray>Good luck — the Glitch awaits.</gray>";
        String startedRaw = plugin.getConfig().getString(key, fallback);
        if (startedRaw == null) startedRaw = fallback;
        leader.sendMessage(MM.deserialize(startedRaw));
        leader.sendActionBar(MM.deserialize("<gray>Extract before <white>" + formatted + "</white> or lose everything!</gray>"));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(uuid), 20L, 20L);
        timers.put(uuid, task);

        plugin.getLogger().info("Raid started for " + leader.getName() + (auto ? " (auto)" : "") + " (duration=" + durationSeconds + "s, partyMax=" + partyMaxSize + ")");
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
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());

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
        int computedPayout = (int) Math.round(baseLoot * payoutMultiplier);
        if (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH) {
            computedPayout = 0;
        }
        final int finalPayout = computedPayout;
        final RaidSession summarySession = session;
        final RaidEndReason summaryReason = reason;

        Component endedComp;
        if (reason == RaidEndReason.EXTRACTED) {
            String raw = plugin.getConfig().getString("messages.raid-extracted", "<green><bold>Extracted!</bold> <gray>Loot secured.</gray></green>");
            endedComp = MM.deserialize(raw);
        } else if (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH) {
            String raw = plugin.getConfig().getString("messages.raid-timeout-killed", "<dark_red><bold>The Glitch consumed you.</bold> <gray>Loot lost.</gray></dark_red>");
            endedComp = MM.deserialize(raw);
        } else {
            String endedRaw = plugin.getConfig().getString("messages.raid-ended", "<red>Raid ended <gray>(<reason>)</gray> — returning to hub...</red>");
            endedComp = MM.deserialize(endedRaw.replace("<reason>", reason.name().toLowerCase()));
        }

        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p != null) {
                p.sendMessage(endedComp);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> sendSummary(summarySession, summaryReason, finalPayout), summaryDelayTicks);

        // For non-timeout cases that are not extraction, teleport survivors to hub after a short delay (except death cases where they already died)
        if (reason != RaidEndReason.TIMEOUT && reason != RaidEndReason.TIMEOUT_DEATH && reason != RaidEndReason.EXTRACTED) {
            for (UUID memberId : membersSnapshot) {
                Player p = Bukkit.getPlayer(memberId);
                if (p != null && p.isOnline() && !p.isDead()) {
                    String wn = p.getWorld().getName();
                    if (wn.equalsIgnoreCase(autoStartWorld)) {
                        // Don't auto-teleport manual ends that are not timeout — let admin decide?
                        // For now, keep players in place; admin can teleport.
                    }
                }
            }
        }

        String leaderName = Bukkit.getOfflinePlayer(leaderId).getName();
        if (leaderName == null) leaderName = leaderId.toString();
        plugin.getLogger().info("Raid ended for " + leaderName + " reason=" + reason + " loot=" + baseLoot + " payout=" + finalPayout + " deaths=" + session.getDeaths() + " members=" + membersSnapshot.size());
    }

    /**
     * Called when a player successfully extracts (Koth win / hub teleport).
     * Ends their raid as EXTRACTED — loot is preserved (already stashed).
     */
    public void handleExtraction(Player player) {
        if (!isInRaid(player.getUniqueId())) return;
        endRaid(player.getUniqueId(), RaidEndReason.EXTRACTED);
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

    /**
     * Add loot value derived from ItemStacks' sell prices (GlitchShops) or fallback.
     * Used for container loot and other item-based rewards so the BossBar/status reflects real value.
     */
    public void addLootFromItems(Player player, Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        RaidSession session = activeRaids.get(player.getUniqueId());
        if (session == null) return;
        int value = 0;
        // Try GlitchShops sellPrice via reflection (soft dep — no compile-time)
        try {
            Plugin shopsPlugin = Bukkit.getPluginManager().getPlugin("GlitchShops");
            if (shopsPlugin != null && shopsPlugin.isEnabled()) {
                Object manager = null;
                try {
                    manager = shopsPlugin.getClass().getMethod("getShopManager").invoke(shopsPlugin);
                } catch (NoSuchMethodException e) {
                    Object inst = shopsPlugin.getClass().getMethod("getInstance").invoke(null);
                    if (inst != null) manager = inst.getClass().getMethod("getShopManager").invoke(inst);
                }
                if (manager != null) {
                    java.lang.reflect.Method sellMethod = manager.getClass().getMethod("sellPrice", ItemStack.class);
                    for (ItemStack item : items) {
                        if (item == null || item.getType().isAir()) continue;
                        Integer price = (Integer) sellMethod.invoke(manager, item);
                        if (price != null && price > 0) {
                            value += price * item.getAmount();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (value <= 0) {
            // Fallback: small value per item amount so containers still tick the counter
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                // Oraxen materials are Paper/GLOWSTONE_DUST etc. — give modest value
                value += item.getAmount() * 5;
                // Gear or rifts could be higher? Use amount * 10 as fallback for unknown
            }
            if (value <= 0) value = items.size() * 10;
        }
        if (value > 0) {
            session.addLoot(value);
            String lootRaw = plugin.getConfig().getString("messages.loot-added", "<gold>+<amount> loot value</gold>");
            try {
                player.sendActionBar(MM.deserialize(lootRaw.replace("<amount>", String.valueOf(value))));
            } catch (Exception ignored) {
            }
        }
    }

    public void incrementDeaths(UUID uuid) {
        RaidSession session = activeRaids.get(uuid);
        if (session == null) return;
        session.incrementDeaths();
    }

    /**
     * Tick handler for a specific raid (identified by leader UUID).
     * Updates bossbar and handles warnings / timeout kill.
     */
    public void tick(UUID leaderId) {
        RaidSession session = activeRaids.get(leaderId);
        if (session == null) {
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
            for (UUID memberId : session.getMembers()) {
                if (memberId.equals(leaderId)) continue;
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.showBossBar(bar);
                }
            }
        }

        // Warnings in the last minute
        if (remainingSeconds == 60 || remainingSeconds == 30 || remainingSeconds == 10
                || (remainingSeconds <= 5 && remainingSeconds > 0)) {
            sendTimeoutWarning(session, remainingSeconds);
        }

        if (remainingMs <= 0) {
            handleTimeout(leaderId);
        }
    }

    private void sendTimeoutWarning(RaidSession session, int remainingSeconds) {
        String key;
        String fallback;
        if (remainingSeconds == 60) {
            key = "messages.raid-warn-60";
            fallback = "<red><bold>WARNING:</bold> <gray>60 seconds left — extract now or the Glitch will consume you!</gray></red>";
        } else if (remainingSeconds == 30) {
            key = "messages.raid-warn-30";
            fallback = "<red><bold>30 seconds left — get to an extraction beacon!</bold></red>";
        } else if (remainingSeconds == 10) {
            key = "messages.raid-warn-10";
            fallback = "<red><bold>10 seconds!</bold> <gray>The Glitch closes — extract or die!</gray></red>";
        } else {
            key = null;
            fallback = "<red><bold>" + remainingSeconds + "</bold></red>";
        }
        Component msg;
        if (key != null) {
            String raw = plugin.getConfig().getString(key, fallback);
            try { msg = MM.deserialize(raw); } catch (Exception e) { msg = Component.text(remainingSeconds + "s left"); }
        } else {
            msg = MM.deserialize(fallback);
        }
        Title.Times times = Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(800), Duration.ofMillis(200));
        Component title = MM.deserialize("<red><bold>" + remainingSeconds + "</bold></red>");
        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) continue;
            p.sendMessage(msg);
            if (remainingSeconds <= 10) {
                p.showTitle(Title.title(title, msg, times));
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
            } else {
                p.sendActionBar(msg);
                p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
            }
        }
        // Include leader if not in members (edge)
        if (!session.getMembers().contains(session.getLeader())) {
            Player lp = Bukkit.getPlayer(session.getLeader());
            if (lp != null && lp.isOnline() && lp.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                lp.sendMessage(msg);
            }
        }
    }

    private void handleTimeout(UUID leaderId) {
        RaidSession session = activeRaids.get(leaderId);
        if (session == null) return;
        Set<UUID> membersSnapshot = new HashSet<>(session.getMembers());
        // Ensure leader is included if party logic missed him
        membersSnapshot.add(leaderId);
        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) continue;
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                // Don't kill creatives — just teleport
                teleportToHub(p);
                continue;
            }
            final UUID victimId = memberId;
            timeoutVictims.add(victimId);
            Bukkit.getScheduler().runTaskLater(plugin, () -> timeoutVictims.remove(victimId), 600L);
            String killedRaw = plugin.getConfig().getString("messages.raid-timeout-killed",
                    "<dark_red><bold>The Glitch consumed you.</bold> <gray>You failed to extract — raid loot lost. Stash is safe.</gray></dark_red>");
            try { p.sendMessage(MM.deserialize(killedRaw)); } catch (Exception ignored) {}
            Title.Times times = Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500));
            try {
                p.showTitle(Title.title(MM.deserialize("<dark_red><bold>Time's up</bold></dark_red>"),
                        MM.deserialize("<red>The Glitch consumed you</red>"), times));
            } catch (Exception ignored) {}
            try { p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_DEATH, 1.0f, 0.8f); } catch (Exception ignored) {}
            session.incrementDeaths();
            // Kill — triggers PlayerDeathEvent (mercy keep still applies, stash safe because not extracted)
            try {
                p.setHealth(0.0);
            } catch (Exception e) {
                try { p.damage(1000.0); } catch (Exception ignored) {}
            }
            // Failsafe teleport a bit later (after respawn) in case death was cancelled or player survived
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player pp = Bukkit.getPlayer(victimId);
                if (pp != null && pp.isOnline() && pp.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    teleportToHub(pp);
                }
            }, 60L);
        }
        endRaid(leaderId, RaidEndReason.TIMEOUT_DEATH);
    }

    public void teleportToHub(Player player) {
        try {
            World hub = Bukkit.getWorld(hubWorld);
            if (hub != null) {
                player.teleport(hub.getSpawnLocation());
                return;
            }
        } catch (Exception ignored) {}
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv tp " + player.getName() + " " + hubWorld);
        } catch (Exception ignored) {}
    }

    private void sendSummary(RaidSession session, RaidEndReason reason, int payout) {
        String titleRaw = plugin.getConfig().getString("messages.raid-summary-title", "<gold><bold>Raid Summary</bold></gold>");
        Component title = MM.deserialize(titleRaw);
        long durationMs = System.currentTimeMillis() - session.getStartTime();
        int durationSec = (int) Math.min(durationMs / 1000, durationSeconds);
        String durationStr = formatTime(durationSec);
        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);

        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            p.sendMessage(Component.empty());
            p.sendMessage(title);
            p.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
            String reasonLabel = reason.name();
            if (reason == RaidEndReason.TIMEOUT_DEATH) reasonLabel = "TIMEOUT (consumed)";
            else if (reason == RaidEndReason.EXTRACTED) reasonLabel = "EXTRACTED";
            p.sendMessage(MM.deserialize("<gray>Reason: <white>" + reasonLabel + "</white>"));
            if (lost) {
                p.sendMessage(MM.deserialize("<gray>Loot value: <gold>" + session.getLootValue() + "</gold> <gray>→ <red>LOST</red> <gray>(not extracted)</gray>"));
                p.sendMessage(MM.deserialize("<gray>Payout: <red>0</red> <gray>(stash safe)</gray>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Loot value: <gold>" + session.getLootValue() + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
            }
            p.sendMessage(MM.deserialize("<gray>Deaths: <red>" + session.getDeaths() + "</red>"));
            if (session.getDeaths() > 0) {
                p.sendMessage(MM.deserialize("<gray>Death recap: <red>" + session.getDeaths() + " death(s) this raid</red>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Death recap: <green>Flawless — no deaths!</green>"));
            }
            p.sendMessage(Component.empty());

            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
            Component subtitle;
            if (lost) {
                subtitle = MM.deserialize("<red>consumed • Loot lost</red>");
            } else if (reason == RaidEndReason.EXTRACTED) {
                subtitle = MM.deserialize("<green>extracted • Loot " + payout + " • Deaths " + session.getDeaths() + "</green>");
            } else {
                subtitle = MM.deserialize("<gray>" + reason.name().toLowerCase() + " • Loot " + payout + " • Deaths " + session.getDeaths() + "</gray>");
            }
            p.showTitle(Title.title(title, subtitle, times));
        }
        // Also show to leader if not in members set
        if (!session.getMembers().contains(session.getLeader())) {
            Player lp = Bukkit.getPlayer(session.getLeader());
            if (lp != null) {
                lp.sendMessage(Component.empty());
                lp.sendMessage(title);
                lp.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
                lp.sendMessage(MM.deserialize("<gray>Reason: <white>" + reason.name() + "</white>"));
                if (lost) {
                    lp.sendMessage(MM.deserialize("<gray>Loot value: <gold>" + session.getLootValue() + "</gold> <gray>→ <red>LOST</red></gray>"));
                } else {
                    lp.sendMessage(MM.deserialize("<gray>Loot value: <gold>" + session.getLootValue() + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
                }
                lp.sendMessage(MM.deserialize("<gray>Deaths: <red>" + session.getDeaths() + "</red>"));
                lp.sendMessage(Component.empty());
            }
        }
    }

    public String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public void shutdown() {
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
        timeoutVictims.clear();
        lastDeathMillis.clear();
    }
}
