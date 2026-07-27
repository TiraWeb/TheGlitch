package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handles all class abilities — activation, cooldowns, effects.
 * Right-click detection for prime/tactical abilities.
 * Event-based passive abilities (traits).
 */
public class AbilityListener implements Listener {

    private final GlitchClasses plugin;
    private final ClassManager classManager;

    // Cooldown tracking: UUID -> ability name -> expiry timestamp
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // Active effects tracking
    private final Map<UUID, Boolean> shieldWallActive = new HashMap<>();
    private final Map<UUID, Boolean> cloakActive = new HashMap<>();
    private final Map<UUID, Boolean> tauntActive = new HashMap<>();
    private final Map<UUID, List<Block>> turretBlocks = new HashMap<>();

    // Passive cooldowns (for traits with internal cooldowns)
    private final Map<UUID, Long> lastStandCooldown = new HashMap<>();
    private final Map<UUID, Long> mendCooldown = new HashMap<>();

    // NamespacedKey for class item identification
    private final NamespacedKey classItemKey;

    public AbilityListener(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        this.classItemKey = new NamespacedKey(plugin, "class_ability");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only trigger on right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ClassData data = classManager.getClassData(uuid);
        if (data.className().equals("none")) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        // Check if the item has our custom metadata
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String abilityType = meta.getPersistentDataContainer().get(classItemKey, PersistentDataType.STRING);
        if (abilityType == null) return;

        event.setCancelled(true);

        // Check cooldown
        if (isOnCooldown(uuid, abilityType)) {
            long remaining = getCooldownRemaining(uuid, abilityType);
            player.sendMessage(plugin.getComponent("ability-cooldown", "<seconds>", String.valueOf(remaining / 1000)));
            return;
        }

        // Activate ability based on class and type
        switch (data.className()) {
            case "vanguard" -> activateVanguardAbility(player, abilityType, data);
            case "warden" -> activateWardenAbility(player, abilityType, data);
            case "specter" -> activateSpecterAbility(player, abilityType, data);
            case "operator" -> activateOperatorAbility(player, abilityType, data);
        }
    }

    // ==================== VANGUARD ABILITIES ====================

    private void activateVanguardAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateShieldWall(player, data);
            case "tactical" -> activateTaunt(player, data);
        }
    }

    private void activateShieldWall(Player player, ClassData data) {
        int cooldown = getCooldown("vanguard", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);

        int duration = 600 + (data.level() >= 6 ? 40 : 0); // 30s base, +2s at level 6
        int absorption = 200 + (data.level() >= 8 ? 100 : 0); // 200 base, +100 at level 8

        // Place 3x3 barrier blocks in front of the player
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.getDirection().setY(0).normalize();
        List<Block> wallBlocks = new ArrayList<>();

        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 2; y++) {
                Block block = playerLoc.clone().add(direction.clone().multiply(2)).add(x, y, 0).getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(Material.BARRIER);
                    wallBlocks.add(block);
                }
            }
        }

        turretBlocks.put(player.getUniqueId(), wallBlocks);
        shieldWallActive.put(player.getUniqueId(), true);

        player.sendMessage(plugin.getComponent("shield-wall-placed"));
        player.sendActionBar(Component.text("SHIELD WALL ACTIVE", NamedTextColor.RED));

        // Visual effects
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(direction.clone().multiply(2)), 50, 1, 1, 1, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.8f);

        // Remove wall after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            shieldWallActive.remove(player.getUniqueId());
            List<Block> blocks = turretBlocks.remove(player.getUniqueId());
            if (blocks != null) {
                for (Block block : blocks) {
                    if (block.getType() == Material.BARRIER) {
                        block.setType(Material.AIR);
                        block.getWorld().spawnParticle(Particle.CLOUD, block.getLocation().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
            player.sendActionBar(Component.empty());
        }, duration);
    }

    private void activateTaunt(Player player, ClassData data) {
        int cooldown = getCooldown("vanguard", "tactical", data.level());
        setCooldown(player.getUniqueId(), "tactical", cooldown);

        int duration = 100; // 5 seconds
        int range = 10 + (data.level() >= 4 ? 5 : 0); // 10 base, +5 at level 4

        tauntActive.put(player.getUniqueId(), true);

        // Apply effects to player
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0)); // 20% DR

        // Make mobs target the player — apply glowing to nearby hostile mobs
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof Mob mob && isHostile(mob)) {
                mob.setTarget(player);
                mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0));

                // Slow enemies if level 9
                if (data.level() >= 9) {
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1));
                }
            }
        }

        player.sendMessage(plugin.getComponent("taunt-activated"));
        player.sendActionBar(Component.text("TAUNT ACTIVE", NamedTextColor.RED));

        // Visual effects
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // Remove taunt effect
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tauntActive.remove(player.getUniqueId());
            player.sendActionBar(Component.empty());
        }, duration);
    }

    // ==================== WARDEN ABILITIES ====================

    private void activateWardenAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateHealingPulse(player, data);
            case "tactical" -> activateReviveBeacon(player, data);
        }
    }

    private void activateHealingPulse(Player player, ClassData data) {
        int cooldown = getCooldown("warden", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);

        int range = 8 + (data.level() >= 3 ? 3 : 0); // 8 base, +3 at level 3
        int healAmount = 40 + (data.level() >= 7 ? 10 : 0); // 40 base, +10 at level 7
        int regenDuration = 100; // 5 seconds

        int healed = 0;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity instanceof Player target && !target.equals(player)) {
                target.heal(healAmount);
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDuration, 1));
                healed++;
                target.sendMessage(Component.text("+ " + healAmount + " HP (Healing Pulse)", NamedTextColor.GREEN));
            }
        }
        // Also heal self
        player.heal(healAmount);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDuration, 1));

        player.sendMessage(plugin.getComponent("healing-pulse"));
        player.sendActionBar(Component.text("HEALING PULSE", NamedTextColor.GREEN));

        // Visual effects
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0),
                40, range / 2.0, 1, range / 2.0, 0.1);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendActionBar(Component.empty()), 60);
    }

    private void activateReviveBeacon(Player player, ClassData data) {
        int cooldown = getCooldown("warden", "tactical", data.level());
        setCooldown(player.getUniqueId(), "tactical", cooldown);

        Location loc = player.getLocation();
        int reviveTime = 60 - (data.level() >= 4 ? 20 : 0); // 3s base, -1s at level 4 (in ticks)

        player.sendMessage(plugin.getComponent("revive-placed"));

        // Place a beacon-like block
        Block beaconBlock = loc.getBlock();
        beaconBlock.setType(Material.BEACON);

        // Particle beacon effect
        player.getWorld().spawnParticle(Particle.END_ROD, loc.add(0.5, 1, 0.5), 100, 0.3, 2, 0.3, 0.05);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

        // Remove beacon after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (beaconBlock.getType() == Material.BEACON) {
                beaconBlock.setType(Material.AIR);
            }
        }, reviveTime);
    }

    // ==================== SPECTER ABILITIES ====================

    private void activateSpecterAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateCloak(player, data);
            case "tactical" -> activateShadowStep(player, data);
        }
    }

    private void activateCloak(Player player, ClassData data) {
        int cooldown = getCooldown("specter", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);

        int duration = 100 + (data.level() >= 3 ? 40 : 0); // 5s base, +2s at level 3

        cloakActive.put(player.getUniqueId(), true);

        // Apply invisibility
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
        // Remove glowing so mobs can't see the player
        player.removePotionEffect(PotionEffectType.GLOWING);

        player.sendMessage(plugin.getComponent("cloak-activated"));
        player.sendActionBar(Component.text("INVISIBLE", NamedTextColor.DARK_PURPLE));

        // Visual effects — smoke particles
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.5f);

        // Remove cloak on damage or attack
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cloakActive.remove(player.getUniqueId());
            player.sendActionBar(Component.empty());
        }, duration);
    }

    private void activateShadowStep(Player player, ClassData data) {
        int cooldown = getCooldown("specter", "tactical", data.level());
        setCooldown(player.getUniqueId(), "tactical", cooldown);

        int range = 10 + (data.level() >= 4 ? 5 : 0); // 10 base, +5 at level 4

        // Ray trace in looking direction
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection().multiply(range);

        // Find first solid block or max range
        Block targetBlock = null;
        try {
            var result = player.getWorld().rayTraceBlocks(eyeLoc, direction, range,
                    FluidCollisionMode.NEVER, true);
            if (result != null) {
                targetBlock = result.getHitBlock();
            }
        } catch (Exception ignored) {
            // Ray trace failed — use max range
        }

        Location destination;
        if (targetBlock != null) {
            destination = targetBlock.getLocation().add(0.5, 1, 0.5);
        } else {
            destination = eyeLoc.add(direction);
        }

        // Ensure destination is safe
        destination.setY(destination.getWorld().getHighestBlockYAt(destination) + 1);

        // Teleport with particles
        Location startLoc = player.getLocation().clone();
        player.getWorld().spawnParticle(Particle.SMOKE, startLoc.add(0, 1, 0), 15, 0.2, 0.3, 0.2, 0.05);

        player.teleport(destination);

        player.getWorld().spawnParticle(Particle.SMOKE, destination.add(0, 1, 0), 15, 0.2, 0.3, 0.2, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.sendMessage(plugin.getComponent("shadow-step"));
    }

    // ==================== OPERATOR ABILITIES ====================

    private void activateOperatorAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateTurretDeploy(player, data);
            case "tactical" -> activateEMPGrenade(player, data);
        }
    }

    private void activateTurretDeploy(Player player, ClassData data) {
        int cooldown = getCooldown("operator", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);

        int duration = 300 + (data.level() >= 3 ? 100 : 0); // 15s base, +5s at level 3
        int damage = 5 + (data.level() >= 6 ? 2 : 0); // 5 base, +2 at level 6

        Location turretLoc = player.getLocation().add(player.getLocation().getDirection().multiply(3).setY(0));

        // Create turret — armor stand with dispenser head
        ArmorStand turret = player.getWorld().spawn(turretLoc, ArmorStand.class);
        turret.setCustomName("§bTURRET");
        turret.setCustomNameVisible(true);
        turret.setGravity(false);
        turret.setInvulnerable(true);
        turret.setInvisible(true);
        turret.getEquipment().setHelmet(new ItemStack(Material.DISPENSER));

        // Set metadata for identification
        turret.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "turret_owner"),
                PersistentDataType.STRING,
                player.getUniqueId().toString());
        turret.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "turret_damage"),
                PersistentDataType.INTEGER,
                damage);

        player.sendMessage(plugin.getComponent("turret-placed"));
        player.sendActionBar(Component.text("TURRET DEPLOYED", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

        // Turret shooting loop
        final int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticks[0]++;
            if (ticks[0] > duration || turret.isDead()) {
                turret.remove();
                task.cancel();
                return;
            }

            // Find nearest hostile mob
            Mob target = null;
            double closestDist = 15; // 15 block range
            for (Entity entity : turret.getNearbyEntities(15, 15, 15)) {
                if (entity instanceof Mob mob && isHostile(mob)) {
                    double dist = mob.getLocation().distance(turret.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = mob;
                    }
                }
            }

            if (target != null && ticks[0] % 20 == 0) { // Shoot every second
                // Arrow projectile
                Location eyeLoc = turret.getEyeLocation();
                Vector dir = target.getEyeLocation().subtract(eyeLoc).toVector().normalize();
                Arrow arrow = turret.getWorld().spawnArrow(eyeLoc, dir, 2.0f, 0.0f);
                arrow.setShooter(player);
                arrow.setDamage(damage);

                turret.getWorld().spawnParticle(Particle.SMOKE, turret.getLocation().add(0, 2.2, 0),
                        3, 0.1, 0.1, 0.1, 0.02);
                turret.getWorld().playSound(turret.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.5f, 1.5f);
            }
        }, 1L, 1L);

        // Remove turret after duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!turret.isDead()) {
                turret.remove();
                turret.getWorld().spawnParticle(Particle.CLOUD, turret.getLocation().add(0, 1, 0),
                        10, 0.3, 0.3, 0.3, 0.02);
            }
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, duration);
    }

    private void activateEMPGrenade(Player player, ClassData data) {
        int cooldown = getCooldown("operator", "tactical", data.level());
        setCooldown(player.getUniqueId(), "tactical", cooldown);

        int range = 6 + (data.level() >= 4 ? 3 : 0); // 6 base, +3 at level 4
        int duration = 100 + (data.level() >= 7 ? 40 : 0); // 5s base, +2s at level 7

        // Throw grenade — snowball
        Snowball grenade = player.launchProjectile(Snowball.class);
        grenade.setVelocity(player.getLocation().getDirection().multiply(2));
        grenade.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "emp_grenade"),
                PersistentDataType.BOOLEAN,
                true);
        grenade.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "emp_range"),
                PersistentDataType.INTEGER,
                range);
        grenade.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "emp_duration"),
                PersistentDataType.INTEGER,
                duration);

        grenade.setItem(new ItemStack(Material.ENDER_PEARL));
        player.sendMessage(plugin.getComponent("emp-thrown"));
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.2f);
    }

    // ==================== PASSIVE ABILITIES ====================

    // Vanguard Trait 1: Ironclad — knockback resistance while holding shield
    @EventHandler
    public void onVanguardKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isClass(player, "vanguard")) return;
        if (!hasShield(player)) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 1) return;

        // Reduce knockback
        event.setDamage(event.getDamage() * 0.5);
    }

    // Vanguard Trait 2: Last Stand — damage resistance when low HP
    @EventHandler
    public void onVanguardLastStand(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isClass(player, "vanguard")) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 6) return;

        if (player.getHealth() <= player.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3) {
            UUID uuid = player.getUniqueId();
            Long lastUsed = lastStandCooldown.get(uuid);
            long now = System.currentTimeMillis();

            if (lastUsed == null || now - lastUsed > 60000) { // 60s cooldown
                lastStandCooldown.put(uuid, now);
                int duration = 100 + (data.level() >= 6 ? 40 : 0); // 5s base, +2s at level 6
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1));
                player.sendActionBar(Component.text("LAST STAND", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            }
        }
    }

    // Warden Trait 1: Mend — food heals nearby allies
    @EventHandler
    public void onWardenMend(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!isClass(player, "warden")) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 3) return;

        UUID uuid = player.getUniqueId();
        Long lastUsed = mendCooldown.get(uuid);
        long now = System.currentTimeMillis();

        if (lastUsed == null || now - lastUsed > 5000) { // 5s internal cooldown
            mendCooldown.put(uuid, now);

            for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
                if (entity instanceof Player target && !target.equals(player)) {
                    target.heal(10);
                    if (data.level() >= 5) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0));
                    }
                    target.sendMessage(Component.text("+ 10 HP (Mend)", NamedTextColor.GREEN));
                }
            }
        }
    }

    // Warden Trait 2: Vigilance — see ally health (handled in a tick task in GlitchClasses onEnable)
    // This is handled separately — we'll add a ticker later

    // Specter Trait 1: Lightweight — movement speed
    @EventHandler
    public void onSpecterSpeed(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isClass(player, "specter")) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 1) return;

        // Apply speed effect if not already active
        if (!player.hasPotionEffect(PotionEffectType.SPEED)) {
            int level = data.level() >= 7 ? 1 : 0; // Speed II at level 7, Speed I otherwise
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, level, true, false));
        }
    }

    // Specter Trait 1: Lightweight — fall damage reduction
    @EventHandler
    public void onSpecterFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isClass(player, "specter")) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 1) return;

        event.setDamage(event.getDamage() * 0.9); // 10% reduction
    }

    // Specter Trait 2: Scavenge — +loot (handled in GlitchLoot plugin, metadata-based)

    // Operator Trait 1: Engineer — repair turrets on right-click
    @EventHandler
    public void onOperatorRepair(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isClass(player, "operator")) return;

        // Check for nearby turrets and repair
        // Simplified — just check if the player is holding a wrench-like item
    }

    // ==================== UTILITY METHODS ====================

    private boolean isClass(Player player, String className) {
        ClassData data = classManager.getClassData(player.getUniqueId());
        return data.className().equals(className);
    }

    private boolean hasShield(Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        return offhand.getType() == Material.SHIELD;
    }

    private boolean isHostile(Mob mob) {
        if (mob instanceof Zombie) return true;
        if (mob instanceof Skeleton) return true;
        if (mob instanceof Creeper) return true;
        if (mob instanceof Spider) return true;
        if (mob instanceof Enderman) return true;
        if (mob instanceof Witch) return true;
        if (mob instanceof Vindicator) return true;
        if (mob instanceof Pillager) return true;
        if (mob instanceof Vex) return true;
        // Check for MythicMobs by display name
        if (mob.getCustomName() != null) {
            String name = mob.getCustomName();
            return name.contains("Glitch") || name.contains("Corrupted");
        }
        return false;
    }

    private int getCooldown(String className, String abilityType, int level) {
        int baseCooldown = plugin.getConfig().getInt("abilities." + className + "." + abilityType + ".cooldown", 20);
        int reduction = plugin.getConfig().getInt("cooldown-reduction-per-level", 3);
        return Math.max(5, baseCooldown - (level * reduction));
    }

    private void setCooldown(UUID uuid, String ability, int seconds) {
        cooldowns.computeIfAbsent(uuid, k -> new HashMap<>())
                .put(ability, System.currentTimeMillis() + (seconds * 1000L));
    }

    private boolean isOnCooldown(UUID uuid, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return false;
        Long expiry = playerCooldowns.get(ability);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    private long getCooldownRemaining(UUID uuid, String ability) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0;
        Long expiry = playerCooldowns.get(ability);
        if (expiry == null) return 0;
        return Math.max(0, expiry - System.currentTimeMillis());
    }
}
