package com.theglitch.glitchstash;

import com.theglitch.glitchitems.GlitchItems;
import com.theglitch.glitchshops.GlitchShops;
import com.theglitch.glitchshops.ShopManager;
import dev.velmax.velkoth.api.event.KothWinEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

/**
 * Listens for VelKoth extraction wins and saves player inventory to stash.
 * Applies the Residual Glitch payout multiplier (GlitchItems) as bonus shards
 * based on the saved loot's sell value (GlitchShops prices).
 * Handles teleport to hub directly (EssentialsX is incompatible with MC 26.x).
 */
public record ExtractionListener(GlitchStash plugin, StashManager stashManager) implements Listener {

    /**
     * GlitchClasses marks class ability items (prime/tactical/ultimate) with
     * this PDC key — they are kit items, not loot, and must survive extraction.
     */
    private static final NamespacedKey CLASS_ABILITY_KEY = new NamespacedKey("glitchclasses", "class_ability");

    @EventHandler
    public void onExtractionWin(KothWinEvent event) {
        Player player = event.getWinner();
        if (player == null) return;

        // 1. Save inventory to stash — EXCEPT class ability items, which stay
        //    with the player (they are kit items, not loot).
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack[] armor = player.getInventory().getArmorContents();
        ItemStack offhand = player.getInventory().getItemInOffHand();

        ItemStack[] lootContents = new ItemStack[contents.length];
        ItemStack[] keepContents = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (isClassAbilityItem(stack)) {
                keepContents[i] = stack;
            } else {
                lootContents[i] = stack;
            }
        }
        boolean keepOffhand = isClassAbilityItem(offhand);
        stashManager.saveStash(player.getUniqueId(), player.getName(),
                lootContents, armor, keepOffhand ? null : offhand);

        // 2. Clear inventory, then put ability items back in their slots.
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(keepOffhand ? offhand : null);
        for (int i = 0; i < keepContents.length; i++) {
            if (keepContents[i] != null) {
                player.getInventory().setItem(i, keepContents[i]);
            }
        }

        // 3. Residual Glitch payout + extraction variant bonus. Payout errors
        //    must never block the stash save or the teleport.
        int variantBonusPct = 0;
        try {
            ExtractionVariantManager.Variant variant = variantAt(player.getLocation());
            if (variant != null) {
                if (!variant.requiresKey() || plugin.getExtractionVariantManager().isArmed(player, variant)) {
                    variantBonusPct = variant.payoutBonus();
                    if (variantBonusPct > 0) {
                        player.sendMessage(plugin.getComponent("variant-bonus",
                                "<variant>", variant.name(),
                                "<pct>", String.valueOf(variantBonusPct)));
                    }
                } else if (plugin.getConfig().getBoolean("extraction-variants.enforce-key", true)) {
                    // VelKoth still counts the win — we cannot cancel it — but a keyless
                    // win in a key zone earns no variant bonus and is logged.
                    player.sendMessage(plugin.getComponent("variant-no-key", "<variant>", variant.name()));
                    plugin.getLogger().warning(player.getName() + " extracted in key zone '"
                            + variant.name() + "' without consuming the required key.");
                }
                plugin.getExtractionVariantManager().clearArmed(player);
            }
            payGlitchBonus(player, lootContents, armor, keepOffhand ? null : offhand, variantBonusPct);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Extraction payout failed for " + player.getName(), e);
        }

        // 4. Clear glitch stacks (design: clears on extraction or death)
        clearGlitchStacks(player);

        // 5. Notify player
        player.sendMessage(plugin.getComponent("extracted"));

        // 6. Teleport to hub (delay 1 tick so client processes the inventory clear)
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (!player.isOnline()) return;
            teleportToHub(player);
        }, 5L);
    }

    private boolean isClassAbilityItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(CLASS_ABILITY_KEY, PersistentDataType.STRING);
    }

    private void payGlitchBonus(Player player, ItemStack[] contents, ItemStack[] armor,
                                ItemStack offhand, int variantBonusPct) {
        if (!plugin.getConfig().getBoolean("payout-enabled", true)) return;
        GlitchItems glitchItems = GlitchItems.getInstance();
        if (glitchItems == null) return;
        double multiplier = glitchItems.getGlitchManager().getPayoutMultiplier(player);
        GlitchShops shops = GlitchShops.getInstance();
        if (shops == null) return;
        ShopManager shopManager = shops.getShopManager();

        int value = lootValue(contents) + lootValue(armor);
        if (offhand != null && !offhand.getType().isAir()) {
            Integer price = shopManager.sellPrice(offhand);
            if (price != null) {
                value += price * offhand.getAmount();
            }
        }
        int bonus = (int) Math.round(value * (multiplier - 1.0));
        if (variantBonusPct > 0) {
            bonus += (int) Math.round(value * variantBonusPct / 100.0);
        }
        if (bonus <= 0) return;

        RegisteredServiceProvider<Economy> provider =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) return;
        provider.getProvider().depositPlayer(player, bonus);

        String raw = plugin.getMessage("glitch-payout");
        player.sendMessage(MiniMessage.miniMessage().deserialize(raw
                .replace("<multiplier>", String.format(java.util.Locale.ROOT, "%.1f", multiplier))
                .replace("<amount>", String.valueOf(bonus))));
    }

    private int lootValue(ItemStack[] items) {
        GlitchShops shops = GlitchShops.getInstance();
        if (shops == null) return 0;
        ShopManager shopManager = shops.getShopManager();
        int value = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            Integer price = shopManager.sellPrice(item);
            if (price != null) {
                value += price * item.getAmount();
            }
        }
        return value;
    }

    private void clearGlitchStacks(Player player) {
        GlitchItems glitchItems = GlitchItems.getInstance();
        if (glitchItems != null) {
            glitchItems.getGlitchManager().clear(player);
        }
    }

    private ExtractionVariantManager.Variant variantAt(Location location) {
        return plugin.getExtractionVariantManager().variantAt(location);
    }

    /**
     * Teleport player to hub spawn. Direct teleport first — dispatchCommand
     * returns true even when the underlying command fails, so command-based
     * paths must only be fallbacks.
     */
    private void teleportToHub(Player player) {
        // 1. Direct teleport (hub is the main world, always loaded).
        World hub = Bukkit.getWorld("hub");
        if (hub != null) {
            player.teleport(hub.getSpawnLocation());
            plugin.getLogger().info("Teleported " + player.getName() + " to hub via direct teleport.");
            return;
        }

        // 2. Multiverse fallback (e.g. hub not loaded under that name yet).
        if (Bukkit.getPluginManager().getPlugin("Multiverse-Core") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv tp " + player.getName() + " hub");
            return;
        }

        plugin.getLogger().warning("Could not teleport " + player.getName() + " — hub world not found.");
        player.sendMessage(plugin.getComponent("teleport-failed"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (stashManager.hasStash(player.getUniqueId())) {
            // Delay message to ensure client is ready
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) {
                    player.sendMessage(plugin.getComponent("stash-saved"));
                    player.sendMessage(net.kyori.adventure.text.Component.text(
                            "Use /stash to retrieve your items",
                            net.kyori.adventure.text.format.NamedTextColor.GRAY));
                }
            }, 40L); // 2 second delay
        }
    }
}
