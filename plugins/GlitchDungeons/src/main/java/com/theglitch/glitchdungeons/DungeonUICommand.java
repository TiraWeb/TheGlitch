package com.theglitch.glitchdungeons;

import com.theglitch.glitchdungeons.gui.DungeonSelectGUI;
import com.theglitch.glitchdungeons.ui.DungeonPanel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DungeonUICommand implements CommandExecutor {

    private final GlitchDungeons plugin;

    public DungeonUICommand(GlitchDungeons plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "open" -> {
                int tier = parseTier(args);
                DungeonSelectGUI gui = plugin.getSelectGui();
                if (gui != null && tier > 0) {
                    try {
                        gui.dispatchJoin(player, tier);
                    } catch (Throwable ignored) {
                    }
                }
            }
            case "panel" -> handlePanel(player, args);
            default -> { }
        }
        return true;
    }

    private void handlePanel(Player player, String[] args) {
        if (!isPanelAdmin(player)) {
            return;
        }
        String op = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "";
        switch (op) {
            case "here" -> DungeonPanel.placeHere(player);
            case "undo" -> DungeonPanel.undo();
            case "show" -> DungeonPanel.showAt();
            default -> DungeonPanel.showAt();
        }
    }

    private boolean isPanelAdmin(Player player) {
        try {
            return player.isOp() || player.hasPermission("glitchdungeons.admin");
        } catch (Throwable t) {
            return false;
        }
    }

    private static int parseTier(String[] args) {
        if (args.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
