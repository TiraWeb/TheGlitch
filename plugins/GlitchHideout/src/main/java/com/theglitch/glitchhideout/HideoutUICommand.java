package com.theglitch.glitchhideout;

import com.theglitch.glitchhideout.ui.DialogUI;
import com.theglitch.glitchhideout.ui.HideoutPanel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class HideoutUICommand implements CommandExecutor {

    private final GlitchHideout plugin;

    public HideoutUICommand(GlitchHideout plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "noop" -> {
                return true;
            }
            case "panel" -> {
                handlePanel(player, args);
                return true;
            }
        }
        if (!DialogUI.supported()) return true;
        HideoutGUI gui = plugin.getGui();
        if (gui == null) return true;
        // Dialog-only subcommands — same rank gate the /hideout root path applies
        // (ops bypass, modern-ui.remote-perm). Graceful chest-GUI fallback.
        if (!DialogUI.canRemote(plugin, player)) {
            gui.openMain(player);
            return true;
        }
        switch (sub) {
            case "station" -> {
                if (args.length < 2) return true;
                String id = args[1].toLowerCase(Locale.ROOT);
                if (plugin.getHideoutManager().getStation(id) == null) {
                    player.sendMessage(Component.text("Unknown station.", NamedTextColor.RED));
                    return true;
                }
                DialogUI.openStation(plugin, player, id, "hideoutui noop");
            }
            case "workbench" -> DialogUI.openWorkbench(plugin, player, "hideoutui root");
            case "upgrade-armor" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "armor upgrade " + player.getName());
            case "upgrade" -> {
                if (args.length < 2) return true;
                final String id = args[1].toLowerCase(Locale.ROOT);
                if (plugin.getHideoutManager().getStation(id) == null) {
                    player.sendMessage(Component.text("Unknown station.", NamedTextColor.RED));
                    return true;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        gui.upgradeFromUi(player, id);
                    } finally {
                        DialogUI.openStation(plugin, player, id, "hideoutui noop");
                    }
                });
            }
            case "craft" -> {
                if (args.length < 2) return true;
                final String recipeId = args[1].toLowerCase(Locale.ROOT);
                if (plugin.getHideoutManager().getRecipe(recipeId) == null) {
                    player.sendMessage(Component.text("Unknown recipe.", NamedTextColor.RED));
                    return true;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        gui.craftFromUi(player, recipeId);
                    } finally {
                        DialogUI.openWorkbench(plugin, player, "hideoutui root");
                    }
                });
            }
            case "root" -> DialogUI.openRoot(plugin, player, () -> gui.openMain(player));
            default -> DialogUI.openRoot(plugin, player, () -> gui.openMain(player));
        }
        return true;
    }

    private void handlePanel(Player player, String[] args) {
        if (!isPanelAdmin(player)) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        FileConfiguration cfg = plugin.getConfig();
        switch (mode) {
            case "here" -> {
                Location loc = player.getLocation();
                cfg.set("modern-ui.world-panel.enabled", true);
                cfg.set("modern-ui.world-panel.world", player.getWorld().getName());
                cfg.set("modern-ui.world-panel.x", loc.getX());
                cfg.set("modern-ui.world-panel.y", loc.getY());
                cfg.set("modern-ui.world-panel.z", loc.getZ());
                cfg.set("modern-ui.world-panel.facing", facingFromYaw(loc.getYaw()));
                plugin.saveConfig();
                HideoutPanel.reconfigureAndRebuild();
                player.sendMessage(Component.text("Hideout wall panel moved here.", NamedTextColor.GREEN));
            }
            case "undo" -> {
                cfg.set("modern-ui.world-panel.enabled", false);
                plugin.saveConfig();
                HideoutPanel.reconfigureAndRebuild();
                player.sendMessage(Component.text("Hideout wall panel removed.", NamedTextColor.YELLOW));
            }
            case "show" -> {
                cfg.set("modern-ui.world-panel.enabled", true);
                plugin.saveConfig();
                HideoutPanel.reconfigureAndRebuild();
                player.sendMessage(Component.text("Hideout wall panel rebuilt.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /hideoutui panel <here|undo|show>", NamedTextColor.GRAY));
        }
    }

    private boolean isPanelAdmin(Player player) {
        return player.isOp() || player.hasPermission("glitchhideout.admin");
    }

    private String facingFromYaw(float rawYaw) {
        float yaw = (rawYaw % 360.0F + 360.0F) % 360.0F;
        if (yaw < 45.0F || yaw >= 315.0F) return "south";
        if (yaw < 135.0F) return "west";
        if (yaw < 225.0F) return "north";
        return "east";
    }
}
