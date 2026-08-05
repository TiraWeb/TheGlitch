package com.theglitch.glitchhealthbar;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class GlitchHealthBar extends JavaPlugin {

    private HealthBarManager manager;
    private List<String> enabledWorlds;
    private String mobsMode;
    private int barLength;
    private boolean showNumbers;
    private double offsetFraction;
    private TextColor colorHigh;
    private TextColor colorMid;
    private TextColor colorLow;
    private TextColor colorEmpty;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        manager = new HealthBarManager(this);
        Bukkit.getPluginManager().registerEvents(new HealthBarListener(this, manager), this);
        Bukkit.getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);

        getLogger().info("GlitchHealthBar enabled (mobs=" + mobsMode + ", worlds=" + enabledWorlds + ").");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.clearAll();
        }
        getLogger().info("GlitchHealthBar disabled.");
    }

    private void loadSettings() {
        reloadConfig();
        enabledWorlds = getConfig().getStringList("enabled-worlds");
        mobsMode = getConfig().getString("mobs", "MYTHICMOBS").toUpperCase();
        barLength = Math.max(1, getConfig().getInt("bar-length", 10));
        showNumbers = getConfig().getBoolean("show-numbers", true);
        offsetFraction = Math.max(0, getConfig().getDouble("offset-fraction", 0.6));
        colorHigh = parseColor("colors.high", 0x55FF55);
        colorMid = parseColor("colors.mid", 0xFFFF55);
        colorLow = parseColor("colors.low", 0xFF5555);
        colorEmpty = parseColor("colors.empty", 0x4A4A4A);
    }

    private TextColor parseColor(String path, int fallback) {
        String hex = getConfig().getString(path);
        if (hex != null && hex.matches("#[0-9a-fA-F]{6}")) {
            TextColor color = TextColor.fromHexString(hex);
            if (color != null) {
                return color;
            }
        }
        return TextColor.color(fallback);
    }

    public boolean shouldTrack(Mob mob) {
        if (mob instanceof Player) return false;
        if (!isEnabledWorld(mob.getWorld().getName())) return false;
        return mobsMode.equals("ALL") || mob.hasMetadata("MythicMob");
    }

    public boolean isEnabledWorld(String world) {
        return enabledWorlds.contains(world);
    }

    public String mobsMode() {
        return mobsMode;
    }

    public int barLength() {
        return barLength;
    }

    public boolean showNumbers() {
        return showNumbers;
    }

    public double offsetFraction() {
        return offsetFraction;
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
}
