package com.theglitch.glitchstash;

import com.theglitch.glitchstash.ui.DialogUI;
import com.theglitch.glitchstash.ui.StashPanel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StashUICommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchStash plugin;

    private record PanelSnapshot(String world, double x, double y, double z,
                                 String facing, double spacing) {}

    private static volatile PanelSnapshot undoState;

    public StashUICommand(GlitchStash plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        String sub = args.length == 0 ? "noop" : args[0].toLowerCase();
        switch (sub) {
            case "noop": {
                return true;
            }
            case "take": {
                if (args.length < 2) return true;
                int parsed;
                try {
                    parsed = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    return true;
                }
                StashManager manager = plugin.getStashManager();
                if (manager == null) return true;
                final int index = parsed;
                FoliaScheduler.runDelayedEntity(player, plugin, () -> {
                    manager.takeFromUi(player, index);
                    DialogUI.openStash(plugin, player, () -> Bukkit.dispatchCommand(player, "stash"));
                }, 1L);
                return true;
            }
            case "open": {
                FoliaScheduler.runEntity(player, plugin, () ->
                        DialogUI.openStash(plugin, player, () -> Bukkit.dispatchCommand(player, "stash")));
                return true;
            }
            case "panel": {
                handlePanel(player, args);
                return true;
            }
            default: {
                return true;
            }
        }
    }

    private void handlePanel(Player player, String[] args) {
        if (!player.hasPermission("glitchstash.admin")) {
            player.sendMessage(MM.deserialize("<red>No permission.</red>"));
            return;
        }
        String action = args.length < 2 ? "show" : args[1].toLowerCase();
        switch (action) {
            case "here": {
                if (!player.getWorld().getName()
                        .equals(plugin.getConfig().getString("modern-ui.world-panel.world", "hub"))) {
                    plugin.getConfig().set("modern-ui.world-panel.world", player.getWorld().getName());
                }
                undoState = snapshotFromConfig();
                plugin.getConfig().set("modern-ui.world-panel.enabled", true);
                plugin.getConfig().set("modern-ui.world-panel.x", player.getLocation().getX());
                plugin.getConfig().set("modern-ui.world-panel.y", player.getLocation().getY());
                plugin.getConfig().set("modern-ui.world-panel.z", player.getLocation().getZ());
                plugin.getConfig().set("modern-ui.world-panel.facing", facingFromYaw(player));
                plugin.saveConfig();
                StashPanel.reconfigureAndRebuild();
                player.sendMessage(MM.deserialize("<green>Stash kiosk moved to your position ("
                        + String.format("%.1f, %.1f, %.1f", player.getLocation().getX(),
                        player.getLocation().getY(), player.getLocation().getZ())
                        + ", facing " + facingFromYaw(player) + ").</green>"));
                return;
            }
            case "undo": {
                PanelSnapshot snap = undoState;
                if (snap == null) {
                    player.sendMessage(MM.deserialize("<gray>Nothing to undo.</gray>"));
                    return;
                }
                undoState = null;
                plugin.getConfig().set("modern-ui.world-panel.enabled", true);
                plugin.getConfig().set("modern-ui.world-panel.world", snap.world());
                plugin.getConfig().set("modern-ui.world-panel.x", snap.x());
                plugin.getConfig().set("modern-ui.world-panel.y", snap.y());
                plugin.getConfig().set("modern-ui.world-panel.z", snap.z());
                plugin.getConfig().set("modern-ui.world-panel.facing", snap.facing());
                plugin.getConfig().set("modern-ui.world-panel.spacing", snap.spacing());
                plugin.saveConfig();
                StashPanel.reconfigureAndRebuild();
                player.sendMessage(MM.deserialize("<green>Stash kiosk position restored.</green>"));
                return;
            }
            case "show":
            default: {
                StashPanel.reconfigureAndRebuild();
                player.sendMessage(MM.deserialize("<green>Stash kiosk rebuilt.</green>"));
                return;
            }
        }
    }

    private PanelSnapshot snapshotFromConfig() {
        try {
            return new PanelSnapshot(
                    plugin.getConfig().getString("modern-ui.world-panel.world", "hub"),
                    plugin.getConfig().getDouble("modern-ui.world-panel.x", 67.5D),
                    plugin.getConfig().getDouble("modern-ui.world-panel.y", -43.5D),
                    plugin.getConfig().getDouble("modern-ui.world-panel.z", -5.5D),
                    plugin.getConfig().getString("modern-ui.world-panel.facing", "west"),
                    plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.35D));
        } catch (Throwable t) {
            return new PanelSnapshot("hub", 67.5D, -43.5D, -5.5D, "west", 1.35D);
        }
    }

    private String facingFromYaw(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360F) + 360F) % 360F;
        if (yaw >= 315F || yaw < 45F) {
            return "south";
        }
        if (yaw < 135F) {
            return "west";
        }
        if (yaw < 225F) {
            return "north";
        }
        return "east";
    }
}
