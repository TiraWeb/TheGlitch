package com.theglitch.glitchhideout.ui;

import com.theglitch.glitchhideout.GlitchHideout;
import com.theglitch.glitchhideout.HideoutManager;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogUI {

    private static final boolean SUPPORTED = DialogBridge.dialogsRuntime();

    private DialogUI() {
    }

    public static boolean supported() {
        return SUPPORTED;
    }

    public static boolean show(GlitchHideout plugin, Player player, String snbt) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dialog show " + player.getName() + " " + snbt);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String esc(String s) {
        StringBuilder sb = new StringBuilder("\"");
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\' || c == '"') sb.append('\\');
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    public static String txt(String text, String color, boolean bold) {
        StringBuilder sb = new StringBuilder("{text:").append(esc(text));
        if (color != null && !color.isEmpty()) sb.append(",color:").append(esc(color));
        if (bold) sb.append(",bold:1b");
        return sb.append('}').toString();
    }

    public static String button(String label, String color, String tooltip, String template) {
        StringBuilder sb = new StringBuilder("{label:").append(txt(label, color, false));
        if (tooltip != null) sb.append(",tooltip:").append(txt(tooltip, "dark_gray", false));
        sb.append(",action:{type:\"minecraft:run_command\",command:")
                .append(esc(template)).append("}}");
        return sb.toString();
    }

    public static String wideButton(String label, String color, String tooltip, String template, int width) {
        StringBuilder sb = new StringBuilder("{label:").append(txt(label, color, false));
        if (tooltip != null) sb.append(",tooltip:").append(txt(tooltip, "gray", false));
        sb.append(",width:").append(Math.max(1, width));
        sb.append(",action:{type:\"minecraft:run_command\",command:")
                .append(esc(template)).append("}}");
        return sb.toString();
    }

    public static String multiAction(String title, String titleColor, String body,
                                     String actionsSnbt, int columns, String exitLabel) {
        return multiActionBody(title, titleColor,
                "[{type:\"minecraft:plain_message\",contents:" + txt(body, "gray", false) + "}]",
                actionsSnbt, columns, exitLabel);
    }

    public static String multiActionBody(String title, String titleColor, String bodyJsonArray,
                                         String actionsSnbt, int columns, String exitLabel) {
        StringBuilder sb = new StringBuilder("{type:\"minecraft:multi_action\",title:")
                .append(txt(title, titleColor, true));
        String rawBody = bodyJsonArray == null || bodyJsonArray.isBlank()
                ? "[]" : bodyJsonArray.trim();
        if (!rawBody.startsWith("[")) rawBody = "[" + rawBody + "]";
        sb.append(",body:").append(rawBody);
        sb.append(",columns:").append(Math.max(1, columns));
        sb.append(",actions:[").append(actionsSnbt).append(']');
        if (exitLabel != null) {
            sb.append(",exit_action:{label:").append(txt(exitLabel, "dark_gray", false)).append('}');
        }
        return sb.append('}').toString();
    }

    public static boolean canRemote(GlitchHideout plugin, Player player) {
        String perm;
        try {
            perm = plugin.getConfig().getString("modern-ui.remote-perm", "theglitch.remoteui");
        } catch (Throwable t) {
            perm = "theglitch.remoteui";
        }
        if (perm == null || perm.isBlank() || "*".equals(perm.trim())) return true;
        return player.isOp() || player.hasPermission(perm.trim());
    }

    public static void openRoot(GlitchHideout plugin, Player player, Runnable chestFallback) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        HideoutManager manager = plugin.getHideoutManager();
        StringBuilder lines = new StringBuilder();
        Economy economy = null;
        try {
            economy = plugin.getEconomy();
        } catch (Throwable ignored) {
        }
        if (economy != null) {
            int balance;
            try {
                balance = (int) economy.getBalance(player);
            } catch (Throwable t) {
                balance = 0;
            }
            lines.append("Balance: ").append(balance).append(" Shards\n");
        }
        lines.append("Upgrade stations, craft gear.");
        String bodyJson = "[{type:\"minecraft:plain_message\",contents:"
                + txt(lines.toString(), "gray", false) + "}]";

        StringBuilder actions = new StringBuilder();
        List<HideoutManager.Station> stations = manager.getStations();
        for (int i = 0; i < stations.size(); i++) {
            if (i > 0) actions.append(',');
            HideoutManager.Station station = stations.get(i);
            int level = Math.min(manager.getLevel(player.getUniqueId(), station.id()), costs(station));
            String tooltip = level < costs(station)
                    ? "Next: " + station.costs()[level] + " shards"
                    : "Fully upgraded";
            actions.append(button(plainDisplay(station.display()) + " Lv " + level + "/" + costs(station),
                    "aqua", tooltip, "hideoutui station " + station.id()));
        }
        boolean shown = show(plugin, player, multiActionBody("THE HIDEOUT", "light_purple",
                bodyJson, actions.toString(), 2, "Close"));
        if (!shown && chestFallback != null) chestFallback.run();
    }

    public static void openStation(GlitchHideout plugin, Player player, String id, String backTemplate) {
        if (!SUPPORTED) return;
        HideoutManager manager = plugin.getHideoutManager();
        HideoutManager.Station station = manager.getStation(id);
        if (station == null) {
            openRoot(plugin, player, null);
            return;
        }
        int level = Math.min(manager.getLevel(player.getUniqueId(), station.id()), costs(station));
        int max = costs(station);
        boolean maxed = level >= max;

        List<String> bodyParts = new ArrayList<>();
        bodyParts.add(messageBody(station.id().toUpperCase(Locale.ROOT), "gold", true));
        bodyParts.add(messageBody("Level " + level + "/" + max, "gold", false));
        String description = plainDisplay(station.description());
        if (!description.isEmpty()) {
            bodyParts.add(messageBody(description, "gray", false));
        }
        if (maxed) {
            bodyParts.add(componentBody(txt("MAX LEVEL", "gold", true)));
        } else {
            bodyParts.add(messageBody("Next upgrade: " + station.costs()[level] + " shards", "yellow", false));
            String prereq = unmetPrereq(manager, player, station, level);
            if (prereq != null) {
                bodyParts.add(messageBody(prereq, "red", false));
            }
        }
        StringBuilder bodyJson = new StringBuilder("[");
        for (int i = 0; i < bodyParts.size(); i++) {
            if (i > 0) bodyJson.append(',');
            bodyJson.append(bodyParts.get(i));
        }
        bodyJson.append(']');

        StringBuilder actions = new StringBuilder();
        if (maxed) {
            actions.append(wideButton("MAX LEVEL", "dark_gray", null, backTemplate, 250));
        } else {
            actions.append(wideButton("UPGRADE \u2014 " + station.costs()[level] + " shards", "green",
                    null, "hideoutui upgrade " + station.id(), 250));
        }
        if ("workbench".equals(station.id()) && !manager.getRecipes().isEmpty()) {
            actions.append(',').append(button("WORKBENCH", "gold", "Open crafting", "hideoutui workbench"));
        }
        actions.append(',').append(button("\u00ab BACK", "yellow", null, backTemplate));

        show(plugin, player, multiActionBody(plainDisplay(station.display()).toUpperCase(Locale.ROOT),
                "light_purple", bodyJson.toString(), actions.toString(), 2, "Close"));
    }

    public static void openWorkbench(GlitchHideout plugin, Player player, String backTemplate) {
        if (!SUPPORTED) return;
        List<HideoutManager.Recipe> recipes = plugin.getHideoutManager().getRecipes();
        StringBuilder actions = new StringBuilder();
        for (int i = 0; i < recipes.size(); i++) {
            if (i > 0) actions.append(',');
            HideoutManager.Recipe recipe = recipes.get(i);
            actions.append(button(recipeLabel(plugin, recipe), "aqua",
                    materialsTooltip(recipe), "hideoutui craft " + recipe.id()));
        }
        if (recipes.isEmpty()) {
            actions.append(button("No recipes available", "dark_gray",
                    "The rift provides nothing yet.", backTemplate));
        }
        actions.append(',').append(button("Upgrade Held Armor", "yellow",
                "Upgrade the armor piece you are holding", "hideoutui upgrade-armor"));
        actions.append(',').append(button("\u00ab BACK", "yellow", null, backTemplate));
        show(plugin, player, multiAction("WORKBENCH", "gold",
                "Craft gear from rift materials:", actions.toString(), 2, "Close"));
    }

    private static int costs(HideoutManager.Station station) {
        return station.costs().length;
    }

    private static String unmetPrereq(HideoutManager manager, Player player, HideoutManager.Station station, int level) {
        try {
            // Prereqs are keyed by the level being upgraded TO — match the chest
            // GUI (HideoutGUI#stationCard) which reads requires().get(level + 1).
            String req = station.requires().get(level + 1);
            if (req == null || req.isBlank()) return null;
            String[] parts = req.split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return null;
            int needed = Integer.parseInt(parts[1].trim());
            String dep = parts[0].trim();
            if (manager.getLevel(player.getUniqueId(), dep) < needed) {
                return "Requires: " + dep + " Lv " + needed;
            }
        } catch (NumberFormatException | ClassCastException ignored) {
        }
        return null;
    }

    private static String recipeLabel(GlitchHideout plugin, HideoutManager.Recipe recipe) {
        int mats = recipe.materials() == null ? 0 : recipe.materials().size();
        return plainDisplay(recipe.display()) + " (" + mats + " mats)";
    }

    private static String materialsTooltip(HideoutManager.Recipe recipe) {
        StringBuilder sb = new StringBuilder();
        if (recipe.materials() != null) {
            for (var entry : recipe.materials().entrySet()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(entry.getKey()).append(" x").append(entry.getValue());
            }
        }
        String full = sb.length() == 0 ? "Free" : sb.toString();
        return truncate(full, 96);
    }

    private static String plainDisplay(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return "";
        try {
            String plain = PlainTextComponentSerializer.plainText()
                    .serialize(GlitchHideout.mm().deserialize(miniMessage));
            return plain == null ? "" : plain.strip();
        } catch (Throwable t) {
            return miniMessage.replaceAll("<[^>]*>", "");
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    private static String messageBody(String text, String color, boolean bold) {
        return componentBody(txt(text, color, bold));
    }

    private static String componentBody(String componentJson) {
        return "{type:\"minecraft:plain_message\",contents:" + componentJson + "}";
    }
}
