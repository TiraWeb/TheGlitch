package com.theglitch.glitchitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ArmorCommand implements CommandExecutor {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchItems plugin;
    private final GearManager gearManager;

    public ArmorCommand(GlitchItems plugin, GearManager gearManager) {
        this.plugin = plugin;
        this.gearManager = gearManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("upgrade")) {
            sender.sendMessage(MM.deserialize("<gray>Usage: /armor upgrade [player]</gray>"));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            if (!(sender.isOp() || sender.hasPermission("glitchitems.admin"))) {
                sender.sendMessage(MM.deserialize("<gray>Usage: /armor upgrade [player]</gray>"));
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MM.deserialize("<red>That player is not online.</red>"));
                return true;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MM.deserialize("<gray>Usage: /armor upgrade [player]</gray>"));
                return true;
            }
            target = player;
        }

        ItemStack item = target.getInventory().getItemInMainHand();
        GearRolls rolls = gearManager.parse(item);
        if (rolls == null || rolls.type.isWeapon()) {
            target.sendMessage(MM.deserialize("<red>Hold an armor piece to upgrade.</red>"));
            return true;
        }

        int max = gearManager.armorUpgradeMaxLevel();
        if (rolls.level >= max) {
            target.sendMessage(MM.deserialize("<gray>This piece is fully upgraded (<white>+"
                    + rolls.level + "</white>/" + max + ").</gray>"));
            return true;
        }

        int cost = gearManager.shardCostFor(rolls.rarity, rolls.level);
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            target.sendMessage(MM.deserialize("<red>Economy not available.</red>"));
            return true;
        }
        double balance = economy.getBalance(target);
        if (balance < cost) {
            target.sendMessage(MM.deserialize("<red>You need " + cost + " Shards (you have "
                    + (int) balance + ").</red>"));
            return true;
        }

        Map<String, Integer> materials = gearManager.materialsForLevel(rolls.level + 1);
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String id = entry.getKey();
            int needed = entry.getValue();
            int have = 0;
            for (ItemStack stack : target.getInventory().getContents()) {
                if (stack == null) continue;
                String stackId = OraxenUtil.idOf(stack);
                if (id.equals(stackId)) {
                    have += stack.getAmount();
                }
            }
            if (have < needed) {
                missing.add("<gold>" + id + " x" + needed + "</gold> <gray>(have " + have + ")</gray>");
            }
        }
        if (!missing.isEmpty()) {
            target.sendMessage(MM.deserialize("<red>Missing materials: " + String.join(", ", missing) + "</red>"));
            return true;
        }

        if (!economy.withdrawPlayer(target, cost).transactionSuccess()) {
            target.sendMessage(MM.deserialize("<red>Could not charge the upgrade cost.</red>"));
            return true;
        }

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String id = entry.getKey();
            int needed = entry.getValue();
            int remaining = needed;
            for (int i = 0; i < target.getInventory().getSize() && remaining > 0; i++) {
                ItemStack stack = target.getInventory().getItem(i);
                if (stack == null) continue;
                String stackId = OraxenUtil.idOf(stack);
                if (!id.equals(stackId)) continue;
                int take = Math.min(remaining, stack.getAmount());
                int left = stack.getAmount() - take;
                if (left <= 0) {
                    target.getInventory().setItem(i, null);
                } else {
                    stack.setAmount(left);
                }
                remaining -= take;
            }
        }

        ItemStack upgraded = gearManager.applyUpgrade(item);
        if (upgraded == null) {
            target.sendMessage(MM.deserialize("<red>Could not apply the upgrade.</red>"));
            return true;
        }
        target.getInventory().setItemInMainHand(upgraded);

        StringBuilder matsPart = new StringBuilder();
        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            if (entry.getValue() > 0) {
                matsPart.append(", -").append(entry.getValue()).append("x ").append(entry.getKey());
            }
        }
        target.sendMessage(MM.deserialize("<gold>Upgrade complete — <white>"
                + rolls.type.getLabel() + " +" + (rolls.level + 1) + "</white>/" + max
                + "</gold> <gray>(-" + cost + " Shards" + matsPart + ")</gray>"));
        return true;
    }
}
