package com.theglitch.glitchbots;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.mcmonkey.sentinel.SentinelTrait;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One Rogue Raider: a Citizens player NPC with Sentinel doing the fighting, and a
 * small state machine (this class) deciding where to go when it isn't fighting:
 * roam → loot crates → extract. Bots live in an in-memory registry and are never saved.
 */
final class RogueBot {

    enum State { ROAM, LOOT, FLEE, EXTRACT }

    /** Scoreboard tag prefix on a training rogue's body: {@code glitch_trainee_<player uuid>}. */
    static final String TRAINEE_TAG = "glitch_trainee_";

    private static final String[] MELEE = {"BLADE", "GREATBLADE"};
    private static final String[] ARMOR = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};

    private final GlitchBots plugin;
    final NPC npc;
    final String handle;
    final String world;
    final String rarity;
    /** Personality used for this rogue's chat lines. */
    final String quirk;
    /** "british" or "american" — picks the chat style. */
    final String dialect;
    final List<ItemStack> bag = new ArrayList<>();
    private final Set<String> skippedCrates = new HashSet<>();

    State state = State.ROAM;
    /** Set for the GlitchTutorial training rogue: fights only this player, weak, drops nothing real. */
    java.util.UUID tutorialTarget;
    final long spawnedAt = System.currentTimeMillis();
    /** Last time the body was (re)spawned — Citizens may finish a spawn a tick later. */
    long lastSpawnAt = System.currentTimeMillis();
    /** Where a parked rogue (no body) waits for a raider to come near; null while it's live. */
    Location parkedAt;
    private boolean prepared;
    /** The spawned body's UUID (for Essentials userdata cleanup). */
    java.util.UUID entityId;
    /** Leaving because the world has more rogues than it needs (not because it's done looting). */
    boolean leaving;
    private int cratesLooted;
    private Location crateTarget;
    private Location exitTarget;
    private int exitRadius;
    private long fleeUntil;
    private double normalRange;
    private Location lastPos;
    private long lastMovedAt = System.currentTimeMillis();

    RogueBot(GlitchBots plugin, NPC npc, String handle, String world, String rarity) {
        this.plugin = plugin;
        this.npc = npc;
        this.handle = handle;
        this.world = world;
        this.rarity = rarity;
        this.quirk = plugin.chat().randomQuirk();
        this.dialect = plugin.chat().randomDialect();
    }

    int cratesLooted() {
        return cratesLooted;
    }

    // ---- spawn / gear ----

    /** Traits + gear, once per rogue (a parked rogue is prepared on its first spawn). */
    private void prepare() {
        BotConfig cfg = plugin.cfg();
        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false);
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
        npc.data().setPersistent(NPC.Metadata.DROPS_ITEMS, false);
        npc.data().setPersistent(NPC.Metadata.PICKUP_ITEMS, false);
        npc.setProtected(false);
        // Never fetch a skin by name: the name isn't an account, and we must not wear a
        // real player's skin. Citizens then shows one of Minecraft's default skins.
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.setFetchDefaultSkin(false);
        npc.getOrAddTrait(net.citizensnpcs.trait.ScoreboardTrait.class).setColor(cfg.nameColor);

        boolean ranged = tutorialTarget == null && ThreadLocalRandom.current().nextDouble() < cfg.rangedChance;
        Equipment eq = npc.getOrAddTrait(Equipment.class);
        ItemStack weapon;
        if (tutorialTarget != null) {
            weapon = new ItemStack(Material.STONE_SWORD); // plain, worthless — the tutorial hands out the loot
        } else if (ranged) {
            weapon = new ItemStack(ThreadLocalRandom.current().nextBoolean() ? Material.BOW : Material.CROSSBOW);
            eq.set(Equipment.EquipmentSlot.OFF_HAND, new ItemStack(Material.ARROW, 32));
        } else {
            weapon = plugin.hooks().gear(MELEE[ThreadLocalRandom.current().nextInt(MELEE.length)], rarity);
            if (weapon == null) weapon = new ItemStack(Material.IRON_SWORD);
        }
        eq.set(Equipment.EquipmentSlot.HAND, weapon);
        Equipment.EquipmentSlot[] slots = {Equipment.EquipmentSlot.HELMET, Equipment.EquipmentSlot.CHESTPLATE,
                Equipment.EquipmentSlot.LEGGINGS, Equipment.EquipmentSlot.BOOTS};
        for (int i = 0; i < slots.length; i++) {
            if (tutorialTarget != null || ThreadLocalRandom.current().nextDouble() >= cfg.armorChance) continue;
            ItemStack piece = plugin.hooks().gear(ARMOR[i], rarity);
            if (piece != null) eq.set(slots[i], piece);
        }

        SentinelTrait s = npc.getOrAddTrait(SentinelTrait.class);
        org.bukkit.entity.Player trainee = tutorialTarget == null ? null : org.bukkit.Bukkit.getPlayer(tutorialTarget);
        if (trainee != null) {
            s.addTarget("player:" + trainee.getName());
        } else {
            s.addTarget("players");
            s.addTarget("monsters");
        }
        s.addIgnore("npcs");
        s.squad = "glitch_rogues";
        s.respawnTime = -1;
        s.fightback = true;
        s.realistic = true;
        s.ignoreLOS = false;
        s.invincible = false;
        s.enemyDrops = false;
        s.closeChase = true;
        s.rangedChase = ranged;
        s.health = tutorialTarget != null ? 14.0 : cfg.health;
        s.range = cfg.range;
        normalRange = cfg.range;
        s.chaseRange = cfg.chaseRange;
        s.attackRate = cfg.attackRateTicks;
        s.attackRateRanged = cfg.attackRateTicks + 6;
        s.accuracy = cfg.accuracy(rarity);
        s.damage = cfg.damage(rarity); // fixed per hit — not the weapon's (gear) damage
        if (tutorialTarget != null) {
            s.damage = 2.0;          // a training dummy with opinions
            s.attackRate = 24;
            s.range = 16;
            normalRange = 16;
        }
        s.allowKnockback = true;
        s.needsAmmo = false;
    }

    /** Spawns (or re-spawns a parked rogue) at {@code at}. Returns false if the spawn failed. */
    boolean spawn(Location at) {
        if (!prepared) {
            prepare();
            prepared = true;
        }
        if (!npc.spawn(at)) return false;
        parkedAt = null;
        lastSpawnAt = System.currentTimeMillis();
        lastMovedAt = lastSpawnAt;
        if (state == State.FLEE || state == State.LOOT) state = State.ROAM;
        crateTarget = null;
        exitTarget = null;
        npc.getOrAddTrait(SentinelTrait.class).range = normalRange;
        if (npc.getEntity() != null) {
            entityId = npc.getEntity().getUniqueId();
            if (tutorialTarget != null) npc.getEntity().addScoreboardTag(TRAINEE_TAG + tutorialTarget);
        }
        lastPos = at.clone();
        return true;
    }

    // ---- brain ----

    Entity entity() {
        return npc.isSpawned() ? npc.getEntity() : null;
    }

    Location location() {
        Entity e = entity();
        return e == null ? null : e.getLocation();
    }

    /** Called every second by the director. Returns false when the bot is done (extracted). */
    boolean tick() {
        Entity e = entity();
        if (!(e instanceof LivingEntity body)) {
            // parked rogues just wait; a training rogue whose private tutorial world was unloaded is done for good
            return tutorialTarget == null || System.currentTimeMillis() - spawnedAt < 10_000L;
        }
        SentinelTrait s = npc.getOrAddTrait(SentinelTrait.class);
        if (tutorialTarget != null) {
            // Training rogue: no roaming/looting/extracting — just taunt and fight its one trainee.
            org.bukkit.entity.Player trainee = org.bukkit.Bukkit.getPlayer(tutorialTarget);
            if (trainee == null || !trainee.getWorld().equals(body.getWorld())) return false;
            // GlitchTutorial finds "its" rogue by this tag (several newcomers can train at once);
            // re-applied every second because Citizens may respawn the body
            body.addScoreboardTag(TRAINEE_TAG + tutorialTarget);
            spotCheck(body, s);
            if (s.chasing == null && trainee.getLocation().distanceSquared(body.getLocation()) > 9) {
                npc.getNavigator().setTarget(trainee, true);
            }
            return true;
        }
        BotConfig cfg = plugin.cfg();
        long now = System.currentTimeMillis();

        // Flee: break off at low health, run straight away from the attacker for a few seconds
        double max = maxHealth(body);
        if (state != State.FLEE && s.chasing != null && body.getHealth() / max < cfg.fleeHealth) {
            retreat(body, s, s.chasing.getLocation(), cfg.fleeSeconds * 1000L);
            return true;
        }
        if (state == State.FLEE) {
            if (now < fleeUntil) return true;
            s.range = normalRange;
            state = leaving || cratesLooted >= cfg.maxCrates ? State.EXTRACT : State.ROAM;
        }
        spotCheck(body, s);
        if (s.chasing != null) return true; // Sentinel is fighting — let it

        // Stuck detection while walking
        Location here = body.getLocation();
        if (lastPos == null || lastPos.getWorld() != here.getWorld() || lastPos.distanceSquared(here) > 1.5) {
            lastPos = here.clone();
            lastMovedAt = now;
        } else if (npc.getNavigator().isNavigating() && now - lastMovedAt > 10_000L) {
            npc.getNavigator().cancelNavigation();
            if (crateTarget != null) skippedCrates.add(key(crateTarget));
            crateTarget = null;
            lastMovedAt = now;
        }

        int left = plugin.hooks().raidSecondsLeft(world);
        if (state != State.EXTRACT && (leaving || cratesLooted >= cfg.maxCrates
                || (left >= 0 && left < cfg.extractWhenRemaining))) {
            state = State.EXTRACT;
            exitTarget = null;
        }

        switch (state) {
            case EXTRACT -> {
                if (exitTarget == null || !npc.getNavigator().isNavigating()) pickExit(here);
                if (exitTarget != null && flatDistance(here, exitTarget) <= exitRadius) {
                    extractEffect(here);
                    return false;
                }
                if (exitTarget == null && !npc.getNavigator().isNavigating()) wander(here);
            }
            case LOOT -> {
                if (crateTarget == null) {
                    state = State.ROAM;
                } else if (here.distanceSquared(crateTarget) <= 9.0) {
                    List<ItemStack> got = plugin.hooks().lootCrate(crateTarget);
                    if (!got.isEmpty()) {
                        bag.addAll(got);
                        cratesLooted++;
                        here.getWorld().playSound(crateTarget, Sound.BLOCK_BARREL_OPEN, 0.8f, 1.0f);
                    }
                    skippedCrates.add(key(crateTarget));
                    crateTarget = null;
                    state = State.ROAM;
                } else if (!npc.getNavigator().isNavigating()) {
                    skippedCrates.add(key(crateTarget)); // couldn't path there
                    crateTarget = null;
                    state = State.ROAM;
                }
            }
            default -> {
                if (npc.getNavigator().isNavigating()) return true;
                if (!pickCrate(here)) wander(here);
            }
        }
        return true;
    }

    /** Break off and run straight away from {@code from} for a while (low health, or too many on one raider). */
    private void retreat(LivingEntity body, SentinelTrait s, Location from, long millis) {
        Vector away = body.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
        Location to = body.getLocation().add(away.normalize().multiply(18));
        to.setY(body.getWorld().getHighestBlockYAt(to) + 1);
        s.range = 2;
        s.chasing = null;
        npc.getNavigator().setTarget(to);
        fleeUntil = System.currentTimeMillis() + millis;
        state = State.FLEE;
    }

    /** The real raider this rogue is fighting right now, or null. */
    Player chasingPlayer() {
        if (!npc.isSpawned()) return null;
        SentinelTrait s = npc.getOrAddTrait(SentinelTrait.class);
        return s.chasing instanceof Player p && !com.theglitch.common.Bots.isBot(p) ? p : null;
    }

    boolean fighting() {
        return npc.isSpawned() && npc.getOrAddTrait(SentinelTrait.class).chasing != null;
    }

    /** Too many rogues on one raider: this one backs off for a while instead of piling on. */
    void breakOff(Location from) {
        if (!(entity() instanceof LivingEntity body) || state == State.FLEE) return;
        retreat(body, npc.getOrAddTrait(SentinelTrait.class), from, 10_000L);
    }

    /** No raider anywhere near: drop the body and wait here (entities only tick near players anyway). */
    void park() {
        Location l = location();
        if (l == null) return;
        npc.getNavigator().cancelNavigation();
        parkedAt = l;
        npc.despawn(net.citizensnpcs.api.event.DespawnReason.PLUGIN);
    }

    /** Shout at a real raider when we start chasing one, or first see one close by. */
    private void spotCheck(LivingEntity body, SentinelTrait s) {
        if (s.chasing instanceof Player target && !com.theglitch.common.Bots.isBot(target)) {
            plugin.chat().onSpot(this, target);
            return;
        }
        Player nearest = null;
        double best = 24 * 24;
        for (Player p : com.theglitch.common.Bots.realPlayers(body.getWorld())) {
            double d = p.getLocation().distanceSquared(body.getLocation());
            if (d < best && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && body.hasLineOfSight(p)) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) plugin.chat().onSpot(this, nearest);
    }

    private boolean pickCrate(Location here) {
        BotConfig cfg = plugin.cfg();
        GlitchHooks.Crate best = null;
        double bestD = Double.MAX_VALUE;
        for (GlitchHooks.Crate c : plugin.hooks().cratesNear(here, cfg.crateSearchRadius)) {
            if (!c.ready() || skippedCrates.contains(key(c.location()))) continue;
            double d = c.location().distanceSquared(here);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        if (best == null) return false;
        crateTarget = best.location();
        npc.getNavigator().setTarget(crateTarget.clone().add(0, 0.5, 0));
        state = State.LOOT;
        return true;
    }

    private void pickExit(Location here) {
        GlitchHooks.Exit best = null;
        double bestD = Double.MAX_VALUE;
        for (GlitchHooks.Exit x : plugin.hooks().openExits(here.getWorld())) {
            double d = flatDistance(here, x.center());
            if (d < bestD) {
                bestD = d;
                best = x;
            }
        }
        if (best == null) {
            exitTarget = null;
            return;
        }
        exitTarget = best.center();
        exitRadius = Math.max(2, best.radius() - 1);
        Location dest = exitTarget.clone();
        dest.setY(here.getWorld().getHighestBlockYAt(dest) + 1);
        npc.getNavigator().setTarget(dest);
    }

    private void wander(Location here) {
        World w = here.getWorld();
        for (int attempt = 0; attempt < 6; attempt++) {
            double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(30, 60);
            int x = (int) Math.floor(here.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(here.getZ() + Math.sin(ang) * dist);
            Location spot = BotDirector.groundSpot(w, x, z);
            if (spot != null && w.getWorldBorder().isInside(spot)) {
                npc.getNavigator().setTarget(spot);
                return;
            }
        }
    }

    private void extractEffect(Location at) {
        World w = at.getWorld();
        w.spawnParticle(Particle.PORTAL, at.clone().add(0, 1, 0), 60, 0.4, 0.9, 0.4, 0.2);
        w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
        for (Player p : com.theglitch.common.Bots.realPlayers(w)) {
            if (p.getLocation().distanceSquared(at) < 48 * 48) {
                p.sendActionBar(plugin.mm().deserialize("<gray>Rogue <red>" + handle + "</red> extracted.</gray>"));
            }
        }
    }

    static double flatDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double maxHealth(LivingEntity e) {
        var attr = e.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : Math.max(1.0, attr.getValue());
    }

    private static String key(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    /** Worn and held items, for death drops. */
    List<ItemStack> wornItems() {
        List<ItemStack> out = new ArrayList<>();
        Equipment eq = npc.getOrAddTrait(Equipment.class);
        for (Equipment.EquipmentSlot slot : new Equipment.EquipmentSlot[]{Equipment.EquipmentSlot.HAND,
                Equipment.EquipmentSlot.HELMET, Equipment.EquipmentSlot.CHESTPLATE,
                Equipment.EquipmentSlot.LEGGINGS, Equipment.EquipmentSlot.BOOTS}) {
            ItemStack it = eq.get(slot);
            if (it != null && !it.getType().isAir()) out.add(it.clone());
        }
        return out;
    }
}
