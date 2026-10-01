package com.theglitch.glitchbots;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.theglitch.common.Bots;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rogue trash talk: when a rogue first spots a real raider it shouts one short line,
 * written by Gemini (gemini-2.5-flash-lite) in the rogue's random personality, or a
 * canned fallback line when no API key is set, the API errors, or a rate cap is hit.
 * Lines always go out under the "Rogue <name>" label and the prompt forbids claiming
 * to be human. The API key is read from plugins/GlitchBots/gemini.key (or the
 * GEMINI_API_KEY env var / chat.api-key) and is never logged or committed.
 */
final class RogueChat {

    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    private final GlitchBots plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<UUID, Long> botCooldown = new ConcurrentHashMap<>();
    private final Map<String, Long> pairCooldown = new ConcurrentHashMap<>();
    private final Deque<Long> recentCalls = new ArrayDeque<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile String apiKey = "";
    private volatile boolean warnedFailure;

    private boolean enabled;
    private String model;
    private int botCooldownSec, pairCooldownSec, maxPerMinute, maxWords, hearRadius;
    private String systemPrompt;
    private List<String> quirks, fallback;

    RogueChat(GlitchBots plugin) {
        this.plugin = plugin;
    }

    void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("chat.enabled", true);
        model = c.getString("chat.model", "gemini-2.5-flash-lite");
        botCooldownSec = c.getInt("chat.bot-cooldown-seconds", 60);
        pairCooldownSec = c.getInt("chat.same-player-cooldown-seconds", 300);
        maxPerMinute = Math.max(0, c.getInt("chat.max-ai-per-minute", 10));
        maxWords = Math.max(4, c.getInt("chat.max-words", 16));
        hearRadius = Math.max(8, c.getInt("chat.hear-radius", 40));
        systemPrompt = c.getString("chat.system-prompt", "");
        quirks = c.getStringList("chat.quirks");
        fallback = c.getStringList("chat.fallback-lines");
        apiKey = loadKey(c);
        plugin.getLogger().info("Rogue chat: " + (!enabled ? "off" : apiKey.isEmpty()
                ? "fallback lines only (no Gemini key — put it in plugins/GlitchBots/gemini.key)"
                : "Gemini " + model + " (max " + maxPerMinute + " calls/min)"));
    }

    private String loadKey(FileConfiguration c) {
        String k = c.getString("chat.api-key", "");
        if (k == null || k.isBlank()) {
            File f = new File(plugin.getDataFolder(), "gemini.key");
            try {
                if (f.isFile()) k = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                plugin.getLogger().warning("Couldn't read gemini.key: " + e.getClass().getSimpleName());
            }
        }
        if (k == null || k.isBlank()) k = System.getenv("GEMINI_API_KEY");
        return k == null ? "" : k.trim();
    }

    /** A random personality for a new rogue. */
    String randomQuirk() {
        if (quirks.isEmpty()) return "a chaotic scavenger";
        return quirks.get(ThreadLocalRandom.current().nextInt(quirks.size()));
    }

    /** The rogue noticed a real raider (Sentinel target or line of sight). */
    void onSpot(RogueBot bot, Player target) {
        if (!enabled || Bots.isBot(target)) return;
        long now = System.currentTimeMillis();
        Long b = botCooldown.get(bot.npc.getUniqueId());
        if (b != null && now - b < botCooldownSec * 1000L) return;
        String pair = bot.npc.getUniqueId() + ">" + target.getUniqueId();
        Long p = pairCooldown.get(pair);
        if (p != null && now - p < pairCooldownSec * 1000L) return;
        botCooldown.put(bot.npc.getUniqueId(), now);
        pairCooldown.put(pair, now);

        String context = context(bot, target);
        if (apiKey.isEmpty() || !takeAiSlot(now)) {
            say(bot, fallbackLine(target));
            return;
        }
        inFlight.incrementAndGet();
        ask(bot, target, context).whenComplete((line, err) -> {
            inFlight.decrementAndGet();
            String out = (err != null || line == null || line.isBlank()) ? fallbackLine(target) : line;
            if (err != null && !warnedFailure) {
                warnedFailure = true;
                plugin.getLogger().warning("Gemini call failed (" + err.getClass().getSimpleName() + ": "
                        + safe(err.getMessage()) + ") — using fallback lines when it does. Logged once.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> say(bot, out));
        });
    }

    private synchronized boolean takeAiSlot(long now) {
        while (!recentCalls.isEmpty() && now - recentCalls.peekFirst() > 60_000L) recentCalls.pollFirst();
        if (recentCalls.size() >= maxPerMinute || inFlight.get() >= 3) return false;
        recentCalls.addLast(now);
        return true;
    }

    private String context(RogueBot bot, Player target) {
        StringBuilder sb = new StringBuilder();
        sb.append("You just spotted a raider named ").append(target.getName()).append('.');
        var hand = target.getInventory().getItemInMainHand();
        if (!hand.getType().isAir()) {
            String item = hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(hand.getItemMeta().displayName())
                    : hand.getType().name().toLowerCase().replace('_', ' ');
            sb.append(" They're holding ").append(item).append('.');
        }
        Location a = bot.location();
        if (a != null) sb.append(" They are ").append((int) a.distance(target.getLocation())).append(" blocks away.");
        long t = target.getWorld().getTime();
        sb.append(t > 13000 && t < 23000 ? " It's night." : " It's daytime.");
        sb.append(" You've looted ").append(bot.cratesLooted()).append(" crates this raid.");
        sb.append(" Shout your line now.");
        return sb.toString();
    }

    private java.util.concurrent.CompletableFuture<String> ask(RogueBot bot, Player target, String userText) {
        String sys = systemPrompt
                .replace("<name>", bot.handle)
                .replace("<quirk>", bot.quirk)
                .replace("<max_words>", String.valueOf(maxWords));
        JsonObject body = new JsonObject();
        JsonObject sysObj = new JsonObject();
        sysObj.add("parts", parts(sys));
        body.add("systemInstruction", sysObj);
        JsonArray contents = new JsonArray();
        JsonObject user = new JsonObject();
        user.addProperty("role", "user");
        user.add("parts", parts(userText));
        contents.add(user);
        body.add("contents", contents);
        JsonObject gen = new JsonObject();
        gen.addProperty("temperature", 1.1);
        gen.addProperty("maxOutputTokens", 60);
        JsonObject thinking = new JsonObject();
        thinking.addProperty("thinkingBudget", 0);
        gen.add("thinkingConfig", thinking);
        body.add("generationConfig", gen);

        HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(ENDPOINT, model)))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(resp -> {
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + resp.statusCode() + " " + snippet(resp.body()));
            }
            return clean(extract(resp.body()));
        });
    }

    private static JsonArray parts(String text) {
        JsonArray arr = new JsonArray();
        JsonObject p = new JsonObject();
        p.addProperty("text", text);
        arr.add(p);
        return arr;
    }

    private static String extract(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray cands = root.getAsJsonArray("candidates");
        if (cands == null || cands.isEmpty()) return "";
        JsonObject content = cands.get(0).getAsJsonObject().getAsJsonObject("content");
        if (content == null || !content.has("parts")) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonElement e : content.getAsJsonArray("parts")) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("text")) sb.append(o.get("text").getAsString());
        }
        return sb.toString();
    }

    /** One line, no quotes/markdown, word-capped. */
    private String clean(String raw) {
        String s = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("[*_`#\"]", "").trim();
        if (s.startsWith("'") && s.endsWith("'") && s.length() > 1) s = s.substring(1, s.length() - 1);
        String[] words = s.split("\\s+");
        if (words.length > maxWords + 4) s = String.join(" ", java.util.Arrays.copyOf(words, maxWords + 4)) + "...";
        if (s.length() > 140) s = s.substring(0, 140) + "...";
        return s;
    }

    private String fallbackLine(Player target) {
        if (fallback.isEmpty()) return "Fresh loot just walked in.";
        return fallback.get(ThreadLocalRandom.current().nextInt(fallback.size())).replace("<player>", target.getName());
    }

    private void say(RogueBot bot, String line) {
        Location at = bot.location();
        if (at == null || line == null || line.isBlank()) return;
        String msg = "<dark_gray>[</dark_gray><red>Rogue " + bot.handle + "</red><dark_gray>]</dark_gray> <gray>"
                + plugin.mm().escapeTags(line) + "</gray>";
        var comp = plugin.mm().deserialize(msg);
        double r2 = (double) hearRadius * hearRadius;
        for (Player p : Bots.realPlayers(at.getWorld())) {
            if (p.getLocation().distanceSquared(at) <= r2) p.sendMessage(comp);
        }
    }

    void forget(UUID npcId) {
        botCooldown.remove(npcId);
        pairCooldown.keySet().removeIf(k -> k.startsWith(npcId.toString()));
    }

    private String safe(String s) {
        if (s == null) return "";
        return apiKey.isEmpty() ? s : s.replace(apiKey, "***");
    }

    private String snippet(String body) {
        String s = safe(body == null ? "" : body.replaceAll("\\s+", " "));
        return s.length() > 160 ? s.substring(0, 160) : s;
    }
}
