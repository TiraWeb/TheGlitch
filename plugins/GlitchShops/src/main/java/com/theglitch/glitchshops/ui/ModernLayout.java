package com.theglitch.glitchshops.ui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ModernLayout {

    /** Footer centre: "SELLING" / "Out of stock" state. */
    public static final int STATE_SLOT = 49;

    /** Inner columns 1-7 of rows 2-4 on the BAZAAR background. */
    public static final int[] STOCK_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private ModernLayout() {
    }

    /** Category tab slots on row 1, symmetric around the centre column (max 6). */
    public static int[] tabSlots(int count) {
        return switch (Math.max(0, Math.min(count, 6))) {
            case 0 -> new int[0];
            case 1 -> new int[]{13};
            case 2 -> new int[]{12, 14};
            case 3 -> new int[]{12, 13, 14};
            case 4 -> new int[]{11, 12, 14, 15};
            case 5 -> new int[]{11, 12, 13, 14, 15};
            default -> new int[]{10, 11, 12, 14, 15, 16};
        };
    }

    /** Fills {@link #STOCK_SLOTS} row by row (7 wide), centring the last partial row. */
    public static void placeCentered(Inventory inv, java.util.List<ItemStack> items) {
        int n = Math.min(items.size(), STOCK_SLOTS.length);
        int fullRows = n / 7;
        for (int i = 0; i < n; i++) {
            int row = i / 7;
            int col = i % 7;
            if (row == fullRows) {
                int rem = n - fullRows * 7;
                col += (7 - rem) / 2;
            }
            inv.setItem(STOCK_SLOTS[row * 7 + col], items.get(i));
        }
    }

    public static void paintBands(Inventory inv, int size) {
        int cells = Math.min(size, inv.getSize());
        int rows = cells / 9;
        if (rows < 3) return;
        int footerRow = rows - 1;
        for (int slot = 0; slot < cells; slot++) {
            if (slot == STATE_SLOT) continue;
            if (inv.getItem(slot) != null) continue;
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0) {
                inv.setItem(slot, UiKit.blankPane(UiKit.RAMP[Math.min(col, UiKit.RAMP.length - 1)]));
            } else if (row == footerRow) {
                inv.setItem(slot, UiKit.blankPane(Material.BLACK_STAINED_GLASS_PANE));
            } else if (col == 0 || col == 8) {
                inv.setItem(slot, UiKit.blankPane(UiKit.RAMP[4]));
            }
        }
    }

    public static void setStateIcon(Inventory inv, ItemStack icon) {
        inv.setItem(STATE_SLOT, icon);
    }
}
