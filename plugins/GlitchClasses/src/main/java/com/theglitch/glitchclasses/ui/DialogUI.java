package com.theglitch.glitchclasses.ui;

import com.theglitch.glitchclasses.ClassData;
import com.theglitch.glitchclasses.ClassGUI;
import com.theglitch.glitchclasses.ClassManager;
import com.theglitch.glitchclasses.GlitchClasses;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

    public static boolean canRemote(Player player) {
        GlitchClasses pl = GlitchClasses.getInstance();
        String perm = pl != null
                ? pl.getConfig().getString("modern-ui.remote-perm", "theglitch.remoteui")
                : null;
        if (perm == null || perm.isBlank() || "*".equals(perm.trim())) return true;
        return player.isOp() || player.hasPermission(perm.trim());
    }

    public static boolean show(JavaPlugin plugin, Player player, String snbt) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dialog show " + player.getName() + " " + snbt);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean showConfirmation(JavaPlugin plugin, Player player, String snbt, Runnable fallback) {
        boolean ok = show(plugin, player, snbt);
        if (!ok && fallback != null) fallback.run();
        return ok;
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

    public static String glyph(String ch, String color) {
        StringBuilder sb = new StringBuilder("{text:").append(esc(ch));
        if (color != null && !color.isEmpty()) sb.append(",color:").append(esc(color));
        return sb.append('}').toString();
    }

    public static String itemBody(String materialId, int count, Integer customModelData) {
        String id = materialId == null || materialId.isEmpty() ? "shield" : materialId;
        String mcId = id.startsWith("minecraft:") ? id : "minecraft:" + id;
        int n = Math.max(1, count);
        if (customModelData == null) {
            return "{type:\"minecraft:item\",item:\"" + mcId + "\",count:" + n + "}";
        }
        return "{type:\"minecraft:item\",item:{id:\"" + mcId + "\",count:" + n
                + ",components:{\"minecraft:custom_model_data\":{floats:[" + customModelData + "]}}}}";
    }

    public static String pipLine(int level, int max) {
        int m = Math.max(1, max);
        int cur = Math.max(0, Math.min(level, m));
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < m; i++) stars.append(i < cur ? "\u2605" : "\u2606");
        return txt(stars + "  Level " + cur + "/" + m, "gold", false);
    }

    public static String button(String label, String color, String tooltip, String template) {
        StringBuilder sb = new StringBuilder("{label:").append(txt(label, color, false));
        if (tooltip != null) sb.append(",tooltip:").append(txt(tooltip, "gray", false));
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

    public static String confirmation(String title, String titleColor, String bodyText,
                                      String yesLabel, String yesTemplate,
                                      String noLabel, String noTemplate) {
        StringBuilder sb = new StringBuilder();
        sb.append("{type:\"minecraft:confirmation\",title:").append(txt(title, titleColor, true));
        sb.append(",body:[{type:\"minecraft:plain_message\",contents:")
                .append(txt(bodyText, "gray", false)).append("}]");
        sb.append(",yes:{label:").append(txt(yesLabel, "green", true))
                .append(",action:{type:\"minecraft:run_command\",command:").append(esc(yesTemplate)).append("}}");
        sb.append(",no:{label:").append(txt(noLabel, "red", true))
                .append(",action:{type:\"minecraft:run_command\",command:").append(esc(noTemplate)).append("}}");
        return sb.append('}').toString();
    }

    public static String multiAction(String title, String titleColor, String body,
                                     String actionsSnbt, int columns, String exitLabel) {
        return multiActionRaw(title, titleColor,
                "[{type:\"minecraft:plain_message\",contents:" + txt(body, "gray", false) + "}]",
                actionsSnbt, columns, exitLabel);
    }

    public static String multiActionRaw(String title, String titleColor, String bodyJsonArray,
                                        String actionsSnbt, int columns, String exitLabel) {
        StringBuilder sb = new StringBuilder();
        String rawBody = bodyJsonArray == null || bodyJsonArray.isBlank()
                ? "[]" : bodyJsonArray.trim();
        if (!rawBody.startsWith("[")) rawBody = "[" + rawBody + "]";
        sb.append("{type:\"minecraft:multi_action\",title:").append(txt(title, titleColor, true));
        sb.append(",body:").append(rawBody);
        sb.append(",columns:").append(Math.max(1, columns));
        sb.append(",actions:[").append(actionsSnbt).append(']');
        if (exitLabel != null) {
            sb.append(",exit_action:{label:").append(txt(exitLabel, "dark_gray", false)).append('}');
        }
        return sb.append('}').toString();
    }

    public static void openRoot(GlitchClasses plugin, ClassGUI gui, Player player, Runnable chestFallback) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        ClassManager cm = plugin.getClassManager();
        ClassData data = cm.getClassData(player.getUniqueId());
        StringBuilder bodyJson = new StringBuilder("[");
        bodyJson.append(componentBody(glyph("\uE048 \uE048 \uE048", null)));
        if (data.className().equals("none")) {
            bodyJson.append(',').append(messageBody(
                    "No class selected.\nFirst pick grants the starter kit.", "gray", false));
        } else {
            bodyJson.append(',').append(messageBody(
                    "Current: " + data.className().toUpperCase(Locale.ROOT)
                            + "\nLevel: " + data.level() + "/" + cm.getMaxLevel()
                            + "\nXP: " + data.xp() + "/" + cm.getXpForLevel(data.level() + 1),
                    "gray", false));
            bodyJson.append(',').append(componentBody(pipLine(data.level(), cm.getMaxLevel())));
        }
        bodyJson.append(']');
        StringBuilder actions = new StringBuilder();
        String[] order = gui.classOrder();
        for (int i = 0; i < order.length; i++) {
            if (i > 0) actions.append(',');
            actions.append(wideButton(plainLabel(plugin, order[i]), dialogColor(order[i]), null,
                    "classui view " + order[i], 150));
        }
        deliver(plugin, player,
                multiActionRaw("CHOOSE YOUR CLASS", "gold", bodyJson.toString(), actions.toString(), 2, "Close"),
                UiKit.titleCustom(UiKit.classGradientFrom(data.className()),
                        UiKit.classGradientTo(data.className()), "CHOOSE YOUR CLASS"),
                chestFallback);
    }

    public static void openClass(GlitchClasses plugin, ClassGUI gui, Player player, String className,
                                 Runnable chestFallback) {
        openClass(plugin, gui, player, className, chestFallback, "classui root");
    }

    public static void openClass(GlitchClasses plugin, ClassGUI gui, Player player, String className,
                                 Runnable chestFallback, String backTemplate) {
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

        String iconMaterial = iconId(cls);

        StringBuilder bodyJson = new StringBuilder("[");
        bodyJson.append(itemBody(iconMaterial, 1, null));
        bodyJson.append(',').append(componentBody(glyph("\uE048 \uE048 \uE048", null)));
        if (!role.isEmpty()) bodyJson.append(',').append(componentBody(txtItalic(role, "gray")));
        if (!description.isEmpty()) bodyJson.append(',').append(messageBody(description, "gray", false));
        int maxLevel = cm.getMaxLevel();
        if (owned) {
            bodyJson.append(',').append(componentBody(pipLine(data.level(), maxLevel)));
            if (data.level() < maxLevel) {
                bodyJson.append(',').append(messageBody(
                        "Level " + data.level() + "/" + maxLevel
                                + " \u00b7 XP " + data.xp() + "/" + cm.getXpForLevel(data.level() + 1)
                                + "\nNext upgrade cost: " + cm.getUpgradeCost(data.level()) + " shards",
                        "yellow", false));
            } else {
                bodyJson.append(',').append(messageBody("MAX LEVEL", "gold", true));
            }
        } else {
            bodyJson.append(',').append(messageBody("Click SELECT to choose this class.", "green", false));
        }
        bodyJson.append(']');

        StringBuilder actions = new StringBuilder();
        if (!owned) {
            actions.append(wideButton("SELECT " + plainLabel(plugin, className), "green", null,
                    "classui select " + className, 250));
        } else if (data.level() < maxLevel) {
            actions.append(wideButton("UPGRADE TO LVL " + (data.level() + 1) + " ("
                            + cm.getUpgradeCost(data.level()) + " shards)", "gold", null,
                    "classui upgrade " + className, 250));
            actions.append(',').append(button("RESET CLASS (" + cm.getResetCost() + " shards)", "red",
                    null, "classui resetask"));
        } else {
            actions.append(wideButton("MAX LEVEL", "gold", null, "classui view " + className, 250));
        }
        actions.append(',').append(wideButton("\u00ab BACK", "yellow", null,
                backTemplate == null || backTemplate.isBlank() ? "classui root" : backTemplate, 100));

        deliver(plugin, player,
                multiActionRaw(className.toUpperCase(Locale.ROOT), dialogColor(className),
                        bodyJson.toString(), actions.toString(), 2, "Close"),
                UiKit.titleCustom(UiKit.classGradientFrom(className),
                        UiKit.classGradientTo(className), className.toUpperCase(Locale.ROOT)),
                chestFallback);
    }

    public static void openResetConfirm(GlitchClasses plugin, ClassGUI gui, Player player,
                                        Runnable chestFallback) {
        openResetConfirm(plugin, gui, player, chestFallback, "classui root");
    }

    public static void openResetConfirm(GlitchClasses plugin, ClassGUI gui, Player player,
                                        Runnable chestFallback, String backTemplate) {
        if (!SUPPORTED) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        String noTemplate = backTemplate == null || backTemplate.isBlank()
                ? "classui root" : backTemplate;
        int cost = plugin.getClassManager().getResetCost();
        String body = "This wipes your class and level.\nCost: " + cost + " shards.\nAre you sure?";
        String legacyActions = button("YES, RESET", "red", null, "classui resetyes")
                + "," + button("NO", "green", null, noTemplate);
        FloatingBanner.show(plugin, player,
                UiKit.titleCustom("#F87171", "#FCA5A5", "RESET CLASS"), 60L);
        showConfirmation(plugin, player,
                confirmation("RESET CLASS", "red", body,
                        "YES", "classui resetyes", "NO", noTemplate),
                () -> deliver(plugin, player,
                        multiAction("RESET CLASS", "red", body, legacyActions, 2, "Cancel"),
                        UiKit.titleCustom("#F87171", "#FCA5A5", "RESET CLASS"),
                        chestFallback));
    }

    private static void deliver(GlitchClasses plugin, Player player, String snbt, String titleMini,
                                Runnable chestFallback) {
        FloatingBanner.show(plugin, player, titleMini, 60L);
        if (!show(plugin, player, snbt) && chestFallback != null) {
            chestFallback.run();
        }
    }

    private static String componentBody(String componentJson) {
        return "{type:\"minecraft:plain_message\",contents:" + componentJson + "}";
    }

    private static String messageBody(String text, String color, boolean bold) {
        return componentBody(txt(text, color, bold));
    }

    private static String txtItalic(String text, String color) {
        return "{text:" + esc(text) + ",color:" + esc(color) + ",italic:1b}";
    }

    private static String iconId(ConfigurationSection cls) {
        String raw = cls != null ? cls.getString("icon", "SHIELD") : "SHIELD";
        if (raw == null || raw.isEmpty()) raw = "SHIELD";
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT)).getKey().getKey();
        } catch (Throwable t) {
            return "shield";
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
}
