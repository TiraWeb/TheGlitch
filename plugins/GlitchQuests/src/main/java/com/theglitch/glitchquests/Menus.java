package com.theglitch.glitchquests;

import com.theglitch.glitchquests.QuestManager.ClaimResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The two chest menus. Both are 3-row chests whose background is a full-window
 * glyph from the GUI pack (font theglitch:menus, built by
 * scripts/gen-menu-backgrounds.py), so slot positions below are pinned to the
 * painted boxes:
 *   Rewards: 10 = daily streak, 13 = weekly, 16 = monthly, 0 = quest board.
 *   Quests:  4 = header plate, 10-16 = quest row, 9/17 = painted arrows
 *            (daily <-> weekly), 18 = back to rewards, 22 = all-dailies bonus.
 */
public final class Menus implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Title glyphs: white so the bitmap isn't tinted by the default title grey.
    private static final Component REWARDS_TITLE = MM.deserialize("<white><font:theglitch:menus>\uF000\uF001</font></white>");
    private static final Component QUESTS_TITLE = MM.deserialize("<white><font:theglitch:menus>\uF000\uF002</font></white>");
    // Bedrock (Floodgate) clients can't render the glyph background.
    private static final Component REWARDS_TITLE_PLAIN = MM.deserialize("<dark_purple>Rewards</dark_purple>");
    private static final Component QUESTS_TITLE_PLAIN = MM.deserialize("<dark_purple>Quests</dark_purple>");

    private final QuestManager quests;

    public Menus(QuestManager quests) {
        this.quests = quests;
    }

    private enum Kind { REWARDS, QUESTS_DAILY, QUESTS_WEEKLY }

    private static final class Holder implements InventoryHolder {
        final Kind kind;
        final Map<Integer, String> actions = new HashMap<>();
        Inventory inventory;
        Holder(Kind kind) { this.kind = kind; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static boolean bedrock(Player p) {
        return p.getUniqueId().getMostSignificantBits() == 0;
    }

    // ------------------------------------------------------------------ rewards

    public void openRewards(Player p) {
        PlayerData d = quests.data(p);
        Holder h = new Holder(Kind.REWARDS);
        Inventory inv = Bukkit.createInventory(h, 27, bedrock(p) ? REWARDS_TITLE_PLAIN : REWARDS_TITLE);
        h.inventory = inv;

        // daily streak
        int shownStreak = quests.currentStreak(d);
        if (quests.canClaimStreak(d)) {
            int day = quests.nextStreakDay(d);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Streak: <gold>" + shownStreak + " day" + (shownStreak == 1 ? "" : "s") + "</gold></gray>");
            lore.add("");
            lore.add("<gray>Today's reward (day " + dayInCycle(day) + "/" + quests.streakCycle() + "):</gray>");
            lore.addAll(quests.streakReward(day).describe());
            lore.add("");
            lore.addAll(streakPreview(day));
            lore.add("");
            lore.add("<green><bold>▶ Click to claim!</bold></green>");
            set(h, 10, icon(Material.CHEST_MINECART, "<gold><bold>Daily Reward</bold></gold>", lore, true), "claim_daily");
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Streak: <gold>" + shownStreak + " day" + (shownStreak == 1 ? "" : "s") + "</gold></gray>");
            lore.add("");
            lore.add("<gray>Tomorrow (day " + dayInCycle(d.streak + 1) + "/" + quests.streakCycle() + "):</gray>");
            lore.addAll(quests.streakReward(d.streak + 1).describe());
            lore.add("");
            lore.add("<dark_gray>Claimed — come back in " + fmt(quests.untilNextDay()) + "</dark_gray>");
            lore.add("<dark_gray>Miss a day and the streak resets.</dark_gray>");
            set(h, 10, icon(Material.MINECART, "<gray>Daily Reward</gray>", lore, false), null);
        }

        // weekly
        periodic(h, 13, quests.canClaimWeekly(d), Material.ENDER_CHEST, "<light_purple><bold>Weekly Reward</bold></light_purple>",
                "Weekly Reward", quests.weeklyReward(), quests.untilNextWeek(), "claim_weekly");
        // monthly
        periodic(h, 16, quests.canClaimMonthly(d), Material.NETHER_STAR, "<aqua><bold>Monthly Reward</bold></aqua>",
                "Monthly Reward", quests.monthlyReward(), quests.untilNextMonth(), "claim_monthly");

        // quest board shortcut
        int ready = readyCount(d);
        List<String> qLore = new ArrayList<>();
        qLore.add("<gray>" + quests.activeDaily().size() + " daily and " + quests.activeWeekly().size() + " weekly quests.</gray>");
        if (ready > 0) qLore.add("<green>" + ready + " ready to claim!</green>");
        qLore.add("");
        qLore.add("<yellow>Click to open</yellow>");
        set(h, 0, icon(Material.WRITABLE_BOOK, "<gradient:#C084FC:#F0ABFC><bold>Quest Board</bold></gradient>", qLore, ready > 0), "open_daily");

        p.openInventory(inv);
    }

    private void periodic(Holder h, int slot, boolean claimable, Material mat, String name, String plainName,
                          Reward reward, Duration until, String action) {
        List<String> lore = new ArrayList<>(reward.describe());
        lore.add("");
        if (claimable) {
            lore.add("<green><bold>▶ Click to claim!</bold></green>");
            set(h, slot, icon(mat, name, lore, true), action);
        } else {
            lore.add("<dark_gray>Claimed — resets in " + fmt(until) + "</dark_gray>");
            set(h, slot, icon(Material.GRAY_DYE, "<gray>" + plainName + "</gray>", lore, false), null);
        }
    }

    private List<String> streakPreview(int today) {
        List<String> out = new ArrayList<>();
        int cycle = quests.streakCycle();
        int start = today - (today - 1) % cycle;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cycle; i++) {
            int day = start + i;
            String dot = day < today ? "<green>■</green>" : day == today ? "<gold>■</gold>" : "<dark_gray>■</dark_gray>";
            sb.append(dot).append(i < cycle - 1 ? " " : "");
        }
        out.add(sb.toString());
        return out;
    }

    private int dayInCycle(int day) {
        return (day - 1) % quests.streakCycle() + 1;
    }

    // ------------------------------------------------------------------ quests

    public void openQuests(Player p, boolean weekly) {
        PlayerData d = quests.data(p);
        Holder h = new Holder(weekly ? Kind.QUESTS_WEEKLY : Kind.QUESTS_DAILY);
        Inventory inv = Bukkit.createInventory(h, 27, bedrock(p) ? QUESTS_TITLE_PLAIN : QUESTS_TITLE);
        h.inventory = inv;

        List<QuestDef> list = weekly ? quests.activeWeekly() : quests.activeDaily();
        Duration reset = weekly ? quests.untilNextWeek() : quests.untilNextDay();
        int done = 0;
        for (QuestDef q : list) if (quests.progressOf(d, q) >= q.amount()) done++;

        set(h, 4, icon(weekly ? Material.ENDER_EYE : Material.CLOCK,
                weekly ? "<light_purple><bold>Weekly Quests</bold></light_purple>" : "<gold><bold>Daily Quests</bold></gold>",
                List.of("<gray>Completed: <white>" + done + "/" + list.size() + "</white></gray>",
                        "<gray>New quests in <white>" + fmt(reset) + "</white></gray>",
                        "",
                        "<dark_gray>Click the arrows for " + (weekly ? "daily" : "weekly") + " quests</dark_gray>"), false), null);

        // centre the quests in the 7-slot row 10..16
        int start = 10 + (7 - Math.min(7, list.size())) / 2;
        for (int i = 0; i < list.size() && i < 7; i++) {
            QuestDef q = list.get(i);
            set(h, start + i, questIcon(d, q), "quest:" + q.id());
        }

        // painted arrows (background art, no item on top): either one toggles daily <-> weekly
        h.actions.put(9, weekly ? "open_daily" : "open_weekly");
        h.actions.put(17, weekly ? "open_daily" : "open_weekly");

        set(h, 18, icon(Material.CHEST_MINECART, "<gold>◀ Rewards</gold>",
                List.of("<gray>Daily, weekly and monthly rewards</gray>"), false), "open_rewards");

        if (!weekly) {
            boolean all = quests.allDailiesDone(d);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Finish every daily quest today.</gray>");
            lore.add("");
            lore.addAll(quests.dailyBonus().describe());
            lore.add("");
            if (d.dailyBonusClaimed) {
                lore.add("<dark_gray>Claimed — new quests in " + fmt(reset) + "</dark_gray>");
                set(h, 22, icon(Material.GRAY_DYE, "<gray>Daily Completion Bonus</gray>", lore, false), null);
            } else if (all) {
                lore.add("<green><bold>▶ Click to claim!</bold></green>");
                set(h, 22, icon(Material.GOLD_BLOCK, "<gold><bold>Daily Completion Bonus</bold></gold>", lore, true), "claim_bonus");
            } else {
                lore.add("<yellow>" + done + "/" + list.size() + " complete</yellow>");
                set(h, 22, icon(Material.RAW_GOLD_BLOCK, "<yellow>Daily Completion Bonus</yellow>", lore, false), "claim_bonus");
            }
        }

        p.openInventory(inv);
    }

    private ItemStack questIcon(PlayerData d, QuestDef q) {
        int prog = quests.progressOf(d, q);
        boolean complete = prog >= q.amount();
        boolean claimed = quests.isClaimed(d, q);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + q.description() + "</gray>");
        lore.add("");
        lore.add(bar(prog, q.amount()) + " <white>" + prog + "/" + q.amount() + "</white>");
        lore.add("");
        lore.add("<gray>Reward:</gray>");
        lore.addAll(q.reward().describe());
        lore.add("");
        String name;
        if (claimed) {
            lore.add("<dark_gray>✔ Claimed</dark_gray>");
            name = "<dark_gray><st>" + q.name() + "</st></dark_gray>";
        } else if (complete) {
            lore.add("<green><bold>▶ Click to claim!</bold></green>");
            name = "<green><bold>" + q.name() + "</bold></green>";
        } else {
            lore.add("<yellow>In progress</yellow>");
            name = "<white>" + q.name() + "</white>";
        }
        return icon(claimed ? Material.GRAY_DYE : q.icon(), name, lore, complete && !claimed);
    }

    private static String bar(int prog, int max) {
        int filled = (int) Math.round(10.0 * prog / max);
        return "<green>" + "▮".repeat(filled) + "</green><dark_gray>" + "▮".repeat(10 - filled) + "</dark_gray>";
    }

    private int readyCount(PlayerData d) {
        int n = 0;
        for (QuestDef q : quests.activeDaily()) if (quests.progressOf(d, q) >= q.amount() && !quests.isClaimed(d, q)) n++;
        for (QuestDef q : quests.activeWeekly()) if (quests.progressOf(d, q) >= q.amount() && !quests.isClaimed(d, q)) n++;
        if (!d.dailyBonusClaimed && quests.allDailiesDone(d)) n++;
        return n;
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getInventory()) return;
        String action = h.actions.get(e.getRawSlot());
        if (action == null) return;

        switch (action) {
            case "open_rewards" -> { click(p); openRewards(p); }
            case "open_daily" -> { click(p); openQuests(p, false); }
            case "open_weekly" -> { click(p); openQuests(p, true); }
            case "claim_daily" -> result(p, quests.claimStreak(p), "Daily reward claimed!", Kind.REWARDS);
            case "claim_weekly" -> result(p, quests.claimWeekly(p), "Weekly reward claimed!", Kind.REWARDS);
            case "claim_monthly" -> result(p, quests.claimMonthly(p), "Monthly reward claimed!", Kind.REWARDS);
            case "claim_bonus" -> result(p, quests.claimDailyBonus(p), "Daily completion bonus claimed!", Kind.QUESTS_DAILY);
            default -> {
                if (action.startsWith("quest:")) {
                    QuestDef q = quests.findActive(action.substring(6));
                    if (q == null) { openQuests(p, h.kind == Kind.QUESTS_WEEKLY); return; }
                    result(p, quests.claimQuest(p, q), "Quest reward claimed: " + q.name(),
                            q.period() == QuestDef.Period.WEEKLY ? Kind.QUESTS_WEEKLY : Kind.QUESTS_DAILY);
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder(false) instanceof Holder) e.setCancelled(true);
    }

    private void result(Player p, ClaimResult r, String okMsg, Kind reopen) {
        switch (r) {
            case OK -> {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
                p.sendMessage(MM.deserialize("<dark_purple>✦</dark_purple> <green>" + okMsg + "</green>"));
            }
            case NOT_READY -> {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                p.sendMessage(MM.deserialize("<dark_purple>✦</dark_purple> <gray>Not finished yet — keep going!</gray>"));
            }
            case ALREADY -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
        switch (reopen) {
            case REWARDS -> openRewards(p);
            case QUESTS_DAILY -> openQuests(p, false);
            case QUESTS_WEEKLY -> openQuests(p, true);
        }
    }

    private static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    // ------------------------------------------------------------------ helpers

    private static void set(Holder h, int slot, ItemStack item, String action) {
        h.inventory.setItem(slot, item);
        if (action != null) h.actions.put(slot, action);
    }

    private static ItemStack icon(Material mat, String name, List<String> lore, boolean glint) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) {
            lines.add(l.isEmpty() ? Component.empty()
                    : MM.deserialize(l).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        }
        meta.lore(lines);
        if (glint) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    static String fmt(Duration d) {
        long mins = Math.max(1, d.toMinutes());
        long days = mins / 1440, hours = (mins % 1440) / 60, m = mins % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + m + "m";
        return m + "m";
    }
}
