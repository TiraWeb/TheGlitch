package com.theglitch.glitchdungeons.ui;

import com.theglitch.glitchdungeons.GlitchDungeons;
import com.theglitch.glitchdungeons.config.DungeonConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DialogUI {

    private static final Boolean SUPPORTED = probeRuntime();

    private DialogUI() {
    }

    private static Boolean probeRuntime() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean supported() {
        return SUPPORTED != null && SUPPORTED;
    }

    public static boolean canRemote(GlitchDungeons plugin, Player player) {
        String perm = "theglitch.remoteui";
        try {
            String configured = plugin.getConfig().getString("modern-ui.remote-perm", "theglitch.remoteui");
            if (configured != null) {
                perm = configured.trim();
            }
        } catch (Throwable ignored) {
        }
        if (perm.isBlank() || "*".equals(perm)) {
            return true;
        }
        try {
            return player.isOp() || player.hasPermission(perm);
        } catch (Throwable t) {
            return false;
        }
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
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String txt(String text, String color, boolean bold) {
        return "{text:" + esc(text)
                + (color == null ? "" : ",color:" + esc(color))
                + (bold ? ",bold:1b" : "")
                + "}";
    }

    public static String button(String label, String color, String tooltip, String template) {
        return "{label:" + txt(label, color, false)
                + (tooltip == null ? "" : ",tooltip:" + txt(tooltip, "dark_gray", false))
                + ",action:{type:\"minecraft:run_command\",command:" + esc(template) + "}}";
    }

    public static String wideButton(String label, String color, String tooltip, String template, int width) {
        return "{label:" + txt(label, color, false)
                + (tooltip == null ? "" : ",tooltip:" + txt(tooltip, "dark_gray", false))
                + ",width:" + Math.max(1, width)
                + ",action:{type:\"minecraft:run_command\",command:" + esc(template) + "}}";
    }

    public static String multiAction(String titleText, String titleColor, String bodyText,
                                     String actionsSnbt, int columns, String exitLabel) {
        return "{type:\"minecraft:multi_action\",title:" + txt(titleText, titleColor, true)
                + ",body:[{type:\"minecraft:plain_message\",contents:" + txt(bodyText, "gray", false)
                + "}],columns:" + columns
                + ",actions:[" + actionsSnbt + "]"
                + (exitLabel == null ? "" : ",exit_action:{label:" + txt(exitLabel, "dark_gray", false) + "}")
                + "}";
    }

    private static int partyLimit(GlitchDungeons plugin) {
        try {
            return Math.max(1, plugin.getDungeonConfig().getMaxPartySize());
        } catch (Throwable t) {
            return 4;
        }
    }

    private record Gate(int tier, String name, int maxTime) {
    }

    private static List<Gate> gates(GlitchDungeons plugin) {
        List<Gate> out = new ArrayList<>();
        try {
            DungeonConfig config = plugin.getDungeonConfig();
            if (config == null) return out;
            List<Integer> tiers = new ArrayList<>(config.getDungeons().keySet());
            tiers.sort(Integer::compareTo);
            for (Integer id : tiers) {
                if (id == null) continue;
                var tc = config.getDungeon(id);
                String name = tc != null && tc.getName() != null && !tc.getName().isBlank()
                        ? tc.getName() : ("Tier " + id);
                int maxTime = tc != null ? tc.getMaxTime() : 600;
                out.add(new Gate(id, name, maxTime));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String fmtTime(int seconds) {
        int safe = Math.max(0, seconds);
        return (safe / 60) + ":" + String.format(Locale.ROOT, "%02d", safe % 60);
    }

    public static void openRoot(GlitchDungeons plugin, Player player, Runnable chestFallback) {
        if (!supported()) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        int limit = partyLimit(plugin);
        StringBuilder body = new StringBuilder();
        body.append("Pick a dungeon gate.\nParty size limits apply.");
        List<Gate> list = gates(plugin);
        for (Gate g : list) {
            body.append("\n- ").append(g.name()).append(" \u00b7 TIER ").append(g.tier());
        }
        if (list.isEmpty()) {
            body.append("\n(no dungeons configured)");
        }
        StringBuilder actions = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) actions.append(',');
            Gate g = list.get(i);
            actions.append(button(
                    g.name().toUpperCase(Locale.ROOT) + " \u2014 TIER " + g.tier(),
                    "aqua",
                    "Max " + limit + " players \u00b7 " + fmtTime(g.maxTime()) + " timer",
                    "dungeonui open " + g.tier()));
        }
        if (list.isEmpty()) {
            actions.append(button("NO GATES CONFIGURED", "dark_gray",
                    "Ask an operator to enable dungeons", "dungeonui noop"));
        }
        boolean shown = show(plugin, player, multiAction(
                "DUNGEON GATES", "light_purple",
                body.toString(), actions.toString(), 2, "Close"));
        if (!shown && chestFallback != null) {
            chestFallback.run();
        }
    }
}
