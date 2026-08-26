package com.theglitch.glitchshops;

import com.theglitch.glitchshops.ui.DialogUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ShopUICommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchShops plugin;
    private final ShopGUI gui;

    public ShopUICommand(GlitchShops plugin, ShopGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!DialogUI.supported()) return true;
        String sub = args.length == 0 ? "" : args[0].toLowerCase();
        switch (sub) {
            case "root": {
                String category = gui.defaultTab();
                DialogUI.openRoot(plugin, gui, player, () -> gui.open(player, category));
                return true;
            }
            case "open": {
                String category = args.length > 1 ? args[1] : gui.defaultTab();
                DialogUI.openCategory(plugin, gui, player, category, () -> gui.open(player, category));
                return true;
            }
            case "buy": {
                if (args.length < 2) return true;
                String itemId = args[1];
                int parsed = 1;
                if (args.length > 2) {
                    try {
                        parsed = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        parsed = 1;
                    }
                }
                final int amount = Math.max(1, Math.min(parsed, 64));
                String found = findCategory(itemId);
                if (found == null) {
                    player.sendMessage(MM.deserialize("<red>That item can't be traded.</red>"));
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            DialogUI.openRoot(plugin, gui, player, () -> gui.open(player, gui.defaultTab())));
                    return true;
                }
                final String category = found;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    gui.buyFromDialog(player, category, itemId, amount);
                    DialogUI.openCategory(plugin, gui, player, category, () -> gui.open(player, category));
                });
                return true;
            }
            case "buygear": {
                if (args.length < 2) return true;
                int index;
                try {
                    index = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    return true;
                }
                final int gearIndex = index;
                final String category = "gear";
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    gui.buyGearFromDialog(player, gearIndex);
                    DialogUI.openCategory(plugin, gui, player, category, () -> gui.open(player, category));
                });
                return true;
            }
            case "sellmode": {
                String category = args.length > 1 ? args[1] : gui.defaultTab();
                gui.open(player, category, true);
                return true;
            }
            default:
                return true;
        }
    }

    private String findCategory(String itemId) {
        for (String tab : gui.tabOrder()) {
            if (tab.equals("gear")) continue;
            if (gui.buyPriceFor(tab, itemId) != null) return tab;
        }
        return null;
    }
}
