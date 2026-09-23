package com.theglitch.glitchquests;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A reward bundle: shards (Vault) + Nexo items + console commands. */
public record Reward(int shards, Map<String, Integer> items, List<String> commands) {

    public static final Reward NONE = new Reward(0, Map.of(), List.of());

    public static Reward parse(ConfigurationSection sec) {
        if (sec == null) return NONE;
        Map<String, Integer> items = new LinkedHashMap<>();
        ConfigurationSection itemSec = sec.getConfigurationSection("items");
        if (itemSec != null) {
            for (String id : itemSec.getKeys(false)) items.put(id, Math.max(1, itemSec.getInt(id, 1)));
        }
        return new Reward(sec.getInt("shards", 0), items, new ArrayList<>(sec.getStringList("commands")));
    }

    public static Reward parseMap(Map<?, ?> map) {
        if (map == null) return NONE;
        int shards = map.get("shards") instanceof Number n ? n.intValue() : 0;
        Map<String, Integer> items = new LinkedHashMap<>();
        if (map.get("items") instanceof Map<?, ?> im) {
            im.forEach((k, v) -> items.put(String.valueOf(k), v instanceof Number n ? Math.max(1, n.intValue()) : 1));
        }
        List<String> commands = new ArrayList<>();
        if (map.get("commands") instanceof List<?> cl) cl.forEach(c -> commands.add(String.valueOf(c)));
        return new Reward(shards, items, commands);
    }

    public void give(Player player, Economy economy) {
        if (shards > 0 && economy != null) economy.depositPlayer(player, shards);
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            ItemStack stack = nexoItem(e.getKey());
            if (stack == null) {
                GlitchQuests.get().getLogger().warning("Reward item '" + e.getKey() + "' is not a Nexo item — skipped");
                continue;
            }
            stack.setAmount(e.getValue());
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
        }
    }

    /** MiniMessage lines describing the reward, for lore. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        if (shards > 0) lines.add("<aqua>+" + shards + " Shards</aqua>");
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            lines.add("<light_purple>+" + e.getValue() + "x " + prettify(e.getKey()) + "</light_purple>");
        }
        return lines;
    }

    private static ItemStack nexoItem(String id) {
        try {
            ItemBuilder builder = NexoItems.itemFromId(id);
            return builder == null ? null : builder.build();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
