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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatListener implements Listener {

    private record PendingSideEffects(Player attacker, LivingEntity victim, double healed, double maxHealth, int fireTicks) {}
    private record PendingReflect(Player defender, LivingEntity attacker, double amount) {}

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private final ResidualGlitchManager glitchManager;
    private final Map<EntityDamageByEntityEvent, PendingSideEffects> pendingSideEffects = new ConcurrentHashMap<>();
    private final Map<EntityDamageByEntityEvent, PendingReflect> pendingReflect = new ConcurrentHashMap<>();

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
            maxHp = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                    ? 20.0 : attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            healed = out * lifesteal / 100.0;
        }
        Integer fire = attributes.get("fire-aspect");
        int fireTicks = fire != null && fire > 0 ? fire * 20 : 0;
        if (healed > 0.0 || fireTicks > 0) {
            pendingSideEffects.put(event, new PendingSideEffects(attacker, victim, healed, maxHp, fireTicks));
        }
        // Execute: +% damage vs targets below 30% max HP
        Integer execute = attributes.get("execute");
        if (execute != null && execute > 0) {
            double victimMax = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                    ? 20.0 : victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
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

        for (ItemStack piece : armor) {
            GearRolls rolls = gearManager.parse(piece);
            if (rolls == null) continue;
            if (damager instanceof LivingEntity le && resonanceMatches(le, rolls.resonance)) {
                resonanceReduction += armorReductionPerPiece();
            }
            armorPoints += rolls.armor;
            Map<String, Integer> attributes = gearManager.parseAttributes(rolls.attributes);
            Integer reduction = attributes.get("damage-reduction");
            if (reduction != null) {
                attributeReduction += reduction;
            }
            // Glitch Ward: extra Resonance-damage reduction (same capped bucket)
            Integer ward = attributes.get("glitch-ward");
            if (ward != null && damager instanceof LivingEntity le && resonanceMatches(le, rolls.resonance)) {
                resonanceReduction += ward;
            }
            Integer thorns = attributes.get("thorns");
            if (thorns != null) {
                thornsTotal += thorns;
            }
        }

        resonanceReduction = Math.min(resonanceReduction, armorReductionCap());
        attributeReduction = Math.min(attributeReduction, armorAttributeReductionCap());

        double armorReduction = Math.min(armorPoints * armorPointsReductionPerPoint(), armorPointsCap());
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
        String lower = resonance.name().toLowerCase();
        String tagColon = "res:" + lower;
        String tagUnderscore = "res_" + lower;
        for (String t : entity.getScoreboardTags()) {
            String tl = t.toLowerCase();
            if (tl.equals(tagColon) || tl.equals(tagUnderscore)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
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
