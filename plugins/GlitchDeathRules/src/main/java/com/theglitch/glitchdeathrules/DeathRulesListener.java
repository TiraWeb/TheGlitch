package com.theglitch.glitchdeathrules;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Iterator;
import java.util.Objects;

/**
 * Red Zone mercy rule: on death the player keeps leggings + boots.
 * Everything else (helmet, chestplate, weapons, inventory) drops as normal.
 * Design: docs/ITEM_SYSTEM.md §12.
 */
public record DeathRulesListener(GlitchDeathRules plugin) implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Cached HashSet lookup — no getStringList + List.contains per death
        if (!plugin.isMercyWorld(player.getWorld().getName())) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        // Snapshot references before death — use identity (== / identityHashCode) to avoid
        // matching backpack duplicates that are merely isSimilar.
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();

        boolean kept = false;
        boolean leggingsKept = false;
        boolean bootsKept = false;
        Iterator<ItemStack> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            boolean isLeggings = !leggingsKept && isMercyPiece(drop, leggings);
            boolean isBoots = !bootsKept && isMercyPiece(drop, boots);
            if (isLeggings || isBoots) {
                drops.remove(); // Iterator removal — correct vs drops.remove(drop) ConcurrentModification
                event.getItemsToKeep().add(drop);
                if (isLeggings) leggingsKept = true;
                if (isBoots) bootsKept = true;
                kept = true;
            }
        }
        if (kept) {
            player.sendMessage(plugin.getComponent("mercy-kept"));
        }
    }

    /**
     * Identity-aware mercy check: prefers reference equality (== / identityHashCode)
     * over broad {@code isSimilar}. Falls back to strict type + displayName check
     * to distinguish a backpack duplicate with same material but different name.
     * Limited to one keep per equipped slot so a backpack stack of similar leggings
     * cannot all be retained.
     */
    private boolean isMercyPiece(ItemStack drop, ItemStack equipped) {
        if (equipped == null || equipped.getType().isAir() || drop == null) return false;
        // Exact reference — most reliable when the server reuses inventory objects for drops
        if (drop == equipped) return true;
        if (System.identityHashCode(drop) == System.identityHashCode(equipped)
                && drop.getType() == equipped.getType()) return true;
        // Strict type check — isSimilar is too broad for backpack duplicates
        if (drop.getType() != equipped.getType()) return false;
        var dropMeta = drop.getItemMeta();
        var equipMeta = equipped.getItemMeta();
        if (dropMeta == null || equipMeta == null) return false;
        boolean dropHasName = dropMeta.hasCustomName();
        boolean equipHasName = equipMeta.hasCustomName();
        if (dropHasName != equipHasName) return false;
        if (dropHasName) {
            String dropName = PlainTextComponentSerializer.plainText().serialize(dropMeta.customName());
            String equipName = PlainTextComponentSerializer.plainText().serialize(equipMeta.customName());
            if (!Objects.equals(dropName, equipName)) return false;
        }
        // Names (or lack thereof) and type match — now verify full similarity.
        // This still allows plain iron_leggings duplicates to match, but the
        // per-slot single-keep guard above prevents keeping an entire backpack.
        return drop.isSimilar(equipped);
    }
}
