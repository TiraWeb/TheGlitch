package com.theglitch.glitchclasses;

import com.theglitch.glitchclasses.ui.ClassPanel;
import com.theglitch.glitchclasses.ui.DialogUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class ClassUICommand implements CommandExecutor {

    private final GlitchClasses plugin;

    public ClassUICommand(GlitchClasses plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!DialogUI.supported()) return true;

        ClassGUI gui = plugin.getClassGUI();
        if (gui == null) return true;

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "view" -> {
                String className = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (isKnownClass(gui, className)) {
                    DialogUI.openClass(plugin, gui, player, className,
                            () -> gui.openClassMenu(player, className));
                } else {
                    DialogUI.openRoot(plugin, gui, player, () -> gui.openMainMenu(player));
                }
            }
            case "select" -> {
                String className = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (!gui.selectFromDialog(player, className)) {
                    DialogUI.openRoot(plugin, gui, player, () -> gui.openMainMenu(player));
                }
            }
            case "upgrade" -> gui.upgradeFromDialog(player);
            case "resetask" -> DialogUI.openResetConfirm(plugin, gui, player,
                    () -> gui.openMainMenu(player));
            case "resetyes" -> gui.resetFromDialog(player);
            case "noop" -> {
            }
            case "panel" -> handlePanel(player, args);
            default -> DialogUI.openRoot(plugin, gui, player, () -> gui.openMainMenu(player));
        }
        return true;
    }

    private void handlePanel(Player player, String[] args) {
        if (!player.isOp() && !player.hasPermission("glitchclasses.admin")) {
            player.sendMessage(Component.text("You do not have permission for that.", NamedTextColor.RED));
            return;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (mode) {
            case "here" -> placeHere(player);
            case "undo" -> {
                plugin.getConfig().set("modern-ui.class-panel.enabled", false);
                plugin.saveConfig();
                ClassPanel.hide();
                player.sendMessage(Component.text("Class wall removed.", NamedTextColor.RED));
            }
            case "show" -> {
                plugin.getConfig().set("modern-ui.class-panel.enabled", true);
                plugin.saveConfig();
                ClassPanel.reconfigureAndRebuild();
                player.sendMessage(Component.text("Class wall shown.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "Usage: /classui panel <here|undo|show>", NamedTextColor.YELLOW));
        }
    }

    private void placeHere(Player player) {
        Location loc = player.getLocation();
        float yawNorm = ((loc.getYaw() % 360.0F) + 360.0F) % 360.0F;
        String facing;
        if (yawNorm >= 315.0F || yawNorm < 45.0F) {
            facing = "south";
        } else if (yawNorm < 135.0F) {
            facing = "west";
        } else if (yawNorm < 225.0F) {
            facing = "north";
        } else {
            facing = "east";
        }
        plugin.getConfig().set("modern-ui.class-panel.world",
                loc.getWorld() != null ? loc.getWorld().getName() : "hub");
        plugin.getConfig().set("modern-ui.class-panel.x", Math.floor(loc.getX()) + 0.5D);
        plugin.getConfig().set("modern-ui.class-panel.y", loc.getY() + 1.0D);
        plugin.getConfig().set("modern-ui.class-panel.z", Math.floor(loc.getZ()) + 0.5D);
        plugin.getConfig().set("modern-ui.class-panel.facing", facing);
        plugin.getConfig().set("modern-ui.class-panel.enabled", true);
        plugin.saveConfig();
        ClassPanel.reconfigureAndRebuild();
        player.sendMessage(Component.text(
                "Wall placed here (facing " + facing + ").", NamedTextColor.GREEN));
    }

    private boolean isKnownClass(ClassGUI gui, String className) {
        for (String c : gui.classOrder()) {
            if (c.equals(className)) return true;
        }
        return false;
    }
}
