package com.theglitch.glitchbots;

import com.theglitch.common.Bots;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps each red world at {@code target − real players} rogues while anyone real is
 * there. Rogues are spread over the whole imported map: new ones start <em>parked</em>
 * (no body) at random spots on the map footprint (GlitchWorldGen {@code <world>.keep}),
 * or some in a wide ring around raiders; a parked rogue gets its body when a raider comes
 * within {@code spread.materialize-radius} and parks again once nobody is within
 * {@code spread.park-radius}. At most {@code spread.max-near-player} rogues are live
 * around one raider and at most {@code spread.max-chasers-per-player} fight the same
 * raider (extras break off), so meeting one rogue doesn't pull in the whole lobby.
 * Surplus rogues extract; a world is cleared once it has been empty for the grace period
 * or enters the scatter buffer.
 */
final class BotDirector {

    private final GlitchBots plugin;
    private final NPCRegistry registry;
    private final Map<UUID, RogueBot> bots = new ConcurrentHashMap<>(); // entity/NPC uuid -> bot
    private final Map<String, Long> emptySince = new HashMap<>();
    /** Admin test spawns (/bots spawn ... x z) keep a world's rogues even with nobody there, until this time. */
    private final Map<String, Long> testHoldUntil = new HashMap<>();
    private final Set<String> namesInUse = new HashSet<>();
    /** Imported-map chunks per world (GlitchWorldGen footprint), for spreading rogues over the real map. */
    private final Map<String, List<long[]>> footprints = new HashMap<>();
    private BukkitTask directorTask;
    private BukkitTask brainTask;

    BotDirector(GlitchBots plugin) {
        this.plugin = plugin;
        this.registry = CitizensAPI.createInMemoryNPCRegistry("glitchbots");
    }

    void start() {
        stop();
        long period = plugin.cfg().tickSeconds * 20L;
        directorTask = Bukkit.getScheduler().runTaskTimer(plugin, this::direct, 100L, period);
        brainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::think, 40L, 20L);
    }

    void stop() {
        if (directorTask != null) directorTask.cancel();
        if (brainTask != null) brainTask.cancel();
        directorTask = null;
        brainTask = null;
    }

    void holdForTest(String world, long millis) {
        testHoldUntil.put(world, System.currentTimeMillis() + millis);
    }

    NPCRegistry registry() {
        return registry;
    }

    RogueBot bot(UUID npcUuid) {
        return bots.get(npcUuid);
    }

    Collection<RogueBot> all() {
        return bots.values();
    }

    List<RogueBot> inWorld(String world) {
        List<RogueBot> out = new ArrayList<>();
        for (RogueBot b : bots.values()) if (b.world.equals(world)) out.add(b);
        return out;
    }

    // ---- director ----

    private void direct() {
        BotConfig cfg = plugin.cfg();
        long now = System.currentTimeMillis();
        for (String name : cfg.worlds) {
            World w = Bukkit.getWorld(name);
            if (w == null) continue;
            pruneDead(name);
            if (!cfg.enabled || plugin.hooks().inBuffer(name)) {
                clearWorld(name);
                continue;
            }
            List<Player> real = raiders(w);
            Long hold = testHoldUntil.get(name);
            if (hold != null && hold > now) continue;
            testHoldUntil.remove(name);
            if (real.isEmpty()) {
                long since = emptySince.computeIfAbsent(name, k -> now);
                if (now - since >= cfg.despawnGraceSeconds * 1000L) clearWorld(name);
                continue;
            }
            emptySince.remove(name);
            // Parked rogues "extract" off-screen once the raid is nearly over
            int left = plugin.hooks().raidSecondsLeft(name);
            if (left >= 0 && left < cfg.extractWhenRemaining) {
                for (RogueBot b : inWorld(name)) if (b.parkedAt != null) remove(b);
            }
            int target = Math.max(0, cfg.targetPerWorld - real.size());
            List<RogueBot> active = new ArrayList<>();
            for (RogueBot b : inWorld(name)) if (!b.leaving) active.add(b);

            if (active.size() < target) {
                // parked spawns cost nothing (no body yet), so top up quickly
                int toSpawn = Math.min(5, target - active.size());
                for (int i = 0; i < toSpawn; i++) spawnParked(w, real);
            } else if (active.size() > target) {
                // Surplus: a parked rogue simply goes; otherwise the one farthest from everyone leaves
                RogueBot far = null;
                double farD = -1;
                for (RogueBot b : active) {
                    double d = b.parkedAt != null ? Double.MAX_VALUE : nearestRealDistance(b.location(), real);
                    if (d > farD) {
                        farD = d;
                        far = b;
                    }
                }
                if (far != null) {
                    if (farD > 64) remove(far);
                    else far.leaving = true;
                }
            }
            materialize(w, real);
            parkIdle(w, real);
        }
    }

    // ---- spread: parked rogues ----

    /** Gives parked rogues near a raider their body, up to the per-raider cap. */
    private void materialize(World w, List<Player> real) {
        BotConfig cfg = plugin.cfg();
        for (RogueBot b : inWorld(w.getName())) {
            if (b.parkedAt == null) continue;
            if (b.npc.isSpawned()) { // Citizens brought the body back on a chunk load
                b.parkedAt = null;
                continue;
            }
            Player near = nearestPlayer(b.parkedAt, real);
            if (near == null) continue;
            double d = flat(b.parkedAt, near.getLocation());
            if (d > cfg.materializeRadius || liveNear(w, near.getLocation(), cfg.materializeRadius) >= cfg.maxNearPlayer) continue;
            double x = b.parkedAt.getX(), z = b.parkedAt.getZ();
            if (d < cfg.minPlayerDistance) {
                // they walked right onto the spot: appear a little way off instead of in their face
                double dx = x - near.getLocation().getX(), dz = z - near.getLocation().getZ();
                double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
                x = near.getLocation().getX() + dx / len * (cfg.minPlayerDistance + 8);
                z = near.getLocation().getZ() + dz / len * (cfg.minPlayerDistance + 8);
            }
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) continue;
            Location spot = groundNear(w, bx, bz, 12);
            if (spot == null || plugin.hooks().isProtected(spot)) {
                remove(b); // void/water/protected after all — the director places a new one
                continue;
            }
            if (!b.spawn(spot)) remove(b);
        }
    }

    /** Live rogues with no raider within the park radius (and not fighting) drop their body. */
    private void parkIdle(World w, List<Player> real) {
        BotConfig cfg = plugin.cfg();
        for (RogueBot b : inWorld(w.getName())) {
            if (b.parkedAt != null || b.tutorialTarget != null || b.fighting()) continue;
            Location l = b.location();
            if (l == null) continue;
            if (nearestRealDistance(l, real) > cfg.parkRadius) b.park();
        }
    }

    /** Creates a parked rogue somewhere on the map (or in a wide ring around a raider). */
    boolean spawnParked(World w, List<Player> real) {
        BotConfig cfg = plugin.cfg();
        java.util.concurrent.ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<long[]> map = footprint(w.getName());
        for (int attempt = 0; attempt < 24; attempt++) {
            double x, z;
            if (!real.isEmpty() && rnd.nextDouble() < cfg.nearShare) {
                Location base = real.get(rnd.nextInt(real.size())).getLocation();
                double ang = rnd.nextDouble(Math.PI * 2), dist = rnd.nextDouble(cfg.nearMin, cfg.nearMax);
                x = base.getX() + Math.cos(ang) * dist;
                z = base.getZ() + Math.sin(ang) * dist;
            } else if (!map.isEmpty()) {
                long[] c = map.get(rnd.nextInt(map.size()));
                x = c[0] * 16 + rnd.nextDouble(16);
                z = c[1] * 16 + rnd.nextDouble(16);
            } else {
                org.bukkit.WorldBorder wb = w.getWorldBorder();
                double half = wb.getSize() / 2 - 8;
                x = wb.getCenter().getX() + rnd.nextDouble(-half, half);
                z = wb.getCenter().getZ() + rnd.nextDouble(-half, half);
            }
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            Location at = new Location(w, bx + 0.5, w.getMinHeight(), bz + 0.5);
            if (!w.getWorldBorder().isInside(at)) continue;
            // never pops into view: parked spots start outside the materialise radius
            if (nearestRealDistance(at, real) < cfg.materializeRadius + 24) continue;
            if (w.isChunkLoaded(bx >> 4, bz >> 4)) {
                Location g = groundSpot(w, bx, bz);
                if (g == null || plugin.hooks().isProtected(g)) continue;
                at = g;
            }
            String handle = pickName();
            NPC npc = registry.createNPC(EntityType.PLAYER, cfg.nameFormat.replace("<name>", handle));
            RogueBot bot = new RogueBot(plugin, npc, handle, w.getName(), cfg.rollRarity());
            bot.parkedAt = at;
            bots.put(npc.getUniqueId(), bot);
            return true;
        }
        return false;
    }

    private List<long[]> footprint(String world) {
        return footprints.computeIfAbsent(world, k -> {
            List<long[]> out = new ArrayList<>();
            org.bukkit.plugin.Plugin gen = Bukkit.getPluginManager().getPlugin("GlitchWorldGen");
            if (gen == null) return out;
            java.io.File f = new java.io.File(gen.getDataFolder(), k + ".keep");
            if (!f.isFile()) return out;
            try {
                for (String line : java.nio.file.Files.readAllLines(f.toPath())) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 2) out.add(new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])});
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Couldn't read map footprint " + f + ": " + e);
            }
            return out;
        });
    }

    /** Walkable ground at or near x/z (spiral up to {@code r} blocks), or null. */
    private static Location groundNear(World w, int x0, int z0, int r) {
        for (int ring = 0; ring <= r; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                for (int dz = -ring; dz <= ring; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    Location g = groundSpot(w, x0 + dx, z0 + dz);
                    if (g != null && w.getWorldBorder().isInside(g)) return g;
                }
            }
        }
        return null;
    }

    private int liveNear(World w, Location at, double radius) {
        int n = 0;
        for (RogueBot b : inWorld(w.getName())) {
            Location l = b.location();
            if (l != null && flat(l, at) <= radius) n++;
        }
        return n;
    }

    private static Player nearestPlayer(Location at, List<Player> real) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : real) {
            if (p.getWorld() != at.getWorld()) continue;
            double d = flat(at, p.getLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void think() {
        // No pile-ons: only the closest few rogues may fight the same raider, the rest back off
        Map<UUID, List<RogueBot>> chasers = new HashMap<>();
        for (RogueBot b : bots.values()) {
            if (b.tutorialTarget != null) continue;
            Player t = b.chasingPlayer();
            if (t != null) chasers.computeIfAbsent(t.getUniqueId(), k -> new ArrayList<>()).add(b);
        }
        int max = plugin.cfg().maxChasers;
        for (Map.Entry<UUID, List<RogueBot>> e : chasers.entrySet()) {
            if (e.getValue().size() <= max) continue;
            Player t = Bukkit.getPlayer(e.getKey());
            if (t == null) continue;
            Location tl = t.getLocation();
            List<RogueBot> list = e.getValue();
            list.sort(java.util.Comparator.comparingDouble(b -> {
                Location l = b.location();
                return l == null || l.getWorld() != tl.getWorld() ? Double.MAX_VALUE : l.distanceSquared(tl);
            }));
            for (int i = max; i < list.size(); i++) list.get(i).breakOff(tl);
        }
        for (RogueBot b : new ArrayList<>(bots.values())) {
            try {
                if (!b.tick()) remove(b);
            } catch (Exception e) {
                plugin.getLogger().warning("Rogue " + b.handle + " brain error: " + e);
                remove(b);
            }
        }
    }

    /** Real survival/adventure players in a world (staff in creative/spectator don't count). */
    static List<Player> raiders(World w) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bots.realPlayers(w)) {
            if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) out.add(p);
        }
        return out;
    }

    private static double nearestRealDistance(Location at, List<Player> real) {
        if (at == null) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (Player p : real) {
            if (p.getWorld() != at.getWorld()) continue;
            best = Math.min(best, flat(p.getLocation(), at));
        }
        return best;
    }

    private void pruneDead(String world) {
        for (RogueBot b : inWorld(world)) {
            // A body that vanished on its own (chunk unload) just becomes a parked rogue at its last
            // spot; killed rogues are removed by BotListener. Fresh spawns get a few seconds first.
            if (b.parkedAt != null || b.tutorialTarget != null || b.npc.isSpawned()) continue;
            if (System.currentTimeMillis() - b.lastSpawnAt < 10_000L) continue;
            Location stored = b.npc.getStoredLocation();
            if (stored != null && stored.getWorld() != null) b.parkedAt = stored;
            else remove(b);
        }
    }

    // ---- spawning ----

    /** Spawns one rogue out of sight near a random real player. */
    boolean spawnNear(World w, List<Player> real) {
        BotConfig cfg = plugin.cfg();
        if (real.isEmpty()) return false;
        for (int attempt = 0; attempt < 12; attempt++) {
            Player anchor = real.get(ThreadLocalRandom.current().nextInt(real.size()));
            Location base = anchor.getLocation();
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(cfg.ringMin, cfg.ringMax);
            int x = (int) Math.floor(base.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(base.getZ() + Math.sin(ang) * dist);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Location spot = groundSpot(w, x, z);
            if (spot == null || !w.getWorldBorder().isInside(spot)) continue;
            if (nearestRealDistance(spot, real) < cfg.minPlayerDistance) continue;
            if (plugin.hooks().isProtected(spot)) continue;
            return spawnAt(spot) != null;
        }
        return false;
    }

    /** Creates + spawns a rogue at an exact spot (used by /bots spawn too). */
    RogueBot spawnAt(Location spot) {
        return spawnAt(spot, null);
    }

    /** {@code trainee} non-null = GlitchTutorial training rogue for that player. */
    RogueBot spawnAt(Location spot, UUID trainee) {
        BotConfig cfg = plugin.cfg();
        String handle = pickName();
        NPC npc = registry.createNPC(EntityType.PLAYER, cfg.nameFormat.replace("<name>", handle));
        RogueBot bot = new RogueBot(plugin, npc, handle, spot.getWorld().getName(), trainee != null ? "COMMON" : cfg.rollRarity());
        bot.tutorialTarget = trainee;
        if (!bot.spawn(spot)) {
            npc.destroy();
            namesInUse.remove(handle);
            return null;
        }
        bots.put(npc.getUniqueId(), bot);
        return bot;
    }

    private String pickName() {
        List<String> pool = plugin.cfg().names;
        for (int i = 0; i < 20; i++) {
            String n = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            if (namesInUse.add(n)) return n;
        }
        String n = pool.get(ThreadLocalRandom.current().nextInt(pool.size())) + ThreadLocalRandom.current().nextInt(10, 99);
        namesInUse.add(n);
        return n;
    }

    /**
     * Highest walkable block at x/z: solid, not liquid/leaves/barrier, with two free
     * blocks above. Null over void (outside the map) or water.
     */
    static Location groundSpot(World w, int x, int z) {
        int y = w.getHighestBlockYAt(x, z);
        if (y <= w.getMinHeight() + 1) return null;
        Block ground = w.getBlockAt(x, y, z);
        Material m = ground.getType();
        if (!m.isSolid() || m == Material.BARRIER || ground.isLiquid() || m.name().endsWith("_LEAVES")) return null;
        if (!ground.getRelative(0, 1, 0).isPassable() || !ground.getRelative(0, 2, 0).isPassable()) return null;
        if (ground.getRelative(0, 1, 0).isLiquid()) return null;
        return new Location(w, x + 0.5, y + 1, z + 0.5);
    }

    // ---- removal ----

    void remove(RogueBot b) {
        bots.remove(b.npc.getUniqueId());
        namesInUse.remove(b.handle);
        plugin.chat().forget(b.npc.getUniqueId());
        try {
            b.npc.destroy();
        } catch (Exception ignored) {
        }
        for (UUID body : b.bodyIds) forgetEssentialsUser(body);
    }

    /**
     * Essentials creates a userdata file for every player entity it sees, NPCs included
     * ("Created a User for Rogue X"). Rogues are throwaway — delete the file once gone.
     */
    private void forgetEssentialsUser(UUID id) {
        if (id == null) return;
        java.io.File f = new java.io.File(plugin.getDataFolder().getParentFile(), "Essentials/userdata/" + id + ".yml");
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                java.nio.file.Files.deleteIfExists(f.toPath());
            } catch (Exception ignored) {
            }
        }, 100L);
    }

    int clearWorld(String world) {
        int n = 0;
        for (RogueBot b : inWorld(world)) {
            remove(b);
            n++;
        }
        return n;
    }

    /** Removes the training rogue(s) of one tutorial player. */
    int clearTutorial(UUID trainee) {
        int n = 0;
        for (RogueBot b : new ArrayList<>(bots.values())) {
            if (trainee.equals(b.tutorialTarget)) {
                remove(b);
                n++;
            }
        }
        return n;
    }

    int clearAll() {
        int n = bots.size();
        for (RogueBot b : new ArrayList<>(bots.values())) remove(b);
        try {
            registry.deregisterAll();
        } catch (Exception ignored) {
        }
        return n;
    }
}
