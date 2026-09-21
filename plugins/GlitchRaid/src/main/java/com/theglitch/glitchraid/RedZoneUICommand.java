package com.theglitch.glitchraid;

import com.theglitch.glitchraid.gui.RedZoneSelectGUI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens the Red Zone world-picker GUI — wired to the hub's RedZoneGate NPC. */
public final class RedZoneUICommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final RedZoneSelectGUI gui;

    public RedZoneUICommand(RedZoneSelectGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MM.deserialize("<red>Players only.</red>"));
            return true;
        }
        gui.open(player);
        return true;
    }
}
