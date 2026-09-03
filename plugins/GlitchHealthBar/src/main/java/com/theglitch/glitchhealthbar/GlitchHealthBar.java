package com.theglitch.glitchhealthbar;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;

public final class GlitchHealthBar extends JavaPlugin {

    private HealthBarManager manager;
    private volatile Set<String> enabledWorlds = Set.of();
    private int barLength;
    private boolean showNumbers;
    private double offsetExtra;
    private TextColor colorHigh;
    private TextColor colorMid;
    private TextColor colorLow;
    private TextColor colorEmpty;

    // Cached hot-path config — refreshed on reload
    private int tickPeriod = 5;
    private int rescanPeriod = 40;
    private String trackMode = "monster"; // monster | hostile | all

    private BukkitTask tickTask;
    private BukkitTask rescanTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        manager = new HealthBarManager(this);
        Bukkit.getPluginManager().registerEvents(new HealthBarListener(this, manager), this);
        scheduleTasks();

        GhBarCommand command = new GhBarCommand(this);
        if (getCommand("ghb") != null) {
            getCommand("ghb").setExecutor(command);
        }

        getLogger().info("GlitchHealthBar enabled (worlds=" + enabledWorlds + ", tick=" + tickPeriod + ").");
    }

    private void scheduleTasks() {
        if (tickTask != null) tickTask.cancel();
        if (rescanTask != null) rescanTask.cancel();
        // 5 ticks = 0.25s: smooth bar tracking without excess packets.
        tickTask = Bukkit.getScheduler().runTaskTimer(this, manager::tick, tickPeriod, tickPeriod);
        // Safety net: scan for untracked hostiles
        rescanTask = Bukkit.getScheduler().runTaskTimer(this, manager::rescan, rescanPeriod, rescanPeriod);
    }

    @Override
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (rescanTask != null) rescanTask.cancel();
        if (manager != null) {
            manager.clearAll();
        }
        getLogger().info("GlitchHealthBar disabled.");
    }

    public void reloadPlugin() {
        loadSettings();
        scheduleTasks(); // apply new periods
        manager.rescan();
        getLogger().info("GlitchHealthBar reloaded (worlds=" + enabledWorlds + ", tick=" + tickPeriod + ").");
    }

    private void loadSettings() {
        reloadConfig();
        try {
            Set<String> worlds = Set.copyOf(getConfig().getStringList("enabled-worlds"));
            if (worlds.isEmpty()) {
                getLogger().warning("enabled-worlds empty — no health bars will show until configured.");
            }
            enabledWorlds = worlds;

            barLength = Math.max(1, getConfig().getInt("bar-length", 10));
            if (barLength > 20) {
                getLogger().warning("bar-length " + barLength + " large — clamped to 20.");
                barLength = 20;
            }
            showNumbers = getConfig().getBoolean("show-numbers", true);
            offsetExtra = Math.max(0, getConfig().getDouble("offset-extra", 0.6));
            if (offsetExtra > 5) {
                getLogger().warning("offset-extra " + offsetExtra + " large — clamped to 5.");
                offsetExtra = 5;
            }
            colorHigh = parseColor("colors.high", 0x55FF55);
            colorMid = parseColor("colors.mid", 0xFFFF55);
            colorLow = parseColor("colors.low", 0xFF5555);
            colorEmpty = parseColor("colors.empty", 0x4A4A4A);

            int tp = getConfig().getInt("tick-period", 5);
            if (tp < 1 || tp > 20) {
                getLogger().warning("Invalid tick-period " + tp + " — clamped to 5.");
                tp = 5;
            }
            tickPeriod = tp;

            int rp = getConfig().getInt("rescan-period", 40);
            if (rp < 10 || rp > 200) {
                getLogger().warning("Invalid rescan-period " + rp + " — clamped to 40.");
                rp = 40;
            }
            rescanPeriod = rp;

            String mode = getConfig().getString("track-mode", "monster");
            if (mode == null || (!mode.equalsIgnoreCase("monster") && !mode.equalsIgnoreCase("all") && !mode.equalsIgnoreCase("hostile"))) {
                if (mode != null && !mode.isBlank()) getLogger().warning("Unknown track-mode '" + mode + "' — falling back to monster.");
                mode = "monster";
            }
            trackMode = mode.toLowerCase(java.util.Locale.ROOT);

        } catch (Exception e) {
            getLogger().warning("Failed to load GlitchHealthBar settings: " + e.getMessage());
        }
    }

    private TextColor parseColor(String path, int fallback) {
        String hex = getConfig().getString(path);
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) {
            TextColor color = TextColor.fromHexString(hex);
            if (color != null) {
                return color;
            }
            getLogger().warning("Invalid color hex at " + path + ": " + hex + " — using fallback.");
        } else if (hex != null) {
            getLogger().warning("Invalid color format at " + path + ": " + hex + " — using fallback.");
        }
        return TextColor.color(fallback);
    }

    public boolean shouldTrack(Mob mob) {
        if (mob instanceof Player) return false;
        if (!isEnabledWorld(mob.getWorld().getName())) return false;
        // Cached mobs mode — no getConfig() per spawn/tick
        if ("all".equals(trackMode)) return true;
        // default: only Monster (hostile) — covers MythicMobs that are monsters too
        return mob instanceof org.bukkit.entity.Monster;
    }

    public boolean isEnabledWorld(String world) {
        return enabledWorlds.contains(world);
    }

    public Set<String> getEnabledWorlds() {
        return enabledWorlds;
    }

    public HealthBarManager getManager() {
        return manager;
    }

    public int barLength() {
        return barLength;
    }

    public boolean showNumbers() {
        return showNumbers;
    }

    public double offsetExtra() {
        return offsetExtra;
    }

    public TextColor colorHigh() {
        return colorHigh;
    }

    public TextColor colorMid() {
        return colorMid;
    }

    public TextColor colorLow() {
        return colorLow;
    }

    public TextColor colorEmpty() {
        return colorEmpty;
    }

    public int tickPeriod() {
        return tickPeriod;
    }

    public int rescanPeriod() {
        return rescanPeriod;
    }

    public String trackMode() {
        return trackMode;
    }
}
