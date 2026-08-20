package com.theglitch.glitchclasses;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handles all class abilities — activation, cooldowns, effects.
 * Keybind activation (F, Sneak+F, Sneak+Q) for prime/tactical/ultimate abilities.
 * Event-based passive abilities (traits).
 */
public class AbilityListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String SCAVENGE_TAG = "specter_scavenge";

    private final GlitchClasses plugin;
    private final ClassManager classManager;
    private final Map<String, Integer> baseCooldowns = new HashMap<>();
    private final Map<String, Component> keyHintCache = new HashMap<>();
    private volatile int cooldownReduction = 2;
    private volatile int cooldownFloor = 12;
    private volatile int ultimateLevel = 10;
    private volatile Set<String> gameWorlds = Set.of("glitch_pve", "glitch_red");
    private volatile Component lastVigilanceBar = Component.empty();

    // Cooldown tracking: UUID -> ability name -> expiry timestamp
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    // Active effects tracking
    private final Map<UUID, Boolean> shieldWallActive = new HashMap<>();
    private final Map<UUID, Boolean> cloakActive = new HashMap<>();
    private final Map<UUID, Boolean> tauntActive = new HashMap<>();
    private final Map<UUID, List<Block>> turretBlocks = new HashMap<>();

    // Turret lifecycle (owned armor stands) — used by Engineer repair + Cataclysm
    private final Map<UUID, ArmorStand> turrets = new HashMap<>();
    private final Map<UUID, BukkitTask> turretTasks = new HashMap<>();
    private final Map<UUID, Long> turretExpiry = new HashMap<>();
    private final Map<UUID, Integer> turretRepairs = new HashMap<>();
    private final Map<UUID, Long> repairCooldown = new HashMap<>();

    // Guardian Angel (Warden ultimate) — protected players and post-save invulnerability
    private final Map<UUID, Long> guardianProtection = new HashMap<>();
    private final Map<UUID, Long> deathInvuln = new HashMap<>();

    // Ghost Protocol (Specter ultimate) — untargetable window
    private final Map<UUID, Long> ghostUntil = new HashMap<>();

    // Passive cooldowns (for traits with internal cooldowns)
    private final Map<UUID, Long> lastStandCooldown = new HashMap<>();
    private final Map<UUID, Long> mendCooldown = new HashMap<>();

    // NamespacedKey for turret / grenade identification
    private final NamespacedKey turretOwnerKey;
    private final NamespacedKey turretDamageKey;
    private final NamespacedKey turretBaseExpiryKey;
    private final NamespacedKey empGrenadeKey;
    private final NamespacedKey empRangeKey;
    private final NamespacedKey empDurationKey;

    public AbilityListener(GlitchClasses plugin, ClassManager classManager) {
        this.plugin = plugin;
        this.classManager = classManager;
        this.turretOwnerKey = new NamespacedKey(plugin, "turret_owner");
        this.turretDamageKey = new NamespacedKey(plugin, "turret_damage");
        this.turretBaseExpiryKey = new NamespacedKey(plugin, "turret_base_expiry");
        this.empGrenadeKey = new NamespacedKey(plugin, "emp_grenade");
        this.empRangeKey = new NamespacedKey(plugin, "emp_range");
        this.empDurationKey = new NamespacedKey(plugin, "emp_duration");
        reloadConfig();
    }

    public void reloadConfig() {
        cooldownReduction = plugin.getConfig().getInt("cooldown-reduction-per-level", 2);
        cooldownFloor = plugin.getConfig().getInt("cooldown-floor", 12);
        ultimateLevel = plugin.getConfig().getInt("ultimate-level", 10);
        List<String> worlds = plugin.getConfig().getStringList("game-worlds");
        if (worlds == null || worlds.isEmpty()) worlds = List.of("glitch_pve", "glitch_red");
        gameWorlds = Set.copyOf(worlds);
        baseCooldowns.clear();
        keyHintCache.clear();
        lastVigilanceBar = Component.empty();
        for (String cls : new String[]{"vanguard", "warden", "specter", "operator"}) {
            for (String type : new String[]{"prime", "tactical", "ultimate"}) {
                String key = cls + "." + type;
                int base = plugin.getConfig().getInt("abilities." + cls + "." + type + ".cooldown", 20);
                baseCooldowns.put(key, base);
            }
        }
        for (String cls : new String[]{"vanguard", "warden", "specter", "operator"}) {
            String prime = plugin.getConfig().getString("abilities." + cls + ".prime.name", "Prime");
            String tactical = plugin.getConfig().getString("abilities." + cls + ".tactical.name", "Tactical");
            String ultimate = plugin.getConfig().getString("abilities." + cls + ".ultimate.name", "Ultimate");
            Component hint = Component.text("F ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(prime, NamedTextColor.WHITE))
                    .append(Component.text("  Sneak+F ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(tactical, NamedTextColor.WHITE))
                    .append(Component.text("  Sneak+Q ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(ultimate, NamedTextColor.WHITE));
            keyHintCache.put(cls, hint);
        }
    }

    // Activation gate + routing shared by all keybind handlers
    private void tryActivate(Player player, String type) {
        UUID uuid = player.getUniqueId();
        ClassData data = classManager.getClassData(uuid);
        if (data.className().equals("none")) return;

        // Ultimates require level 10
        if (type.equals("ultimate") && data.level() < getUltimateLevel()) {
            player.sendMessage(plugin.getComponent("ultimate-locked",
                    "<level>", String.valueOf(getUltimateLevel())));
            return;
        }

        // Check cooldown
        if (isOnCooldown(uuid, type)) {
            long remaining = getCooldownRemaining(uuid, type);
            player.sendMessage(plugin.getComponent("ability-cooldown", "<seconds>", String.valueOf(remaining / 1000)));
            return;
        }

        switch (data.className()) {
            case "vanguard" -> activateVanguardAbility(player, type, data);
            case "warden" -> activateWardenAbility(player, type, data);
            case "specter" -> activateSpecterAbility(player, type, data);
            case "operator" -> activateOperatorAbility(player, type, data);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!gameWorlds.contains(player.getWorld().getName())) return;
        event.setCancelled(true);
        tryActivate(player, player.isSneaking() ? "tactical" : "prime");
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!gameWorlds.contains(player.getWorld().getName())) return;
        if (!player.isSneaking()) return;
        event.setCancelled(true);
        tryActivate(player, "ultimate");
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!gameWorlds.contains(player.getWorld().getName())) return;
        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.className().equals("none")) return;
        player.sendActionBar(keyHint(data.className()));
    }

    private Component keyHint(String className) {
        Component cached = keyHintCache.get(className);
        if (cached != null) return cached;
        String prime = plugin.getConfig().getString("abilities." + className + ".prime.name", "Prime");
        String tactical = plugin.getConfig().getString("abilities." + className + ".tactical.name", "Tactical");
        String ultimate = plugin.getConfig().getString("abilities." + className + ".ultimate.name", "Ultimate");
        Component hint = Component.text("F ", NamedTextColor.DARK_GRAY)
                .append(Component.text(prime, NamedTextColor.WHITE))
                .append(Component.text("  Sneak+F ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tactical, NamedTextColor.WHITE))
                .append(Component.text("  Sneak+Q ", NamedTextColor.DARK_GRAY))
                .append(Component.text(ultimate, NamedTextColor.WHITE));
        keyHintCache.put(className, hint);
        return hint;
    }

    // ==================== VANGUARD ABILITIES ====================

    private void activateVanguardAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateShieldWall(player, data);
            case "tactical" -> activateTaunt(player, data);
            case "ultimate" -> activateFortress(player, data);
        }
    }

    private void activateShieldWall(Player player, ClassData data) {
        int cooldown = getCooldown("vanguard", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);
        placeShieldWall(player, data);
        player.sendMessage(plugin.getComponent("shield-wall-placed"));
        player.sendActionBar(Component.text("SHIELD WALL ACTIVE", NamedTextColor.RED));
    }

    private void placeShieldWall(Player player, ClassData data) {
        int duration = 600 + (data.level() >= 6 ? 40 : 0); // 30s base, +2s at level 6

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
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, duration);
    }

    // Vanguard ultimate: Fortress — indestructible wall + heavy ally resistance
    private void activateFortress(Player player, ClassData data) {
        int cooldown = getCooldown("vanguard", "ultimate", data.level());
        setCooldown(player.getUniqueId(), "ultimate", cooldown);
        placeShieldWall(player, data);
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player ally && !ally.equals(player)) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 3));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 3));
        player.sendMessage(plugin.getComponent("fortress-active"));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.6f);
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
            case "ultimate" -> activateGuardianAngel(player, data);
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
        int channelTicks = 60 - (data.level() >= 4 ? 20 : 0); // 3s base, -1s at level 4

        player.sendMessage(plugin.getComponent("revive-placed"));

        // Place a beacon-like block
        Block beaconBlock = loc.getBlock();
        beaconBlock.setType(Material.BEACON);

        // Particle beacon effect — clone so the delayed ally search below
        // still targets the beacon position (add() would mutate loc).
        player.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0.5, 1, 0.5), 100, 0.3, 2, 0.3, 0.05);
        player.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);

        // After the channel completes, surge-heal the most injured allies nearby
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int maxTargets = data.level() >= 8 ? 2 : 1; // level 8 upgrade: can reach 2 allies
            List<Player> allies = new ArrayList<>();
            for (Entity entity : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
                if (entity instanceof Player target && !target.equals(player) && !target.isDead()) {
                    allies.add(target);
                }
            }
            allies.sort(Comparator.comparingDouble(Player::getHealth));

            int healed = 0;
            for (Player target : allies) {
                if (healed >= maxTargets) break;
                double max = target.getAttribute(Attribute.MAX_HEALTH).getValue();
                target.setHealth(max);
                target.removePotionEffect(PotionEffectType.POISON);
                target.removePotionEffect(PotionEffectType.WITHER);
                target.removePotionEffect(PotionEffectType.SLOWNESS);
                target.removePotionEffect(PotionEffectType.WEAKNESS);
                target.removePotionEffect(PotionEffectType.HUNGER);
                target.removePotionEffect(PotionEffectType.MINING_FATIGUE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                healed++;
            }
            if (healed > 0 && player.isOnline()) {
                player.sendMessage(plugin.getComponent("revive-healed"));
            }
            if (beaconBlock.getType() == Material.BEACON) {
                beaconBlock.setType(Material.AIR);
            }
        }, channelTicks);
    }

    // Warden ultimate: Guardian Angel — next fatal blow spares a protected ally
    private void activateGuardianAngel(Player player, ClassData data) {
        int cooldown = getCooldown("warden", "ultimate", data.level());
        setCooldown(player.getUniqueId(), "ultimate", cooldown);

        long until = System.currentTimeMillis() + 30_000L;
        guardianProtection.put(player.getUniqueId(), until);
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player ally && !ally.equals(player)) {
                guardianProtection.put(ally.getUniqueId(), until);
                ally.sendMessage(plugin.getComponent("guardian-protected"));
            }
        }
        player.sendMessage(plugin.getComponent("ultimate-activated"));
        player.sendActionBar(Component.text("GUARDIAN ANGEL ACTIVE", NamedTextColor.GOLD));
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 60, 0.5, 2, 0.5, 0.05);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
    }

    @EventHandler
    public void onDeathInvuln(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Long until = deathInvuln.get(uuid);
        if (until == null) return;
        if (until < System.currentTimeMillis()) {
            deathInvuln.remove(uuid);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onGuardianSave(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        Long until = guardianProtection.get(uuid);
        if (until == null || until < System.currentTimeMillis()) {
            guardianProtection.remove(uuid);
            return;
        }
        // Only trigger on a blow that would kill the player
        if (event.getFinalDamage() < player.getHealth() - 0.001) return;

        guardianProtection.remove(uuid);
        event.setCancelled(true);
        player.setHealth(1.0);
        deathInvuln.put(uuid, System.currentTimeMillis() + 3000L);
        player.sendMessage(plugin.getComponent("guardian-saved"));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
    }

    // ==================== SPECTER ABILITIES ====================

    private void activateSpecterAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateCloak(player, data);
            case "tactical" -> activateShadowStep(player, data);
            case "ultimate" -> activateGhostProtocol(player, data);
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

    // Specter ultimate: Ghost Protocol — 10s undetectable, 2x speed
    private void activateGhostProtocol(Player player, ClassData data) {
        int cooldown = getCooldown("specter", "ultimate", data.level());
        setCooldown(player.getUniqueId(), "ultimate", cooldown);

        int duration = 200; // 10 seconds
        ghostUntil.put(player.getUniqueId(), System.currentTimeMillis() + duration * 50L);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2)); // ~+40% speed
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0)); // brief casting flash only

        player.sendMessage(plugin.getComponent("ghost-protocol"));
        player.sendActionBar(Component.text("GHOST PROTOCOL", NamedTextColor.DARK_PURPLE));
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 40, 0.4, 1, 0.4, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.8f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ghostUntil.remove(player.getUniqueId());
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }, duration);
    }

    // ==================== OPERATOR ABILITIES ====================

    private void activateOperatorAbility(Player player, String type, ClassData data) {
        switch (type) {
            case "prime" -> activateTurretDeploy(player, data);
            case "tactical" -> activateEMPGrenade(player, data);
            case "ultimate" -> activateCataclysm(player, data);
        }
    }

    private void activateTurretDeploy(Player player, ClassData data) {
        int cooldown = getCooldown("operator", "prime", data.level());
        setCooldown(player.getUniqueId(), "prime", cooldown);

        int duration = 300 + (data.level() >= 3 ? 100 : 0); // 15s base, +5s at level 3
        int damage = 5 + (data.level() >= 6 ? 2 : 0); // 5 base, +2 at level 6

        spawnTurret(player, damage, duration);
    }

    private void spawnTurret(Player player, int damage, int durationTicks) {
        UUID uuid = player.getUniqueId();
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
        long baseExpiry = System.currentTimeMillis() + durationTicks * 50L;
        turret.getPersistentDataContainer().set(turretOwnerKey, PersistentDataType.STRING, uuid.toString());
        turret.getPersistentDataContainer().set(turretDamageKey, PersistentDataType.INTEGER, damage);
        turret.getPersistentDataContainer().set(turretBaseExpiryKey, PersistentDataType.LONG, baseExpiry);

        // Track lifecycle for Engineer repair + Cataclysm
        turrets.put(uuid, turret);
        turretExpiry.put(uuid, baseExpiry);
        turretRepairs.put(uuid, 0);

        player.sendMessage(plugin.getComponent("turret-placed"));
        player.sendActionBar(Component.text("TURRET DEPLOYED", NamedTextColor.AQUA));
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.0f);

        startTurretFireLoop(player, turret);
        scheduleTurretRemoval(uuid, durationTicks);
    }

    private void startTurretFireLoop(Player player, ArmorStand turret) {
        UUID uuid = player.getUniqueId();
        int damage = turret.getPersistentDataContainer().getOrDefault(turretDamageKey, PersistentDataType.INTEGER, 5);
        int rate = turretFireRate(uuid); // Resonance Surge: faster at level 3+, +50% at level 9

        final int[] ticks = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            ticks[0]++;
            Long expiry = turretExpiry.get(uuid);
            if (turret.isDead() || expiry == null || System.currentTimeMillis() >= expiry) {
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

            if (target != null && ticks[0] % rate == 0) {
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
    }

    private int turretFireRate(UUID uuid) {
        int level = classManager.getClassData(uuid).level();
        if (level >= 9) return 10; // Overclock +50%
        if (level >= 3) return 15; // Resonance Surge +25%
        return 20;
    }

    private void scheduleTurretRemoval(UUID uuid, long delayTicks) {
        BukkitTask old = turretTasks.remove(uuid);
        if (old != null) {
            old.cancel();
        }
        ArmorStand turret = turrets.get(uuid);
        if (turret == null) return;

        turretTasks.put(uuid, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            turretTasks.remove(uuid);
            turrets.remove(uuid);
            turretExpiry.remove(uuid);
            turretRepairs.remove(uuid);
            if (!turret.isDead()) {
                turret.remove();
                turret.getWorld().spawnParticle(Particle.CLOUD, turret.getLocation().add(0, 1, 0),
                        10, 0.3, 0.3, 0.3, 0.02);
            }
            Player owner = Bukkit.getPlayer(uuid);
            if (owner != null && owner.isOnline()) {
                owner.sendActionBar(Component.empty());
            }
        }, delayTicks));
    }

    // Operator ultimate: Cataclysm — turret detonates for 80 damage, deploys a new one
    private void activateCataclysm(Player player, ClassData data) {
        int cooldown = getCooldown("operator", "ultimate", data.level());
        setCooldown(player.getUniqueId(), "ultimate", cooldown);

        UUID uuid = player.getUniqueId();
        ArmorStand turret = turrets.get(uuid);
        if (turret != null && !turret.isDead()) {
            turret.remove();
            turrets.remove(uuid);
            turretExpiry.remove(uuid);
            BukkitTask task = turretTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }
        }

        // Explosion: 80 damage to all hostiles within 10 blocks
        int killed = 0;
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Mob mob && isHostile(mob)) {
                mob.damage(80, player);
                killed++;
            }
        }
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 1);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Deploy a fresh construct at full power
        spawnTurret(player, 5 + (data.level() >= 6 ? 2 : 0), 300 + (data.level() >= 3 ? 100 : 0));
        player.sendMessage(plugin.getComponent("cataclysm"));
    }

    // Operator trait 1: Engineer — right-click your turret to extend its duration
    @EventHandler
    public void onOperatorRepair(PlayerInteractEntityEvent event) {
        // Fires once per hand — only act on the main hand.
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!isClass(player, "operator")) return;
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        String owner = pdc.get(turretOwnerKey, PersistentDataType.STRING);
        if (owner == null || !owner.equals(player.getUniqueId().toString())) return;

        event.setCancelled(true);
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long last = repairCooldown.get(uuid);
        if (last != null && now - last < 30_000L) {
            player.sendMessage(plugin.getComponent("turret-repair-cooldown",
                    "<seconds>", String.valueOf((30_000L - (now - last)) / 1000L)));
            return;
        }
        Integer repairs = turretRepairs.get(uuid);
        if (repairs != null && repairs >= 3) {
            player.sendMessage(Component.text("Construct fully reinforced.", NamedTextColor.YELLOW));
            return;
        }
        Long expiry = turretExpiry.get(uuid);
        if (expiry == null) return;

        repairCooldown.put(uuid, now);
        long base = pdc.getOrDefault(turretBaseExpiryKey, PersistentDataType.LONG, now);
        long cap = base + 15_000L; // Engineer can extend a construct by up to 15s total
        long remaining = Math.max(0L, expiry - now);
        long extended = Math.min(now + remaining + 5_000L, cap);
        if (extended <= now) return;

        turretExpiry.put(uuid, extended);
        turretRepairs.put(uuid, (repairs == null ? 0 : repairs) + 1);

        scheduleTurretRemoval(uuid, Math.max(1L, (extended - now) / 50L));
        player.sendMessage(plugin.getComponent("turret-repaired"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    }

    private void activateEMPGrenade(Player player, ClassData data) {
        int cooldown = getCooldown("operator", "tactical", data.level());
        setCooldown(player.getUniqueId(), "tactical", cooldown);

        int range = 6 + (data.level() >= 4 ? 3 : 0); // 6 base, +3 at level 4
        int duration = 100 + (data.level() >= 3 ? 40 : 0); // 5s base, +2s at level 3 (Resonance Surge)

        // Throw grenade — snowball
        Snowball grenade = player.launchProjectile(Snowball.class);
        grenade.setVelocity(player.getLocation().getDirection().multiply(2));
        grenade.getPersistentDataContainer().set(empGrenadeKey, PersistentDataType.BOOLEAN, true);
        grenade.getPersistentDataContainer().set(empRangeKey, PersistentDataType.INTEGER, range);
        grenade.getPersistentDataContainer().set(empDurationKey, PersistentDataType.INTEGER, duration);

        grenade.setItem(new ItemStack(Material.ENDER_PEARL));
        player.sendMessage(plugin.getComponent("emp-thrown"));
        player.playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.2f);
    }

    @EventHandler
    public void onEMPImpact(ProjectileHitEvent event) {
        PersistentDataContainer pdc = event.getEntity().getPersistentDataContainer();
        if (!pdc.has(empGrenadeKey, PersistentDataType.BOOLEAN)) return;

        int range = pdc.getOrDefault(empRangeKey, PersistentDataType.INTEGER, 6);
        int duration = pdc.getOrDefault(empDurationKey, PersistentDataType.INTEGER, 100);
        Location loc = event.getEntity().getLocation();

        for (Entity entity : loc.getWorld().getNearbyEntities(loc, range, range, range)) {
            if (entity instanceof Mob mob && isHostile(mob)) {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 0));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1));
                mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0));
            }
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);

        Player thrower = event.getEntity().getShooter() instanceof Player p ? p : null;
        if (thrower != null) {
            thrower.sendMessage(plugin.getComponent("emp-detonated"));
        }
    }

    // ==================== PASSIVE ABILITIES ====================

    // Vanguard Trait 1: Ironclad — knockback resistance while holding shield
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanguardKnockback(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isClass(player, "vanguard")) return;
        if (!hasShield(player)) return;

        // Paper 1.21.4 does not expose knockback getters on this event. Apply
        // the passive after vanilla knockback has been calculated, preserving
        // vertical lift while halving horizontal displacement.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            Vector velocity = player.getVelocity();
            player.setVelocity(new Vector(velocity.getX() * 0.5, velocity.getY(), velocity.getZ() * 0.5));
        });
    }

    // Vanguard Trait 2: Last Stand — damage resistance when low HP
    @EventHandler
    public void onVanguardLastStand(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isClass(player, "vanguard")) return;

        ClassData data = classManager.getClassData(player.getUniqueId());
        if (data.level() < 3) return; // trait2 unlock level

        if (player.getHealth() <= player.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3) {
            UUID uuid = player.getUniqueId();
            Long lastUsed = lastStandCooldown.get(uuid);
            long now = System.currentTimeMillis();

            if (lastUsed == null || now - lastUsed > 60000) { // 60s cooldown
                lastStandCooldown.put(uuid, now);
                int duration = 100 + (data.level() >= 6 ? 40 : 0); // 5s base, +2s at level 6
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1));
                if (data.level() >= 7) { // level 7: +10% damage while Last Stand active
                    player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0));
                }
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

    // Warden Trait 2: Vigilance — ally health through walls (ticker in startTickers)

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

    // Specter Trait 2: Scavenge — +loot (GlitchItems reads the specter_scavenge
    // scoreboard tag; synced in startTickers)

    // Operator Trait 1: Engineer — right-click repair (handled in onOperatorRepair)
    // Operator Trait 2: Resonance Surge — turret fire rate + EMP duration
    //                    (applied in turretFireRate and activateEMPGrenade)

    // Cloak breaks when the specter attacks or takes damage
    @EventHandler
    public void onCloakBreak(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker
                && cloakActive.remove(attacker.getUniqueId()) != null) {
            attacker.removePotionEffect(PotionEffectType.INVISIBILITY);
            attacker.sendMessage(plugin.getComponent("cloak-broken"));
        }
        if (event.getEntity() instanceof Player victim
                && cloakActive.remove(victim.getUniqueId()) != null) {
            victim.removePotionEffect(PotionEffectType.INVISIBILITY);
            victim.sendMessage(plugin.getComponent("cloak-broken"));
        }
    }

    /**
     * Periodic trait tickers:
     * - syncs the specter_scavenge scoreboard tag (Scavenge, level 3+)
     * - Warden Vigilance: ally health through walls (action bar, level 3+)
     * - Ghost Protocol: hostiles cannot target the specter
     */
    public void startTickers() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ClassData data = classManager.getClassData(player.getUniqueId());

                // Scavenge tag sync — only mutate when state flips (saves NBT + packet)
                boolean shouldHave = data.className().equals("specter") && data.level() >= 3;
                boolean has = player.getScoreboardTags().contains(SCAVENGE_TAG);
                if (shouldHave != has) {
                    if (shouldHave) player.addScoreboardTag(SCAVENGE_TAG);
                    else player.removeScoreboardTag(SCAVENGE_TAG);
                }

                // Ghost Protocol — clear hostiles' targets while active
                Long ghost = ghostUntil.get(player.getUniqueId());
                if (ghost != null) {
                    if (ghost < now) {
                        ghostUntil.remove(player.getUniqueId());
                    } else {
                        for (Entity entity : player.getNearbyEntities(16, 16, 16)) {
                            if (entity instanceof Mob mob && isHostile(mob)) {
                                mob.setTarget(null);
                            }
                        }
                    }
                }

                // Vigilance — warden sees ally health through walls (level 3+)
                if (!data.className().equals("warden") || data.level() < 3) continue;
                if (!gameWorlds.contains(player.getWorld().getName())) continue;

                List<Player> allies = new ArrayList<>();
                for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                    if (entity instanceof Player ally && !ally.equals(player)) {
                        allies.add(ally);
                    }
                }
                if (allies.isEmpty()) {
                    if (!lastVigilanceBar.equals(Component.empty())) {
                        lastVigilanceBar = Component.empty();
                        player.sendActionBar(Component.empty());
                    }
                    continue;
                }
                allies.sort(Comparator.comparingDouble(a -> a.getLocation().distanceSquared(player.getLocation())));

                Component bar = Component.empty();
                int shown = 0;
                for (Player ally : allies) {
                    if (shown >= 3) break;
                    double max = ally.getAttribute(Attribute.MAX_HEALTH).getValue();
                    bar = bar.append(Component.text(ally.getName(), NamedTextColor.GREEN))
                            .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                            .append(Component.text((int) Math.max(0, ally.getHealth()), NamedTextColor.WHITE))
                            .append(Component.text("/", NamedTextColor.DARK_GRAY))
                            .append(Component.text((int) max, NamedTextColor.WHITE))
                            .append(Component.text("  ", NamedTextColor.DARK_GRAY));
                    shown++;
                }
                if (!bar.equals(lastVigilanceBar)) {
                    lastVigilanceBar = bar;
                    player.sendActionBar(bar);
                }
            }
        }, 40L, 20L);
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
        int baseCooldown = baseCooldowns.getOrDefault(className + "." + abilityType, 20);
        return Math.max(cooldownFloor, baseCooldown - (level * cooldownReduction));
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

    private int getUltimateLevel() {
        return ultimateLevel;
    }
}
