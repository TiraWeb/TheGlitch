package com.theglitch.glitchshops;

import com.theglitch.glitchshops.ui.DialogBridge;
import com.theglitch.glitchshops.ui.DialogUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchShops plugin;
    private final ShopGUI shopGUI;

    public ShopCommand(GlitchShops plugin, ShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                openDefault(player);
            } else {
                sender.sendMessage(MM.deserialize("<red>Only players can open the bazaar.</red>"));
            }
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "open":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MM.deserialize("<red>Only players can open the bazaar.</red>"));
                    return true;
                }
                openTab(player, args.length > 1 ? args[1] : plugin.getDefaultTab());
                return true;
            case "reload":
                if (!sender.hasPermission("glitchshops.admin")) {
                    sender.sendMessage(MM.deserialize("<red>No permission.</red>"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(MM.deserialize("<green>GlitchShops reloaded.</green>"));
                return true;
            case "restock":
                if (!sender.hasPermission("glitchshops.admin")) {
                    sender.sendMessage(MM.deserialize("<red>No permission.</red>"));
                    return true;
                }
                plugin.getShopManager().restockGear();
                sender.sendMessage(MM.deserialize("<green>Gear vendor restocked.</green>"));
                return true;
            default:
                sender.sendMessage(MM.deserialize("<gray>Usage: /shop [open <tab>|reload|restock]</gray>"));
                return true;
        }
    }

    private void openDefault(Player player) {
        String category = plugin.getDefaultTab();
        if (shopGUI.dialogsEnabled() && DialogBridge.dialogsRuntime()) {
            DialogUI.openRoot(plugin, shopGUI, player, () -> shopGUI.open(player, category));
        } else {
            shopGUI.open(player, category);
        }
    }

    private void openTab(Player player, String category) {
        if (shopGUI.dialogsEnabled() && DialogBridge.dialogsRuntime()) {
            DialogUI.openCategory(plugin, shopGUI, player, category, () -> shopGUI.open(player, category));
        } else {
            shopGUI.open(player, category);
        }
    }
}
