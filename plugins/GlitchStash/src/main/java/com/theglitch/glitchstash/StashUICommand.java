package com.theglitch.glitchstash;

import com.theglitch.glitchstash.ui.DialogUI;
import com.theglitch.glitchstash.ui.PanelConfig;
import com.theglitch.glitchstash.ui.StashPanel;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
                // Dialog-only path — same rank gate the stash panel applies.
                if (!DialogUI.canRemote(player)) {
                    player.sendMessage(MM.deserialize(
                            "<gray>Remote stash is a rank perk — use the chest menu.</gray>"));
                    Bukkit.dispatchCommand(player, "stash");
                    return true;
                }
                StashManager manager = plugin.getStashManager();
                if (manager == null) return true;
                // Positional index + expected Material: if the stash changed since
                // the dialog was rendered, re-render instead of giving a wrong item.
                final String expected = args.length >= 3 ? args[2].toUpperCase(java.util.Locale.ROOT) : null;
                final int index = parsed;
                FoliaScheduler.runDelayedEntity(player, plugin, () -> {
                    java.util.List<ItemStack> flat = manager.listStash(player.getUniqueId());
                    ItemStack at = index >= 0 && index < flat.size() ? flat.get(index) : null;
                    if (expected != null && (at == null || !at.getType().name().equals(expected))) {
                        player.sendMessage(MM.deserialize("<red>Stash changed — reopening.</red>"));
                        DialogUI.openStash(plugin, player, () -> Bukkit.dispatchCommand(player, "stash"));
                        return;
                    }
                    manager.takeFromUi(player, index);
                    DialogUI.openStash(plugin, player, () -> Bukkit.dispatchCommand(player, "stash"));
                }, 1L);
                return true;
            }
            case "open": {
                // Dialog-only path — same rank gate the stash panel applies.
                if (!DialogUI.canRemote(player)) {
                    player.sendMessage(MM.deserialize(
                            "<gray>Remote stash is a rank perk — use the chest menu.</gray>"));
                    Bukkit.dispatchCommand(player, "stash");
                    return true;
                }
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
                        .equals(plugin.getConfig().getString(PanelConfig.WORLD_KEY, PanelConfig.WORLD_DEFAULT))) {
                    plugin.getConfig().set(PanelConfig.WORLD_KEY, player.getWorld().getName());
                }
                undoState = snapshotFromConfig();
                plugin.getConfig().set(PanelConfig.ENABLED_KEY, true);
                plugin.getConfig().set(PanelConfig.X_KEY, player.getLocation().getX());
                plugin.getConfig().set(PanelConfig.Y_KEY, player.getLocation().getY());
                plugin.getConfig().set(PanelConfig.Z_KEY, player.getLocation().getZ());
                plugin.getConfig().set(PanelConfig.FACING_KEY, facingFromYaw(player));
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
                plugin.getConfig().set(PanelConfig.ENABLED_KEY, true);
                plugin.getConfig().set(PanelConfig.WORLD_KEY, snap.world());
                plugin.getConfig().set(PanelConfig.X_KEY, snap.x());
                plugin.getConfig().set(PanelConfig.Y_KEY, snap.y());
                plugin.getConfig().set(PanelConfig.Z_KEY, snap.z());
                plugin.getConfig().set(PanelConfig.FACING_KEY, snap.facing());
                plugin.getConfig().set(PanelConfig.SPACING_KEY, snap.spacing());
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
            PanelConfig.Snapshot snap = PanelConfig.load(plugin);
            return new PanelSnapshot(snap.world(), snap.x(), snap.y(), snap.z(), snap.facing(), snap.spacing());
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
