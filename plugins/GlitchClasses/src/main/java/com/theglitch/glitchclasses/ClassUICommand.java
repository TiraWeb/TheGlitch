package com.theglitch.glitchclasses;

import com.theglitch.glitchclasses.ui.DialogUI;
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
            default -> DialogUI.openRoot(plugin, gui, player, () -> gui.openMainMenu(player));
        }
        return true;
    }

    private boolean isKnownClass(ClassGUI gui, String className) {
        for (String c : gui.classOrder()) {
            if (c.equals(className)) return true;
        }
        return false;
    }
}
