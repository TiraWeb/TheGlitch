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
 * written by Gemini (gemini-3.5-flash-lite; 2.5 is closed to new API users) in the rogue's random personality, or a
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
    // Voices: each rogue is British or American (chat.styles), share set by chat.american-share
    private final Map<String, String> styles = new java.util.LinkedHashMap<>();
    private double americanShare, spotAiChance;
    // Free-tier daily budget (Gemini RPD resets at midnight Pacific)
    private int dailyLimit;
    private int usedToday;
    private java.time.LocalDate usageDay;
    private static final java.time.ZoneId PACIFIC = java.time.ZoneId.of("America/Los_Angeles");
    // Conversations: a raider talking in chat near a rogue (or saying its name) gets a reply
    private final Map<String, Deque<String>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReplyTo = new ConcurrentHashMap<>();
    private boolean replyEnabled;
    private int replyRadius, replyCooldownSec, replyWords, historyLines;
    private String replyInstruction;
    private List<String> replyFallback;
    private java.util.regex.Pattern blocked;
    /** Style rejects (forced similes etc.) — same effect as blocked words: use a fallback. */
    private final List<java.util.regex.Pattern> rejects = new java.util.ArrayList<>();
    /** After a 429 (free-tier quota) Gemini is skipped until this time; fallbacks fill in. */
    private volatile long backoffUntil;

    RogueChat(GlitchBots plugin) {
        this.plugin = plugin;
    }

    void reload() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("chat.enabled", true);
        model = c.getString("chat.model", "gemini-3.5-flash-lite");
        botCooldownSec = c.getInt("chat.bot-cooldown-seconds", 60);
        pairCooldownSec = c.getInt("chat.same-player-cooldown-seconds", 300);
        maxPerMinute = Math.max(0, c.getInt("chat.max-ai-per-minute", 10));
        dailyLimit = Math.max(0, c.getInt("chat.daily-ai-limit", 450));
        spotAiChance = Math.max(0, Math.min(1, c.getDouble("chat.spot-ai-chance", 0.6)));
        americanShare = Math.max(0, Math.min(1, c.getDouble("chat.american-share", 0.5)));
        styles.clear();
        var st = c.getConfigurationSection("chat.styles");
        if (st != null) for (String k : st.getKeys(false)) styles.put(k, st.getString(k, ""));
        maxWords = Math.max(4, c.getInt("chat.max-words", 16));
        hearRadius = Math.max(8, c.getInt("chat.hear-radius", 40));
        systemPrompt = c.getString("chat.system-prompt", "");
        quirks = c.getStringList("chat.quirks");
        fallback = c.getStringList("chat.fallback-lines");
        rejects.clear();
        for (String r : c.getStringList("chat.reject-patterns")) {
            try {
                rejects.add(java.util.regex.Pattern.compile(r, java.util.regex.Pattern.CASE_INSENSITIVE));
            } catch (java.util.regex.PatternSyntaxException e) {
                plugin.getLogger().warning("Bad chat.reject-patterns entry: " + r);
            }
        }
        replyEnabled = c.getBoolean("chat.replies.enabled", true);
        replyRadius = Math.max(4, c.getInt("chat.replies.radius", 24));
        replyCooldownSec = Math.max(1, c.getInt("chat.replies.player-cooldown-seconds", 4));
        replyWords = Math.max(4, c.getInt("chat.replies.max-words", 24));
        historyLines = Math.max(2, c.getInt("chat.replies.memory-lines", 8));
        replyInstruction = c.getString("chat.replies.instruction", "");
        replyFallback = c.getStringList("chat.replies.fallback-lines");
        // Lines matching any blocked word are thrown away for a fallback (identity insults,
        // slurs, sexual accusations etc. the prompt forbids but the model occasionally emits)
        List<String> words = c.getStringList("chat.blocked-words");
        blocked = words.isEmpty() ? null : java.util.regex.Pattern.compile(
                // \b = word start, so "rape" doesn't hit "grape" and "jews" doesn't hit "jewel"
                "(?i)\\b(" + String.join("|", words.stream().map(java.util.regex.Pattern::quote).toList()) + ")");
        apiKey = loadKey(c);
        plugin.getLogger().info("Rogue chat: " + (!enabled ? "off" : apiKey.isEmpty()
                ? "fallback lines only (no Gemini key — put it in plugins/GlitchBots/gemini.key)"
                : "Gemini " + model + " (max " + maxPerMinute + " calls/min, " + dailyLimit + "/day)"));
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

    /** british or american, for a new rogue. */
    String randomDialect() {
        return ThreadLocalRandom.current().nextDouble() < americanShare ? "american" : "british";
    }

    /** "AI lines today: n/limit" for /bots status. */
    synchronized String usageLine() {
        rollDay();
        return "Gemini today: " + usedToday + "/" + dailyLimit + (apiKey.isEmpty() ? " (no key)" : "")
                + (System.currentTimeMillis() < backoffUntil ? " [rate-limited, backing off]" : "");
    }

    private void rollDay() {
        java.time.LocalDate today = java.time.LocalDate.now(PACIFIC);
        if (!today.equals(usageDay)) {
            usageDay = today;
            usedToday = 0;
        }
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
        boolean useAi = ThreadLocalRandom.current().nextDouble() < spotAiChance; // save budget for replies
        if (!useAi || apiKey.isEmpty() || now < backoffUntil || !takeAiSlot(now)) {
            say(bot, fallbackLine(target));
            return;
        }
        deliver(bot, target, ask(bot, context, null), () -> fallbackLine(target));
    }

    /** Waits for Gemini, swaps in a fallback when it fails or says something blocked, then speaks. */
    private void deliver(RogueBot bot, Player target, java.util.concurrent.CompletableFuture<String> call,
                         java.util.function.Supplier<String> fallbackSupplier) {
        inFlight.incrementAndGet();
        call.whenComplete((line, err) -> {
            inFlight.decrementAndGet();
            boolean bad = line == null || line.isBlank() || (blocked != null && blocked.matcher(line).find())
                    || rejects.stream().anyMatch(p -> p.matcher(line).find());
            String out = (err != null || bad) ? fallbackSupplier.get() : line;
            if (err != null && String.valueOf(err.getMessage()).contains("HTTP 429")) {
                backoffUntil = System.currentTimeMillis() + 60_000L; // free-tier quota — pause AI for a minute
            } else if (err != null && !warnedFailure) {
                warnedFailure = true;
                plugin.getLogger().warning("Gemini call failed (" + err.getClass().getSimpleName() + ": "
                        + safe(err.getMessage()) + ") — using fallback lines when it does. Logged once.");
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.director().bot(bot.npc.getUniqueId()) == null) return; // died while Gemini was thinking
                remember(bot, target, bot.handle + ": " + out);
                say(bot, out);
            });
        });
    }

    // ---- conversations ----

    /**
     * A real raider said something in chat (main thread). The rogue whose name they used
     * (within hearing range) answers, otherwise the nearest rogue within reply range.
     */
    void onPlayerChat(Player player, String message) {
        if (!enabled || !replyEnabled || Bots.isBot(player) || message == null || message.isBlank()) return;
        long now = System.currentTimeMillis();
        Long last = lastReplyTo.get(player.getUniqueId());
        if (last != null && now - last < replyCooldownSec * 1000L) return;
        Location at = player.getLocation();
        String lower = " " + message.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", " ") + " ";
        RogueBot named = null, nearest = null;
        double namedD = Double.MAX_VALUE, nearestD = Double.MAX_VALUE;
        for (RogueBot b : plugin.director().inWorld(player.getWorld().getName())) {
            Location l = b.location();
            if (l == null || l.getWorld() != at.getWorld()) continue;
            double d = l.distance(at);
            if (d <= hearRadius && d < namedD && lower.contains(" " + b.handle.toLowerCase(java.util.Locale.ROOT) + " ")) {
                named = b;
                namedD = d;
            }
            if (d <= replyRadius && d < nearestD) {
                nearest = b;
                nearestD = d;
            }
        }
        RogueBot bot = named != null ? named : nearest;
        if (bot == null) return;
        lastReplyTo.put(player.getUniqueId(), now);
        String said = message.length() > 200 ? message.substring(0, 200) : message;
        remember(bot, player, player.getName() + ": " + said);

        StringBuilder convo = new StringBuilder("Chat so far between you (").append(bot.handle)
                .append(") and the raider ").append(player.getName()).append(":\n");
        for (String l : history.getOrDefault(key(bot, player), new ArrayDeque<>())) convo.append(l).append("\n");
        convo.append("Reply to their last message now.");

        if (apiKey.isEmpty() || now < backoffUntil || !takeAiSlot(now)) {
            String fb = replyFallbackLine(player);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.director().bot(bot.npc.getUniqueId()) == null) return;
                remember(bot, player, bot.handle + ": " + fb);
                say(bot, fb);
            }, 20L);
            return;
        }
        deliver(bot, player, ask(bot, convo.toString(), replyInstruction.replace("<reply_words>", String.valueOf(replyWords))),
                () -> replyFallbackLine(player));
    }

    private String key(RogueBot bot, Player p) {
        return bot.npc.getUniqueId() + ">" + p.getUniqueId();
    }

    private void remember(RogueBot bot, Player p, String line) {
        Deque<String> h = history.computeIfAbsent(key(bot, p), k -> new ArrayDeque<>());
        synchronized (h) {
            h.addLast(line);
            while (h.size() > historyLines) h.pollFirst();
        }
    }

    private String replyFallbackLine(Player p) {
        if (replyFallback.isEmpty()) return "yeah yeah, talk is cheap, drop the loot";
        return replyFallback.get(ThreadLocalRandom.current().nextInt(replyFallback.size())).replace("<player>", p.getName());
    }

    private synchronized boolean takeAiSlot(long now) {
        rollDay();
        while (!recentCalls.isEmpty() && now - recentCalls.peekFirst() > 60_000L) recentCalls.pollFirst();
        if (recentCalls.size() >= maxPerMinute || inFlight.get() >= 3 || usedToday >= dailyLimit) return false;
        recentCalls.addLast(now);
        usedToday++;
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

    private java.util.concurrent.CompletableFuture<String> ask(RogueBot bot, String userText, String systemSuffix) {
        String sys = systemPrompt
                .replace("<name>", bot.handle)
                .replace("<quirk>", bot.quirk)
                .replace("<style>", styles.getOrDefault(bot.dialect, styles.getOrDefault("british", "")))
                .replace("<max_words>", String.valueOf(systemSuffix == null ? maxWords : replyWords));
        if (systemSuffix != null && !systemSuffix.isBlank()) sys = sys + "\n" + systemSuffix;
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
        gen.addProperty("temperature", 0.95);
        // No thinkingConfig: Gemini 3.x rejects thinkingBudget (HTTP 400), and its default
        // answers in ~0.7 s; "low" thinking spent the token budget and cut lines short.
        gen.addProperty("maxOutputTokens", 60);
        // Swearing is wanted (PG-16 banter); Google's defaults block too much of it. Only
        // block high-severity output — the prompt + blocked-words list cover the rest.
        JsonArray safety = new JsonArray();
        for (String cat : new String[]{"HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"}) {
            JsonObject s = new JsonObject();
            s.addProperty("category", cat);
            s.addProperty("threshold", "BLOCK_ONLY_HIGH");
            safety.add(s);
        }
        body.add("safetySettings", safety);
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
        history.keySet().removeIf(k -> k.startsWith(npcId.toString()));
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
