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

    private static final double MOVE_THRESHOLD_SQ = 0.01; // ~0.1 block; prevents packet spam when stationary

    private static final class BarEntry {
        final LivingEntity target;
        final TextDisplay display;
        double lastHp = -1;
        Location lastLoc = null;

        BarEntry(LivingEntity target, TextDisplay display) {
            this.target = target;
            this.display = display;
        }
    }

    public HealthBarManager(GlitchHealthBar plugin) {
        this.plugin = plugin;
    }

    public void attach(LivingEntity mob) {
        try {
            if (!mob.isValid() || mob.isDead()) return;
            if (bars.containsKey(mob.getUniqueId())) return;

            TextDisplay display = mob.getWorld().spawn(barLocation(mob), TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setViewRange(2);
                d.setPersistent(false);
            });
            display.text(barText(mob));

            BarEntry entry = new BarEntry(mob, display);
            entry.lastLoc = barLocation(mob);
            entry.lastHp = Math.max(0, mob.getHealth());
            bars.put(mob.getUniqueId(), entry);
            // Reduced log spam: only log at fine level or every N? Keep info but not per mob flood
            // plugin.getLogger().info("Bar attached to " + mob.getType() + " in " + mob.getWorld().getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to attach bar to " + mob.getType() + ": " + e.getMessage());
        }
    }

    public void refresh(LivingEntity mob) {
        try {
            BarEntry entry = bars.get(mob.getUniqueId());
            if (entry == null || !mob.isValid() || mob.isDead()) return;
            // Only teleport if moved — check threshold
            Location newLoc = barLocation(mob);
            if (entry.lastLoc == null || newLoc.distanceSquared(entry.lastLoc) > MOVE_THRESHOLD_SQ) {
                entry.display.teleport(newLoc);
                entry.lastLoc = newLoc;
            }
            double hp = Math.max(0, mob.getHealth());
            if (hp != entry.lastHp) {
                entry.display.text(barText(mob));
                entry.lastHp = hp;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to refresh bar for " + mob.getType() + ": " + e.getMessage());
        }
    }

    public void remove(LivingEntity mob) {
        BarEntry entry = bars.remove(mob.getUniqueId());
        if (entry != null) {
            entry.display.remove();
        }
    }

    /**
     * Follow + refresh pass. Runs every tickPeriod so bars track moving mobs
     * smoothly; text is only re-sent when HP changed, teleport only when moved.
     */
    public void tick() {
        if (bars.isEmpty()) return;
        // Skip whole tick if no player in enabled worlds — no one can see bars anyway
        if (!hasPlayersInEnabledWorlds()) return;

        Iterator<Map.Entry<UUID, BarEntry>> it = bars.entrySet().iterator();
        while (it.hasNext()) {
            BarEntry entry = it.next().getValue();
            LivingEntity target = entry.target;
            try {
                Location targetLoc = target.getLocation();
                if (!target.isValid() || target.isDead()
                        || !target.getWorld().isChunkLoaded(
                                targetLoc.getBlockX() >> 4,
                                targetLoc.getBlockZ() >> 4)) {
                    entry.display.remove();
                    it.remove();
                    continue;
                }
                // Only teleport if moved — saves packets for stationary mobs (reuse cached Location)
                Location curLoc = targetLoc.add(0, target.getHeight() + plugin.offsetExtra(), 0);
                if (entry.lastLoc == null || curLoc.distanceSquared(entry.lastLoc) > MOVE_THRESHOLD_SQ) {
                    entry.display.teleport(curLoc);
                    entry.lastLoc = curLoc;
                }
                double hp = Math.max(0, target.getHealth());
                if (hp != entry.lastHp) {
                    entry.display.text(barText(target));
                    entry.lastHp = hp;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Bar tick error for " + target.getType() + ": " + e.getMessage());
                entry.display.remove();
                it.remove();
            }
        }
    }

    /** Attach bars to any untracked hostile in enabled worlds (safety net). */
    public void rescan() {
        // Skip scan when no players can see bars — major Hot-path saving
        if (!hasPlayersInEnabledWorlds()) return;
        // Early exit if too many bars — prevents runaway scanning/allocations
        if (bars.size() > 200) return;

        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.isEnabledWorld(world.getName())) continue;
            // Skip world with no players — avoids scanning empty worlds
            if (world.getPlayers().isEmpty()) continue;
            // Filtered API avoids iterating all entities (items, armor stands, etc.)
            for (Mob mob : world.getEntitiesByClass(Mob.class)) {
                if (bars.size() > 200) return;
                if (bars.containsKey(mob.getUniqueId())) continue;
                if (plugin.shouldTrack(mob)) {
                    attach(mob);
                }
            }
        }
    }

    private boolean hasPlayersInEnabledWorlds() {
        // Single pass over online players — cheap vs scanning all entities in all worlds
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (plugin.isEnabledWorld(p.getWorld().getName())) return true;
        }
        return false;
    }

    public int count() {
        return bars.size();
    }

    public void attachTestBar(Player player) {
        try {
            Location loc = player.getLocation().add(0, 2.5, 0);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setSeeThrough(true);
                d.setShadowed(true);
                d.setViewRange(2);
                d.setPersistent(false);
            });
            display.text(Component.text("██████████ 100/100", TextColor.color(0x55FF55)));

            int taskId = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline() && display.isValid()) {
                    Location newLoc = player.getLocation().add(0, 2.5, 0);
                    // Test bar also respects move threshold for consistency
                    display.teleport(newLoc);
                }
            }, 2L, 2L).getTaskId();
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
            entry.display.remove();
        }
        bars.clear();
    }

    private Location barLocation(LivingEntity mob) {
        return mob.getLocation().add(0, mob.getHeight() + plugin.offsetExtra(), 0);
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
