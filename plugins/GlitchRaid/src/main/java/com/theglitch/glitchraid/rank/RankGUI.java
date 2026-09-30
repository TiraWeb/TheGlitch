package com.theglitch.glitchraid.rank;

import com.theglitch.common.MenuTitles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /rank — your Raider Rank, the tier ladder, recent RR changes and the top 10. */
public final class RankGUI implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int SUMMARY_SLOT = 4;
    private static final int[] LADDER_SLOTS = {20, 21, 23, 24, 29, 30, 32, 33};
    private static final int HISTORY_SLOT = 39;
    private static final int TOP_SLOT = 41;
    private static final int CLOSE_SLOT = 49;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final RankManager ranks;

    public RankGUI(RankManager ranks) {
        this.ranks = ranks;
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                MenuTitles.title(player, MenuTitles.RANK, "<gold>Raider Rank</gold>"));
        holder.inventory = gui;

        UUID id = player.getUniqueId();
        int rr = ranks.rr(id);
        RankTier tier = ranks.tierOf(rr);
        RankTier next = tier.next();
        RankManager.Entry entry = ranks.entry(id);

        List<String> summary = new ArrayList<>();
        summary.add("<gray>Rating: <white>" + rr + " RR</white></gray>");
        summary.add("<gray>Leaderboard: <white>#" + ranks.position(id) + "</white></gray>");
        if (next != null) {
            int from = ranks.min(tier), to = ranks.min(next);
            summary.add(" ");
            summary.add("<gray>Next: " + next.styled() + " <gray>(" + (to - rr) + " RR to go)</gray></gray>");
            summary.add(bar(rr - from, to - from));
        } else {
            summary.add(" ");
            summary.add("<light_purple>Top tier reached.</light_purple>");
        }
        summary.add(" ");
        summary.add(entry.shield()
                ? "<green>Demotion shield: ready</green>"
                : "<red>Demotion shield: used</red> <gray>(extract to rearm)</gray>");
        summary.add(" ");
        summary.add("<dark_gray>Extract with loot to gain RR.</dark_gray>");
        summary.add("<dark_gray>Dying in a raid costs RR.</dark_gray>");
        gui.setItem(SUMMARY_SLOT, item(tier.icon(), tier.styled() + " <white>Raider</white>", true, summary));

        RankTier[] all = RankTier.values();
        for (int i = 0; i < all.length && i < LADDER_SLOTS.length; i++) {
            RankTier t = all[i];
            boolean current = t == tier;
            boolean reached = t.ordinal() <= tier.ordinal();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Requires <white>" + ranks.min(t) + " RR</white></gray>");
            lore.add(" ");
            lore.add(current ? "<yellow>▶ Your rank</yellow>" : reached ? "<green>✔ Reached</green>" : "<dark_gray>Locked</dark_gray>");
            Material mat = reached ? t.icon() : Material.GRAY_DYE;
            gui.setItem(LADDER_SLOTS[i], item(mat, t.styled(), current, lore));
        }

        List<String> hist = new ArrayList<>(entry.history());
        if (hist.isEmpty()) hist.add("<dark_gray>No raids yet.</dark_gray>");
        gui.setItem(HISTORY_SLOT, item(Material.WRITABLE_BOOK, "<yellow>Recent RR changes</yellow>", false, hist));

        List<String> top = new ArrayList<>();
        int n = 1;
        for (Map.Entry<UUID, RankManager.Entry> e : ranks.top(10)) {
            RankTier t = ranks.tierOf(e.getValue().rr());
            String name = e.getValue().name().isEmpty() ? "?" : e.getValue().name();
            top.add("<gray>#" + n++ + "</gray> <" + t.color() + ">" + name + "</" + t.color() + "> <gray>"
                    + e.getValue().rr() + " RR</gray>");
        }
        if (top.isEmpty()) top.add("<dark_gray>Nobody ranked yet.</dark_gray>");
        gui.setItem(TOP_SLOT, item(Material.GOLDEN_HELMET, "<gold>Top Raiders</gold>", false, top));

        gui.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>", false, List.of()));
        player.openInventory(gui);
    }

    static String bar(int have, int need) {
        int cells = 20;
        int filled = need <= 0 ? cells : Math.max(0, Math.min(cells, have * cells / need));
        return "<green>" + "|".repeat(filled) + "</green><dark_gray>" + "|".repeat(cells - filled) + "</dark_gray>"
                + " <gray>" + (need <= 0 ? 100 : Math.max(0, have) * 100 / need) + "%</gray>";
    }

    private static ItemStack item(Material mat, String name, boolean glow, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(MM.deserialize(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        meta.addItemFlags(ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == CLOSE_SLOT) event.getWhoClicked().closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
