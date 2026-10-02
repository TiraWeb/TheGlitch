package com.theglitch.glitchtutorial;

import com.theglitch.common.ChatConfirm;
import com.theglitch.common.ConfigDefaults;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GlitchTutorial — the new-player tutorial ("First Raid"): a guided run through a slice of a
 * red map in the private "tutorial" world (pick a class, loot, fight, beat a training rogue,
 * extract), a hub tour, then a first boss dungeon; lent gear is tagged and taken back, the
 * first finish pays Shards. Auto-starts for brand-new players; /tutorial skip to opt out.
 */
public final class GlitchTutorial extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private TutorialStore store;
    private TutorialManager manager;
    private EchoNpc echo;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigDefaults.merge(this);
        store = new TutorialStore(this);
        manager = new TutorialManager(this, store, new Hooks(this));
        Bukkit.getPluginManager().registerEvents(new TutorialListener(this, manager), this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TutorialExpansion(this, manager).register();
        }
        hookDungeonEvents();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            manager.placeCrates();
            if (Bukkit.getPluginManager().getPlugin("Citizens") != null) {
                try {
                    echo = new EchoNpc(this, manager);
                    echo.spawn();
                } catch (Throwable t) {
                    getLogger().warning("Echo NPC unavailable: " + t);
                }
            }
        }, 60L);
        getLogger().info("Tutorial ready (world " + getConfig().getString("world") + ", auto-start " + getConfig().getBoolean("auto-start") + ")");
    }

    @Override
    public void onDisable() {
        if (echo != null) echo.remove();
        if (store != null) store.saveNow();
    }

    /** MythicDungeons isn't on the compile classpath — listen to its events by class name. */
    @SuppressWarnings("unchecked")
    private void hookDungeonEvents() {
        if (Bukkit.getPluginManager().getPlugin("MythicDungeons") == null) return;
        ClassLoader cl = Bukkit.getPluginManager().getPlugin("MythicDungeons").getClass().getClassLoader();
        Listener holder = new Listener() { };
        String base = "net.playavalon.mythicdungeons.api.events.dungeon.";
        for (String[] e : new String[][]{{"PlayerFinishDungeonEvent", "finish"}, {"PlayerLeaveDungeonEvent", "leave"}}) {
            try {
                Class<? extends Event> cls = (Class<? extends Event>) Class.forName(base + e[0], true, cl);
                java.lang.reflect.Method getPlayer = cls.getMethod("getPlayer");
                String kind = e[1];
                Bukkit.getPluginManager().registerEvent(cls, holder, EventPriority.MONITOR, (l, ev) -> {
                    if (!cls.isInstance(ev)) return;
                    try {
                        Player p = (Player) getPlayer.invoke(ev);
                        if (p == null) return;
                        if (kind.equals("finish")) manager.onDungeonFinish(p);
                        else manager.onDungeonLeave(p);
                    } catch (ReflectiveOperationException ignored) {
                    }
                }, this, true);
            } catch (ReflectiveOperationException | LinkageError ex) {
                getLogger().warning("MythicDungeons event " + e[0] + " unavailable: " + ex);
            }
        }
    }

    // ---- commands ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(sender, args);
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only (or /tutorial admin ...).");
            return true;
        }
        switch (sub) {
            case "skip" -> {
                if (!manager.isActive(p)) {
                    p.sendMessage(MM.deserialize("<gray>You're not in the tutorial.</gray>"));
                    return true;
                }
                ChatConfirm.ask(p, MM.deserialize("<yellow>Skip the tutorial?</yellow> <gray>The lent gear goes back and there's no reward. You can replay it later with /tutorial.</gray>"),
                        () -> manager.skip(p));
            }
            case "next" -> manager.next(p);
            default -> {
                if (manager.isActive(p)) {
                    manager.resume(p);
                } else {
                    ChatConfirm.ask(p, MM.deserialize("<aqua>Start the tutorial?</aqua> <gray>Your inventory is kept safe and given back at the end."
                            + (store.get(p.getUniqueId()) != null && store.get(p.getUniqueId()).rewarded ? " (No reward on a replay.)" : "") + "</gray>"),
                            () -> manager.start(p));
                }
            }
        }
        return true;
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("glitchtutorial.admin")) {
            sender.sendMessage(MM.deserialize("<red>No permission.</red>"));
            return true;
        }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "help";
        switch (sub) {
            case "status" -> {
                if (args.length > 2) {
                    Player t = Bukkit.getPlayerExact(args[2]);
                    TutorialStore.Record r = t == null ? null : store.get(t.getUniqueId());
                    sender.sendMessage(r == null ? "No record." : t.getName() + ": " + r.status + " step " + r.step + " progress " + r.progress + " rewarded " + r.rewarded);
                } else {
                    sender.sendMessage("Tutorial world: " + (manager.world() == null ? "MISSING" : manager.world().getName()));
                    for (String k : new String[]{"spawn", "echo", "crate1", "crate2", "crate3", "mobs", "rogue", "extract"}) {
                        Location l = manager.point(k);
                        sender.sendMessage(" " + k + ": " + (l == null ? "unset" : l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                                + " on " + l.clone().add(0, -1, 0).getBlock().getType()));
                    }
                }
            }
            case "reset" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) {
                    sender.sendMessage("Usage: /tutorial admin reset <online player>");
                    return true;
                }
                if (manager.isActive(t)) manager.skip(t);
                TutorialStore.Record r = store.get(t.getUniqueId());
                if (r != null) {
                    r.status = TutorialStore.Status.SKIPPED;
                    r.rewarded = false;
                    store.saveNow();
                }
                sender.sendMessage("Reset " + t.getName() + " (reward available again). /tutorial starts it.");
            }
            case "start" -> {
                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (t == null) {
                    sender.sendMessage("Usage: /tutorial admin start <online player>");
                    return true;
                }
                manager.start(t);
            }
            case "setpoint" -> {
                if (!(sender instanceof Player p) || args.length < 3) {
                    sender.sendMessage("Stand on the spot: /tutorial admin setpoint <spawn|echo|crate1|crate2|crate3|mobs|rogue|extract>");
                    return true;
                }
                Location l = p.getLocation();
                setPoint(args[2].toLowerCase(Locale.ROOT), l.getBlockX() + 0.5, l.getBlockY(), l.getBlockZ() + 0.5, l.getYaw());
                saveConfig();
                manager.placeCrates();
                if (echo != null) echo.spawn();
                sender.sendMessage("Point " + args[2] + " set.");
            }
            case "autolayout" -> autolayout(sender);
            case "reload" -> {
                reloadConfig();
                manager.placeCrates();
                if (echo != null) echo.spawn();
                sender.sendMessage("GlitchTutorial reloaded.");
            }
            default -> sender.sendMessage("/tutorial admin <status [player]|reset <player>|start <player>|setpoint <name>|autolayout|reload>");
        }
        return true;
    }

    private void setPoint(String name, double x, double y, double z, float yaw) {
        // Plain keys (a Map value isn't readable as a ConfigurationSection until the next reload)
        String base = "points." + name;
        getConfig().set(base, null);
        getConfig().set(base + ".x", x);
        getConfig().set(base + ".y", y);
        getConfig().set(base + ".z", z);
        getConfig().set(base + ".yaw", (double) Math.round(yaw));
    }

    /**
     * Lays the step points out along a straight path from the slice centre (spawn → crates →
     * mobs → rogue → extraction), trying four directions and keeping the one where every point
     * lands on walkable ground with the least height change. Fine-tune with setpoint.
     */
    private void autolayout(CommandSender sender) {
        World w = manager.world();
        if (w == null) {
            sender.sendMessage("Tutorial world missing — run scripts/setup-tutorial.sh");
            return;
        }
        Location c = w.getWorldBorder().getCenter();
        int cx = c.getBlockX(), cz = c.getBlockZ();
        // distance along the path, sideways offset
        Object[][] plan = {{"spawn", 0, 0}, {"echo", 3, 3}, {"crate1", 18, 0}, {"crate2", 28, 6}, {"crate3", 38, -6},
                {"mobs", 54, 0}, {"rogue", 72, 0}, {"extract", 92, 0}};
        int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        List<double[]> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] d : dirs) {
            List<double[]> pts = new ArrayList<>();
            double prevY = Double.NaN, score = 0;
            boolean ok = true;
            for (Object[] step : plan) {
                int along = (Integer) step[1], side = (Integer) step[2];
                int x = cx + d[0] * along + d[1] * side;
                int z = cz + d[1] * along - d[0] * side;
                double[] spot = ground(w, x, z);
                if (spot == null) {
                    ok = false;
                    break;
                }
                if (!Double.isNaN(prevY)) score += Math.abs(spot[1] - prevY);
                prevY = spot[1];
                pts.add(spot);
            }
            if (ok && score < bestScore) {
                bestScore = score;
                best = pts;
            }
        }
        if (best == null) {
            sender.sendMessage("No clean straight path found from the centre — place points with /tutorial admin setpoint.");
            return;
        }
        for (int i = 0; i < plan.length; i++) {
            double[] s = best.get(i);
            float yaw = 0;
            if (i == 0 && best.size() > 2) {
                double dx = best.get(2)[0] - s[0], dz = best.get(2)[2] - s[2];
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // spawn faces the path
            }
            setPoint((String) plan[i][0], s[0], s[1], s[2], yaw);
        }
        saveConfig();
        manager.placeCrates();
        if (echo != null) echo.spawn();
        sender.sendMessage("Layout set (height change along the path: " + Math.round(bestScore) + " blocks). /tutorial admin status to review.");
    }

    /** Walkable ground near x/z (spiral up to 6 blocks): solid, not leaves/water, 2 air above. */
    private static double[] ground(World w, int x0, int z0) {
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = x0 + dx, z = z0 + dz;
                    w.getChunkAt(x >> 4, z >> 4);
                    int y = w.getHighestBlockYAt(x, z);
                    Block b = w.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (!m.isSolid() || m == Material.BARRIER || b.isLiquid() || m.name().endsWith("_LEAVES")) continue;
                    if (!b.getRelative(0, 1, 0).isPassable() || !b.getRelative(0, 2, 0).isPassable()) continue;
                    if (b.getRelative(0, 1, 0).isLiquid()) continue;
                    if (!w.getWorldBorder().isInside(b.getLocation())) continue;
                    return new double[]{x + 0.5, y + 1, z + 0.5};
                }
            }
        }
        return null;
    }
}
