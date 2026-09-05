package com.theglitch.glitchitems;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {

    /** Prebuilt scoreboard tags per resonance — avoids per-hit concat/lower. */
    private static final Map<Resonance, String> RESONANCE_TAG_COLON = new EnumMap<>(Resonance.class);
    private static final Map<Resonance, String> RESONANCE_TAG_UNDERSCORE = new EnumMap<>(Resonance.class);

    static {
        for (Resonance resonance : Resonance.values()) {
            String lower = resonance.name().toLowerCase(java.util.Locale.ROOT);
            RESONANCE_TAG_COLON.put(resonance, "res:" + lower);
            RESONANCE_TAG_UNDERSCORE.put(resonance, "res_" + lower);
        }
    }

    private record PendingSideEffects(Player attacker, LivingEntity victim, double healed, double maxHealth, int fireTicks, double selfDamage) {}
    private record PendingReflect(Player defender, LivingEntity attacker, double amount) {}

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private final ResidualGlitchManager glitchManager;
    private final Map<EntityDamageByEntityEvent, PendingSideEffects> pendingSideEffects = new ConcurrentHashMap<>();
    private final Map<EntityDamageByEntityEvent, PendingReflect> pendingReflect = new ConcurrentHashMap<>();
    // Veil Tether yank spam guard: attacker UUID -> last pull epoch-ms
    private final Map<java.util.UUID, Long> tetherCooldown = new ConcurrentHashMap<>();

    public CombatListener(GlitchItems plugin, GearManager gearManager, ResidualGlitchManager glitchManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        this.glitchManager = glitchManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        double damage = event.getDamage();

        if (event.getDamager() instanceof Player attacker) {
            damage = applyWeaponModifiers(attacker, victim, damage, event);
        }

        if (victim instanceof Player defender) {
            damage = applyDefenseModifiers(defender, event.getDamager(), damage, event);
        }

        event.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageMonitor(EntityDamageByEntityEvent event) {
        PendingSideEffects pending = pendingSideEffects.remove(event);
        if (pending != null && !event.isCancelled()) {
            if (pending.healed() > 0.0) {
                pending.attacker().setHealth(Math.min(pending.attacker().getHealth() + pending.healed(), pending.maxHealth()));
            }
            if (pending.fireTicks() > 0) {
                pending.victim().setFireTicks(pending.fireTicks());
            }
            // Blood price (Dream Eater): true HP cost, never suicides — floors at 1 HP.
            if (pending.selfDamage() > 0.0 && !pending.attacker().isDead()) {
                double hp = pending.attacker().getHealth() - pending.selfDamage();
                pending.attacker().setHealth(Math.max(1.0, Math.min(hp, pending.maxHealth())));
            }
        }
        PendingReflect reflect = pendingReflect.remove(event);
        if (reflect != null && !event.isCancelled()) {
            // damage(double) without source fires a generic EntityDamageEvent — no thorns recursion.
            reflect.attacker().damage(reflect.amount());
        }
    }

    private double applyWeaponModifiers(Player attacker, LivingEntity victim, double damage, EntityDamageByEntityEvent event) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        GearRolls rolls = gearManager.parse(held);
        if (rolls == null || !rolls.type.isWeapon()) return damage;

        double out = damage * (1.0 + rolls.damage / 100.0);

        if (resonanceMatches(victim, rolls.resonance)) {
            out *= 1.0 + (gearManager.weaponResonanceBase() + rolls.boost) / 100.0;
        }

        Map<String, Integer> attributes = gearManager.parseAttributes(rolls.attributes);
        Integer lifesteal = attributes.get("lifesteal");
        double healed = 0.0;
        double maxHp = 0.0;
        if (lifesteal != null && lifesteal > 0) {
            maxHp = maxHealthOf(attacker);
            healed = out * lifesteal / 100.0;
        }
        Integer fire = attributes.get("fire-aspect");
        int fireTicks = fire != null && fire > 0 ? fire * 20 : 0;
        double selfDamage = 0.0;
        // Miser's Maw: hunger scales with Residual Glitch stacks (greed cleaver).
        if (rolls.type == GearType.MISERS_MAW) {
            int stacks = glitchManager.getStacks(attacker);
            if (stacks > 0) {
                out *= 1.0 + stacks * gearManager.miserGreedPerStack() / 100.0;
            }
        }
        // Veil Tether: long-hook bonus + yank victims toward you (extraction control).
        if (rolls.type == GearType.VEIL_TETHER) {
            double dist = 0.0;
            try {
                dist = attacker.getLocation().distance(victim.getLocation());
            } catch (IllegalArgumentException ignored) {
                // Cross-world (shouldn't happen for melee) — skip bonus/pull.
            }
            if (dist >= gearManager.tetherFarDistance()) {
                out *= 1.0 + gearManager.tetherFarBonus() / 100.0;
            }
            long now = System.currentTimeMillis();
            long last = tetherCooldown.getOrDefault(attacker.getUniqueId(), 0L);
            if (now - last >= gearManager.tetherCooldownSeconds() * 1000L) {
                tetherCooldown.put(attacker.getUniqueId(), now);
                try {
                    org.bukkit.util.Vector pull = attacker.getLocation().toVector()
                            .subtract(victim.getLocation().toVector());
                    pull.setY(0);
                    double len = pull.length();
                    if (len > 0.001) {
                        pull.normalize().multiply(gearManager.tetherPullStrength());
                        pull.setY(0.25);
                        victim.setVelocity(pull);
                    }
                } catch (IllegalArgumentException ignored) {
                }
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SLOWNESS, 20, 0));
            }
        }
        // Hollow relic: blood price + reality tear (levitate + wither + rupture fx).
        if (rolls.type == GearType.DREAM_EATER) {
            selfDamage = gearManager.dreamSelfDamage();
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) < gearManager.dreamTearChance()) {
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.LEVITATION, 40, 0));
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.WITHER, 60, 1));
                try {
                    victim.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                            victim.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);
                    victim.getWorld().playSound(victim.getLocation(),
                            org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.4f);
                } catch (Exception ignored) {
                }
            }
        }
        if (healed > 0.0 || fireTicks > 0 || selfDamage > 0.0) {
            if (maxHp <= 0.0) maxHp = maxHealthOf(attacker);
            pendingSideEffects.put(event, new PendingSideEffects(attacker, victim, healed, maxHp, fireTicks, selfDamage));
        }
        // Execute: +% damage vs targets below 30% max HP
        Integer execute = attributes.get("execute");
        if (execute != null && execute > 0) {
            double victimMax = maxHealthOf(victim);
            if (victimMax > 0 && victim.getHealth() < victimMax * 0.3) {
                out *= 1.0 + execute / 100.0;
            }
        }
        // Frost Touch: Slowness (amplifier = level-1) for 2s on hit
        Integer frost = attributes.get("frost-touch");
        if (frost != null && frost > 0) {
            victim.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 40, Math.min(frost - 1, 1)));
        }

        return out;
    }

    private double applyDefenseModifiers(Player defender, org.bukkit.entity.Entity damager, double damage,
                                         EntityDamageByEntityEvent event) {
        double out = damage;
        int resonanceReduction = 0;
        int armorPoints = 0;
        int attributeReduction = 0;
        int thornsTotal = 0;

        PlayerInventory inventory = defender.getInventory();
        ItemStack[] armor = new ItemStack[]{
                inventory.getHelmet(), inventory.getChestplate(),
                inventory.getLeggings(), inventory.getBoots()
        };

        // Hoisted per-hit config so the loop does no repeated lookups.
        int perPiece = armorReductionPerPiece();
        int pointsPerPoint = armorPointsReductionPerPoint();
        LivingEntity livingDamager = damager instanceof LivingEntity le ? le : null;

        for (ItemStack piece : armor) {
            GearRolls rolls = gearManager.parse(piece);
            if (rolls == null) continue;
            // Single resonance check per piece — reused by both reduction buckets.
            boolean matches = livingDamager != null && resonanceMatches(livingDamager, rolls.resonance);
            if (matches) {
                resonanceReduction += perPiece;
            }
            armorPoints += rolls.armor;
            Map<String, Integer> attributes = gearManager.parseAttributes(rolls.attributes);
            Integer reduction = attributes.get("damage-reduction");
            if (reduction != null) {
                attributeReduction += reduction;
            }
            // Glitch Ward: extra Resonance-damage reduction (same capped bucket)
            Integer ward = attributes.get("glitch-ward");
            if (ward != null && matches) {
                resonanceReduction += ward;
            }
            Integer thorns = attributes.get("thorns");
            if (thorns != null) {
                thornsTotal += thorns;
            }
        }

        resonanceReduction = Math.min(resonanceReduction, armorReductionCap());
        attributeReduction = Math.min(attributeReduction, armorAttributeReductionCap());

        double armorReduction = Math.min(armorPoints * pointsPerPoint, armorPointsCap());
        out *= (1.0 - resonanceReduction / 100.0)
                * (1.0 - armorReduction / 100.0)
                * (1.0 - attributeReduction / 100.0);

        out *= glitchManager.getDamageTakenMultiplier(defender);

        // Thorns: reflect a % of the received melee damage (applied at MONITOR,
        // only if the hit actually lands)
        if (thornsTotal > 0 && damager instanceof LivingEntity le) {
            pendingReflect.put(event, new PendingReflect(defender, le, damage * Math.min(thornsTotal, 25) / 100.0));
        }

        return Math.max(out, 0.0);
    }

    private boolean resonanceMatches(LivingEntity entity, Resonance resonance) {
        if (resonance == null) return false;
        String tagColon = RESONANCE_TAG_COLON.get(resonance);
        String tagUnderscore = RESONANCE_TAG_UNDERSCORE.get(resonance);
        for (String t : entity.getScoreboardTags()) {
            if (t.equalsIgnoreCase(tagColon) || t.equalsIgnoreCase(tagUnderscore)) {
                return true;
            }
        }
        return false;
    }

    private static double maxHealthOf(LivingEntity entity) {
        org.bukkit.attribute.AttributeInstance attr =
                entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    /**
     * Hollow relic kill-surge: the Maw devours kills and spits out an Unstable Rift.
     * Weighted common-heavy so it prints loot without flooding legendaries.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(org.bukkit.event.entity.EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        GearRolls rolls = gearManager.parse(killer.getInventory().getItemInMainHand());
        if (rolls == null || rolls.type != GearType.DREAM_EATER) return;
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= gearManager.dreamRiftChance()) return;
        int roll = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        String riftId = roll < 40 ? "unstable_rift_common"
                : roll < 70 ? "unstable_rift_uncommon"
                : roll < 90 ? "unstable_rift_rare"
                : roll < 98 ? "unstable_rift_epic" : "unstable_rift_legendary";
        try {
            ItemStack rift = OraxenUtil.build(riftId);
            if (rift == null) return;
            event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), rift);
            killer.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                    "<light_purple>The Maw devours the kill and spits out a rift.</light_purple>"));
        } catch (Exception ignored) {
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {        Player player = event.getEntity();
        if (glitchManager.isEnabledWorld(player.getWorld().getName())) {
            glitchManager.clear(player);
        }
    }

    private int armorReductionPerPiece() {
        return gearManager.getArmorReductionPerPiece();
    }

    private int armorReductionCap() {
        return gearManager.getArmorReductionCap();
    }

    private int armorPointsReductionPerPoint() {
        return gearManager.getArmorPointsReductionPerPoint();
    }

    private int armorPointsCap() {
        return gearManager.getArmorPointsCap();
    }

    private int armorAttributeReductionCap() {
        return gearManager.getArmorAttributeReductionCap();
    }
}
