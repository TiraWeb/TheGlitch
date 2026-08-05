package com.theglitch.glitchhealthbar;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GhBarCommand implements CommandExecutor {

    private final GlitchHealthBar plugin;

    public GhBarCommand(GlitchHealthBar plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "test" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can use /ghb test.");
                    return true;
                }
                plugin.getManager().attachTestBar(player);
                sender.sendMessage("Test bar spawned above you — visible for 10 seconds.");
                return true;
            }
            case "count" -> {
                sender.sendMessage("Tracked mobs: " + plugin.getManager().count());
                return true;
            }
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage("Config reloaded and mobs rescanned.");
                return true;
            }
            default -> sender.sendMessage("Usage: /ghb <test|count|reload>");
        }
        return true;
    }
}
