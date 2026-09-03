package com.theglitch.glitchitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class IdentifyCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final IdentifyManager identifyManager;

    public IdentifyCommand(IdentifyManager identifyManager) {
        this.identifyManager = identifyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Only players can identify.</red>"));
            return true;
        }
        boolean force = args.length > 0 && args[0].equalsIgnoreCase("force")
                && sender.hasPermission("glitchitems.admin");
        identifyManager.identify(player, force);
        return true;
    }
}
