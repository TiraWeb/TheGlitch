package com.theglitch.glitchinsurance;

import com.theglitch.glitchinsurance.ui.DialogUI;
import com.theglitch.glitchinsurance.ui.InsurancePanel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class InsuranceUICommand implements CommandExecutor {

    private final GlitchInsurance plugin;

    public InsuranceUICommand(GlitchInsurance plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (sub) {
            case "buy" -> plugin.uiBuy(player);
            case "claim" -> {
                int index = parseIndex(args);
                plugin.uiClaim(player, index);
                scheduleReopen(() -> DialogUI.openRoot(plugin, player, () ->
                        safePerform(player)));
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
            case "here" -> InsurancePanel.placeHere(player);
            case "undo" -> InsurancePanel.undo();
            case "show" -> InsurancePanel.showAt();
            default -> InsurancePanel.showAt();
        }
    }

    private boolean isPanelAdmin(Player player) {
        try {
            return player.isOp() || player.hasPermission("glitchinsurance.admin");
        } catch (Throwable t) {
            return false;
        }
    }

    private void scheduleReopen(Runnable task) {
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    task.run();
                } catch (Throwable ignored) {
                }
            }, 1L);
        } catch (Throwable ignored) {
        }
    }

    private void safePerform(Player player) {
        try {
            player.performCommand("insurance list");
        } catch (Throwable ignored) {
        }
    }

    private static int parseIndex(String[] args) {
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
