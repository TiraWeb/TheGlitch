package com.theglitch.glitchevents;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Handles /glitchevents {start supply_drop|start roaming_boss|stop|reload|status}
 */
public final class GlitchEventsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final List<String> SUBS = List.of("start", "stop", "reload", "status");
    private static final List<String> EVENT_TYPES = List.of("supply_drop", "roaming_boss");

    private final GlitchEvents plugin;
    private final EventManager manager;

    public GlitchEventsCommand(GlitchEvents plugin, EventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("glitchevents.admin")) {
            sender.sendMessage(MM.deserialize("<red>You don't have permission (glitchevents.admin).</red>"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> handleStart(sender, args);
            case "stop" -> {
                manager.cancelAll();
                sender.sendMessage(MM.deserialize("<yellow>All GlitchEvents tasks stopped — auto-scheduling halted until reload.</yellow>"));
            }
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Messages.msg(plugin, "reloaded"));
            }
            case "status" -> sendStatus(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(MM.deserialize("<red>Usage: /glitchevents start <supply_drop|roaming_boss></red>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "supply_drop" -> {
                World world = resolveWorld(sender);
                if (world == null) {
                    sender.sendMessage(MM.deserialize("<red>No players online in an enabled world.</red>"));
                    return;
                }
                if (manager.startSupplyDrop(world)) {
                    sender.sendMessage(MM.deserialize("<green>Supply drop started in <white>" + world.getName() + "</white>.</green>"));
                } else {
                    sender.sendMessage(MM.deserialize("<red>Supply drop failed — see console.</red>"));
                }
            }
            case "roaming_boss" -> {
                Player near = sender instanceof Player p ? p : Bukkit.getOnlinePlayers().stream()
                        .filter(pl -> manager.getEnabledWorlds().contains(pl.getWorld().getName().toLowerCase(Locale.ROOT)))
                        .findFirst().orElse(null);
                if (near == null) {
                    sender.sendMessage(MM.deserialize("<red>No players online in an enabled world to anchor the boss.</red>"));
                    return;
                }
                if (manager.startRoamingBoss(near)) {
                    sender.sendMessage(MM.deserialize("<green>Roaming boss spawned near <white>" + near.getName() + "</white>.</green>"));
                } else {
                    sender.sendMessage(MM.deserialize("<red>Roaming boss failed — see console (MythicMobs installed?).</red>"));
                }
            }
            default -> sender.sendMessage(MM.deserialize("<red>Unknown event type. Use supply_drop or roaming_boss.</red>"));
        }
    }

    private World resolveWorld(CommandSender sender) {
        if (sender instanceof Player p && manager.getEnabledWorlds().contains(p.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return p.getWorld();
        }
        return manager.pickEnabledWorld();
    }

    private void sendStatus(CommandSender sender) {
        long next = manager.getNextEventAtMillis();
        String nextText = "not scheduled";
        if (next > 0L) {
            long seconds = Math.max(0L, (next - System.currentTimeMillis()) / 1000L);
            nextText = seconds / 60 + "m " + seconds % 60 + "s";
        }
        sender.sendMessage(MM.deserialize("<gold><bold>GlitchEvents Status</bold></gold>"));
        sender.sendMessage(MM.deserialize("<gray>Auto-events: <white>" + manager.isAutoEventsEnabled()
                + "</white> | Interval: <white>" + manager.getMinIntervalMinutes() + "-" + manager.getMaxIntervalMinutes() + "m</white></gray>"));
        sender.sendMessage(MM.deserialize("<gray>Next event: <white>" + nextText + "</white> | Active tasks: <white>"
                + manager.getActiveTaskCount() + "</white></gray>"));
        sender.sendMessage(MM.deserialize("<gray>Enabled worlds: <white>" + String.join(", ", manager.getEnabledWorlds()) + "</white></gray>"));
        sender.sendMessage(MM.deserialize("<gray>Types: supply_drop=<white>" + manager.isSupplyDropEnabled()
                + "</white> roaming_boss=<white>" + manager.isRoamingBossEnabled()
                + "</white> extraction_window=<white>" + manager.isExtractionWindowEnabled() + "</white></gray>"));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MM.deserialize("<gold><bold>GlitchEvents</bold></gold>"));
        sender.sendMessage(MM.deserialize("<yellow>/glitchevents start supply_drop</yellow> <gray>— Force a supply drop</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/glitchevents start roaming_boss</yellow> <gray>— Spawn a roaming boss nearby</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/glitchevents stop</yellow> <gray>— Cancel all tasks and scheduling</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/glitchevents reload</yellow> <gray>— Reload config</gray>"));
        sender.sendMessage(MM.deserialize("<yellow>/glitchevents status</yellow> <gray>— Show active tasks and next event</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("glitchevents.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return EVENT_TYPES.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
