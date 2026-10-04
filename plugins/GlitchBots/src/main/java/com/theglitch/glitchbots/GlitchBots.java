package com.theglitch.glitchbots;

import com.theglitch.common.ConfigDefaults;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * GlitchBots — Rogue Raiders. Openly AI raider bots (Citizens player NPCs fighting
 * through Sentinel) that keep quiet Red Zones alive: each red world is topped up to
 * {@code target-per-world} raiders while a real player is in it. Rogues never show
 * in the tab list or player count and their names always carry the "Rogue" label.
 */
public final class GlitchBots extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private BotConfig config;
    private GlitchHooks hooks;
    private BotDirector director;
    private RogueChat chat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigDefaults.merge(this);
        config = new BotConfig(getConfig());
        hooks = new GlitchHooks(this);
        chat = new RogueChat(this);
        chat.reload();
        director = new BotDirector(this);
        Bukkit.getPluginManager().registerEvents(new BotListener(this), this);
        director.start();
        getLogger().info("Rogue Raiders ready — target " + config.targetPerWorld + " raiders per occupied world " + config.worlds
                + (config.enabled ? "" : " (disabled in config)"));
    }

    @Override
    public void onDisable() {
        if (director != null) {
            director.stop();
            int n = director.clearAll();
            if (n > 0) getLogger().info("Removed " + n + " rogues.");
        }
    }

    BotConfig cfg() {
        return config;
    }

    GlitchHooks hooks() {
        return hooks;
    }

    RogueChat chat() {
        return chat;
    }

    BotDirector director() {
        return director;
    }

    MiniMessage mm() {
        return MM;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(MM.deserialize("<gold>Rogue Raiders</gold> <gray>(" + (config.enabled ? "on" : "off")
                        + ", target " + config.targetPerWorld + "/world) · " + chat.usageLine() + "</gray>"));
                for (String name : config.worlds) {
                    World w = Bukkit.getWorld(name);
                    if (w == null) continue;
                    int real = BotDirector.raiders(w).size();
                    List<RogueBot> here = director.inWorld(name);
                    long leaving = here.stream().filter(b -> b.leaving || b.state == RogueBot.State.EXTRACT).count();
                    long parked = here.stream().filter(b -> b.parkedAt != null).count();
                    StringBuilder states = new StringBuilder();
                    for (RogueBot b : here) {
                        Location l = b.location();
                        if (l == null && b.parkedAt != null) {
                            states.append(" ").append(b.handle).append("[").append(b.disposition.name().substring(0, 1).toLowerCase()).append("]:parked@").append(b.parkedAt.getBlockX()).append(",").append(b.parkedAt.getBlockZ());
                            continue;
                        }
                        // admin-only view: hidden disposition (f friendly, b betrayer, B betrayed, h hostile)
                        String disp = switch (b.disposition) {
                            case FRIENDLY -> "f";
                            case BETRAYER -> b.betrayed ? "B" : "b";
                            case HOSTILE -> "h";
                        };
                        states.append(" ").append(b.handle).append("[").append(disp).append("]:").append(b.state.name().toLowerCase())
                                .append(l == null ? "" : "@" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
                    }
                    sender.sendMessage(MM.deserialize("<gray>" + name + ": <white>" + real + "</white> real, <red>" + here.size()
                            + "</red> rogues (" + parked + " parked, " + leaving + " leaving)" + (hooks.inBuffer(name) ? " <yellow>[buffer]</yellow>" : "") + "</gray>"));
                    if (!here.isEmpty()) sender.sendMessage(MM.deserialize("<dark_gray>" + states.toString().trim() + "</dark_gray>"));
                }
            }
            case "spawn" -> {
                // /bots spawn <world> [count] [x z] — admin test; x/z spawns there (chunk force-loaded) even with nobody online
                if (args.length < 2) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /bots spawn <world> [count] [x z]</red>"));
                    return true;
                }
                World w = Bukkit.getWorld(args[1]);
                if (w == null) {
                    sender.sendMessage(MM.deserialize("<red>Unknown world.</red>"));
                    return true;
                }
                int count = args.length >= 3 ? Math.max(1, Math.min(20, parse(args[2], 1))) : 1;
                int made = 0;
                if (args.length >= 5) {
                    int x = parse(args[3], 0), z = parse(args[4], 0);
                    w.getChunkAt(x >> 4, z >> 4).addPluginChunkTicket(this);
                    director.holdForTest(w.getName(), 120_000L);
                    for (int i = 0; i < count; i++) {
                        Location spot = BotDirector.groundSpot(w, x + i * 2, z);
                        if (spot != null && director.spawnAt(spot) != null) made++;
                    }
                } else {
                    for (int i = 0; i < count; i++) if (director.spawnNear(w, BotDirector.raiders(w))) made++;
                }
                sender.sendMessage(MM.deserialize("<gray>Spawned <white>" + made + "</white>/" + count + " rogues in " + w.getName() + ".</gray>"));
            }
            case "tutorial" -> {
                // /bots tutorial <player> <x> <y> <z>  — GlitchTutorial's training rogue (fights only that player)
                // /bots tutorial <player> clear       — remove it again
                org.bukkit.entity.Player trainee = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
                if (trainee == null) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /bots tutorial <player> <x> <y> <z> | clear</red>"));
                    return true;
                }
                director.clearTutorial(trainee.getUniqueId());
                if (args.length >= 3 && args[2].equalsIgnoreCase("clear")) return true;
                if (args.length < 5) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /bots tutorial <player> <x> <y> <z></red>"));
                    return true;
                }
                try {
                    Location at = new Location(trainee.getWorld(), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
                    RogueBot bot = director.spawnAt(at, trainee.getUniqueId());
                    sender.sendMessage(MM.deserialize(bot == null ? "<red>Couldn't spawn the training rogue.</red>" : "<gray>Training rogue " + bot.handle + " spawned.</gray>"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(MM.deserialize("<red>Bad coordinates.</red>"));
                }
            }
            case "clear" -> {
                int n = args.length >= 2 ? director.clearWorld(args[1]) : director.clearAll();
                for (World w : Bukkit.getWorlds()) w.removePluginChunkTickets(this);
                sender.sendMessage(MM.deserialize("<gray>Removed <white>" + n + "</white> rogues.</gray>"));
            }
            case "reload" -> {
                reloadConfig();
                config = new BotConfig(getConfig());
                chat.reload();
                director.start();
                sender.sendMessage(MM.deserialize("<green>GlitchBots reloaded.</green>"));
            }
            default -> sender.sendMessage(MM.deserialize("<red>Usage: /bots <status|spawn <world> [count] [x z]|clear [world]|reload></red>"));
        }
        return true;
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
