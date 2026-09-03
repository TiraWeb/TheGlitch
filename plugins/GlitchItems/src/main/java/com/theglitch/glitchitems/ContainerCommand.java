package com.theglitch.glitchitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /glitchcontainers — mark and manage in-world loot containers.
 * Usage: /glitchcontainers set &lt;type&gt; | clear | info | types | reload | scatter | scatterinfo
 * <p>
 * The {@code scatter} sub-command triggers the automatic RED-world scatter
 * ({@link ScatterManager#scatterNow()}) that normally fires every 30m and
 * right after extraction ends. The {@code scatterinfo} view shows cached
 * scatter counts and persisted positions.
 * </p>
 */
public record ContainerCommand(GlitchItems plugin, ContainerManager manager, ScatterManager scatterManager) implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Legacy ctor for tests / callers that don't have a ScatterManager. */
    public ContainerCommand(GlitchItems plugin, ContainerManager manager) {
        this(plugin, manager, null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text(
                    "Usage: /glitchcontainers <set <type>|clear|info|types|reload|scatter|scatterinfo>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "scatter" -> {
                ScatterManager scatter = scatterManager != null ? scatterManager : plugin.getScatterManager();
                if (scatter == null) {
                    sender.sendMessage(Component.text("Scatter not initialized.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Scattering loot in " + scatter.getEnabledWorlds() + "...", NamedTextColor.GRAY));
                // Run scatter off the command thread? Command is already on global region, safe to run direct
                try {
                    scatter.scatterNow();
                    sender.sendMessage(Component.text("Scatter complete — " + scatter.getTrackedCount() + " containers now tracked.", NamedTextColor.GREEN));
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Scatter failed: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            case "scatterinfo" -> {
                ScatterManager scatter = scatterManager != null ? scatterManager : plugin.getScatterManager();
                if (scatter == null) {
                    sender.sendMessage(Component.text("Scatter not initialized.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Scatter — enabled=" + scatter.isEnabled()
                        + " interval=" + scatter.getIntervalMinutes() + "m worlds=" + scatter.getEnabledWorlds(), NamedTextColor.GOLD));
                sender.sendMessage(Component.text("Tracked: " + scatter.getTrackedCount() + " in " + scatter.getDataFile().getPath(), NamedTextColor.GRAY));
                sender.sendMessage(Component.text("Counts: " + scatter.getCounts() + " borderRadius=" + scatter.getBorderRadius()
                        + " clearPrevious=" + scatter.isClearPrevious(), NamedTextColor.GRAY));
            }
            case "set" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /glitchcontainers set <type>", NamedTextColor.RED));
                    return true;
                }
                ContainerManager.ContainerType type = manager.getType(args[1].toLowerCase());
                if (type == null) {
                    sender.sendMessage(Component.text("Unknown container type. Use /glitchcontainers types.",
                            NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || block.getType().isAir()) {
                    player.sendMessage(Component.text("Look at a block first.", NamedTextColor.RED));
                    return true;
                }
                if (!manager.mark(block, type)) {
                    player.sendMessage(Component.text("That block cannot store container data.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(MM.deserialize(
                        "<green>Set <white>" + type.display() + "</white> on "
                                + block.getType() + " at " + block.getX() + "," + block.getY() + "," + block.getZ()));
            }
            case "clear" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || !manager.isContainer(block)) {
                    player.sendMessage(Component.text("Look at a Glitch container first.", NamedTextColor.RED));
                    return true;
                }
                manager.clear(block);
                player.sendMessage(Component.text("Container flag cleared.", NamedTextColor.GRAY));
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                ContainerManager.ContainerType type = block == null ? null : manager.typeOf(block);
                if (type == null) {
                    player.sendMessage(Component.text("That is not a Glitch container.", NamedTextColor.RED));
                    return true;
                }
                player.sendMessage(Component.text(type.display() + " (" + type.name() + ")", NamedTextColor.GOLD));
                player.sendMessage(Component.text("Key: "
                        + (type.requiresKey() ? type.keyId() : "none")
                        + " | Regen: " + type.regenSeconds() + "s | Rolls: " + type.maxRolls(),
                        NamedTextColor.GRAY));
            }
            case "types" -> {
                List<ContainerManager.ContainerType> types = manager.getTypes();
                sender.sendMessage(Component.text("Container types (" + types.size() + "):", NamedTextColor.GOLD));
                for (ContainerManager.ContainerType t : types) {
                    sender.sendMessage(Component.text(" - " + t.name() + " (" + t.material() + ") key="
                            + (t.requiresKey() ? t.keyId() : "none"), NamedTextColor.GRAY));
                }
            }
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("GlitchItems reloaded.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Usage: /glitchcontainers <set <type>|clear|info|types|reload|scatter|scatterinfo>", NamedTextColor.RED));
        }
        return true;
    }
}
