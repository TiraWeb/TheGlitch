package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /extractadmin — manage extraction variants.
 * Usage: /extractadmin reload | zones | armed
 */
public record ExtractionVariantCommand(GlitchStash plugin, ExtractionVariantManager manager)
        implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /extractadmin <reload|zones|armed>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.getComponent("admin-reloaded"));
            }
            case "zones" -> {
                List<ExtractionVariantManager.Variant> variants = manager.getVariants();
                sender.sendMessage(Component.text("Extraction variants (" + variants.size() + "):", NamedTextColor.GOLD));
                for (ExtractionVariantManager.Variant v : variants) {
                    String key = v.requiresKey() ? v.keyId() : "none";
                    sender.sendMessage(Component.text(" - ", NamedTextColor.GRAY)
                            .append(Component.text(v.name(), NamedTextColor.WHITE))
                            .append(Component.text(" [" + v.world() + " (" + v.x1() + "," + v.z1()
                                    + ")-(" + v.x2() + "," + v.z2() + ")] key=" + key
                                    + " bonus=" + v.payoutBonus() + "%", NamedTextColor.GRAY)));
                }
            }
            case "armed" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                ExtractionVariantManager.Variant armed = armedVariant(player);
                if (armed == null) {
                    player.sendMessage(plugin.getComponent("variant-not-armed"));
                } else {
                    player.sendMessage(plugin.getComponent("variant-armed-status", "<variant>", armed.name()));
                }
            }
            default -> sender.sendMessage(Component.text("Usage: /extractadmin <reload|zones|armed>", NamedTextColor.RED));
        }
        return true;
    }

    private ExtractionVariantManager.Variant armedVariant(Player player) {
        for (ExtractionVariantManager.Variant v : manager.getVariants()) {
            if (manager.isArmed(player, v)) {
                return v;
            }
        }
        return null;
    }
}
