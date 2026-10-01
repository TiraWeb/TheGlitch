package com.theglitch.glitchbots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Reflection bridge into the other Glitch plugins (they're built as separate jars with
 * no shared compile classpath — same approach GlitchItems uses to reach GlitchRaid).
 * Every call degrades to a safe default when a plugin or method is missing.
 */
final class GlitchHooks {

    /** A crate a rogue could walk to. */
    record Crate(Location location, boolean ready) {}

    /** An open extraction point. */
    record Exit(Location center, int radius) {}

    private final GlitchBots plugin;
    private final Map<String, Method> cache = new ConcurrentHashMap<>();
    private final java.util.Set<String> warned = ConcurrentHashMap.newKeySet();

    GlitchHooks(GlitchBots plugin) {
        this.plugin = plugin;
    }

    // ---- GlitchItems ----

    /** A GlitchItems gear piece (e.g. BLADE/COMMON), or null. */
    ItemStack gear(String type, String rarity) {
        Plugin items = enabled("GlitchItems");
        if (items == null) return null;
        try {
            Object gm = call(items, "getGearManager");
            ClassLoader cl = items.getClass().getClassLoader();
            Object gearType = enumValue(cl, "com.theglitch.glitchitems.GearType", type);
            Object rar = enumValue(cl, "com.theglitch.glitchitems.Rarity", rarity);
            Method m = method(gm.getClass(), "generateGear", gearType.getClass(), rar.getClass());
            return (ItemStack) m.invoke(gm, gearType, rar);
        } catch (Throwable t) {
            warnOnce("gear", t);
            return null;
        }
    }

    List<Crate> cratesNear(Location center, double radius) {
        List<Crate> out = new ArrayList<>();
        Plugin items = enabled("GlitchItems");
        if (items == null) return out;
        try {
            Object cm = call(items, "getContainerManager");
            Method m = method(cm.getClass(), "nearby", Location.class, double.class);
            for (Object view : (List<?>) m.invoke(cm, center, radius)) {
                Location loc = (Location) call(view, "location");
                boolean ready = (Boolean) call(view, "ready");
                out.add(new Crate(loc, ready));
            }
        } catch (Throwable t) {
            warnOnce("crates", t);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    List<ItemStack> lootCrate(Location crate) {
        Plugin items = enabled("GlitchItems");
        if (items == null) return List.of();
        try {
            Object cm = call(items, "getContainerManager");
            Method m = method(cm.getClass(), "botLoot", Location.class);
            return (List<ItemStack>) m.invoke(cm, crate);
        } catch (Throwable t) {
            warnOnce("botLoot", t);
            return List.of();
        }
    }

    /** WorldGuard-protected spot (safe zones, extraction pads)? */
    boolean isProtected(Location loc) {
        Plugin items = enabled("GlitchItems");
        if (items == null) return false;
        try {
            Object sm = call(items, "getScatterManager");
            Method m = method(sm.getClass(), "isProtectedRegion", Location.class);
            return (Boolean) m.invoke(sm, loc);
        } catch (Throwable t) {
            warnOnce("protected", t);
            return false;
        }
    }

    // ---- GlitchRaid ----

    boolean inBuffer(String world) {
        Object rm = raidManager();
        if (rm == null) return false;
        try {
            return (Boolean) method(rm.getClass(), "isInBufferPeriod", String.class).invoke(rm, world);
        } catch (Throwable t) {
            warnOnce("buffer", t);
            return false;
        }
    }

    /** Seconds left on the world's shared raid timer, or -1 when none is running. */
    int raidSecondsLeft(String world) {
        Object rm = raidManager();
        if (rm == null) return -1;
        try {
            Object session = method(rm.getClass(), "findActiveGlobalSession", String.class).invoke(rm, world);
            if (session == null) return -1;
            return (Integer) call(session, "getRemainingSeconds");
        } catch (Throwable t) {
            warnOnce("raidTime", t);
            return -1;
        }
    }

    private Object raidManager() {
        Plugin raid = enabled("GlitchRaid");
        if (raid == null) return null;
        try {
            return call(raid, "getRaidManager");
        } catch (Throwable t) {
            warnOnce("raidManager", t);
            return null;
        }
    }

    // ---- GlitchStash ----

    List<Exit> openExits(World world) {
        List<Exit> out = new ArrayList<>();
        Plugin stash = enabled("GlitchStash");
        if (stash == null || world == null) return out;
        try {
            Object dem = method(stash.getClass(), "getDynamicExtractionManager", String.class).invoke(stash, world.getName());
            if (dem == null) return out;
            long now = System.currentTimeMillis();
            for (Object p : (List<?>) call(dem, "getCurrentPoints")) {
                if (!world.getName().equals(call(p, "world"))) continue;
                long until = (Long) call(p, "openUntilEpochMs");
                if (until > 0 && until < now) continue;
                int x = (Integer) call(p, "x");
                int y = (Integer) call(p, "y");
                int z = (Integer) call(p, "z");
                int r = (Integer) call(p, "radiusBlocks");
                out.add(new Exit(new Location(world, x + 0.5, y, z + 0.5), Math.max(2, r)));
            }
        } catch (Throwable t) {
            warnOnce("exits", t);
        }
        return out;
    }

    // ---- reflection helpers ----

    private Plugin enabled(String name) {
        Plugin p = Bukkit.getPluginManager().getPlugin(name);
        return p != null && p.isEnabled() ? p : null;
    }

    private Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    private Method method(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        String key = owner.getName() + "#" + name + "/" + params.length;
        Method m = cache.get(key);
        if (m == null) {
            m = owner.getMethod(name, params);
            m.setAccessible(true);
            cache.put(key, m);
        }
        return m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(ClassLoader cl, String cls, String name) throws ClassNotFoundException {
        Class<? extends Enum> e = (Class<? extends Enum>) Class.forName(cls, true, cl);
        return Enum.valueOf(e, name);
    }

    private void warnOnce(String what, Throwable t) {
        if (warned.add(what)) {
            plugin.getLogger().log(Level.WARNING, "Glitch hook '" + what + "' unavailable: " + t);
        }
    }
}
