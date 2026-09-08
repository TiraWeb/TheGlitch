package com.theglitch.glitchraid;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /redportal {pos1, pos2, set, remove, info}.
 * <p>
 * Stand where a portal corner should be (feet position is used, so this works
 * in mid-air over the pit), mark both corners, then {@code set} fills only
 * AIR with END_PORTAL — existing blocks are never replaced or broken.
 * </p>
 */
public final class RedPortalCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;
    private final RedPortalManager portals;

    public RedPortalCommand(GlitchRaid plugin, RedPortalManager portals) {
        this.plugin = plugin;
        this.portals = portals;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchraid.admin")) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission (glitchraid.admin).</red>"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "pos1" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                portals.markPos1(player);
                sender.sendMessage(MM.deserialize("<green>pos1 set at <white>"
                        + RedPortalManager.describe(portals.getPos1(player.getUniqueId())) + "</white></green>"));
            }
            case "pos2" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                portals.markPos2(player);
                sender.sendMessage(MM.deserialize("<green>pos2 set at <white>"
                        + RedPortalManager.describe(portals.getPos2(player.getUniqueId())) + "</white></green>"));
            }
            case "set" -> {
                Player player = requirePlayer(sender);
                if (player == null) return true;
                RedPortalManager.FillResult result = portals.fill(player);
                switch (result.status()) {
                    case OK -> sender.sendMessage(MM.deserialize(
                            "<green><bold>Portal set.</bold></green> <gray>Filled <white>" + result.filled()
                                    + "</white> air blocks with portal, left <white>" + result.skipped()
                                    + "</white> existing blocks untouched.</gray>"));
                    case NO_MARKS -> sender.sendMessage(MM.deserialize(
                            "<red>Mark both corners first: <white>/redportal pos1</white> and <white>/redportal pos2</white>.</red>"));
                    case WORLD_MISMATCH -> sender.sendMessage(MM.deserialize(
                            "<red>pos1 and pos2 are in different worlds — mark both corners in the same world.</red>"));
                    case WORLD_MISSING -> sender.sendMessage(MM.deserialize(
                            "<red>Marked world is not loaded.</red>"));
                    case TOO_BIG -> sender.sendMessage(MM.deserialize(
                            "<red>Region is <white>" + result.volume() + "</white> blocks (max "
                                    + portals.maxVolume() + ") — mark a smaller area.</red>"));
                }
            }
            case "remove" -> {
                int cleared = portals.clear();
                sender.sendMessage(MM.deserialize("<yellow>Portal removed — <white>" + cleared
                        + "</white> portal blocks turned back to air. Everything else untouched.</yellow>"));
            }
            case "info" -> {
                sender.sendMessage(MM.deserialize("<gold><bold>Red Portal</bold> <gray>enabled=<white>"
                        + portals.isEnabled() + "</white> cooldown=<white>" + portals.cooldownSeconds() + "s</white></gray></gold>"));
                RedPortalManager.Mark p1 = sender instanceof Player p ? portals.getPos1(p.getUniqueId()) : null;
                RedPortalManager.Mark p2 = sender instanceof Player p ? portals.getPos2(p.getUniqueId()) : null;
                sender.sendMessage(MM.deserialize("<gray>pos1: <white>" + (p1 == null ? "—" : RedPortalManager.describe(p1)) + "</white></gray>"));
                sender.sendMessage(MM.deserialize("<gray>pos2: <white>" + (p2 == null ? "—" : RedPortalManager.describe(p2)) + "</white></gray>"));
                sender.sendMessage(MM.deserialize("<gray>portal: <white>"
                        + (portals.getRegion() == null ? "not set" : RedPortalManager.describe(portals.getRegion())) + "</white></gray>"));
                if (sender instanceof Player self) {
                    org.bukkit.block.Block feet = self.getLocation().getBlock();
                    sender.sendMessage(MM.deserialize("<gray>your feet: <white>" + feet.getType()
                            + "</white> inside portal: <white>" + portals.contains(self.getLocation()) + "</white></gray>"));
                }
            }
            default -> {
                sender.sendMessage(MM.deserialize("<red>Unknown subcommand.</red>"));
                sendHelp(sender);
            }
        }
        return true;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        sender.sendMessage(MM.deserialize("<red>Players only — stand where the corner should be.</red>"));
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>Red Portal</bold></gold>"));
        sender.sendMessage(MM.deserialize("<yellow>/redportal pos1</yellow> <gray>— mark corner 1 at your feet</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/redportal pos2</yellow> <gray>— mark corner 2 at your feet</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/redportal set</yellow> <gray>— fill air in the area with portal (blocks untouched)</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/redportal remove</yellow> <gray>— turn the portal back to air</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/redportal info</yellow> <gray>— show marks + portal area</gray>"));
    }
}
