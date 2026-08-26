package com.theglitch.glitchclasses.ui;

import com.theglitch.glitchclasses.ClassData;
import com.theglitch.glitchclasses.ClassGUI;
import com.theglitch.glitchclasses.ClassManager;
import com.theglitch.glitchclasses.GlitchClasses;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class DialogUI {

    private static final boolean SUPPORTED = DialogBridge.dialogsRuntime();

    private DialogUI() {
    }

    public static boolean supported() {
        return SUPPORTED;
    }

    public static boolean show(JavaPlugin plugin, Player player, String snbt) {
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

    public static String multiAction(String title, String titleColor, String body,
                                     String actionsSnbt, int columns, String exitLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("{type:\"minecraft:multi_action\",title:").append(txt(title, titleColor, true));
        sb.append(",body:[{type:\"minecraft:plain_message\",contents:")
                .append(txt(body, "gray", false)).append("}]");
        sb.append(",columns:").append(Math.max(1, columns));
        sb.append(",actions:[").append(actionsSnbt).append(']');
        if (exitLabel != null) {
            sb.append(",exit_action:{label:").append(txt(exitLabel, "dark_gray", false)).append('}');
        }
        return sb.append('}').toString();
    }

    public static String button(String label, String color, String tooltip, String template) {
        StringBuilder sb = new StringBuilder("{label:").append(txt(label, color, false));
        if (tooltip != null) sb.append(",tooltip:").append(txt(tooltip, "gray", false));
        sb.append(",action:{type:\"minecraft:run_command\",command:")
                .append(esc(template)).append("}}");
        return sb.toString();
    }

    public static void openRoot(GlitchClasses plugin, ClassGUI gui, Player player, Runnable chestFallback) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        ClassManager cm = plugin.getClassManager();
        ClassData data = cm.getClassData(player.getUniqueId());
        String body;
        if (data.className().equals("none")) {
            body = "No class selected.\nFirst pick grants the starter kit.";
        } else {
            body = "Current: " + data.className().toUpperCase(Locale.ROOT)
                    + "\nLevel: " + data.level() + "/" + cm.getMaxLevel()
                    + "\nXP: " + data.xp() + "/" + cm.getXpForLevel(data.level() + 1);
        }
        StringBuilder actions = new StringBuilder();
        String[] order = gui.classOrder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) actions.append(',');
            actions.append(button(plainLabel(plugin, order[i]), dialogColor(order[i]), null,
                    "classui view " + order[i]));
        }
        deliver(plugin, player,
                multiAction("CHOOSE YOUR CLASS", "gold", body, actions.toString(), 2, "Close"),
                UiKit.titleCustom(UiKit.classGradientFrom(data.className()),
                        UiKit.classGradientTo(data.className()), "CHOOSE YOUR CLASS"),
                chestFallback);
    }

    public static void openClass(GlitchClasses plugin, ClassGUI gui, Player player, String className,
                                 Runnable chestFallback) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        ClassManager cm = plugin.getClassManager();
        ClassData data = cm.getClassData(player.getUniqueId());
        boolean owned = className.equals(data.className());

        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String role = cls != null ? cls.getString("role", "") : "";
        String description = cls != null ? cls.getString("description", "") : "";
        if (!role.isEmpty()) role = UiKit.mm().stripTags(role);
        if (!description.isEmpty()) description = UiKit.mm().stripTags(description);

        StringBuilder body = new StringBuilder();
        appendLine(body, role);
        appendLine(body, description);
        if (owned) {
            appendLine(body, "Level " + data.level() + "/" + cm.getMaxLevel()
                    + " \u00b7 XP " + data.xp() + "/" + cm.getXpForLevel(data.level() + 1));
            if (data.level() < cm.getMaxLevel()) {
                appendLine(body, "Next upgrade cost: " + cm.getUpgradeCost(data.level()) + " shards");
            } else {
                appendLine(body, "MAX LEVEL");
            }
        } else {
            appendLine(body, "Click SELECT to choose this class.");
        }

        StringBuilder actions = new StringBuilder();
        if (!owned) {
            actions.append(button("SELECT " + plainLabel(plugin, className), "green", null,
                    "classui select " + className));
        } else if (data.level() < cm.getMaxLevel()) {
            actions.append(button("UPGRADE TO LVL " + (data.level() + 1) + " ("
                            + cm.getUpgradeCost(data.level()) + " shards)", "gold", null,
                    "classui upgrade " + className));
            actions.append(',').append(button("RESET CLASS (" + cm.getResetCost() + " shards)", "red",
                    null, "classui resetask"));
        } else {
            actions.append(button("MAX LEVEL", "gold", null, "classui view " + className));
        }
        actions.append(',').append(button("\u00ab BACK", "yellow", null, "classui root"));

        deliver(plugin, player,
                multiAction(className.toUpperCase(Locale.ROOT), dialogColor(className), body.toString(),
                        actions.toString(), 2, "Close"),
                UiKit.titleCustom(UiKit.classGradientFrom(className),
                        UiKit.classGradientTo(className), className.toUpperCase(Locale.ROOT)),
                chestFallback);
    }

    public static void openResetConfirm(GlitchClasses plugin, ClassGUI gui, Player player,
                                        Runnable chestFallback) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        int cost = plugin.getClassManager().getResetCost();
        String body = "This wipes your class and level.\nCost: " + cost + " shards.\nAre you sure?";
        String actions = button("YES, RESET", "red", null, "classui resetyes")
                + "," + button("NO", "green", null, "classui root");
        deliver(plugin, player,
                multiAction("RESET CLASS", "red", body, actions, 2, "Cancel"),
                UiKit.titleCustom("#F87171", "#FCA5A5", "RESET CLASS"),
                chestFallback);
    }

    private static void deliver(GlitchClasses plugin, Player player, String snbt, String titleMini,
                                Runnable chestFallback) {
        FloatingBanner.show(plugin, player, titleMini, 60L);
        if (!show(plugin, player, snbt) && chestFallback != null) {
            chestFallback.run();
        }
    }

    private static String dialogColor(String className) {
        return switch (className == null ? "" : className.toLowerCase(Locale.ROOT)) {
            case "vanguard" -> "red";
            case "warden" -> "green";
            case "specter" -> "light_purple";
            case "operator" -> "dark_purple";
            default -> "white";
        };
    }

    private static String plainLabel(GlitchClasses plugin, String className) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String display = cls != null ? cls.getString("display-name", "") : "";
        String base = display.isEmpty() ? className : UiKit.mm().stripTags(display);
        return base.toUpperCase(Locale.ROOT);
    }

    private static void appendLine(StringBuilder b, String line) {
        if (line == null || line.isEmpty()) return;
        if (b.length() > 0) b.append('\n');
        b.append(line);
    }
}
