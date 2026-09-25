package com.theglitch.glitchraid.gui;

import com.theglitch.common.ChatConfirm;
import com.theglitch.common.MenuTitles;
import com.theglitch.common.NexoUtil;
import com.theglitch.glitchraid.GlitchRaid;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * /dungeons — picks one of the MythicDungeons boss dungeons. Entry costs one
 * tier key (Nexo dungeon_key_t1..3, sold in the Bazaar): the key is taken
 * after a chat [YES]/[NO], then MythicDungeons is asked to start the dungeon
 * for the player's party (players have no direct /md play permission, so the
 * key can't be skipped). If the player hasn't landed in the dungeon within
 * REFUND_TICKS (queue full, party not ready, ...) the key is given back.
 *
 * Maps, bosses and the MD configs are installed by scripts/setup-dungeons.sh;
 * this table must match scripts/dungeon_md.py. Gated by dungeons.enabled.
 */
public class DungeonSelectGUI implements Listener {

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Dungeon(String id, String name, int tier, String boss, Material icon, int slot) {
    }

    private static final List<Dungeon> DUNGEONS = List.of(
            new Dungeon("small", "Goblin Hollow", 1, "Goblin Warlord", Material.MOSSY_COBBLESTONE, 10),
            new Dungeon("haunted", "Haunted Crypt", 1, "Bone Tyrant", Material.BONE_BLOCK, 12),
            new Dungeon("puzzle", "Illusion Vault", 1, "The Illusionist", Material.PURPUR_BLOCK, 14),
            new Dungeon("desert", "Sunken Sands", 1, "Earth Spider", Material.CHISELED_SANDSTONE, 16),
            new Dungeon("aztec", "Temple of Moldar", 2, "Moldar", Material.MOSSY_STONE_BRICKS, 19),
            new Dungeon("crimson", "Crimson Keep", 2, "Akaza, Upper Moon", Material.CRIMSON_NYLIUM, 21),
            new Dungeon("nether", "Ember Depths", 2, "Ember Claw", Material.MAGMA_BLOCK, 23),
            new Dungeon("pirate", "Wreck of the Tide", 2, "Glitch Reaver", Material.DARK_OAK_PLANKS, 25),
            new Dungeon("medium", "Moonlit Sanctum", 3, "Selenia", Material.END_STONE_BRICKS, 29),
            new Dungeon("town", "Hollow Town", 3, "The Lovers", Material.CHERRY_LEAVES, 31),
            new Dungeon("mythic", "Mythic Spire", 3, "The Awakened Mage", Material.AMETHYST_BLOCK, 33));

    private static final String[] TIER_COLOUR = {"", "<green>", "<gold>", "<light_purple>"};
    private static final String[] KEY_NAME = {"", "Iron", "Golden", "Mythic"};
    private static final int[] REWARD = {0, 400, 900, 1800};
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 49;
    private static final long REFUND_TICKS = 20L * 60;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchRaid plugin;

    public DungeonSelectGUI(GlitchRaid plugin) {
        this.plugin = plugin;
    }

    private boolean open() {
        return plugin.getConfig().getBoolean("dungeons.enabled", false);
    }

    public void open(Player player) {
        Holder holder = new Holder();
        Inventory gui = Bukkit.createInventory(holder, 54,
                MenuTitles.title(player, MenuTitles.DUNGEONS, "<dark_purple>Dungeons</dark_purple>"));
        holder.inventory = gui;

        gui.setItem(INFO_SLOT, item(Material.WRITABLE_BOOK, "<yellow><bold>Boss Dungeons</bold></yellow>",
                "<gray>Private instance for you and your party (1-4).</gray>",
                "<gray>Entry uses one dungeon key of the dungeon's tier.</gray>",
                "<gray>Keys: Bazaar → Keys tab.</gray>",
                " ",
                "<gray>Your keys: <white>" + keyCount(player, 1) + "</white> Iron, <white>"
                        + keyCount(player, 2) + "</white> Golden, <white>" + keyCount(player, 3) + "</white> Mythic</gray>"));

        for (Dungeon d : DUNGEONS) {
            String col = TIER_COLOUR[d.tier()];
            List<String> lore = new ArrayList<>(List.of(
                    col + "Tier " + d.tier() + "</" + col.substring(1),
                    "<gray>Boss: <white>" + d.boss() + "</white></gray>",
                    "<gray>Clear reward: <aqua>" + REWARD[d.tier()] + " Shards</aqua> + loot</gray>",
                    "<gray>Needs: " + col + KEY_NAME[d.tier()] + " Dungeon Key</" + col.substring(1) + "</gray>",
                    " "));
            if (!open() && !player.hasPermission("glitchraid.admin")) {
                lore.add("<red>Opening soon</red>");
            } else if (keyCount(player, d.tier()) > 0) {
                lore.add("<yellow>Click to enter</yellow>");
            } else {
                lore.add("<red>You have no " + KEY_NAME[d.tier()] + " Dungeon Key</red>");
            }
            gui.setItem(d.slot(), item(d.icon(), col + "<bold>" + d.name() + "</bold></" + col.substring(1),
                    lore.toArray(new String[0])));
        }
        gui.setItem(CLOSE_SLOT, item(Material.BARRIER, "<red><bold>Close</bold></red>"));
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        for (Dungeon d : DUNGEONS) {
            if (d.slot() == slot) {
                player.closeInventory();
                tryEnter(player, d);
                return;
            }
        }
    }

    private void tryEnter(Player player, Dungeon d) {
        if (!open() && !player.hasPermission("glitchraid.admin")) {
            player.sendMessage(MM.deserialize("<red>Dungeons are opening soon.</red>"));
            return;
        }
        String keyId = keyId(d.tier());
        if (keyCount(player, d.tier()) < 1) {
            player.sendMessage(MM.deserialize("<red>You need a " + KEY_NAME[d.tier()]
                    + " Dungeon Key — buy one in the Bazaar (Keys tab).</red>"));
            return;
        }
        Component question = MM.deserialize("<gray>Use a " + TIER_COLOUR[d.tier()] + KEY_NAME[d.tier()]
                + " Dungeon Key</" + TIER_COLOUR[d.tier()].substring(1) + "> <gray>to enter <white>" + d.name()
                + "</white> with your party?</gray>");
        ChatConfirm.ask(player, question, () -> {
            // Re-check on YES: the key may have been used or sold since.
            if (!takeKey(player, keyId)) {
                player.sendMessage(MM.deserialize("<red>You no longer have that key.</red>"));
                return;
            }
            player.sendMessage(MM.deserialize("<gray>Key used — opening <white>" + d.name() + "</white>...</gray>"));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "md play " + d.id() + " " + player.getName());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (player.getWorld().getName().startsWith(d.id() + "_")) return;
                ItemStack refund = NexoUtil.build(keyId);
                if (refund != null) {
                    player.getInventory().addItem(refund).values()
                            .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                }
                player.sendMessage(MM.deserialize("<yellow>The dungeon didn't start — your key was returned.</yellow>"));
            }, REFUND_TICKS);
        });
    }

    private static String keyId(int tier) {
        return "dungeon_key_t" + tier;
    }

    private static int keyCount(Player player, int tier) {
        String id = keyId(tier);
        int n = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it != null && id.equals(NexoUtil.idOf(it))) n += it.getAmount();
        }
        return n;
    }

    private static boolean takeKey(Player player, String id) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && id.equals(NexoUtil.idOf(it))) {
                if (it.getAmount() > 1) {
                    it.setAmount(it.getAmount() - 1);
                } else {
                    contents[i] = null;
                }
                player.getInventory().setStorageContents(contents);
                return true;
            }
        }
        return false;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.customName(MM.deserialize("<!italic>" + name));
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(line.equals(" ") ? Component.empty() : MM.deserialize("<!italic>" + line));
            }
            meta.lore(lines);
            item.setItemMeta(meta);
        }
        return item;
    }
}
