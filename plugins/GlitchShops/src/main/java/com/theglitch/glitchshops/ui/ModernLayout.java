package com.theglitch.glitchshops.ui;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ModernLayout {

    public static final int STATE_SLOT = 31;

    public static final int[] STOCK_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private ModernLayout() {
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
