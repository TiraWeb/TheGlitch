package com.theglitch.glitchstash.extract;

import com.theglitch.common.FoliaScheduler;
import com.theglitch.glitchstash.AutoExtractScheduler;
import com.theglitch.glitchstash.GlitchStash;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player extraction guidance, shown only inside the red world whose cycle it
 * describes: nearest open point with distance + a turn arrow relative to where the
 * player is looking, or the countdown to the next window between cycles.
 * <p>
 * Java players see it as the MythicHUD top-left card (server/plugins/MythicHUD,
 * scripts/gen-hud.py), fed by {@link #state(UUID)} through %glitchstash_hud_*%.
 * The boss bar is kept for Bedrock (Geyser/Floodgate) players, whom MythicHUD
 * skips, or for everyone when auto-extract.hud.bossbar is "all".
 * <p>
 * Replaces VelKoth's own idle boss bar, which was global (every online player saw
 * every world's arenas, hub included) and labelled with raw arena ids — VelKoth's
 * boss bar is switched off in its config; its action bar still shows capture progress.
 */
public final class ExtractionHud {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    // Relative turn arrows, index 0 = straight ahead, clockwise in 45° steps.
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final GlitchStash plugin;
    private final Map<String, DynamicExtractionManager> managers;
    private final Map<String, AutoExtractScheduler> schedulers;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, HudState> states = new ConcurrentHashMap<>();

    /**
     * Snapshot for the HUD card. icon: a0..a7 (turn arrow, 0 = straight ahead,
     * clockwise), zone, closed. Texts are MiniMessage.
     */
    public record HudState(String icon, String title, String line1, String line2) {}

    /** Current HUD snapshot for a player, or null outside red worlds. */
    public HudState state(UUID id) {
        return states.get(id);
    }
    private FoliaScheduler.Cancellable task;

    public ExtractionHud(GlitchStash plugin, Map<String, DynamicExtractionManager> managers,
                         Map<String, AutoExtractScheduler> schedulers) {
        this.plugin = plugin;
        this.managers = managers;
        this.schedulers = schedulers;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("auto-extract.hud.enabled", true)) return;
        task = FoliaScheduler.runAtFixedRateGlobal(plugin, this::tick, 40L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        bars.clear();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String world = player.getWorld().getName().toLowerCase(java.util.Locale.ROOT);
            DynamicExtractionManager manager = managers.get(world);
            if (manager == null) {
                hide(player);
                states.remove(player.getUniqueId());
                continue;
            }
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(),
                    id -> BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS));
            List<ExtractionPoint> points = manager.getCurrentPoints();
            if (points.isEmpty()) {
                AutoExtractScheduler scheduler = schedulers.get(world);
                long ms = scheduler == null ? -1L : scheduler.getMillisUntilNextCycle();
                String when = ms < 0 ? "soon" : "in <white>" + mmss(ms / 1000L) + "</white>";
                bar.name(MM.deserialize("<gray>⚡ Extraction closed — next window " + when + "</gray>"));
                bar.color(BossBar.Color.RED);
                bar.progress(1f);
                states.put(player.getUniqueId(), new HudState("closed",
                        "<#9CA3AF>Extraction Closed",
                        "<#D1D5DB>Survive until the next window",
                        "<#9CA3AF>Opens " + (ms < 0 ? "soon" : "in <white>" + mmss(ms / 1000L))));
            } else {
                ExtractionPoint nearest = null;
                double best = Double.MAX_VALUE;
                Location loc = player.getLocation();
                for (ExtractionPoint p : points) {
                    double dx = p.x() + 0.5 - loc.getX();
                    double dz = p.z() + 0.5 - loc.getZ();
                    double d = dx * dx + dz * dz;
                    if (d < best) { best = d; nearest = p; }
                }
                double dx = nearest.x() + 0.5 - loc.getX();
                double dz = nearest.z() + 0.5 - loc.getZ();
                int dist = (int) Math.round(Math.sqrt(best));
                // Minecraft yaw: 0 = +Z (south), 90 = -X (west).
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                String compass = COMPASS[Math.floorMod((int) Math.round(targetYaw / 45.0), 8)];
                int turn = Math.floorMod((int) Math.round((targetYaw - loc.getYaw()) / 45.0), 8);
                String arrow = ARROWS[turn];
                long left = Math.max(0L, nearest.openUntilEpochMs() - System.currentTimeMillis()) / 1000L;
                String status = dist <= nearest.radiusBlocks() + 1
                        ? "<green><bold>IN ZONE</bold> — hold it to extract</green>"
                        : "<white>" + dist + "m</white> <yellow>" + arrow + " " + compass + "</yellow>";
                // No close timer here — the GlitchRaid "Time left" bar right above shows the same window.
                bar.name(MM.deserialize("<gold>⚡ Extraction</gold> " + status
                        + " <dark_gray>|</dark_gray> <gray>" + points.size() + " open</gray>"));
                bar.color(BossBar.Color.YELLOW);
                long total = Math.max(1L, plugin.getConfig().getInt("auto-extract.raid-duration-minutes", 30) * 60L);
                bar.progress((float) Math.max(0.0, Math.min(1.0, left / (double) total)));
                boolean inZone = dist <= nearest.radiusBlocks() + 1;
                states.put(player.getUniqueId(), new HudState(inZone ? "zone" : "a" + turn,
                        inZone ? "<#4ADE80>Extraction Zone" : "<#F5A742>Extraction Point",
                        inZone ? "<white>Hold the zone to extract"
                                : "<white>Reach extraction <#F5A742>" + dist + "m <#9CA3AF>" + compass,
                        "<#9CA3AF>Closes in <white>" + mmss(left) + " <#6B7280>· <#9CA3AF>" + points.size() + " open"));
            }
            if (showBossBar(player)) player.showBossBar(bar);
            else player.hideBossBar(bar);
        }
        bars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        states.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    /** Bedrock (Floodgate) players can't see MythicHUD, so they keep the bar. */
    private boolean showBossBar(Player player) {
        String mode = plugin.getConfig().getString("auto-extract.hud.bossbar", "bedrock-only");
        if ("all".equalsIgnoreCase(mode)) return true;
        if ("none".equalsIgnoreCase(mode)) return false;
        return player.getUniqueId().getMostSignificantBits() == 0L; // Floodgate UUIDs: 00000000-0000-0000-...
    }

    private void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private static String mmss(long seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }
}
