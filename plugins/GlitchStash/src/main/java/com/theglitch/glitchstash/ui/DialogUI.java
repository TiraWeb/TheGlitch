package com.theglitch.glitchstash.ui;

import com.theglitch.glitchstash.GlitchStash;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class DialogUI {

    private static final boolean SUPPORTED = detectRuntime();
    private static final String REMOTE_PERM_DEFAULT = "theglitch.remoteui";
    private static final int MAX_ITEM_BUTTONS = 20;
    private static final int NAME_MAX = 24;

    private DialogUI() {
    }

    public static boolean supported() {
        return SUPPORTED;
    }

    public static boolean canRemote(Player player) {
        String node = REMOTE_PERM_DEFAULT;
        GlitchStash plugin = GlitchStash.getInstance();
        if (plugin != null) {
            try {
                node = plugin.getConfig().getString("modern-ui.remote-perm", REMOTE_PERM_DEFAULT);
            } catch (Throwable ignored) {
            }
        }
        if (node == null || node.isBlank()) {
            node = REMOTE_PERM_DEFAULT;
        }
        return player.hasPermission(node);
    }

    public static void openStash(GlitchStash plugin, Player player, Runnable chestFallback) {
        try {
            if (plugin.getStashManager() == null) {
                runFallback(chestFallback);
                return;
            }
            List<ItemStack> flat = plugin.getStashManager().listStash(player.getUniqueId());
            String body;
            List<String> actions = new ArrayList<>();
            if (flat.isEmpty()) {
                body = "Your stash is empty.\nExtract loot to fill it.";
                actions.add(button("OK", "green", "Close", "stashui noop"));
            } else {
                int nonEmpty = 0;
                for (ItemStack item : flat) {
                    if (item != null && !item.getType().isAir()) {
                        nonEmpty++;
                    }
                }
                body = "Stash: " + nonEmpty + "/" + flat.size() + " slots"
                        + "\nClick an item to withdraw it to your inventory.";
                int built = 0;
                for (int i = 0; i < flat.size(); i++) {
                    ItemStack item = flat.get(i);
                    if (item == null || item.getType().isAir()) {
                        continue;
                    }
                    if (built >= MAX_ITEM_BUTTONS) {
                        actions.add(button("\u2026 more in chest menu", "dark_gray",
                                "Run /stash for the full chest menu", "stashui noop"));
                        break;
                    }
                    String label = truncate(plainName(item), NAME_MAX) + " x" + item.getAmount();
                    actions.add(button(label, "aqua",
                            prettyMaterial(item), "stashui take " + i));
                    built++;
                }
            }
            boolean shown = show(plugin, player, multiAction("YOUR STASH", "light_purple", body,
                    String.join(",", actions), 2, "Close"));
            if (!shown) {
                runFallback(chestFallback);
            }
        } catch (Throwable t) {
            runFallback(chestFallback);
        }
    }

    public static boolean show(GlitchStash plugin, Player player, String snbt) {
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "dialog show " + player.getName() + " " + snbt);
        } catch (Throwable t) {
            return false;
        }
    }

    public static String esc(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
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

    public static String multiAction(String titleText, String titleColor, String bodyText,
                                     String actionsSnbt, int columns, String exitLabel) {
        return "{type:\"minecraft:multi_action\",title:" + txt(titleText, titleColor, true)
                + ",body:[{type:\"minecraft:plain_message\",contents:" + txt(bodyText, "gray", false)
                + "}],columns:" + columns
                + ",actions:[" + actionsSnbt + "]"
                + (exitLabel == null ? "" : ",exit_action:{label:" + txt(exitLabel, "dark_gray", false) + "}")
                + "}";
    }

    private static void runFallback(Runnable fallback) {
        if (fallback != null) {
            try {
                fallback.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private static String plainName(ItemStack stack) {
        try {
            if (stack.hasItemMeta()) {
                net.kyori.adventure.text.Component custom = stack.getItemMeta().customName();
                if (custom != null) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(custom);
                    if (!plain.isEmpty()) {
                        return plain;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return prettyMaterial(stack);
    }

    private static String prettyMaterial(ItemStack stack) {
        try {
            String mat = stack.getType().name().toLowerCase().replace('_', ' ');
            return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
        } catch (Throwable t) {
            return "Item";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + "\u2026";
    }

    private static boolean detectRuntime() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.type.NoticeTypeImpl");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
