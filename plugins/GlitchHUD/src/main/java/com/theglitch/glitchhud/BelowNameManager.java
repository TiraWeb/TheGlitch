package com.theglitch.glitchhud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a compact value under nametag in glitch_red.
 * Uses DisplaySlot.BELOW_NAME on the main scoreboard (shared).
 * Updates are dirty-checked and throttled.
 */
public final class BelowNameManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String OBJ_NAME = "glitchhud_below";

    private final GlitchHUD plugin;
    private final PlaceholderResolver placeholders;
    private final HudManager hudManager;
    private final Map<UUID, Integer> lastValue = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile String world = "glitch_red";
    private volatile String mode = "stacks"; // stacks | payout | level
    private volatile String emptyIcon = "";

    private Objective objective;
    private FoliaScheduler.Cancellable task;

    public BelowNameManager(GlitchHUD plugin, PlaceholderResolver placeholders, HudManager hudManager) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.hudManager = hudManager;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("below-name.enabled", true);
        world = plugin.getConfig().getString("below-name.world", "glitch_red");
        mode = plugin.getConfig().getString("below-name.mode", "stacks");
        emptyIcon = plugin.getConfig().getString("below-name.empty-icon", "<dark_gray>\uE047</dark_gray>");
        if (world == null || world.isBlank()) world = "glitch_red";
        if (mode == null || mode.isBlank()) mode = "stacks";
    }

    public void start() {
        if (task != null) try { task.cancel(); } catch (Exception ignored) {}
        if (!enabled) {
            clear();
            return;
        }
        ensureObjective();
        task = FoliaScheduler.runAtFixedRateGlobal(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) try { task.cancel(); } catch (Exception ignored) {}
        task = null;
        clear();
        lastValue.clear();
    }

    private void ensureObjective() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            try {
                obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY, Component.text("stacks"));
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
                obj.setRenderType(RenderType.INTEGER);
                try { obj.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
                // Show tiny label? Under nametag shows numeric value; we hide red number blank and use Fixed? Keep blank for clean.
            } catch (Exception e) {
                plugin.getLogger().warning("BelowName: failed to create objective: " + e.getMessage());
                return;
            }
        } else {
            try { obj.setDisplaySlot(DisplaySlot.BELOW_NAME); } catch (Exception ignored) {}
        }
        objective = obj;
    }

    private void clear() {
        if (objective != null) {
            try { objective.unregister(); } catch (Exception ignored) {}
            objective = null;
        }
        // Clear scores from main board
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            for (String entry : new java.util.HashSet<>(board.getEntries())) {
                // Only clear if it belongs to our objective (we can't easily know, so clear all below-name scores)
                // Safer: just clear for online players
            }
        } catch (Exception ignored) {}
    }

    private void tick() {
        if (!enabled || objective == null) return;
        try {
            if (objective.getScoreboard() == null) {
                ensureObjective();
                if (objective == null) return;
            }
        } catch (Exception e) {
            ensureObjective();
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            boolean inWorld = world.equalsIgnoreCase(target.getWorld().getName());
            if (!inWorld) {
                Integer last = lastValue.remove(target.getUniqueId());
                if (last != null) {
                    try { objective.getScore(target.getName()).resetScore(); } catch (Exception ignored) {}
                    // Also clear from all viewer personal boards
                    for (Player viewer : Bukkit.getOnlinePlayers()) {
                        Scoreboard vb = hudManager.getBoard(viewer.getUniqueId());
                        if (vb == null) continue;
                        try {
                            Objective vo = vb.getObjective("glitchhud_below");
                            if (vo != null) vo.getScore(target.getName()).resetScore();
                        } catch (Exception ignored) {}
                    }
                }
                continue;
            }
            int value = resolveValue(target);
            Integer last = lastValue.get(target.getUniqueId());
            if (last != null && last == value) continue;
            try {
                // Main board
                applyBelowScore(objective.getScore(target.getName()), value);
                // Mirror to all viewer personal boards so per-player sidebars still show belowName
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    Scoreboard vb = hudManager.getBoard(viewer.getUniqueId());
                    if (vb == null) continue;
                    hudManager.ensureBelowObjective(vb);
                    Objective vo = vb.getObjective("glitchhud_below");
                    if (vo == null) continue;
                    applyBelowScore(vo.getScore(target.getName()), value);
                }
                lastValue.put(target.getUniqueId(), value);
            } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("BelowName update failed for " + target.getName() + ": " + e.getMessage());
                }
            }
        }
        lastValue.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void applyBelowScore(Score score, int value) {
        if (value <= 0) {
            try { score.numberFormat(NumberFormat.fixed(MM.deserialize(emptyIcon))); } catch (Exception ex) {
                try { score.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
            }
            score.setScore(0);
        } else {
            try {
                String glyph = UiConstants.STAR_FULL;
                Component fixed = MM.deserialize("<gold>" + glyph + "</gold> <white>" + value + "</white>");
                score.numberFormat(NumberFormat.fixed(fixed));
            } catch (Exception ex) {
                try { score.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
            }
            score.setScore(value);
        }
    }

    private int resolveValue(Player p) {
        return switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "payout" -> placeholders.getPayout(p);
            case "level" -> {
                String lvl = placeholders.getGlitchLevel(p);
                try { yield Integer.parseInt(lvl); } catch (Exception e) { yield 0; }
            }
            default -> placeholders.getStacks(p); // stacks
        };
    }

    public void onWorldChange(Player p) {
        if (!enabled || objective == null) return;
        FoliaScheduler.runGlobal(plugin, this::tick);
    }

    public void syncViewer(Player viewer) {
        if (!enabled || viewer == null) return;
        Scoreboard vb = hudManager.getBoard(viewer.getUniqueId());
        if (vb == null) return;
        hudManager.ensureBelowObjective(vb);
        Objective vo = vb.getObjective("glitchhud_below");
        if (vo == null) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!world.equalsIgnoreCase(target.getWorld().getName())) continue;
            int value = resolveValue(target);
            try { applyBelowScore(vo.getScore(target.getName()), value); } catch (Exception ignored) {}
        }
    }

    public void onQuit(Player p) {
        lastValue.remove(p.getUniqueId());
        try { if (objective != null) objective.getScore(p.getName()).resetScore(); } catch (Exception ignored) {}
        // Also clear from all viewer boards
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Scoreboard vb = hudManager.getBoard(viewer.getUniqueId());
            if (vb == null) continue;
            try {
                Objective vo = vb.getObjective("glitchhud_below");
                if (vo != null) vo.getScore(p.getName()).resetScore();
            } catch (Exception ignored) {}
        }
    }
}
