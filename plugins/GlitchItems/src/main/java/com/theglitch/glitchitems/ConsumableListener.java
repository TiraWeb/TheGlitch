package com.theglitch.glitchitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Makes the alchemy consumables actually do something on consume
 * (docs/ITEM_BALANCE.md §5). Before this listener the potions were
 * lore-only — right-clicking gave vanilla honey-bottle effects and nothing else.
 */
public final class ConsumableListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchItems plugin;
    private final GearManager gearManager;
    private final IdentifyManager identifyManager;

    public ConsumableListener(GlitchItems plugin, GearManager gearManager, IdentifyManager identifyManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
        this.identifyManager = identifyManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;
        String id = OraxenUtil.idOf(item);
        if (id == null) return;
        Player player = event.getPlayer();
        switch (id) {
            case "healing_potion" -> player.addPotionEffect(
                    new PotionEffect(PotionEffectType.REGENERATION, 100, 1)); // Regen II, 5s
            case "corrupted_heal" -> {
                double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                        ? 20.0 : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                player.setHealth(max);
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2)); // Regen III, 10s
            }
            case "aether_tonic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));       // Speed II, 30s
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1));  // Absorption II, 30s
            }
            case "ward_salve" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 0));  // Resistance I, 20s
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 0));  // Absorption I, 20s
            }
            case "rift_reveal_pack" -> {
                identifyManager.addFreeIdentify(player.getUniqueId(), 1);
                player.sendMessage(MM.deserialize(
                        "<blue>Attunement stored — your next identify is <white>free</white>, any rarity.</blue>"));
            }
            case "void_infusion" -> handleVoidInfusion(event, player);
            default -> { }
        }
    }

    /**
     * Void Infusion: consumes the infusion and reworks the gear held in the
     * OFF-hand (+1 Resonance boost up to cap, +1 star per pip). Cancels the
     * consume when the target is invalid so the item is not wasted.
     */
    private void handleVoidInfusion(PlayerItemConsumeEvent event, Player player) {
        ItemStack offhand = player.getInventory().getItemInOffHand();
        ItemStack infused = gearManager.applyVoidInfusion(offhand);
        if (infused == null) {
            event.setCancelled(true);
            boolean wrongRarity = gearManager.parse(offhand) != null;
            player.sendMessage(MM.deserialize(wrongRarity
                    ? "<red>Void Infusion needs <white>Epic</white> or better gear in your off-hand.</red>"
                    : "<red>Hold the gear you want to infuse in your <white>off-hand</white>.</red>"));
            return;
        }
        player.getInventory().setItemInOffHand(infused);
        player.sendMessage(MM.deserialize(
                "<light_purple>The void seeps into the item — resonance deepens, stars align.</light_purple>"));
    }
}
