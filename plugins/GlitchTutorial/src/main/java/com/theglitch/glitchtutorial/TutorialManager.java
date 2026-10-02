package com.theglitch.glitchtutorial;

import com.theglitch.common.NexoUtil;
import com.theglitch.common.TutorialItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The tutorial state machine: what each step does on entry, per-second checks, and finish/skip. */
final class TutorialManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] CRATES = {"crate1", "crate2", "crate3"};

    record HubStop(String name, Location location, String line) {}

    private final GlitchTutorial plugin;
    private final TutorialStore store;
    private final Hooks hooks;
    private final Map<UUID, List<BukkitTask>> dialogue = new ConcurrentHashMap<>();
    private final Map<UUID, Long> stepStarted = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> mobs = new ConcurrentHashMap<>();
    private final Set<UUID> busy = ConcurrentHashMap.newKeySet(); // in a scripted pause (dialogue → advance)
    private final Set<UUID> finishedDungeon = ConcurrentHashMap.newKeySet();
    /** Players whose training rogue has been seen alive (so "gone" means it died). */
    private final Set<UUID> rogueSeen = ConcurrentHashMap.newKeySet();

    TutorialManager(GlitchTutorial plugin, TutorialStore store, Hooks hooks) {
        this.plugin = plugin;
        this.store = store;
        this.hooks = hooks;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ---- lookups ----

    World world() {
        return Bukkit.getWorld(cfg().getString("world", "tutorial"));
    }

    boolean inTutorialWorld(Player p) {
        World w = world();
        return w != null && p.getWorld().equals(w);
    }

    Location point(String name) {
        World w = world();
        ConfigurationSection s = cfg().getConfigurationSection("points." + name);
        if (w == null || s == null) return null;
        return new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float) s.getDouble("yaw"), 0f);
    }

    List<HubStop> hubStops() {
        List<HubStop> out = new ArrayList<>();
        for (Map<?, ?> m : cfg().getMapList("hub-tour")) {
            World w = Bukkit.getWorld(String.valueOf(m.get("world")));
            if (w == null) continue;
            out.add(new HubStop(String.valueOf(m.get("name")),
                    new Location(w, num(m.get("x")), num(m.get("y")), num(m.get("z"))), String.valueOf(m.get("line"))));
        }
        return out;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    Location hubSpawn() {
        World hub = Bukkit.getWorld("hub");
        return hub == null ? null : hub.getSpawnLocation();
    }

    TutorialStore.Record record(Player p) {
        return store.get(p.getUniqueId());
    }

    boolean isActive(Player p) {
        TutorialStore.Record r = record(p);
        return r != null && r.status == TutorialStore.Status.ACTIVE;
    }

    // ---- lifecycle ----

    void onJoin(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null) {
            if (!p.hasPlayedBefore() && cfg().getBoolean("enabled", true) && cfg().getBoolean("auto-start", true) && world() != null) {
                start(p);
            } else {
                store.create(p.getUniqueId()).status = TutorialStore.Status.LEGACY;
                store.markDirty();
                leaveTutorialWorld(p);
            }
            return;
        }
        if (r.status == TutorialStore.Status.ACTIVE) {
            resume(p);
        } else {
            TutorialItems.stripAll(p); // leftovers from an interrupted run
            leaveTutorialWorld(p);   // logged out in there, then skipped/reset — don't strand them
        }
    }

    private void leaveTutorialWorld(Player p) {
        if (!inTutorialWorld(p) || p.hasPermission("glitchtutorial.admin")) return;
        Location hub = hubSpawn();
        if (hub != null) p.teleport(hub);
    }

    void start(Player p) {
        if (world() == null) {
            p.sendMessage(MM.deserialize("<red>The tutorial world isn't set up yet.</red>"));
            return;
        }
        if (isActive(p)) { // a second start would overwrite the saved inventory with lent gear
            resume(p);
            return;
        }
        String here = p.getWorld().getName();
        if (!here.equals("hub") && !inTutorialWorld(p)) {
            // from a raid this would stash the raid inventory without extracting (and the
            // red-world exit guard would cancel the teleport anyway)
            p.sendMessage(MM.deserialize("<red>Start the tutorial from the hub.</red>"));
            return;
        }
        TutorialStore.Record r = record(p);
        boolean rewarded = r != null && r.rewarded;
        if (r == null) r = store.create(p.getUniqueId());
        r.status = TutorialStore.Status.ACTIVE;
        r.step = Step.INTRO;
        r.progress = 0;
        r.rewarded = rewarded;
        PlayerInventory inv = p.getInventory();
        r.snapshot = TutorialStore.snapshot(inv.getContents());
        inv.clear();
        store.saveNow(); // the snapshot is the player's real inventory — never lose it
        Location spawn = point("spawn");
        if (spawn != null) p.teleport(spawn);
        p.showTitle(Title.title(MM.deserialize("<aqua><bold>Welcome to The Glitch</bold></aqua>"),
                MM.deserialize("<gray>a quick practice raid</gray>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(700))));
        enter(p);
    }

    /** Re-enter the current step after a rejoin or /tutorial. */
    void resume(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null) return;
        if (r.step.inTutorialWorld()) {
            Location cp = checkpoint(r.step);
            if (cp != null) p.teleport(cp);
        }
        enter(p);
    }

    Location checkpoint(Step s) {
        return switch (s) {
            case MOBS -> point("crate3");
            case ROGUE -> point("mobs");
            case EXTRACT -> point("rogue");
            default -> point("spawn");
        };
    }

    void enter(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return;
        stepStarted.put(p.getUniqueId(), System.currentTimeMillis());
        UUID id = p.getUniqueId();
        switch (r.step) {
            case INTRO -> say(p, "intro", () -> {
                if (stepIs(p, Step.INTRO)) p.sendMessage(MM.deserialize("<green><bold>[▶ Continue]</bold></green>")
                        .clickEvent(ClickEvent.runCommand("/tutorial next")));
            });
            case CLASS -> {
                if (hooks.hasClass(id)) {
                    classPicked(p);
                } else {
                    say(p, "class", () -> {
                        if (stepIs(p, Step.CLASS)) p.performCommand("class");
                    });
                }
            }
            case CRATES -> {
                placeCrates();
                if (Integer.bitCount(r.progress) >= CRATES.length) sayThenAdvance(p, "crates-done");
                else say(p, "crates", null);
            }
            case MOBS -> {
                if (r.progress >= cfg().getInt("mobs.count", 3)) sayThenAdvance(p, "mobs-done");
                else say(p, "mobs", () -> spawnMobs(p));
            }
            case ROGUE -> say(p, "rogue", () -> spawnRogue(p));
            case EXTRACT -> {
                r.progress = 0;
                say(p, "extract", null);
            }
            case HUB -> {
                if (inTutorialWorld(p)) {
                    Location hub = hubSpawn();
                    if (hub != null) p.teleport(hub);
                }
                say(p, "hub", null);
            }
            case DUNGEON -> {
                ensureKey(p);
                say(p, "dungeon", null);
            }
            case DONE -> finish(p);
        }
    }

    private boolean stepIs(Player p, Step s) {
        TutorialStore.Record r = record(p);
        return p.isOnline() && r != null && r.status == TutorialStore.Status.ACTIVE && r.step == s;
    }

    void advance(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return;
        busy.remove(p.getUniqueId());
        r.step = r.step.next();
        r.progress = 0;
        store.markDirty();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        enter(p);
    }

    /** Say a dialogue group, then advance (busy meanwhile so the ticker doesn't re-trigger). */
    private void sayThenAdvance(Player p, String key) {
        busy.add(p.getUniqueId());
        say(p, key, () -> advance(p));
    }

    // ---- step actions ----

    void next(Player p) {
        if (stepIs(p, Step.INTRO)) advance(p);
    }

    private void classPicked(Player p) {
        if (busy.contains(p.getUniqueId())) return;
        // Rejoining after the loadout was handed over must not hand it over twice
        boolean hasLoadout = false;
        for (ItemStack it : p.getInventory().getContents()) {
            if (TutorialItems.isTutorial(it)) {
                hasLoadout = true;
                break;
            }
        }
        if (!hasLoadout) giveLoadout(p);
        sayThenAdvance(p, "class-done");
    }

    private void giveLoadout(Player p) {
        List<ItemStack> give = new ArrayList<>();
        for (String spec : cfg().getStringList("loadout.gear")) {
            String[] s = spec.split(":");
            if (s.length != 2) continue;
            ItemStack g = hooks.gear(s[0].toUpperCase(Locale.ROOT), s[1].toUpperCase(Locale.ROOT));
            if (g != null) give.add(g);
        }
        for (String spec : cfg().getStringList("loadout.items")) {
            ItemStack it = item(spec);
            if (it != null) give.add(it);
        }
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : give) {
            TutorialItems.tag(it);
            String t = it.getType().name();
            // wear armor right away so they feel the difference
            if (t.endsWith("_HELMET") && empty(inv.getHelmet())) inv.setHelmet(it);
            else if (t.endsWith("_CHESTPLATE") && empty(inv.getChestplate())) inv.setChestplate(it);
            else if (t.endsWith("_LEGGINGS") && empty(inv.getLeggings())) inv.setLeggings(it);
            else if (t.endsWith("_BOOTS") && empty(inv.getBoots())) inv.setBoots(it);
            else inv.addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
    }

    private static boolean empty(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    /** "nexo_id:amount" or "minecraft:material:amount". */
    private static ItemStack item(String spec) {
        String[] s = spec.split(":");
        try {
            if (s[0].equalsIgnoreCase("minecraft") && s.length >= 2) {
                Material m = Material.matchMaterial(s[1]);
                return m == null ? null : new ItemStack(m, s.length > 2 ? Integer.parseInt(s[2]) : 1);
            }
            ItemStack it = NexoUtil.build(s[0]);
            if (it != null && s.length > 1) it.setAmount(Math.max(1, Math.min(it.getMaxStackSize(), Integer.parseInt(s[1]))));
            return it;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<ItemStack> tagged(List<String> specs) {
        List<ItemStack> out = new ArrayList<>();
        for (String spec : specs) {
            ItemStack it = item(spec);
            if (it != null) out.add(TutorialItems.tag(it));
        }
        return out;
    }

    void placeCrates() {
        for (String c : CRATES) {
            Location l = point(c);
            if (l == null) continue;
            Block b = l.getBlock();
            if (b.getType() != Material.BARREL) b.setType(Material.BARREL, false);
        }
    }

    /** A barrel was clicked in the tutorial world. Returns true when it is a tutorial crate. */
    boolean onCrate(Player p, Block block) {
        int idx = -1;
        for (int i = 0; i < CRATES.length; i++) {
            Location l = point(CRATES[i]);
            if (l != null && l.getBlock().equals(block)) idx = i;
        }
        if (idx < 0) return false;
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE || r.step != Step.CRATES) {
            p.sendActionBar(MM.deserialize("<gray>Not yet — follow Echo.</gray>"));
            return true;
        }
        int bit = 1 << idx;
        if ((r.progress & bit) != 0) {
            p.sendActionBar(MM.deserialize("<gray>Already looted.</gray>"));
            return true;
        }
        r.progress |= bit;
        store.markDirty();
        for (ItemStack it : tagged(cfg().getStringList("crate-loot." + CRATES[idx]))) {
            p.getInventory().addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        p.playSound(block.getLocation(), Sound.BLOCK_BARREL_OPEN, 1f, 1f);
        block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.1, 0.5), 12, 0.3, 0.2, 0.3);
        if (Integer.bitCount(r.progress) >= CRATES.length) sayThenAdvance(p, "crates-done");
        return true;
    }

    private void spawnMobs(Player p) {
        if (!stepIs(p, Step.MOBS)) return;
        Location at = point("mobs");
        if (at == null) return;
        TutorialStore.Record r = record(p);
        Set<UUID> tracked = mobs.get(p.getUniqueId());
        long alive = tracked == null ? 0 : tracked.stream().map(Bukkit::getEntity).filter(e -> e != null && !e.isDead()).count();
        int want = (int) Math.max(0, cfg().getInt("mobs.count", 3) - r.progress - alive);
        if (want == 0) return;
        String type = cfg().getString("mobs.type", "CorruptedCrawler");
        Set<UUID> before = new HashSet<>();
        for (Entity e : at.getWorld().getNearbyEntities(at, 6, 6, 6)) before.add(e.getUniqueId());
        for (int i = 0; i < want; i++) {
            Location l = at.clone().add((i - 1) * 2.5, 0, 0);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT, "mm mobs spawn %s 1 %s,%.1f,%.1f,%.1f",
                    type, l.getWorld().getName(), l.getX(), l.getY(), l.getZ()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Set<UUID> mine = mobs.computeIfAbsent(p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            for (Entity e : at.getWorld().getNearbyEntities(at, 8, 6, 8)) {
                if (e instanceof LivingEntity && !(e instanceof Player) && !before.contains(e.getUniqueId())) mine.add(e.getUniqueId());
            }
        }, 2L);
    }

    /** A mob/rogue died in the tutorial world at the hands of {@code p}. */
    void onKill(Player p, LivingEntity dead, boolean rogue) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return;
        if (rogue && r.step == Step.ROGUE) {
            rogueDefeated(p, dead.getLocation());
            return;
        }
        if (!rogue && r.step == Step.MOBS) {
            Set<UUID> mine = mobs.get(p.getUniqueId());
            if (mine != null) mine.remove(dead.getUniqueId());
            r.progress++;
            store.markDirty();
            if (r.progress >= cfg().getInt("mobs.count", 3)) sayThenAdvance(p, "mobs-done");
        }
    }

    private void spawnRogue(Player p) {
        if (!stepIs(p, Step.ROGUE)) return;
        Location at = point("rogue");
        if (at == null || !cfg().getBoolean("rogue.enabled", true) || Bukkit.getPluginManager().getPlugin("GlitchBots") == null) {
            advance(p); // no bots installed — skip this step rather than block the tutorial
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(Locale.ROOT, "bots tutorial %s %.1f %.1f %.1f",
                p.getName(), at.getX(), at.getY(), at.getZ()));
    }

    private void ensureKey(Player p) {
        String keyId = cfg().getString("dungeon.key-item", "dungeon_key_t1");
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it != null && keyId.equals(NexoUtil.idOf(it))) return;
        }
        ItemStack key = NexoUtil.build(keyId);
        if (key != null) p.getInventory().addItem(TutorialItems.tag(key));
    }

    // ---- dungeon (MythicDungeons events, via GlitchTutorial's reflective listener) ----

    void onDungeonFinish(Player p) {
        if (!stepIs(p, Step.DUNGEON)) return;
        finishedDungeon.add(p.getUniqueId());
        // MythicDungeons returns them to the hub exit a moment after the finish
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finishedDungeon.remove(p.getUniqueId());
            if (stepIs(p, Step.DUNGEON)) advance(p);
        }, 100L);
    }

    void onDungeonLeave(Player p) {
        if (!stepIs(p, Step.DUNGEON) || finishedDungeon.contains(p.getUniqueId())) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!stepIs(p, Step.DUNGEON) || finishedDungeon.contains(p.getUniqueId())) return;
            ensureKey(p);
            say(p, "dungeon-retry", null);
        }, 60L);
    }

    // ---- finish / skip ----

    void finish(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null) return;
        cleanup(p, r);
        r.status = TutorialStore.Status.COMPLETE;
        int reward = cfg().getInt("reward.shards", 500);
        boolean paid = false;
        if (!r.rewarded && reward > 0) {
            paid = hooks.deposit(p, reward);
            r.rewarded = paid;
        }
        store.saveNow();
        final int shown = paid ? reward : 0;
        p.showTitle(Title.title(MM.deserialize("<green><bold>You're ready</bold></green>"),
                MM.deserialize("<gray>raid · loot · extract · repeat</gray>"),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(800))));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        if (shown > 0) {
            say(p, "done", null, shown);
        } else {
            sayLines(p, List.of(cfg().getStringList("dialogue.done").isEmpty() ? "That's it." : cfg().getStringList("dialogue.done").get(0)), null, 0);
        }
    }

    void skip(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return;
        if (p.getWorld().getName().matches("[a-z]+_\\d+")) p.performCommand("md leave"); // inside a dungeon instance
        cleanup(p, r);
        r.status = TutorialStore.Status.SKIPPED;
        store.saveNow();
        p.sendMessage(MM.deserialize("<gray>Tutorial skipped. Replay it any time with <yellow>/tutorial</yellow>.</gray>"));
    }

    /** Strip lent items, give the original inventory back, clear spawned mobs, leave the tutorial world. */
    private void cleanup(Player p, TutorialStore.Record r) {
        cancelDialogue(p);
        busy.remove(p.getUniqueId());
        TutorialItems.stripAll(p);
        List<ItemStack> old = TutorialStore.restore(r.snapshot);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < old.size(); i++) {
            ItemStack it = old.get(i);
            if (it == null) continue;
            if (i < inv.getSize() && empty(inv.getItem(i))) inv.setItem(i, it);
            else inv.addItem(it).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        r.snapshot = new ArrayList<>();
        Set<UUID> mine = mobs.remove(p.getUniqueId());
        if (mine != null) for (UUID id : mine) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        if (Bukkit.getPluginManager().getPlugin("GlitchBots") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bots tutorial " + p.getName() + " clear");
        }
        if (inTutorialWorld(p)) {
            Location hub = hubSpawn();
            if (hub != null) p.teleport(hub);
        }
    }

    void forget(Player p) {
        rogueSeen.remove(p.getUniqueId());
        cancelDialogue(p);
        busy.remove(p.getUniqueId());
        Set<UUID> mine = mobs.remove(p.getUniqueId());
        if (mine != null) for (UUID id : mine) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
    }

    // ---- per-second checks ----

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tick(p);
            } catch (Exception e) {
                plugin.getLogger().warning("Tutorial tick failed for " + p.getName() + ": " + e);
            }
        }
    }

    private void tick(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return;
        if (r.step.inTutorialWorld() && !inTutorialWorld(p)) {
            p.sendActionBar(MM.deserialize("<gray>Tutorial paused — <yellow>/tutorial</yellow> to continue, <yellow>/tutorial skip</yellow> to stop.</gray>"));
            return;
        }
        bless(p); // also during dialogue pauses, so it never lapses mid-fight
        if (busy.contains(p.getUniqueId())) return;
        long since = System.currentTimeMillis() - stepStarted.getOrDefault(p.getUniqueId(), 0L);
        switch (r.step) {
            case INTRO -> {
                if (since > 35_000L) advance(p);
            }
            case CLASS -> {
                if (hooks.hasClass(p.getUniqueId())) classPicked(p);
                else if (since > 8_000L) p.sendActionBar(MM.deserialize("<gray>Pick a class — <yellow>/class</yellow></gray>"));
            }
            case CRATES -> {
                for (int i = 0; i < CRATES.length; i++) {
                    if ((r.progress & (1 << i)) != 0) continue;
                    Location l = point(CRATES[i]);
                    if (l != null) p.spawnParticle(Particle.END_ROD, l.getBlock().getLocation().add(0.5, 1.3, 0.5), 6, 0.15, 0.4, 0.15, 0.01);
                }
            }
            case MOBS -> {
                Set<UUID> mine = mobs.get(p.getUniqueId());
                boolean anyAlive = mine != null && mine.stream().map(Bukkit::getEntity).anyMatch(e -> e != null && !e.isDead());
                if (!anyAlive && since > 15_000L) {
                    stepStarted.put(p.getUniqueId(), System.currentTimeMillis());
                    spawnMobs(p); // despawned or never appeared — bring the rest back
                }
            }
            case ROGUE -> tickRogue(p, since);
            case EXTRACT -> tickExtract(p, r);
            case HUB -> tickHub(p, r);
            default -> { }
        }
    }
    /**
     * The kill event isn't the only signal: a Citizens rogue can die (or be removed) without a
     * credited killer. Once we've seen this player's rogue nearby, its disappearance counts as
     * the kill — no more waiting on a timeout.
     */
    private void tickRogue(Player p, long since) {
        boolean alive = false;
        for (Entity e : p.getWorld().getNearbyEntities(p.getLocation(), 160, 64, 160)) {
            if (com.theglitch.common.Bots.isBot(e) && !e.isDead() && e.getName().startsWith("Rogue ")) {
                alive = true;
                break;
            }
        }
        if (alive) {
            rogueSeen.add(p.getUniqueId());
            return;
        }
        if (rogueSeen.remove(p.getUniqueId())) {
            rogueDefeated(p, p.getLocation());
        } else if (since > 20_000L && since < 22_000L) {
            spawnRogue(p); // never appeared — try once more
        } else if (since > 60_000L) {
            advance(p); // still nothing — don't block the tutorial
        }
    }

    private void rogueDefeated(Player p, Location at) {
        if (!stepIs(p, Step.ROGUE) || busy.contains(p.getUniqueId())) return;
        rogueSeen.remove(p.getUniqueId());
        for (ItemStack it : tagged(cfg().getStringList("rogue-drop"))) {
            at.getWorld().dropItemNaturally(at, it);
        }
        sayThenAdvance(p, "rogue-done");
    }

    /** Tutorial blessing (config "blessing"): short effects refreshed every tick-second. */
    private void bless(Player p) {
        for (String spec : cfg().getStringList("blessing")) {
            String[] s = spec.split(":");
            org.bukkit.potion.PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                    org.bukkit.NamespacedKey.minecraft(s[0].toLowerCase(Locale.ROOT)));
            if (type == null) continue;
            int amp;
            try {
                amp = s.length > 1 ? Integer.parseInt(s[1].trim()) : 0;
            } catch (NumberFormatException e) {
                continue;
            }
            org.bukkit.potion.PotionEffect cur = p.getPotionEffect(type);
            if (cur != null && cur.getAmplifier() > amp) continue; // a stronger effect from elsewhere
            if (cur == null || cur.getDuration() < 200) {
                p.addPotionEffect(new org.bukkit.potion.PotionEffect(type, 300, amp, true, false, true));
            }
        }
    }

    private void tickExtract(Player p, TutorialStore.Record r) {
        Location pad = point("extract");
        if (pad == null) return;
        for (int i = 0; i < 6; i++) {
            p.spawnParticle(Particle.END_ROD, pad.clone().add(0, i * 1.5, 0), 3, 0.2, 0.3, 0.2, 0.01);
        }
        double d = flat(p.getLocation(), pad);
        int need = cfg().getInt("extract-seconds", 5);
        if (d <= 3.0) {
            r.progress++;
            p.sendActionBar(MM.deserialize("<green>Extracting… <white>" + r.progress + "/" + need + "</white></green>"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.8f + r.progress * 0.15f);
            if (r.progress >= need) {
                p.showTitle(Title.title(MM.deserialize("<green><bold>EXTRACTED</bold></green>"), Component.empty(),
                        Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800), Duration.ofMillis(400))));
                sayThenAdvance(p, "extract-done");
            }
        } else {
            if (r.progress > 0) p.sendActionBar(MM.deserialize("<red>Extraction interrupted — stay in the beam.</red>"));
            else p.sendActionBar(MM.deserialize("<gray>Extraction beam: <white>" + (int) d + "m</white> " + arrow(p, pad) + "</gray>"));
            r.progress = 0;
        }
    }

    private void tickHub(Player p, TutorialStore.Record r) {
        List<HubStop> stops = hubStops();
        if (r.progress >= stops.size()) {
            advance(p);
            return;
        }
        HubStop stop = stops.get(r.progress);
        if (!p.getWorld().equals(stop.location().getWorld())) {
            p.sendActionBar(MM.deserialize("<gray>Head back to the hub to continue the tour.</gray>"));
            return;
        }
        double d = flat(p.getLocation(), stop.location());
        if (d <= 4.5) {
            r.progress++;
            store.markDirty();
            busy.add(p.getUniqueId());
            sayLines(p, List.of(stop.line()), () -> busy.remove(p.getUniqueId()), 0);
            return;
        }
        p.sendActionBar(MM.deserialize("<aqua>" + stop.name() + "</aqua> <gray>" + (int) d + "m</gray> <white>" + arrow(p, stop.location()) + "</white>"));
        // short particle trail toward the stop
        Vector dir = stop.location().toVector().subtract(p.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() > 0.01) {
            dir.normalize();
            for (int i = 2; i <= 8; i += 2) {
                p.spawnParticle(Particle.END_ROD, p.getLocation().add(dir.clone().multiply(i)).add(0, 0.3, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    private static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** ↑ ↗ → ... relative to where the player is looking. */
    private static String arrow(Player p, Location to) {
        Vector d = to.toVector().subtract(p.getLocation().toVector());
        double target = Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
        double rel = ((target - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(int) Math.round(((rel + 360) % 360) / 45.0) % 8];
    }

    // ---- dialogue ----

    void say(Player p, String key, Runnable after) {
        say(p, key, after, 0);
    }

    private void say(Player p, String key, Runnable after, int reward) {
        sayLines(p, cfg().getStringList("dialogue." + key), after, reward);
    }

    void sayLines(Player p, List<String> lines, Runnable after, int reward) {
        cancelDialogue(p);
        long delay = Math.max(10, cfg().getLong("line-delay-ticks", 60));
        String guide = cfg().getString("guide-name", "<aqua><bold>Echo</bold></aqua>");
        List<BukkitTask> tasks = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("<player>", p.getName()).replace("<reward>", String.valueOf(reward));
            String prefix = lines.size() > 1 ? "<dark_gray>[" + (i + 1) + "/" + lines.size() + "]</dark_gray> " : "";
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.sendMessage(MM.deserialize(prefix + guide + "<dark_gray>:</dark_gray> <white>" + line + "</white>"));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, 1.6f);
            }, i * delay));
        }
        if (after != null) {
            tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) after.run();
            }, Math.max(1, (lines.size() - 1) * delay + 40)));
        }
        dialogue.put(p.getUniqueId(), tasks);
    }

    private void cancelDialogue(Player p) {
        List<BukkitTask> old = dialogue.remove(p.getUniqueId());
        if (old != null) old.forEach(BukkitTask::cancel);
    }

    /** Right-clicking Echo repeats the current step's lines. */
    void replay(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) {
            p.sendMessage(MM.deserialize(cfg().getString("guide-name", "Echo") + "<dark_gray>:</dark_gray> <white>Good luck out there. <gray>(/tutorial to replay)</gray></white>"));
            return;
        }
        if (busy.contains(p.getUniqueId())) return;
        Runnable after = r.step == Step.CLASS ? () -> {
            if (stepIs(p, Step.CLASS)) p.performCommand("class");
        } : null;
        sayLines(p, cfg().getStringList("dialogue." + r.step.key()), after, 0);
    }

    // ---- HUD ----

    String objective(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return "";
        String raw = cfg().getString("objectives." + r.step.key(), "");
        int max = switch (r.step) {
            case MOBS -> cfg().getInt("mobs.count", 3);
            case HUB -> hubStops().size();
            default -> 0;
        };
        int n = r.step == Step.CRATES ? Integer.bitCount(r.progress) : r.progress;
        String stop = "";
        if (r.step == Step.HUB) {
            List<HubStop> stops = hubStops();
            stop = r.progress < stops.size() ? stops.get(r.progress).name() : "";
        }
        return raw.replace("<n>", String.valueOf(n)).replace("<max>", String.valueOf(max)).replace("<stop>", stop);
    }

    String title(Player p) {
        TutorialStore.Record r = record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return "";
        return "Tutorial · " + (r.step.ordinal() + 1) + "/" + (Step.DUNGEON.ordinal() + 1);
    }

    boolean hasMob(UUID player, UUID mob) {
        Set<UUID> mine = mobs.get(player);
        return mine != null && mine.contains(mob);
    }

    UUID mobOwner(UUID mob) {
        for (Map.Entry<UUID, Set<UUID>> e : mobs.entrySet()) {
            if (e.getValue().contains(mob)) return e.getKey();
        }
        return null;
    }

    Map<UUID, Set<UUID>> mobsView() {
        return new HashMap<>(mobs);
    }
}
