package com.theglitch.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hypixel-style chat confirmation: prints the question followed by clickable
 * [YES] / [NO]. Nothing happens until YES is clicked; each prompt works once
 * and expires after 30s, and a newer prompt for the same player replaces the
 * old one. The confirmed action must re-check balances itself — a prompt can
 * be answered after the player's state has changed.
 */
public final class ChatConfirm {

    private static final Duration LIFETIME = Duration.ofSeconds(30);
    private static final Map<UUID, Long> PENDING = new ConcurrentHashMap<>();

    private ChatConfirm() {
    }

    /** Ask {@code player} to confirm; {@code onYes} runs on the main thread when they click YES. */
    public static void ask(Player player, Component question, Runnable onYes) {
        UUID id = player.getUniqueId();
        long token = System.nanoTime();
        PENDING.put(id, token);

        ClickCallback.Options opts = ClickCallback.Options.builder().uses(1).lifetime(LIFETIME).build();
        Component yes = Component.text("[YES]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Confirm", NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (!(audience instanceof Player p) || !p.getUniqueId().equals(id)) return;
                    if (!PENDING.remove(id, token)) {
                        p.sendMessage(Component.text("That confirmation has expired.", NamedTextColor.GRAY));
                        return;
                    }
                    onYes.run();
                }, opts));
        Component no = Component.text("[NO]", NamedTextColor.RED, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Cancel", NamedTextColor.RED)))
                .clickEvent(ClickEvent.callback(audience -> {
                    if (!(audience instanceof Player p) || !p.getUniqueId().equals(id)) return;
                    if (PENDING.remove(id, token)) {
                        p.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
                    }
                }, opts));

        player.sendMessage(Component.empty());
        player.sendMessage(question);
        player.sendMessage(Component.text("  ").append(yes).append(Component.text("   ")).append(no));
        player.sendMessage(Component.empty());
    }
}
