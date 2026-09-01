package com.theglitch.glitchhud;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player sidebar scoreboard.
 * <p>
 * Uses Paper's {@link NumberFormat} API (1.20.3+):
 * - Left side = {@link Score#customName(Component)} (MiniMessage line)
 * - Right side = {@link NumberFormat#blank()} for clean icon-sidebar (no red numbers).
 * <p>
 * Dirty-checked per-player to avoid packet spam; world-aware titles/lines.
 * Folia-safe via global-region scheduler (GlitchHUD is the owner plugin).
 * </p>
 */
public final class HudManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String OBJ_NAME = "glitchhud";
    // Unique entry keys — invisible § color codes ensure uniqueness without visible text
    private static final String[] KEYS = {
            "\u00A70", "\u00A71", "\u00A72", "\u00A73", "\u00A74",
            "\u00A75", "\u00A76", "\u00A77", "\u00A78", "\u00A79",
            "\u00A7a", "\u00A7b", "\u00A7c", "\u00A7d", "\u00A7e"
    };

    private final GlitchHUD plugin;
    private final PlaceholderResolver placeholders;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> lastRendered = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastTitle = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hiddenTransient = new ConcurrentHashMap<>();
    private final NamespacedKey toggleKey;

    private volatile boolean enabled = true;
    private volatile int refreshTicks = 20;
    private volatile boolean rememberToggle = false;
    private volatile String titleHub = "<gradient:#C084FC:#F0ABFC><bold>THE GLITCH</bold></gradient>";
    private volatile String titlePve = "<gradient:#C084FC:#F0ABFC><bold>THE GLITCH</bold></gradient>";
    private volatile String titleRed = "<gradient:#FF2A2A:#FF6A00><bold>RED ZONE</bold></gradient>";
    private volatile String titleDefault = "<gradient:#C084FC:#F0ABFC><bold>THE GLITCH</bold></gradient>";

    private FoliaScheduler.Cancellable ticker;
    private volatile int tickCounter = 0;

    public HudManager(GlitchHUD plugin, PlaceholderResolver placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        this.toggleKey = new NamespacedKey(plugin, "hud_hidden");
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("hud.enabled", true);
        refreshTicks = Math.max(5, plugin.getConfig().getInt("hud.refresh-ticks", 20));
        rememberToggle = plugin.getConfig().getBoolean("hud.remember-toggle", false);
        titleHub = plugin.getConfig().getString("hud.title.hub", titleHub);
        titlePve = plugin.getConfig().getString("hud.title.pve", titlePve);
        titleRed = plugin.getConfig().getString("hud.title.red", titleRed);
        titleDefault = plugin.getConfig().getString("hud.title.default", titleDefault);
    }

    public void start() {
        if (ticker != null) try { ticker.cancel(); } catch (Exception ignored) {}
        // Rebuild all online players
        for (Player p : Bukkit.getOnlinePlayers()) ensureBoard(p);
        ticker = FoliaScheduler.runAtFixedRateGlobal(plugin, this::tick, refreshTicks, refreshTicks);
    }

    public void shutdown() {
        if (ticker != null) try { ticker.cancel(); } catch (Exception ignored) {}
        ticker = null;
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                try { p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) {}
            }
        }
        boards.clear();
        lastRendered.clear();
        lastTitle.clear();
    }

    private void tick() {
        if (!enabled) return;
        tickCounter++;
        boolean doTeamSync = (tickCounter % 100 == 0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isHidden(p)) continue;
            if (doTeamSync) {
                Scoreboard b = boards.get(p.getUniqueId());
                if (b != null) syncTeams(b);
            }
            try { refresh(p); } catch (Exception e) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("HUD refresh failed for " + p.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    public void ensureBoard(Player player) {
        if (!enabled) {
            hideBoard(player);
            return;
        }
        if (isHidden(player)) {
            hideBoard(player);
            return;
        }
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY, deserialize(titleFor(player)));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.setRenderType(RenderType.INTEGER);
            try { obj.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
            boards.put(player.getUniqueId(), board);
            // Mirror TAB/LuckPerms teams so nametags/tab colors survive per-player scoreboards
            syncTeams(board);
        } else if (tickCounter % 100 == 0) {
            // Periodic team sync (LuckPerms changes, TAB reload)
            syncTeams(board);
        }
        try { player.setScoreboard(board); } catch (Exception ignored) {}
        // Force immediate refresh on ensure
        try { refresh(player); } catch (Exception ignored) {}
    }

    private void syncTeams(Scoreboard dest) {
        try {
            Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Team src : main.getTeams()) {
                Team dst = dest.getTeam(src.getName());
                if (dst == null) {
                    try { dst = dest.registerNewTeam(src.getName()); } catch (Exception e) { continue; }
                }
                try { dst.displayName(src.displayName()); } catch (Exception ignored) {}
                try { dst.prefix(src.prefix()); } catch (Exception ignored) {
                    try { dst.setPrefix(src.getPrefix()); } catch (Exception ignored2) {}
                }
                try { dst.suffix(src.suffix()); } catch (Exception ignored) {
                    try { dst.setSuffix(src.getSuffix()); } catch (Exception ignored2) {}
                }
                try {
                    // Paper team color is NamedTextColor on both sides, but compile signature is TextColor vs NamedTextColor on different versions
                    // Use reflection-safe cast
                    Object c = src.color();
                    if (c instanceof net.kyori.adventure.text.format.NamedTextColor ntc) dst.color(ntc);
                } catch (Exception ignored) {}
                try { dst.setAllowFriendlyFire(src.allowFriendlyFire()); } catch (Exception ignored) {}
                try { dst.setCanSeeFriendlyInvisibles(src.canSeeFriendlyInvisibles()); } catch (Exception ignored) {}
                try { dst.setOption(Team.Option.NAME_TAG_VISIBILITY, src.getOption(Team.Option.NAME_TAG_VISIBILITY)); } catch (Exception ignored) {}
                try { dst.setOption(Team.Option.COLLISION_RULE, src.getOption(Team.Option.COLLISION_RULE)); } catch (Exception ignored) {}
                try { dst.setOption(Team.Option.DEATH_MESSAGE_VISIBILITY, src.getOption(Team.Option.DEATH_MESSAGE_VISIBILITY)); } catch (Exception ignored) {}
                // Copy membership
                for (String entry : src.getEntries()) {
                    try {
                        if (!dst.hasEntry(entry)) dst.addEntry(entry);
                    } catch (Exception ignored) {}
                }
                // Remove stale entries that left the team
                try {
                    for (String entry : new java.util.HashSet<>(dst.getEntries())) {
                        if (!src.hasEntry(entry)) {
                            try { dst.removeEntry(entry); } catch (Exception ignored2) {}
                        }
                    }
                } catch (Exception ignored) {}
            }
            // Remove teams from dest that no longer exist on main (TAB reload removed a group)
            for (Team dst : new java.util.HashSet<>(dest.getTeams())) {
                if (main.getTeam(dst.getName()) == null) {
                    try { dst.unregister(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    public void hideBoard(Player player) {
        Scoreboard board = boards.remove(player.getUniqueId());
        lastRendered.remove(player.getUniqueId());
        lastTitle.remove(player.getUniqueId());
        try { player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) {}
    }

    public void removeBoard(Player player) {
        boards.remove(player.getUniqueId());
        lastRendered.remove(player.getUniqueId());
        lastTitle.remove(player.getUniqueId());
        if (!rememberToggle) hiddenTransient.remove(player.getUniqueId());
    }

    public Scoreboard getBoard(UUID id) { return boards.get(id); }

    public Map<UUID, Scoreboard> getBoards() { return boards; }

    void ensureBelowObjective(Scoreboard board) {
        if (board == null) return;
        try {
            Objective obj = board.getObjective("glitchhud_below");
            if (obj == null) {
                obj = board.registerNewObjective("glitchhud_below", Criteria.DUMMY, Component.text("stacks"));
                obj.setDisplaySlot(DisplaySlot.BELOW_NAME);
                obj.setRenderType(RenderType.INTEGER);
                try { obj.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
            } else {
                try { obj.setDisplaySlot(DisplaySlot.BELOW_NAME); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    public boolean isHidden(Player player) {
        if (rememberToggle) {
            try {
                Byte b = player.getPersistentDataContainer().get(toggleKey, PersistentDataType.BYTE);
                return b != null && b != 0;
            } catch (Exception e) { return false; }
        } else {
            Boolean b = hiddenTransient.get(player.getUniqueId());
            return b != null && b;
        }
    }

    public boolean toggle(Player player) {
        boolean nowHidden = !isHidden(player);
        try {
            if (rememberToggle) {
                if (nowHidden) {
                    player.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, (byte) 1);
                } else {
                    player.getPersistentDataContainer().remove(toggleKey);
                }
            } else {
                if (nowHidden) hiddenTransient.put(player.getUniqueId(), true);
                else hiddenTransient.remove(player.getUniqueId());
            }
            if (nowHidden) hideBoard(player);
            else ensureBoard(player);
        } catch (Exception ignored) {}
        return !nowHidden;
    }

    public void clearTransient(UUID id) { hiddenTransient.remove(id); }

    public void refresh(Player player) {
        if (!enabled || isHidden(player)) return;
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            ensureBoard(player);
            board = boards.get(player.getUniqueId());
            if (board == null) return;
        }
        Objective obj = board.getObjective(OBJ_NAME);
        if (obj == null) {
            // Recreate if TAB or another plugin clobbered it
            try {
                obj = board.registerNewObjective(OBJ_NAME, Criteria.DUMMY, deserialize(titleFor(player)));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
                obj.setRenderType(RenderType.INTEGER);
                try { obj.numberFormat(NumberFormat.blank()); } catch (Exception ignored) {}
            } catch (Exception e) { return; }
        }

        String world = player.getWorld().getName();
        String titleRaw = titleForWorld(world);
        // Subtle pulse for RED extraction active: alternate glow every 2 ticks
        boolean extractionActive = placeholders.isInRaid(player) && "glitch_red".equalsIgnoreCase(world);
        if (extractionActive && (tickCounter % 2 == 1)) {
            // Slight variant — add subtle outer glow via extra tag (use same title but with different gradient intensity)
            // Keep minimal: switch between two red gradients every second
            // We precompute via flag; lines also pulse inside buildLines.
        }

        // Title dirty check + update
        String lastT = lastTitle.get(player.getUniqueId());
        if (!titleRaw.equals(lastT) || extractionActive) {
            Component titleComp = buildTitle(player, world, extractionActive);
            try { obj.displayName(titleComp); } catch (Exception ignored) {}
            lastTitle.put(player.getUniqueId(), titleRaw);
        }

        List<String> rawLines = buildRawLines(player, world, extractionActive);
        List<String> last = lastRendered.get(player.getUniqueId());
        // Include pulse tick in dirty check when extraction active or residual stacks high
        boolean pulseDirty = extractionActive || placeholders.getStacks(player) >= 5;
        if (!pulseDirty && last != null && last.equals(rawLines)) return;

        // Render
        renderLines(obj, rawLines);
        lastRendered.put(player.getUniqueId(), new ArrayList<>(rawLines));
    }

    private Component buildTitle(Player player, String world, boolean extractionActive) {
        String base;
        String lower = world.toLowerCase(java.util.Locale.ROOT);
        if ("hub".equals(lower)) base = titleHub;
        else if ("glitch_pve".equals(lower)) base = titlePve;
        else if ("glitch_red".equals(lower)) {
            if (extractionActive) {
                // Subtle cycling: alternate between two red-orange gradients every tick
                boolean alt = (tickCounter % 2 == 0);
                base = alt ? "<gradient:#FF2A2A:#FF8A00><bold>◆ RED ZONE ◆</bold></gradient>"
                            : "<gradient:#FF3B3B:#FF6A00><bold>◆ RED ZONE ◆</bold></gradient>";
                // Rune bookends with plain fallback
                String rune = UiConstants.TITLE_RUNE;
                String mm = "<font:minecraft:default>" + rune + "</font> " + base + " <font:minecraft:default>" + rune + "</font>";
                try { return MM.deserialize(mm); } catch (Exception e) { return MM.deserialize(base); }
            }
            base = titleRed;
        } else base = titleDefault;

        String rune = UiConstants.TITLE_RUNE;
        String withRune = "<font:minecraft:default>" + rune + "</font> " + base + " <font:minecraft:default>" + rune + "</font>";
        try { return MM.deserialize(withRune); } catch (Exception e) {
            try { return MM.deserialize(base); } catch (Exception ex) { return Component.text("THE GLITCH"); }
        }
    }

    private String titleFor(Player p) {
        return titleForWorld(p.getWorld().getName());
    }

    private String titleForWorld(String world) {
        String lower = world.toLowerCase(java.util.Locale.ROOT);
        if ("hub".equals(lower)) return titleHub;
        if ("glitch_pve".equals(lower)) return titlePve;
        if ("glitch_red".equals(lower)) return titleRed;
        return titleDefault;
    }

    /** Build MiniMessage strings (raw) for diffing; rendering does deserialize+customName. */
    private List<String> buildRawLines(Player p, String world, boolean extractionActive) {
        String lower = world.toLowerCase(java.util.Locale.ROOT);
        if ("hub".equals(lower)) return buildHub(p);
        if ("glitch_pve".equals(lower)) return buildPve(p);
        if ("glitch_red".equals(lower)) return buildRed(p, extractionActive);
        return buildDefault(p);
    }

    private List<String> buildHub(Player p) {
        long shards = placeholders.getBalance(p);
        String clazz = placeholders.getGlitchClass(p);
        String lvl = placeholders.getGlitchLevel(p);
        String resIcon = UiConstants.resIcon(clazz);
        int pingVal = placeholders.getPing(p);
        double tpsVal = placeholders.getTps();
        String ping = pingVal >= 0 ? String.valueOf(pingVal) : "—";
        String tps = tpsVal >= 0 ? String.format("%.1f", tpsVal) : "—";

        List<String> out = new ArrayList<>(10);
        out.add(""); // 1 blank top
        out.add("<gray>Zone:</gray> <white>Hub City</white>");
        out.add("<gray>Shards:</gray> <white>" + UiConstants.SHARD + " " + shards + "</white>");
        out.add("<gray>Class:</gray> <white>" + resIcon + " " + clazz + " <gray>Lv</gray>" + lvl + "</white>");
        out.add("");
        out.add("<dark_gray>" + UiConstants.DIVIDER + "</dark_gray>");
        out.add("<gray>Ping:</gray> <white>" + ping + "ms</white> <gray>TPS:</gray> <white>" + tps + "</white>");
        out.add("");
        out.add("<gray><italic>Warp NPCs to deploy</italic></gray>");
        out.add("");
        return out;
    }

    private List<String> buildPve(Player p) {
        long shards = placeholders.getBalance(p);
        String clazz = placeholders.getGlitchClass(p);
        String lvl = placeholders.getGlitchLevel(p);
        String resIcon = UiConstants.resIcon(clazz);
        List<String> out = new ArrayList<>(10);
        out.add("");
        out.add("<gray>Zone:</gray> <white>Standard Glitch</white>");
        out.add("<gray>Mode:</gray> <green>PvE Instance</green>");
        out.add("<gray>Shards:</gray> <white>" + UiConstants.SHARD + " " + shards + "</white>");
        out.add("<gray>Class:</gray> <white>" + resIcon + " " + clazz + " <gray>Lv</gray>" + lvl + "</white>");
        out.add("");
        out.add("<dark_gray>" + UiConstants.DIVIDER + "</dark_gray>");
        out.add("<green>\u2713 Keep Inventory</green>");
        out.add("<yellow>\u26A0 Shards drop on death</yellow>");
        out.add("");
        return out;
    }

    private List<String> buildRed(Player p, boolean extractionActive) {
        long shards = placeholders.getBalance(p);
        String clazz = placeholders.getGlitchClass(p);
        String lvl = placeholders.getGlitchLevel(p);
        String resIcon = UiConstants.resIcon(clazz);
        int stacks = placeholders.getStacks(p);
        int max = placeholders.getMaxStacks(p);
        if (max <= 0) max = 8;
        int payout = placeholders.getPayout(p);
        int dmg = placeholders.getDmgTaken(p);
        String online = placeholders.resolve(p, "%server_online%");
        if (online == null || online.contains("%")) online = String.valueOf(Bukkit.getOnlinePlayers().size());

        List<String> out = new ArrayList<>(15);
        // Danger header handled as title; keep first line as blank for breathing room
        out.add("");
        out.add("<red><bold>\u26A0 DANGER ZONE \u26A0</bold></red>");
        out.add("<gray>Zone:</gray> <red>The Red Zone</red>");
        out.add("<gray>Mode:</gray> <red>PvPvE Extraction</red>");
        out.add("<gray>Shards:</gray> <white>" + UiConstants.SHARD + " " + shards + "</white>");
        out.add("<gray>Class:</gray> <white>" + resIcon + " " + clazz + " <gray>Lv</gray>" + lvl + "</white>");
        out.add("");
        out.add("<dark_gray>" + UiConstants.DIVIDER + "</dark_gray>");

        // Residual line — subtle pulse when >=5 or max
        if (stacks > 0) {
            String starLine = renderStars(stacks, max);
            // Color intensity cycles subtly for danger feel
            String baseColor = stacks >= max ? "<gradient:#FF2A2A:#FF00FF>" : (stacks >= 5 ? "<light_purple>" : "<gray>");
            String stacksMm;
            if (stacks >= 5 && tickCounter % 2 == 0) {
                // Subtle glow flicker: slightly different shade on alternate ticks
                String alt = stacks >= max ? "<gradient:#FF3B3B:#FF6AFF>" : "<light_purple>";
                stacksMm = alt + "Residual:</gradient> " + starLine + " <white>" + stacks + "/" + max + "</white>";
                // Use alt color for value part
                stacksMm = alt + "Residual:</gradient> " + starLine + " <white>" + stacks + "/" + max + "</white>";
            } else {
                stacksMm = baseColor + "Residual:</gradient> " + starLine + " <white>" + stacks + "/" + max + "</white>";
                if (baseColor.startsWith("<gray")) {
                    stacksMm = "<gray>Residual:</gray> " + starLine + " <white>" + stacks + "/" + max + "</white>";
                }
            }
            out.add(stacksMm);
            out.add("<dark_gray>+" + dmg + "% dmg  +" + payout + "% payout</dark_gray>");
        } else {
            out.add("<gray>Residual:</gray> <dark_gray>" + UiConstants.STAR_EMPTY.repeat(Math.min(5, max)) + "</dark_gray> <white>0/" + max + "</white>");
            out.add("<dark_gray>clear \u2713</dark_gray>");
        }

        // Extraction line — do NOT duplicate BossBar timer. Show status + next cycle when idle.
        if (extractionActive) {
            boolean alt = tickCounter % 2 == 0;
            String label = alt
                    ? "<aqua><bold>\u25C6 EXTRACTION \u25C6</bold></aqua>"
                    : "<dark_aqua><bold>\u25C6 EXTRACTION \u25C6</bold></dark_aqua>";
            out.add(label);
            out.add("<yellow>\u26A1 Extract at beacons</yellow>");
        } else {
            String next = StashCycleProbe.formatNextCycle();
            if (next != null) {
                out.add("<gray>Next:</gray> <white>" + next + "</white> <gray>\u25B6 beacons</gray>");
            } else {
                out.add("<yellow>\u26A1 Extract at beacons</yellow>");
            }
        }

        out.add("");
        out.add("<gray>Players:</gray> <white>" + online + "</white>");
        return out;
    }

    private String renderStars(int stacks, int max) {
        int total = Math.min(5, max);
        // Map 0..max onto 0..5 stars for compact sidebar; but for 8 stacks we want 8 pips? Use 5-star compact then ratio.
        // Instead use star per stack up to 5, then continue with numbers for >5? For 8, show 5 stars fully filled + " +3" ?
        // Keep Wynncraft-like: 5-star meter where filled = ceil(stacks * 5 / max)
        int filled = (int) Math.ceil((double) stacks * total / Math.max(1, max));
        filled = Math.max(0, Math.min(total, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            if (i < filled) sb.append("<gold>").append(UiConstants.STAR_FULL).append("</gold>");
            else sb.append("<dark_gray>").append(UiConstants.STAR_EMPTY).append("</dark_gray>");
        }
        return sb.toString();
    }

    private List<String> buildDefault(Player p) {
        long shards = placeholders.getBalance(p);
        String world = p.getWorld().getName();
        String online = placeholders.resolve(p, "%server_online%");
        if (online == null || online.contains("%")) online = String.valueOf(Bukkit.getOnlinePlayers().size());
        List<String> out = new ArrayList<>(6);
        out.add("");
        out.add("<gray>Zone:</gray> <white>" + world + "</white>");
        out.add("<gray>Shards:</gray> <white>" + UiConstants.SHARD + " " + shards + "</white>");
        out.add("");
        out.add("<gray>Players:</gray> <white>" + online + "</white>");
        out.add("");
        return out;
    }

    private void renderLines(Objective obj, List<String> rawLines) {
        int size = rawLines.size();
        // Hide old scores beyond new size
        for (int i = 0; i < KEYS.length; i++) {
            Score s = obj.getScore(KEYS[i]);
            if (i < size) {
                // Ensure score exists and ordering
                int scoreVal = size - i; // top = highest
                // Deserialize line
                Component comp;
                String raw = rawLines.get(i);
                if (raw == null || raw.isEmpty()) {
                    // Empty line: use a single space invisible but keep slot
                    // Use blank component with one space to keep line height
                    comp = Component.text(" ");
                } else {
                    comp = deserialize(raw);
                }
                try {
                    s.customName(comp);
                    s.numberFormat(NumberFormat.blank());
                    s.setScore(scoreVal);
                } catch (Exception e) {
                    // Fallback legacy setScore only
                    try { s.setScore(scoreVal); } catch (Exception ignored) {}
                }
            } else {
                if (s.isScoreSet()) {
                    try { s.resetScore(); } catch (Exception ignored) {}
                }
            }
        }
        // Extra cleanup: if objective had more than KEYS via legacy, reset extra entries
        // Not needed as we cap at 15.
    }

    private Component deserialize(String mm) {
        if (mm == null || mm.isEmpty()) return Component.text(" ");
        try { return MM.deserialize(mm); } catch (Exception e) { return Component.text(mm); }
    }
}
