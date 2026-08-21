package com.theglitch.glitchevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * Small helper for resolving config messages into MiniMessage components
 * with alternating placeholder name/value pairs ("x", "12", "world", "glitch_red").
 */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Messages() {
    }

    public static Component msg(GlitchEvents plugin, String key, String... ph) {
        String raw = plugin.getEventManager().getMessageRaw(key);
        if (raw == null || raw.isEmpty()) {
            return Component.text(key);
        }
        try {
            TagResolver.Builder resolvers = TagResolver.builder();
            for (int i = 0; i + 1 < ph.length; i += 2) {
                resolvers.resolver(Placeholder.unparsed(ph[i], ph[i + 1]));
            }
            return MM.deserialize(raw, resolvers.build());
        } catch (Exception e) {
            return Component.text(key);
        }
    }

    public static Component deserializeRaw(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        try {
            return MM.deserialize(raw);
        } catch (Exception e) {
            return Component.empty();
        }
    }
}
