package com.theglitch.glitchclasses;

import com.theglitch.glitchclasses.ui.ClassPanel;
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

        ClassGUI gui = plugin.getClassGUI();
        if (gui == null) return true;

        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "view" -> {
                String className = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (isKnownClass(gui, className)) {
                    gui.openClassMenu(player, className);
                } else {
                    gui.openMainMenu(player);
                }
            }
            case "select" -> {
                String className = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (!gui.selectFromDialog(player, className)) {
                    gui.openMainMenu(player);
                }
            }
            case "upgrade" -> gui.upgradeFromDialog(player);
            case "resetask" -> gui.openMainMenu(player);
            case "resetyes" -> gui.resetFromDialog(player);
            case "noop" -> {
            }
            case "panel" -> handlePanel(player, args);
            default -> gui.openMainMenu(player);
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
            case "here" -> placeHere(player, args.length > 2 && args[2].equalsIgnoreCase("force"));
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

    private void placeHere(Player player, boolean force) {
        Location loc = player.getLocation();
        var clash = com.theglitch.common.PanelFootprint.overlaps(loc, 6 * 1.35 + 1.0, 5.0, "glitchclasses");
        if (clash.isPresent() && !force) {
            player.sendMessage(Component.text("That overlaps the " + clash.get()
                    + " panel — step a few blocks away, or use /classui panel here force.", NamedTextColor.RED));
            return;
        }
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
        plugin.getConfig().set("modern-ui.class-panel.x", loc.getX());
        plugin.getConfig().set("modern-ui.class-panel.y", loc.getY() + 1.0D);
        plugin.getConfig().set("modern-ui.class-panel.z", loc.getZ());
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
