package com.theglitch.glitchclasses.ui;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * World-space holographic banner spawned above the player while a class menu
 * is open. Folia-safe: probes the regionized runtime and prefers reflective
 * entity scheduling, falling back to the Bukkit scheduler, then to
 * action-bar-only. Never throws out of show().
 */
public final class FloatingBanner {

    private static final boolean REGIONIZED_RUNTIME = probeRegionized();

    private static final Map<UUID, TextDisplay> ACTIVE = new ConcurrentHashMap<>();

    private FloatingBanner() {
    }

    private static boolean probeRegionized() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void show(JavaPlugin plugin, Player player, String miniText, long ticks) {
        try {
            player.sendActionBar(UiKit.deserialized(miniText));
        } catch (Throwable ignored) {
        }
        try {
            clear(player);
            if (!player.isOnline()) return;
            Location loc = player.getLocation().add(0, 2.6, 0);
            TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, td -> {
                td.text(UiKit.deserialized(miniText + "\n" + UiKit.DIVIDER_MM));
                td.setBillboard(Display.Billboard.CENTER);
                td.setShadowed(true);
                td.setSeeThrough(false);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(0x90000000));
                td.setPersistent(false);
                td.setTeleportDuration(1);
                td.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new Quaternionf(),
                        new Vector3f(1.35f, 1.35f, 1.35f),
                        new Quaternionf()));
            });
            ACTIVE.put(player.getUniqueId(), display);
            scheduleRemoval(plugin, player, display, Math.max(1L, ticks));
        } catch (Throwable t) {
            plugin.getLogger().fine("FloatingBanner hologram skipped: " + t.getMessage());
        }
    }

    public static void clear(Player player) {
        if (player == null) return;
        TextDisplay display = ACTIVE.remove(player.getUniqueId());
        if (display != null) {
            try {
                display.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void scheduleRemoval(JavaPlugin plugin, Player player, TextDisplay display, long ticks) {
        if (REGIONIZED_RUNTIME && runOnEntityScheduler(plugin, player,
                () -> retire(display, player.getUniqueId()), ticks)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> retire(display, player.getUniqueId()), ticks);
        } catch (Throwable ignored) {
        }
    }

    /** Reflective EntityScheduler#runDelayed(Plugin, Consumer, Runnable, long) — no Folia-only imports. */
    private static boolean runOnEntityScheduler(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            Method run = null;
            for (Method m : scheduler.getClass().getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (m.getName().equals("runDelayed") && params.length == 4
                        && JavaPlugin.class.isAssignableFrom(params[0])
                        && Consumer.class.isAssignableFrom(params[1])
                        && Runnable.class.equals(params[2])
                        && params[3] == long.class) {
                    run = m;
                    break;
                }
            }
            if (run == null) return false;
            Consumer<Object> unused = t -> {
            };
            // runDelayed(Plugin, Consumer retired, Runnable task, long delay)
            run.invoke(scheduler, plugin, unused, task, delayTicks);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void retire(TextDisplay display, UUID owner) {
        ACTIVE.remove(owner, display);
        try {
            display.remove();
        } catch (Throwable ignored) {
        }
    }
}
