package com.theglitch.glitchquests;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * PlaceholderAPI expansion "glitchquests" — feeds the MythicHUD contract tracker
 * (top-left, under the extraction card).
 * <ul>
 *   <li>%glitchquests_hud_title% — header text</li>
 *   <li>%glitchquests_hud_&lt;1-4&gt;_state% — todo, done (claimable) or none</li>
 *   <li>%glitchquests_hud_&lt;1-4&gt;_text% — the line (MiniMessage)</li>
 * </ul>
 * Lists today's unclaimed dailies, unfinished first.
 */
public final class QuestExpansion extends PlaceholderExpansion {

    static final int LINES = 4;

    private final GlitchQuests plugin;
    private final QuestManager quests;

    public QuestExpansion(GlitchQuests plugin, QuestManager quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    @Override
    public String getIdentifier() {
        return "glitchquests";
    }

    @Override
    public String getAuthor() {
        return "The Glitch";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    private record Line(String state, String text) {}

    private List<Line> lines(Player player) {
        PlayerData data = quests.data(player);
        List<Line> todo = new ArrayList<>();
        List<Line> done = new ArrayList<>();
        for (QuestDef q : quests.activeDaily()) {
            if (quests.isClaimed(data, q)) continue;
            int progress = Math.min(q.amount(), quests.progressOf(data, q));
            if (progress >= q.amount()) {
                done.add(new Line("done", "<#86EFAC>" + q.name() + " <#6B7280>· claim in /quests"));
            } else {
                todo.add(new Line("todo", "<white>" + q.name() + " <#9CA3AF>" + progress + "/" + q.amount()));
            }
        }
        List<Line> all = new ArrayList<>(todo);
        all.addAll(done);
        if (all.isEmpty()) {
            all.add(new Line("done", "<#86EFAC>All contracts complete"));
            all.add(new Line("todo", "<#9CA3AF>New contracts in <white>" + Menus.fmt(quests.untilNextDay())));
        }
        return all;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";
        String id = identifier.toLowerCase(java.util.Locale.ROOT);
        if (id.equals("hud_title")) {
            long left = lines(player).stream().filter(l -> l.state().equals("todo")).count();
            return "<#F5A742>Daily Contracts" + (left > 0 ? " <#9CA3AF>· " + left + " left" : "");
        }
        if (id.startsWith("hud_") && id.length() > 6 && Character.isDigit(id.charAt(4))) {
            int n = id.charAt(4) - '0';
            String field = id.substring(6);
            List<Line> lines = lines(player);
            Line line = n >= 1 && n <= LINES && n <= lines.size() ? lines.get(n - 1) : null;
            return switch (field) {
                case "state" -> line == null ? "none" : line.state();
                case "text" -> line == null ? "" : line.text();
                default -> null;
            };
        }
        return null;
    }
}
