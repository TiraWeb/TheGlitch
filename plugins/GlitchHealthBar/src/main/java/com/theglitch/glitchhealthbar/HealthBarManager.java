package com.theglitch.glitchhealthbar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class HealthBarManager {

    private final GlitchHealthBar plugin;
    private final Map<UUID, BarEntry> bars = new HashMap<>();

    private record BarEntry(LivingEntity target, TextDisplay display) {
    }

    public HealthBarManager(GlitchHealthBar plugin) {
        this.plugin = plugin;
    }

    public void attach(LivingEntity mob) {
        try {
            if (!mob.isValid() || mob.isDead()) return;
            if (bars.containsKey(mob.getUniqueId())) return;

            TextDisplay display = mob.getWorld().spawn(barLocation(mob), TextDisplay.class);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setViewRange(2);
            display.setPersistent(false);
            display.text(barText(mob));

            bars.put(mob.getUniqueId(), new BarEntry(mob, display));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to attach bar to " + mob.getType() + ": " + e.getMessage());
        }
    }

    public void refresh(LivingEntity mob) {
        try {
            BarEntry entry = bars.get(mob.getUniqueId());
            if (entry == null || !mob.isValid() || mob.isDead()) return;
            entry.display().teleport(barLocation(mob));
            entry.display().text(barText(mob));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh bar for " + mob.getType() + ": " + e.getMessage());
        }
    }

    public void remove(LivingEntity mob) {
        BarEntry entry = bars.remove(mob.getUniqueId());
        if (entry != null) {
            entry.display().remove();
        }
    }

    public void tick() {
        Iterator<Map.Entry<UUID, BarEntry>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            BarEntry entry = it.next().getValue();
            LivingEntity target = entry.target();
            try {
                if (!target.isValid() || target.isDead()
                        || !target.getWorld().isChunkLoaded(
                                target.getLocation().getBlockX() >> 4,
                                target.getLocation().getBlockZ() >> 4)) {
                    entry.display().remove();
                    it.remove();
                    continue;
                }
                entry.display().teleport(barLocation(target));
                entry.display().text(barText(target));
            } catch (Exception e) {
                plugin.getLogger().warning("Bar tick error for " + target.getType() + ": " + e.getMessage());
                entry.display().remove();
                it.remove();
            }
        }
    }

    public void rescan() {
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.isEnabledWorld(world.getName())) continue;
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (bars.containsKey(mob.getUniqueId())) continue;
                if (plugin.shouldTrack(mob)) {
                    attach(mob);
                }
            }
        }
    }

    public int count() {
        return bars.size();
    }

    public void attachTestBar(Player player) {
        try {
            Location loc = player.getLocation().add(0, 2.5, 0);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setViewRange(2);
            display.setPersistent(false);
            display.text(Component.text("██████████ 100/100", TextColor.color(0x55FF55)));

            // Follows the player for 10 seconds — verifies the follow mechanic
            // that the real mob bars use.
            int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline() && display.isValid()) {
                    display.teleport(player.getLocation().add(0, 2.5, 0));
                }
            }, 4L, 4L).getTaskId();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getServer().getScheduler().cancelTask(taskId);
                display.remove();
            }, 200L);
        } catch (Exception e) {
            plugin.getLogger().warning("Test bar failed: " + e.getMessage());
        }
    }

    public void clearAll() {
        for (BarEntry entry : bars.values()) {
            entry.display().remove();
        }
        bars.clear();
    }

    private Location barLocation(LivingEntity mob) {
        return mob.getLocation().add(0, mob.getHeight() * plugin.offsetFraction() + 0.4, 0);
    }

    private Component barText(LivingEntity mob) {
        double hp = Math.max(0, mob.getHealth());
        double max = Math.max(1, mob.getMaxHealth());
        double fraction = hp / max;
        int length = plugin.barLength();
        int filled = (int) Math.round(fraction * length);
        if (filled > length) filled = length;

        TextColor fillColor = fraction >= 0.5 ? plugin.colorHigh()
                : fraction >= 0.25 ? plugin.colorMid()
                : plugin.colorLow();

        Component bar = Component.text("█".repeat(filled), fillColor)
                .append(Component.text("░".repeat(length - filled), plugin.colorEmpty()));
        if (plugin.showNumbers()) {
            bar = bar.append(Component.text(" " + (int) hp + "/" + (int) max, TextColor.color(0xFFFFFF)));
        }
        return bar;
    }
}
