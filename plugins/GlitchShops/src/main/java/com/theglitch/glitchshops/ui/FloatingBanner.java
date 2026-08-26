package com.theglitch.glitchshops.ui;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class FloatingBanner {

    private static final boolean FOLIA_RUNTIME = detectFolia();

    private static final Map<UUID, List<TextDisplay>> ACTIVE_DISPLAYS = new HashMap<>();
    private static final Map<UUID, Object> CANCEL_TOKENS = new HashMap<>();

    private FloatingBanner() {
    }

    public static void show(JavaPlugin plugin, Player player, String miniText, long ticks) {
        try {
            clear(player);
        } catch (Throwable ignored) {
        }
        try {
            player.sendActionBar(UiKit.deserialized(miniText));
        } catch (Throwable ignored) {
        }
        try {
            TextDisplay display = spawnDisplay(plugin, player, miniText);
            ACTIVE_DISPLAYS.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>()).add(display);
            scheduleRemoval(plugin, player, display, Math.max(1L, ticks));
        } catch (Throwable t) {
            plugin.getLogger().fine("FloatingBanner skipped: " + t.getClass().getSimpleName());
        }
    }

    public static void clear(Player player) {
        UUID id = player.getUniqueId();
        cancelToken(CANCEL_TOKENS.remove(id));
        List<TextDisplay> displays = ACTIVE_DISPLAYS.remove(id);
        if (displays == null) return;
        for (TextDisplay display : displays) {
            try {
                display.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static TextDisplay spawnDisplay(JavaPlugin plugin, Player player, String miniText) {
        Location origin = player.getLocation().add(0.0, 2.6, 0.0);
        return player.getWorld().spawn(origin, TextDisplay.class,
                display -> style(plugin, display, miniText));
    }

    private static void style(JavaPlugin plugin, TextDisplay display, String miniText) {
        try {
            display.text(UiKit.deserialized(miniText + "\n" + UiKit.DIVIDER_MM));
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(true);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(0x90000000));
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setTransformation(new Transformation(
                    new Vector3f(0.0f, 0.0f, 0.0f),
                    new Quaternionf(),
                    new Vector3f(1.35f, 1.35f, 1.35f),
                    new Quaternionf()));
        } catch (Throwable t) {
            plugin.getLogger().fine("FloatingBanner styling incomplete: " + t.getClass().getSimpleName());
        }
    }

    private static void scheduleRemoval(JavaPlugin plugin, Player player, TextDisplay display, long ticks) {
        Runnable removal = () -> {
            try {
                display.remove();
            } catch (Throwable ignored) {
            }
            List<TextDisplay> tracked = ACTIVE_DISPLAYS.get(player.getUniqueId());
            if (tracked != null) {
                tracked.remove(display);
            }
        };
        Object token = scheduleDelayed(plugin, player, removal, ticks);
        if (token != null) {
            CANCEL_TOKENS.put(player.getUniqueId(), token);
        } else {
            removal.run();
        }
    }

    private static Object scheduleDelayed(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        if (FOLIA_RUNTIME) {
            try {
                return runDelayedOnEntity(player, plugin, task, delayTicks);
            } catch (Throwable ignored) {
            }
        }
        try {
            return plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
        } catch (Throwable t) {
            plugin.getLogger().fine("FloatingBanner scheduling unavailable: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private static Object runDelayedOnEntity(Player player, Plugin plugin, Runnable task, long delayTicks)
            throws ReflectiveOperationException {
        Method schedulerGetter = null;
        for (Method candidate : player.getClass().getMethods()) {
            if (candidate.getName().equals("getScheduler") && candidate.getParameterCount() == 0) {
                schedulerGetter = candidate;
                break;
            }
        }
        if (schedulerGetter == null) {
            throw new ReflectiveOperationException("getScheduler not found");
        }
        Object scheduler = schedulerGetter.invoke(player);
        if (scheduler == null) {
            throw new ReflectiveOperationException("entity scheduler unavailable");
        }
        Consumer<Object> wrapper = unused -> task.run();
        for (Method candidate : scheduler.getClass().getMethods()) {
            Class<?>[] params = candidate.getParameterTypes();
            if (!candidate.getName().equals("runDelayed")
                    || params.length != 4
                    || !Plugin.class.isAssignableFrom(params[0])
                    || !Consumer.class.isAssignableFrom(params[1])
                    || !Runnable.class.equals(params[2])
                    || params[3] != long.class) {
                continue;
            }
            return candidate.invoke(scheduler, plugin, wrapper, null, delayTicks);
        }
        throw new ReflectiveOperationException("runDelayed signature not found");
    }

    private static void cancelToken(Object token) {
        if (token == null) return;
        try {
            if (token instanceof BukkitTask bukkitTask) {
                bukkitTask.cancel();
                return;
            }
            token.getClass().getMethod("cancel").invoke(token);
        } catch (Throwable ignored) {
        }
    }
}
