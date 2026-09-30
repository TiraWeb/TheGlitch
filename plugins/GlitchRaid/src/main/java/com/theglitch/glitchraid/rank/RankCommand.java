package com.theglitch.glitchraid.rank;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** /rank — open the Raider Rank menu; /rank top — top 10 in chat; /rank <player> — look someone up. */
public final class RankCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RankManager ranks;
    private final RankGUI gui;

    public RankCommand(RankManager ranks, RankGUI gui) {
        this.ranks = ranks;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) gui.open(p);
            else sender.sendMessage("Usage: /rank top | /rank <player>");
            return true;
        }
        if (args[0].equalsIgnoreCase("top")) {
            sender.sendMessage(MM.deserialize("<gold><bold>Top Raiders</bold></gold>"));
            int n = 1;
            for (Map.Entry<UUID, RankManager.Entry> e : ranks.top(10)) {
                RankTier t = ranks.tierOf(e.getValue().rr());
                sender.sendMessage(MM.deserialize("<gray>#" + n++ + "</gray> " + t.glyph() + " <white>"
                        + e.getValue().name() + "</white> <gray>— " + t.styled() + " <gray>" + e.getValue().rr() + " RR</gray>"));
            }
            if (n == 1) sender.sendMessage(MM.deserialize("<gray>Nobody ranked yet.</gray>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            sender.sendMessage(MM.deserialize("<red>Unknown player.</red>"));
            return true;
        }
        int rr = ranks.rr(target.getUniqueId());
        RankTier t = ranks.tierOf(rr);
        sender.sendMessage(MM.deserialize(t.glyph() + " <white>" + target.getName() + "</white> <gray>is</gray> " + t.styled()
                + " <gray>(" + rr + " RR, #" + ranks.position(target.getUniqueId()) + ")</gray>"));
        return true;
    }
}
