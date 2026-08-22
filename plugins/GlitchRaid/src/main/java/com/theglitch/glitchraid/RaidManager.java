package com.theglitch.glitchraid;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
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
 * Loot and deaths are per-player (not shared) as requested.
 */
public final class RaidManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final PartyManager partyManager;

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
        this.partyManager = new PartyManager(plugin);
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
        if (partyManager != null) partyManager.reload();
        plugin.getLogger().info("RaidManager reloaded (duration=" + durationSeconds + "s, payout=" + payoutMultiplier + ", partyMax=" + partyMaxSize + ", hub=" + hubWorld + ", autoWorld=" + autoStartWorld + ").");
    }

    public PartyManager getPartyManager() {
        return partyManager;
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
     * Starts a new raid for the given leader. If leader is in a party, the
     * entire party is pulled into the same raid session and party members
     * not yet in glitch_red are teleported to the leader.
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

        // Include party members if leader has a party
        Party party = partyManager.getParty(uuid);
        if (party != null) {
            members.addAll(party.getMembers());
        } else {
            members.add(uuid);
            // Also ensure leader is in map even if solo party not created
        }

        RaidSession session = new RaidSession(uuid, members, now, end);

        // Map every member to the same session so they share timer but keep per-player loot
        for (UUID mid : members) {
            activeRaids.put(mid, session);
        }

        String timeLeftRaw = plugin.getConfig().getString("messages.raid-time-left", "<aqua>Time left: <white><time></white></aqua>");
        String formatted = formatTime(durationSeconds);
        Component initialName = MM.deserialize(timeLeftRaw.replace("<time>", formatted));
        BossBar bar = BossBar.bossBar(initialName, 1.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        bossBars.put(uuid, bar);
        leader.showBossBar(bar);
        // Also show to party members already online in same world
        for (UUID mid : members) {
            if (mid.equals(uuid)) continue;
            Player p = Bukkit.getPlayer(mid);
            if (p != null && p.isOnline()) {
                p.showBossBar(bar);
            }
        }

        String key = auto ? "messages.raid-auto-started" : "messages.raid-started";
        String fallback = auto ? "<green><bold>Raid started!</bold> <gray>You entered the Glitch — 30:00 to extract!</gray>" : "<green><bold>Raid started!</bold> <gray>Good luck — the Glitch awaits.</gray>";
        String startedRaw = plugin.getConfig().getString(key, fallback);
        if (startedRaw == null) startedRaw = fallback;
        for (UUID mid : members) {
            Player p = Bukkit.getPlayer(mid);
            if (p != null && p.isOnline()) {
                p.sendMessage(MM.deserialize(startedRaw));
                p.sendActionBar(MM.deserialize("<gray>Extract before <white>" + formatted + "</white> or lose everything!</gray>"));
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(uuid), 20L, 20L);
        timers.put(uuid, task);

        // Teleport party members not yet in the raid world to the leader
        if (party != null) {
            for (UUID mid : members) {
                if (mid.equals(uuid)) continue;
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline() && !p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    try {
                        p.teleport(leader.getLocation());
                        p.sendMessage(MM.deserialize("<gray>Teleported to raid leader <white>" + leader.getName() + "</white> in <white>" + autoStartWorld + "</white>.</gray>"));
                        plugin.getLogger().info("Auto-teleported party member " + p.getName() + " to raid leader " + leader.getName() + " in " + autoStartWorld);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to teleport party member " + p.getName() + " to raid: " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().info("Raid started for " + leader.getName() + (auto ? " (auto)" : "") + " members=" + members.size() + " (duration=" + durationSeconds + "s)");
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

        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);
        // Per-player payout handling happens in sendSummary; base for logging is total
        int totalLoot = session.getLootValue();

        Component endedComp;
        if (reason == RaidEndReason.EXTRACTED) {
            String raw = plugin.getConfig().getString("messages.raid-extracted", "<green><bold>Extracted!</bold> <gray>Loot secured.</gray></green>");
            endedComp = MM.deserialize(raw);
        } else if (lost) {
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

        final RaidSession summarySession = session;
        final RaidEndReason summaryReason = reason;
        Bukkit.getScheduler().runTaskLater(plugin, () -> sendSummary(summarySession, summaryReason), summaryDelayTicks);

        String leaderName = Bukkit.getOfflinePlayer(leaderId).getName();
        if (leaderName == null) leaderName = leaderId.toString();
        plugin.getLogger().info("Raid ended for " + leaderName + " reason=" + reason + " totalLoot=" + totalLoot + " members=" + membersSnapshot.size());
    }

    /**
     * Called from VelKoth KothWinEvent bridge — winner's inventory and all
     * party members' inventories are stashed before ending raid.
     */
    public void handleKothWin(Player winner, String kothName) {
        if (!isInRaid(winner.getUniqueId())) return;
        RaidSession session = activeRaids.get(winner.getUniqueId());
        if (session != null) {
            // Stash every party/raid member's inventory (GlitchStash handles merge)
            for (UUID mid : new HashSet<>(session.getMembers())) {
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline() && p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                    try {
                        com.theglitch.glitchstash.GlitchStash stashPlugin = com.theglitch.glitchstash.GlitchStash.getInstance();
                        if (stashPlugin != null) {
                            stashPlugin.getStashManager().saveStash(p.getUniqueId(), p.getName(),
                                    p.getInventory().getContents(), p.getInventory().getArmorContents(), p.getInventory().getItemInOffHand());
                            // Clear non-winner inventories here (winner will be cleared by GlitchStash listener)
                            if (!mid.equals(winner.getUniqueId())) {
                                p.getInventory().clear();
                                p.getInventory().setArmorContents(new ItemStack[4]);
                                p.getInventory().setItemInOffHand(null);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to stash party member " + mid + " on Koth win: " + e.getMessage());
                    }
                }
            }
        }
        plugin.getLogger().info("Handling KothWin extraction for " + winner.getName() + " (" + kothName + ") — party stashed");
        handleExtraction(winner);
    }

    /**
     * Called when a player successfully extracts (Koth win / hub teleport).
     * Ends their raid as EXTRACTED — loot is preserved (already stashed).
     * If party, the entire raid ends together and other members are pulled to hub.
     * Also pays per-player payout via Vault.
     */
    public void handleExtraction(Player player) {
        if (!isInRaid(player.getUniqueId())) return;
        RaidSession session = activeRaids.get(player.getUniqueId());
        Set<UUID> membersSnapshot = session != null ? new HashSet<>(session.getMembers()) : Set.of(player.getUniqueId());
        membersSnapshot.add(player.getUniqueId());

        // Payout per-player before ending (so loot still available)
        if (session != null) {
            for (UUID mid : membersSnapshot) {
                int myLoot = session.getLootValue(mid);
                if (myLoot <= 0) continue;
                int payout = (int) Math.round(myLoot * payoutMultiplier);
                if (payout <= 0) continue;
                Player p = Bukkit.getPlayer(mid);
                if (p != null && p.isOnline()) {
                    try {
                        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                        if (rsp != null) {
                            Economy econ = rsp.getProvider();
                            if (econ != null) {
                                EconomyResponse resp = econ.depositPlayer(p, payout);
                                if (resp.transactionSuccess()) {
                                    p.sendMessage(MM.deserialize("<green>+" + payout + " Shards payout for extraction! <gray>(loot " + myLoot + " ×" + payoutMultiplier + ")</gray></green>"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed payout for " + mid + ": " + e.getMessage());
                    }
                } else {
                    // Offline payout — deposit via OfflinePlayer
                    try {
                        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                        if (rsp != null && rsp.getProvider() != null) {
                            Economy econ = rsp.getProvider();
                            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(mid);
                            EconomyResponse resp = econ.depositPlayer(offline, payout);
                            if (!resp.transactionSuccess()) {
                                plugin.getLogger().warning("Offline payout failed for " + mid + ": " + resp.errorMessage);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Offline payout error for " + mid + ": " + e.getMessage());
                    }
                }
            }
        }

        endRaid(player.getUniqueId(), RaidEndReason.EXTRACTED);
        // Pull remaining party/raid members who are still in glitch_red to hub (shared extraction)
        for (UUID mid : membersSnapshot) {
            if (mid.equals(player.getUniqueId())) continue; // winner already teleported by GlitchStash
            Player other = Bukkit.getPlayer(mid);
            if (other != null && other.isOnline() && other.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player p2 = Bukkit.getPlayer(mid);
                    if (p2 != null && p2.isOnline() && p2.getWorld().getName().equalsIgnoreCase(autoStartWorld)) {
                        teleportToHub(p2);
                        p2.sendMessage(MM.deserialize("<green>Party extraction — pulled to hub with <white>" + player.getName() + "</white>.</green>"));
                    }
                }, 20L);
            }
        }
    }

    /** Adds a party member who accepted mid-raid to the ongoing session. */
    public void handlePartyMemberAddedToActiveRaid(Player newMember, RaidSession session) {
        UUID nid = newMember.getUniqueId();
        if (isInRaid(nid)) return;
        session.getMembers().add(nid);
        activeRaids.put(nid, session);
        BossBar bar = bossBars.get(session.getLeader());
        if (bar != null) {
            try { newMember.showBossBar(bar); } catch (Exception ignored) {}
        }
        newMember.sendMessage(MM.deserialize("<green>Joined ongoing raid! <gray>Time left: <white>" + formatTime(session.getRemainingSeconds()) + "</white></gray>"));
    }

    public BossBar getBossBarForSession(RaidSession session) {
        if (session == null) return null;
        return bossBars.get(session.getLeader());
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
        session.addLoot(uuid, amount);
    }

    /**
     * Add loot value derived from ItemStacks' sell prices (GlitchShops) or fallback.
     * Used for container loot and other item-based rewards so the BossBar/status reflects real value.
     * Per-player — does not share across party.
     */
    public void addLootFromItems(Player player, Collection<ItemStack> items) {
        if (items == null || items.isEmpty()) return;
        RaidSession session = activeRaids.get(player.getUniqueId());
        if (session == null) return;
        int value = 0;
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
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir()) continue;
                value += item.getAmount() * 5;
            }
            if (value <= 0) value = items.size() * 10;
        }
        if (value > 0) {
            session.addLoot(player.getUniqueId(), value);
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
        session.incrementDeaths(uuid);
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
        membersSnapshot.add(leaderId);
        for (UUID memberId : membersSnapshot) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().getName().equalsIgnoreCase(autoStartWorld)) continue;
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
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
            session.incrementDeaths(victimId);
            try {
                p.setHealth(0.0);
            } catch (Exception e) {
                try { p.damage(1000.0); } catch (Exception ignored) {}
            }
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

    private void sendSummary(RaidSession session, RaidEndReason reason) {
        String titleRaw = plugin.getConfig().getString("messages.raid-summary-title", "<gold><bold>Raid Summary</bold></gold>");
        Component title = MM.deserialize(titleRaw);
        long durationMs = System.currentTimeMillis() - session.getStartTime();
        int durationSec = (int) Math.min(durationMs / 1000, durationSeconds);
        String durationStr = formatTime(durationSec);
        boolean lost = (reason == RaidEndReason.TIMEOUT || reason == RaidEndReason.TIMEOUT_DEATH);

        for (UUID memberId : session.getMembers()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            int myLoot = session.getLootValue(memberId);
            int myDeaths = session.getDeaths(memberId);
            int payout = lost ? 0 : (int) Math.round(myLoot * payoutMultiplier);
            p.sendMessage(Component.empty());
            p.sendMessage(title);
            p.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
            String reasonLabel = reason.name();
            if (reason == RaidEndReason.TIMEOUT_DEATH) reasonLabel = "TIMEOUT (consumed)";
            else if (reason == RaidEndReason.EXTRACTED) reasonLabel = "EXTRACTED";
            p.sendMessage(MM.deserialize("<gray>Reason: <white>" + reasonLabel + "</white>"));
            if (lost) {
                p.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>→ <red>LOST</red> <gray>(not extracted)</gray>"));
                p.sendMessage(MM.deserialize("<gray>Payout: <red>0</red> <gray>(stash safe)</gray>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
            }
            p.sendMessage(MM.deserialize("<gray>Your deaths: <red>" + myDeaths + "</red>"));
            if (myDeaths > 0) {
                p.sendMessage(MM.deserialize("<gray>Death recap: <red>" + myDeaths + " death(s) this raid</red>"));
            } else {
                p.sendMessage(MM.deserialize("<gray>Death recap: <green>Flawless — no deaths!</green>"));
            }
            // Party loot recap for context
            if (session.getMembers().size() > 1) {
                p.sendMessage(MM.deserialize("<dark_gray>Party loot total: <gray>" + session.getLootValue() + "</gray></dark_gray>"));
            }
            p.sendMessage(Component.empty());

            Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2000), Duration.ofMillis(500));
            Component subtitle;
            if (lost) {
                subtitle = MM.deserialize("<red>consumed • Loot lost</red>");
            } else if (reason == RaidEndReason.EXTRACTED) {
                subtitle = MM.deserialize("<green>extracted • Loot " + payout + " • Deaths " + myDeaths + "</green>");
            } else {
                subtitle = MM.deserialize("<gray>" + reason.name().toLowerCase() + " • Loot " + payout + " • Deaths " + myDeaths + "</gray>");
            }
            p.showTitle(Title.title(title, subtitle, times));
        }
        if (!session.getMembers().contains(session.getLeader())) {
            Player lp = Bukkit.getPlayer(session.getLeader());
            if (lp != null) {
                int myLoot = session.getLootValue(session.getLeader());
                int myDeaths = session.getDeaths(session.getLeader());
                int payout = lost ? 0 : (int) Math.round(myLoot * payoutMultiplier);
                lp.sendMessage(Component.empty());
                lp.sendMessage(title);
                lp.sendMessage(MM.deserialize("<gray>Duration: <white>" + durationStr + "</white>"));
                lp.sendMessage(MM.deserialize("<gray>Reason: <white>" + reason.name() + "</white>"));
                if (lost) {
                    lp.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>→ <red>LOST</red></gray>"));
                } else {
                    lp.sendMessage(MM.deserialize("<gray>Your loot: <gold>" + myLoot + "</gold> <gray>x" + payoutMultiplier + " = <green>" + payout + "</green>"));
                }
                lp.sendMessage(MM.deserialize("<gray>Your deaths: <red>" + myDeaths + "</red>"));
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
