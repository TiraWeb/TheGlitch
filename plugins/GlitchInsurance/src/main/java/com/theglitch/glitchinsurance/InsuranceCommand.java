package com.theglitch.glitchinsurance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class InsuranceCommand implements CommandExecutor, TabCompleter {

    private final GlitchInsurance plugin;
    private final InsuranceManager manager;

    public InsuranceCommand(GlitchInsurance plugin, InsuranceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getComponent("not-player"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            player.sendMessage(plugin.getComponent("help"));
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "buy":
            case "insure":
            case "add": {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held == null || held.getType().isAir()) {
                    player.sendMessage(plugin.getComponent("hold-item"));
                    return true;
                }
                InsuranceManager.InsureResult result = manager.insureItem(player, held);
                switch (result) {
                    case SUCCESS -> {
                        String itemName = displayName(held);
                        int count = manager.countInsured(player.getUniqueId());
                        player.sendMessage(plugin.getComponent("insured",
                                "<item>", itemName,
                                "<premium>", String.valueOf(manager.getPremiumPerItem()),
                                "<count>", String.valueOf(count),
                                "<max>", String.valueOf(manager.getMaxInsuredItems())));
                    }
                    case ALREADY_INSURED -> player.sendMessage(plugin.getComponent("already-insured"));
                    case MAX_REACHED -> player.sendMessage(plugin.getComponent("max-reached",
                            "<max>", String.valueOf(manager.getMaxInsuredItems())));
                    case NOT_ENOUGH_SHARDS -> player.sendMessage(plugin.getComponent("not-enough-shards",
                            "<premium>", String.valueOf(manager.getPremiumPerItem())));
                    case COOLDOWN -> player.sendMessage(plugin.getComponent("cooldown",
                            "<seconds>", String.valueOf(manager.getCooldownRemaining(player.getUniqueId()))));
                    case AIR -> player.sendMessage(plugin.getComponent("hold-item"));
                    case NO_ECONOMY -> player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
                }
                return true;
            }
            case "list": {
                var insured = manager.getInsured(player.getUniqueId());
                if (insured.isEmpty()) {
                    player.sendMessage(plugin.getComponent("no-insurance"));
                    return true;
                }
                player.sendMessage(plugin.getComponent("list-header",
                        "<count>", String.valueOf(insured.size()),
                        "<max>", String.valueOf(manager.getMaxInsuredItems())));
                for (InsuranceManager.InsuredItem it : insured) {
                    String name = it.itemName();
                    long rem = it.remainingSeconds();
                    player.sendMessage(plugin.getComponent("list-entry",
                            "<item>", name,
                            "<remaining>", String.valueOf(rem)));
                }
                return true;
            }
            case "claim": {
                var claimed = manager.claim(player.getUniqueId());
                if (claimed.isEmpty()) {
                    player.sendMessage(plugin.getComponent("no-insurance"));
                    return true;
                }
                int given = 0;
                for (ItemStack stack : claimed) {
                    var leftover = player.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            player.sendMessage(plugin.getComponent("inventory-full"));
                        }
                    }
                    given++;
                }
                player.sendMessage(plugin.getComponent("claimed",
                        "<count>", String.valueOf(given)));
                // Also send generic claim message
                player.sendMessage(plugin.getComponent("claim"));
                return true;
            }
            default: {
                player.sendMessage(plugin.getComponent("help"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase();
            List<String> opts = List.of("buy", "list", "claim", "help");
            List<String> out = new ArrayList<>();
            for (String o : opts) if (o.startsWith(pref)) out.add(o);
            return out;
        }
        return List.of();
    }

    private static String displayName(ItemStack stack) {
        if (stack == null) return "AIR";
        var meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            try {
                var comp = meta.displayName();
                if (comp != null) {
                    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
                }
            } catch (Throwable ignored) {}
            String d = meta.getDisplayName();
            if (d != null && !d.isBlank()) return d;
        }
        return stack.getType().name().toLowerCase().replace('_', ' ');
    }
}
