package com.theglitch.common;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Shared inventory helpers — extracts manual stacking logic from {@code StashManager}
 * so no {@code Bukkit.createInventory} allocation is needed when merging.
 */
public final class InventoryUtil {

    private InventoryUtil() {
    }

    /**
     * Merge a stack into a target list, topping up existing similar stacks first
     * and splitting remainder into max-stack-sized chunks.
     * Same logic as {@code StashManager.mergeStack} without Bukkit inventory allocation.
     *
     * @param target mutable list of existing stacks
     * @param stack stack to merge (will be cloned; amount may be split)
     */
    public static void mergeStack(List<ItemStack> target, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return;
        int remaining = stack.getAmount();
        int max = stack.getMaxStackSize();
        // Try to top-up existing similar stacks
        for (ItemStack existing : target) {
            if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getAmount();
                int toAdd = Math.min(remaining, space);
                existing.setAmount(existing.getAmount() + toAdd);
                remaining -= toAdd;
                if (remaining <= 0) return;
            }
        }
        // Add remainder as new stack(s), splitting if > max
        while (remaining > 0) {
            int chunk = Math.min(remaining, max);
            ItemStack part = stack.clone();
            part.setAmount(chunk);
            target.add(part);
            remaining -= chunk;
        }
    }
}
