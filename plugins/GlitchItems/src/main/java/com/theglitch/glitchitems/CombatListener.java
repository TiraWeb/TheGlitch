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

public final class CombatListener implements Listener {

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private final ResidualGlitchManager glitchManager;

    public CombatListener(GlitchItems plugin, GearManager gearManager, ResidualGlitchManager glitchManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        this.glitchManager = glitchManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        double damage = event.getFinalDamage();

        if (event.getDamager() instanceof Player attacker) {
            damage = applyWeaponModifiers(attacker, victim, damage);
        }

        if (victim instanceof Player defender) {
            damage = applyDefenseModifiers(defender, event.getDamager(), damage);
        }

        event.setDamage(damage);
    }

    private double applyWeaponModifiers(Player attacker, LivingEntity victim, double damage) {
        ItemStack held = attacker.getInventory().getItemInMainHand();
        GearRolls rolls = gearManager.parse(held);
        if (rolls == null || !rolls.type.isWeapon()) return damage;

        double out = damage * (1.0 + rolls.damage / 100.0);

        if (resonanceMatches(victim, rolls.resonance)) {
            out *= 1.0 + (gearManager.weaponResonanceBase() + rolls.boost) / 100.0;
        }

        Map<String, Integer> attributes = gearManager.parseAttributes(rolls.attributes);
        Integer lifesteal = attributes.get("lifesteal");
        if (lifesteal != null && lifesteal > 0) {
            double healed = out * lifesteal / 100.0;
            attacker.setHealth(Math.min(attacker.getHealth() + healed, attacker.getAttribute(
                    org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
                    ? 20.0 : attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue()));
        }
        Integer fire = attributes.get("fire-aspect");
        if (fire != null && fire > 0) {
            victim.setFireTicks(fire * 20);
        }

        return out;
    }

    private double applyDefenseModifiers(Player defender, org.bukkit.entity.Entity damager, double damage) {
        double out = damage;
        int resonanceReduction = 0;
        int armorPoints = 0;
        int attributeReduction = 0;

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
        }

        resonanceReduction = Math.min(resonanceReduction, armorReductionCap());
        attributeReduction = Math.min(attributeReduction, armorAttributeReductionCap());

        double armorReduction = Math.min(armorPoints * armorPointsReductionPerPoint(), armorPointsCap());
        out *= (1.0 - resonanceReduction / 100.0)
                * (1.0 - armorReduction / 100.0)
                * (1.0 - attributeReduction / 100.0);

        out *= glitchManager.getDamageTakenMultiplier(defender);

        return Math.max(out, 0.0);
    }

    private boolean resonanceMatches(LivingEntity entity, Resonance resonance) {
        if (resonance == null) return false;
        String tag = "res:" + resonance.name();
        for (String t : entity.getScoreboardTags()) {
            if (t.equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getConfig().getStringList("residual-glitch.enabled-worlds")
                .contains(player.getWorld().getName())) {
            glitchManager.clear(player);
        }
    }

    private int armorReductionPerPiece() {
        return plugin.getConfig().getInt("resonance.armor-reduction-per-piece", 10);
    }

    private int armorReductionCap() {
        return plugin.getConfig().getInt("resonance.armor-reduction-cap", 40);
    }

    private int armorPointsReductionPerPoint() {
        return plugin.getConfig().getInt("resonance.armor-points-reduction-per-point", 2);
    }

    private int armorPointsCap() {
        return plugin.getConfig().getInt("resonance.armor-points-cap", 25);
    }

    private int armorAttributeReductionCap() {
        return plugin.getConfig().getInt("resonance.armor-attribute-reduction-cap", 30);
    }
}
