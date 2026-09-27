package com.theglitch.glitchitems;

import com.theglitch.common.NexoUtil;
import com.theglitch.common.Worlds;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Arc Raiders-style gadgets and heals (docs/ITEM_SYSTEM.md §14), all used with
 * right-click: Frag / Smoke grenades and the Lure Beacon are thrown, the Snap
 * Hook grapples, the Barricade Kit raises a temporary wall, the Pulse Mine arms
 * a trap. Bandage and Adrenaline Shot heal/buff anywhere. Also stops vanilla
 * behaviour (placing, equipping, eating) on the use-less loot items listed in
 * gadgets.inert-items.
 */
public final class GadgetListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> THROWN = Set.of("frag_grenade", "smoke_grenade", "lure_beacon");
    private static final Set<String> GAME_ONLY = Set.of(
            "frag_grenade", "smoke_grenade", "lure_beacon", "snap_hook", "barricade_kit", "pulse_mine");

    private record Mine(UUID owner, Location location, ItemDisplay display, long expiresAt) {}

    private final GlitchItems plugin;
    private final NamespacedKey gadgetKey;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    /** Barricade blocks still standing, so they can be cleared on shutdown. */
    private final Set<Block> barricadeBlocks = new HashSet<>();
    private final List<Mine> mines = new ArrayList<>();
    private BukkitTask mineTask;

    public GadgetListener(GlitchItems plugin) {
        this.plugin = plugin;
        this.gadgetKey = new NamespacedKey(plugin, "gadget");
    }

    private ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("gadgets");
        return s != null ? s : plugin.getConfig().createSection("gadgets");
    }

    private boolean gadgetWorld(World world) {
        String name = world.getName();
        if (cfg().getStringList("worlds").contains(name) || Worlds.isGameWorld(name)) return true;
        String pattern = cfg().getString("world-pattern", "");
        return !pattern.isEmpty() && Pattern.matches(pattern, name);
    }

    // ------------------------------------------------------------------ use

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        String id = NexoUtil.idOf(item);
        if (id == null) return;
        if (cfg().getStringList("inert-items").contains(id)) {
            event.setCancelled(true);
            return;
        }
        if (!isGadget(id)) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (GAME_ONLY.contains(id) && !gadgetWorld(player.getWorld())) {
            player.sendMessage(MM.deserialize("<red>Gadgets only work in the Red Zone and dungeons.</red>"));
            return;
        }
        if (onCooldown(player, id)) return;

        boolean used = switch (id) {
            case "frag_grenade", "smoke_grenade", "lure_beacon" -> throwGadget(player, item, id);
            case "snap_hook" -> snapHook(player);
            case "barricade_kit" -> barricade(player, event.getClickedBlock(), event.getBlockFace());
            case "pulse_mine" -> armMine(player, event.getClickedBlock(), event.getBlockFace());
            case "bandage" -> heal(player, new PotionEffect(PotionEffectType.REGENERATION, 60, 2), Sound.ITEM_ARMOR_EQUIP_LEATHER);
            case "adrenaline_shot" -> adrenaline(player);
            default -> false;
        };
        if (used) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private static boolean isGadget(String id) {
        return GAME_ONLY.contains(id) || id.equals("bandage") || id.equals("adrenaline_shot");
    }

    private boolean onCooldown(Player player, String id) {
        int seconds = switch (id) {
            case "snap_hook" -> cfg().getInt("snap-hook.cooldown-seconds", 3);
            case "bandage" -> 5;
            case "adrenaline_shot" -> 20;
            default -> 1;
        };
        long now = System.currentTimeMillis();
        String key = player.getUniqueId() + ":" + id;
        Long until = cooldowns.get(key);
        if (until != null && until > now) {
            player.sendActionBar(MM.deserialize("<red>Ready in " + ((until - now) / 1000 + 1) + "s</red>"));
            return true;
        }
        cooldowns.put(key, now + seconds * 1000L);
        return false;
    }

    /** Stops lead/fire-charge style vanilla uses on mobs. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        String id = NexoUtil.idOf(item);
        if (id != null && (isGadget(id) || cfg().getStringList("inert-items").contains(id))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = NexoUtil.idOf(event.getItemInHand());
        if (id != null && (isGadget(id) || cfg().getStringList("inert-items").contains(id))) {
            event.setCancelled(true);
        }
    }

    // --------------------------------------------------------------- heals

    private boolean heal(Player player, PotionEffect effect, Sound sound) {
        player.addPotionEffect(effect);
        player.getWorld().playSound(player.getLocation(), sound, 1f, 1.2f);
        player.sendActionBar(MM.deserialize("<green>Patched up.</green>"));
        return true;
    }

    private boolean adrenaline(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 120, 0));
        player.setFoodLevel(Math.max(player.getFoodLevel(), 18));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 1f, 0.6f);
        player.sendActionBar(MM.deserialize("<yellow>Adrenaline!</yellow>"));
        return true;
    }

    // ----------------------------------------------------------- throwables

    private boolean throwGadget(Player player, ItemStack item, String id) {
        Snowball ball = player.launchProjectile(Snowball.class);
        ItemStack look = item.clone();
        look.setAmount(1);
        ball.setItem(look);
        ball.setVelocity(ball.getVelocity().multiply(1.1));
        ball.getPersistentDataContainer().set(gadgetKey, PersistentDataType.STRING, id);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.7f);
        return true;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Snowball ball)) return;
        String id = ball.getPersistentDataContainer().get(gadgetKey, PersistentDataType.STRING);
        if (id == null || !THROWN.contains(id)) return;
        event.setCancelled(true);
        Location at = ball.getLocation();
        Player thrower = ball.getShooter() instanceof Player p ? p : null;
        ball.remove();
        switch (id) {
            case "frag_grenade" -> explode(at, thrower);
            case "smoke_grenade" -> smoke(at);
            case "lure_beacon" -> lure(at);
            default -> { }
        }
    }

    private void explode(Location at, Player thrower) {
        double radius = cfg().getDouble("frag.radius", 4.5);
        double damage = cfg().getDouble("frag.damage", 12.0);
        World world = at.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 30, 1, 0.5, 1, 0.05);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1f);
        for (Entity e : world.getNearbyEntities(at, radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e instanceof ArmorStand || e.equals(thrower)) continue;
            double dist = target.getLocation().distance(at);
            if (dist > radius) continue;
            double scaled = damage * (1.0 - 0.6 * dist / radius);
            if (thrower != null) target.damage(scaled, thrower);
            else target.damage(scaled);
            target.setVelocity(target.getLocation().toVector().subtract(at.toVector()).normalize().multiply(0.6).setY(0.35));
        }
    }

    private void smoke(Location at) {
        double radius = cfg().getDouble("smoke.radius", 4.0);
        int seconds = cfg().getInt("smoke.seconds", 8);
        World world = at.getWorld();
        world.playSound(at, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.5f);
        Particle.DustOptions grey = new Particle.DustOptions(Color.fromRGB(120, 110, 140), 4f);
        final int[] ticks = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticks[0]++ >= seconds * 4) {
                task.cancel();
                return;
            }
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, 25, radius * 0.5, 1.2, radius * 0.5, 0.01);
            world.spawnParticle(Particle.DUST, at, 40, radius * 0.6, 1.4, radius * 0.6, grey);
            for (Entity e : world.getNearbyEntities(at, radius, 2.5, radius)) {
                if (e instanceof Player p) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
                } else if (e instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
            // Mobs outside the cloud can't see players hiding inside it.
            for (Entity e : world.getNearbyEntities(at, 16, 6, 16)) {
                if (e instanceof Mob mob && mob.getTarget() instanceof Player p
                        && p.getLocation().distanceSquared(at) <= radius * radius) {
                    mob.setTarget(null);
                }
            }
        }, 0L, 5L);
    }

    private void lure(Location at) {
        double radius = cfg().getDouble("lure.radius", 16.0);
        int seconds = cfg().getInt("lure.seconds", 8);
        World world = at.getWorld();
        ArmorStand decoy = world.spawn(at, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setSmall(true);
            s.setInvulnerable(true);
            s.setGravity(false);
            s.setGlowing(true);
            s.setPersistent(false);
            s.getEquipment().setHelmet(new ItemStack(Material.BELL));
        });
        final int[] ticks = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (ticks[0]++ >= seconds * 2 || !decoy.isValid()) {
                decoy.remove();
                task.cancel();
                return;
            }
            world.playSound(at, Sound.BLOCK_BELL_USE, 1.2f, 1.4f);
            world.spawnParticle(Particle.NOTE, at.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
            for (Entity e : world.getNearbyEntities(at, radius, 8, radius)) {
                if (e instanceof Mob mob) mob.setTarget(decoy);
            }
        }, 0L, 10L);
    }

    // ------------------------------------------------------------ snap hook

    private boolean snapHook(Player player) {
        int range = cfg().getInt("snap-hook.range", 24);
        RayTraceResult hit = player.rayTraceBlocks(range, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null) {
            player.sendActionBar(MM.deserialize("<red>Nothing to hook onto.</red>"));
            return false;
        }
        Vector to = hit.getHitPosition().subtract(player.getLocation().toVector());
        double dist = to.length();
        Vector pull = to.normalize().multiply(Math.min(2.6, 0.8 + dist * 0.09));
        pull.setY(Math.max(pull.getY(), 0) + 0.45);
        player.setVelocity(pull);
        player.setFallDistance(0);
        noFallUntil.put(player.getUniqueId(), System.currentTimeMillis() + 4000);
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        for (double d = 0; d < dist; d += 0.6) {
            world.spawnParticle(Particle.CRIT, eye.clone().add(eye.getDirection().multiply(d)), 1, 0, 0, 0, 0);
        }
        world.playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.5f);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Long until = noFallUntil.get(event.getEntity().getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            event.setCancelled(true);
            noFallUntil.remove(event.getEntity().getUniqueId());
        }
    }

    // ------------------------------------------------------------ barricade

    private boolean barricade(Player player, Block clicked, BlockFace face) {
        if (clicked == null || face != BlockFace.UP) {
            player.sendActionBar(MM.deserialize("<red>Right-click the top of a block to deploy.</red>"));
            return false;
        }
        Material wall = Material.matchMaterial(cfg().getString("barricade.block", "PURPLE_STAINED_GLASS"));
        if (wall == null || !wall.isBlock()) wall = Material.PURPLE_STAINED_GLASS;
        BlockFace facing = player.getFacing();
        BlockFace side = switch (facing) {
            case NORTH, SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
        Block base = clicked.getRelative(BlockFace.UP);
        List<Block> placed = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                Block b = base.getRelative(side, dx).getRelative(BlockFace.UP, dy);
                if (b.getType().isAir()) {
                    b.setType(wall, false);
                    placed.add(b);
                }
            }
        }
        if (placed.isEmpty()) {
            player.sendActionBar(MM.deserialize("<red>No room for a barricade here.</red>"));
            return false;
        }
        barricadeBlocks.addAll(placed);
        final Material placedType = wall;
        player.getWorld().playSound(base.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.4f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Block b : placed) {
                if (b.getType() == placedType) b.setType(Material.AIR, false);
                barricadeBlocks.remove(b);
            }
            base.getWorld().playSound(base.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 1f);
        }, cfg().getInt("barricade.seconds", 20) * 20L);
        return true;
    }

    // ----------------------------------------------------------- pulse mine

    private boolean armMine(Player player, Block clicked, BlockFace face) {
        if (clicked == null || face != BlockFace.UP) {
            player.sendActionBar(MM.deserialize("<red>Right-click the top of a block to arm.</red>"));
            return false;
        }
        int max = cfg().getInt("pulse-mine.max-per-player", 3);
        long mine = mines.stream().filter(m -> m.owner().equals(player.getUniqueId())).count();
        if (mine >= max) {
            player.sendActionBar(MM.deserialize("<red>You already have " + max + " mines armed.</red>"));
            return false;
        }
        Location at = clicked.getLocation().add(0.5, 1.03, 0.5);
        ItemDisplay display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.HEAVY_WEIGHTED_PRESSURE_PLATE));
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(Color.fromRGB(168, 85, 247));
        });
        mines.add(new Mine(player.getUniqueId(), at, display,
                System.currentTimeMillis() + cfg().getInt("pulse-mine.seconds", 120) * 1000L));
        at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 2f);
        startMineTask();
        return true;
    }

    private void startMineTask() {
        if (mineTask != null && !mineTask.isCancelled()) return;
        mineTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickMines, 5L, 5L);
    }

    private void tickMines() {
        long now = System.currentTimeMillis();
        double damage = cfg().getDouble("pulse-mine.damage", 4.0);
        mines.removeIf(m -> {
            if (now > m.expiresAt() || !m.display().isValid()) {
                m.display().remove();
                return true;
            }
            for (Entity e : m.location().getWorld().getNearbyEntities(m.location(), 1.6, 1.5, 1.6)) {
                if (!(e instanceof LivingEntity target) || e instanceof ArmorStand) continue;
                if (e.getUniqueId().equals(m.owner())) continue;
                if (!(e instanceof Mob) && !(e instanceof Player)) continue;
                pulse(m, target, damage);
                return true;
            }
            return false;
        });
        if (mines.isEmpty() && mineTask != null) {
            mineTask.cancel();
            mineTask = null;
        }
    }

    private void pulse(Mine mine, LivingEntity target, double damage) {
        World world = mine.location().getWorld();
        world.spawnParticle(Particle.ELECTRIC_SPARK, mine.location(), 60, 1.2, 0.8, 1.2, 0.2);
        world.playSound(mine.location(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
        Player owner = plugin.getServer().getPlayer(mine.owner());
        if (owner != null) target.damage(damage, owner);
        else target.damage(damage);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 160, 0));
        mine.display().remove();
    }

    /** Removes barricade walls and armed mines (plugin disable). */
    public void shutdown() {
        Material wall = Material.matchMaterial(cfg().getString("barricade.block", "PURPLE_STAINED_GLASS"));
        for (Block b : barricadeBlocks) {
            if (b.getType() == wall) b.setType(Material.AIR, false);
        }
        barricadeBlocks.clear();
        mines.forEach(m -> m.display().remove());
        mines.clear();
    }
}
