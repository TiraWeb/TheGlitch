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
 * there: spawns out of sight near real players (entities only tick within the
 * simulation distance of a player), sends surplus rogues to extract, and clears a
 * world once it has been empty for the grace period or enters the scatter buffer.
 */
final class BotDirector {

    private final GlitchBots plugin;
    private final NPCRegistry registry;
    private final Map<UUID, RogueBot> bots = new ConcurrentHashMap<>(); // entity/NPC uuid -> bot
    private final Map<String, Long> emptySince = new HashMap<>();
    /** Admin test spawns (/bots spawn ... x z) keep a world's rogues even with nobody there, until this time. */
    private final Map<String, Long> testHoldUntil = new HashMap<>();
    private final Set<String> namesInUse = new HashSet<>();
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
            int target = Math.max(0, cfg.targetPerWorld - real.size());
            List<RogueBot> here = inWorld(name);
            List<RogueBot> active = new ArrayList<>();
            for (RogueBot b : here) if (!b.leaving) active.add(b);

            if (active.size() < target) {
                int toSpawn = Math.min(cfg.maxSpawnPerTick, target - active.size());
                for (int i = 0; i < toSpawn; i++) spawnNear(w, real);
            } else if (active.size() > target) {
                // Surplus: the rogue farthest from everyone leaves (quietly when nobody can see it)
                RogueBot far = null;
                double farD = -1;
                for (RogueBot b : active) {
                    double d = nearestRealDistance(b.location(), real);
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
        }
    }

    private void think() {
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
            best = Math.min(best, p.getLocation().distance(at));
        }
        return best;
    }

    private void pruneDead(String world) {
        for (RogueBot b : inWorld(world)) {
            // Chunk unloaded (no player near) or killed — drop it; the director refills near players.
            // Fresh spawns get a few seconds: Citizens can finish spawning a tick later.
            if (!b.npc.isSpawned() && System.currentTimeMillis() - b.spawnedAt > 10_000L) remove(b);
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
        BotConfig cfg = plugin.cfg();
        String handle = pickName();
        NPC npc = registry.createNPC(EntityType.PLAYER, cfg.nameFormat.replace("<name>", handle));
        RogueBot bot = new RogueBot(plugin, npc, handle, spot.getWorld().getName(), cfg.rollRarity());
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
        UUID entityId = b.entityId;
        try {
            b.npc.destroy();
        } catch (Exception ignored) {
        }
        forgetEssentialsUser(entityId);
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
