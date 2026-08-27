package com.theglitch.glitchinsurance.ui;

import com.theglitch.glitchinsurance.GlitchInsurance;
import com.theglitch.glitchinsurance.InsuranceManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

    public static boolean canRemote(GlitchInsurance plugin, Player player) {
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

    private static int premiumOf(GlitchInsurance plugin) {
        try {
            InsuranceManager m = plugin.getManager();
            if (m != null) return m.getPremiumPerItem();
        } catch (Throwable ignored) {
        }
        return 100;
    }

    private static int maxOf(GlitchInsurance plugin) {
        try {
            InsuranceManager m = plugin.getManager();
            if (m != null) return m.getMaxInsuredItems();
        } catch (Throwable ignored) {
        }
        return 3;
    }

    private static int windowOf(GlitchInsurance plugin) {
        try {
            InsuranceManager m = plugin.getManager();
            if (m != null) return m.getClaimWindowSeconds();
        } catch (Throwable ignored) {
        }
        return 300;
    }

    public static void openRoot(GlitchInsurance plugin, Player player, Runnable chestFallback) {
        if (!supported()) {
            if (chestFallback != null) chestFallback.run();
            return;
        }
        int premium = premiumOf(plugin);
        int max = maxOf(plugin);
        int window = windowOf(plugin);
        StringBuilder body = new StringBuilder();
        body.append("Insure held item: ").append(premium).append(" shards (max ").append(max).append(")");
        body.append("\nClaim window: ").append(window).append("s after death.");
        java.util.List<InsuranceManager.InsuredItem> policies = java.util.List.of();
        try {
            policies = plugin.getManager().getInsured(player.getUniqueId());
        } catch (Throwable ignored) {
        }
        int listed = Math.min(3, policies.size());
        for (int i = 0; i < listed; i++) {
            InsuranceManager.InsuredItem it = policies.get(i);
            if (it == null) continue;
            long rem;
            try {
                rem = it.remainingSeconds();
            } catch (Throwable t) {
                rem = 0L;
            }
            String name;
            try {
                name = it.itemName();
            } catch (Throwable t) {
                name = "item";
            }
            if (name == null || name.isBlank()) name = "item";
            body.append("\n- #").append(i + 1).append(' ').append(name)
                    .append(" \u00b7 ").append(rem).append("s");
        }
        StringBuilder actions = new StringBuilder();
        actions.append(wideButton("INSURE HELD ITEM", "green",
                "Pay " + premium + " shards", "insureui buy", 250));
        for (int i = 0; i < listed; i++) {
            actions.append(',').append(button("CLAIM #" + (i + 1), "gold",
                    "Claim policy #" + (i + 1), "insureui claim " + i));
        }
        boolean shown = show(plugin, player, multiAction(
                "INSURANCE OFFICE", "light_purple",
                body.toString(), actions.toString(), 2, "Close"));
        if (!shown && chestFallback != null) {
            chestFallback.run();
        }
    }

    public static void openBuy(GlitchInsurance plugin, Player player, Runnable fallback) {
        if (!supported()) {
            if (fallback != null) fallback.run();
            return;
        }
        int premium = premiumOf(plugin);
        String actions = wideButton("CONFIRM", "green",
                "Insure the item in your main hand", "insureui buyconfirm", 250)
                + ","
                + button("\u00ab BACK", "yellow", "Never mind", "insureui noop");
        boolean shown = show(plugin, player, multiAction(
                "INSURE ITEM", "light_purple",
                "Hold the item you want to insure.\nCost: " + premium + " shards.",
                actions, 1, "Close"));
        if (!shown && fallback != null) {
            fallback.run();
        }
    }
}
