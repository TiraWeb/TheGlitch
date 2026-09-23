package com.theglitch.glitchquests;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class GlitchQuests extends JavaPlugin implements TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static GlitchQuests instance;

    private QuestManager quests;
    private Menus menus;
    private volatile Economy economy;

    public static GlitchQuests get() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        quests = new QuestManager(this);
        menus = new Menus(quests);
        QuestListener listener = new QuestListener(this, quests);

        Bukkit.getPluginManager().registerEvents(listener, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        listener.hookExtraction();

        for (String cmd : List.of("quests", "rewards", "questadmin")) {
            var c = getCommand(cmd);
            if (c != null) { c.setExecutor(this); c.setTabCompleter(this); }
        }

        Bukkit.getScheduler().runTaskTimer(this, listener::tickMinute, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, quests::saveDirty, 6000L, 6000L);
        // players already online after a /reload
        Bukkit.getOnlinePlayers().forEach(quests::data);
    }

    @Override
    public void onDisable() {
        if (quests != null) quests.saveAllSync();
        instance = null;
    }

    public Economy getEconomy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "quests" -> {
                if (sender instanceof Player p) menus.openQuests(p, args.length > 0 && args[0].equalsIgnoreCase("weekly"));
                else sender.sendMessage("Players only.");
            }
            case "rewards" -> {
                if (sender instanceof Player p) menus.openRewards(p);
                else sender.sendMessage("Players only.");
            }
            case "questadmin" -> admin(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void admin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(MM.deserialize("<gray>/questadmin reload | reset <player> | progress <player> <questId> <amount> | list</gray>"));
            return;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                quests.reload();
                sender.sendMessage(MM.deserialize("<green>GlitchQuests reloaded.</green>"));
            }
            case "list" -> {
                sender.sendMessage(MM.deserialize("<gold>Daily:</gold> <gray>" + ids(quests.activeDaily()) + "</gray>"));
                sender.sendMessage(MM.deserialize("<light_purple>Weekly:</light_purple> <gray>" + ids(quests.activeWeekly()) + "</gray>"));
            }
            case "reset" -> {
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) { sender.sendMessage("Player not online."); return; }
                quests.reset(t.getUniqueId());
                sender.sendMessage(MM.deserialize("<green>Reset quest/reward data for " + t.getName() + ".</green>"));
            }
            case "progress" -> {
                Player t = args.length > 3 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) { sender.sendMessage("Usage: /questadmin progress <online player> <questId> <amount>"); return; }
                int amount;
                try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { sender.sendMessage("Bad amount."); return; }
                sender.sendMessage(quests.setProgress(t, args[2], amount)
                        ? "Set " + args[2] + " = " + amount + " for " + t.getName()
                        : "No active quest '" + args[2] + "' (see /questadmin list).");
            }
            default -> sender.sendMessage("Unknown subcommand.");
        }
    }

    private static String ids(List<QuestDef> list) {
        return String.join(", ", list.stream().map(QuestDef::id).toList());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = command.getName().toLowerCase();
        if (name.equals("quests") && args.length == 1) out.addAll(List.of("daily", "weekly"));
        if (name.equals("questadmin")) {
            if (args.length == 1) out.addAll(List.of("reload", "list", "reset", "progress"));
            else if (args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            else if (args.length == 3 && args[0].equalsIgnoreCase("progress")) {
                quests.activeDaily().forEach(q -> out.add(q.id()));
                quests.activeWeekly().forEach(q -> out.add(q.id()));
            }
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        out.removeIf(s -> !s.toLowerCase().startsWith(last));
        return out;
    }
}
