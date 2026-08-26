package com.theglitch.glitchshops.ui;

import com.theglitch.glitchshops.GlitchShops;
import com.theglitch.glitchshops.ShopGUI;
import com.theglitch.glitchshops.ShopManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DialogUI {

    private static final Boolean SUPPORTED = DialogBridge.dialogsRuntime();

    private DialogUI() {
    }

    public static boolean supported() {
        return SUPPORTED != null && SUPPORTED;
    }

    public static boolean show(JavaPlugin plugin, Player player, String snbt) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dialog show " + player.getName() + " " + snbt);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String esc(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String txt(String text, String color, boolean bold) {
        return "{text:" + esc(text)
                + (color == null ? "" : ",color:" + esc(color))
                + (bold ? ",bold:1b" : "")
                + "}";
    }

    public static String multiAction(String titleText, String titleColor, String bodyText,
                                     String actionsSnbt, int columns, String exitLabel) {
        return "{type:\"minecraft:multi_action\",title:" + txt(titleText, titleColor, true)
                + ",body:[{type:\"minecraft:plain_message\",contents:" + txt(bodyText, "gray", false)
                + "}],columns:" + columns
                + ",actions:[" + actionsSnbt + "]"
                + (exitLabel == null ? "" : ",exit_action:{label:" + txt(exitLabel, "dark_gray", false) + "}")
                + "}";
    }

    public static String button(String label, String color, String tooltip, String template) {
        return "{label:" + txt(label, color, false)
                + (tooltip == null ? "" : ",tooltip:" + txt(tooltip, "dark_gray", false))
                + ",action:{type:\"minecraft:run_command\",command:" + esc(template) + "}}";
    }

    public static void openRoot(GlitchShops plugin, ShopGUI gui, Player player, Runnable chestFallback) {
        StringBuilder actions = new StringBuilder();
        List<String> tabs = gui.tabOrder();
        for (int i = 0; i < tabs.size(); i++) {
            if (i > 0) actions.append(',');
            String tab = tabs.get(i);
            String label = gui.categoryLabel(tab);
            actions.append(button(label, "light_purple", "Browse " + label.toLowerCase(),
                    "shopui open " + tab));
        }
        actions.append(',').append(button("SELL MODE (opens chest)", "gold",
                "Click items in your inventory below", "shopui sellmode " + gui.defaultTab()));
        FloatingBanner.show(plugin, player, UiKit.title("GRAND BAZAAR"), 60L);
        boolean shown = show(plugin, player, multiAction(
                "GRAND BAZAAR", "light_purple",
                "Balance: " + balance(plugin, player) + " Shards\nPick a category:",
                actions.toString(), 2, "Close"));
        if (!shown) {
            chestFallback.run();
        }
    }

    public static void openCategory(GlitchShops plugin, ShopGUI gui, Player player,
                                    String category, Runnable chestFallback) {
        String label = gui.categoryLabel(category);
        List<String> actions = new ArrayList<>();
        if (category.equals("gear")) {
            List<ShopManager.GearStockEntry> stock = plugin.getShopManager().getGearStock();
            for (int i = 0; i < stock.size(); i++) {
                ShopManager.GearStockEntry entry = stock.get(i);
                if (entry == null || entry.item() == null) continue;
                boolean superRare = entry.superRare();
                actions.add(button(
                        superRare ? "SUPER RARE Gear \u2014 " + entry.price() + " Shards"
                                : "Buy Gear \u2014 " + entry.price() + " Shards",
                        superRare ? "gold" : "aqua",
                        superRare ? "Legendary max-roll gear" : "Randomly rolled gear",
                        "shopui buygear " + i));
            }
        } else {
            List<String> ids = gui.stockIds(category);
            int stackSize = gui.buyStackSizePublic();
            for (String id : ids) {
                Integer price = gui.buyPriceFor(category, id);
                if (price == null) continue;
                String name = gui.displayNameOf(id);
                actions.add(button("Buy " + name, "aqua",
                        "Cost: " + price + " Shards", "shopui buy " + id + " 1"));
                if (stackSize > 1) {
                    actions.add(button("Buy x" + stackSize, "dark_aqua",
                            "Cost: " + (price * stackSize) + " Shards",
                            "shopui buy " + id + " " + stackSize));
                }
            }
        }
        if (actions.isEmpty()) {
            actions.add(button("Out of stock", "dark_gray",
                    "The vendor will restock soon.", "shopui root"));
        }
        actions.add(button("\u00ab BACK", "yellow", "Back to the bazaar", "shopui root"));
        FloatingBanner.show(plugin, player, UiKit.title(label), 60L);
        boolean shown = show(plugin, player, multiAction(
                label, "light_purple",
                "Balance: " + balance(plugin, player) + " Shards\nBrowsing " + label + ":",
                String.join(",", actions), 2, "Close"));
        if (!shown) {
            chestFallback.run();
        }
    }

    public static String itemBody(String materialId, int count, Integer customModelData) {
        String id = materialId == null || materialId.isBlank() ? "paper" : materialId.toLowerCase();
        if (!id.startsWith("minecraft:")) {
            id = "minecraft:" + id;
        }
        if (customModelData != null) {
            return "{type:\"minecraft:item\",item:{id:" + esc(id) + ",count:" + count
                    + ",components:{\"minecraft:custom_model_data\":{floats:[" + customModelData + "]}}}}";
        }
        return "{type:\"minecraft:item\",item:" + esc(id) + ",count:" + count + "}";
    }

    public static void openBuyConfirm(GlitchShops plugin, ShopGUI gui, Player player,
                                      String category, String itemId) {
        String matId;
        Integer cmd = null;
        String name;
        try {
            var built = io.th0rgal.oraxen.api.OraxenItems.getItemById(itemId).build();
            matId = built.getType().getKey().getKey();
            try {
                if (built.getItemMeta().hasCustomModelData()) {
                    cmd = built.getItemMeta().getCustomModelData();
                }
            } catch (Throwable ignored) {
            }
            name = gui.displayNameOf(itemId);
        } catch (Throwable t) {
            matId = "paper";
            name = itemId;
        }
        Integer price = gui.buyPriceFor(category, itemId);
        if (price == null) {
            String template = plugin.getShopManager().getMessageTemplate("no-value");
            player.sendMessage(GlitchShops.mm().deserialize(template));
            return;
        }
        int maxAmt = Math.max(1, gui.buyStackSizePublic());
        String body = itemBody(matId, 1, cmd)
                + ",{type:\"minecraft:plain_message\",contents:"
                + txt("Price: " + price + " Shards each", "gray", false)
                + "},{type:\"minecraft:plain_message\",contents:"
                + txt("Slide to pick amount, then CONFIRM", "gray", false)
                + "}";
        String inputs = "{type:\"minecraft:number_range\",label:{text:\"Amount\"},key:\"amt\",start:1,end:"
                + maxAmt + ",step:1,initial:1}";
        String actions = "{label:" + txt("CONFIRM PURCHASE", "green", false)
                + ",tooltip:" + txt("Total shown before confirm runs", "dark_gray", false)
                + ",width:250"
                + ",action:{type:\"minecraft:run_command\",command:"
                + esc("shopui buyslider " + category + " " + itemId + " $(amt)")
                + "}}"
                + ","
                + button("\u00ab BACK", "yellow", "Back to " + gui.categoryLabel(category), "shopui open " + category);
        FloatingBanner.show(plugin, player, UiKit.title("BUY"), 60L);
        String snbt = "{type:\"minecraft:multi_action\",title:"
                + txt("BUY " + name.toUpperCase(), "aqua", true)
                + ",body:[" + body + "]"
                + ",inputs:[" + inputs + "]"
                + ",columns:2"
                + ",actions:[" + actions + "]"
                + ",exit_action:{label:" + txt("Close", "dark_gray", false) + "}}";
        boolean shown = show(plugin, player, snbt);
        if (!shown) {
            gui.open(player, category);
        }
    }

    private static int balance(GlitchShops plugin, Player player) {
        try {
            Economy economy = plugin.getEconomy();
            return economy == null ? 0 : (int) economy.getBalance(player);
        } catch (Throwable t) {
            return 0;
        }
    }
}
