package com.theglitch.glitchshops.ui;

import com.theglitch.glitchshops.GlitchShops;
import com.theglitch.glitchshops.ShopGUI;
import com.theglitch.glitchshops.ShopManager;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BazaarPanel implements Listener {

    private static final NamespacedKey PANEL_KEY = new NamespacedKey("glitchshops", "panel");
    private static final NamespacedKey VALUE_KEY = new NamespacedKey("glitchshops", "value");

    private static GlitchShops plugin;
    private static ShopGUI gui;
    private static BazaarPanel instance;
    private static BukkitTask buildTask;
    private static BukkitTask refreshTask;

    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();
    private final Set<UUID> gridEntities = new HashSet<>();

    private String activeCategory;
    private long lastFlip;

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;
    private int instantBuyMax;

    private BazaarPanel() {
    }

    public static void init(GlitchShops pl, ShopGUI shopGui) {
        plugin = pl;
        gui = shopGui;
        if (instance != null) {
            return;
        }
        boolean enabled;
        try {
            enabled = pl.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
        } catch (Throwable t) {
            enabled = false;
        }
        if (!enabled) {
            return;
        }
        BazaarPanel panel = new BazaarPanel();
        if (!panel.loadConfig()) {
            return;
        }
        instance = panel;
        try {
            pl.getServer().getPluginManager().registerEvents(panel, pl);
        } catch (Throwable t) {
            pl.getLogger().warning("BazaarPanel listener registration failed: " + t.getMessage());
            instance = null;
            return;
        }
        long refreshSeconds;
        try {
            refreshSeconds = pl.getConfig().getLong("modern-ui.world-panel.refresh-seconds", 600L);
        } catch (Throwable t) {
            refreshSeconds = 600L;
        }
        long period = Math.max(200L, refreshSeconds * 20L);
        buildTask = pl.getServer().getScheduler().runTaskLater(pl, panel::build, 100L);
        refreshTask = pl.getServer().getScheduler().runTaskTimer(pl, panel::refreshContents, period, period);
        pl.getLogger().info("Grand Bazaar wall panel armed at " + worldNameSafe(panel) + ".");
    }

    public static void shutdown() {
        if (buildTask != null) {
            try {
                buildTask.cancel();
            } catch (Throwable ignored) {
            }
            buildTask = null;
        }
        if (refreshTask != null) {
            try {
                refreshTask.cancel();
            } catch (Throwable ignored) {
            }
            refreshTask = null;
        }
        if (instance != null) {
            try {
                instance.removeAll();
            } catch (Throwable ignored) {
            }
            try {
                HandlerList.unregisterAll(instance);
            } catch (Throwable ignored) {
            }
            instance = null;
        }
    }

    private static String worldNameSafe(BazaarPanel panel) {
        try {
            World w = panel.world;
            return w == null ? "?" : w.getName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private boolean loadConfig() {
        try {
            String name = plugin.getConfig().getString("modern-ui.world-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                plugin.getLogger().warning("world-panel world '" + name + "' not found — wall panel dormant.");
                return false;
            }
            world = w;
            wx = plugin.getConfig().getDouble("modern-ui.world-panel.x", 0.0D);
            wy = plugin.getConfig().getDouble("modern-ui.world-panel.y", 0.0D);
            wz = plugin.getConfig().getDouble("modern-ui.world-panel.z", 0.0D);
            String f = plugin.getConfig().getString("modern-ui.world-panel.facing", "south");
            facing = f == null ? "south" : f.toLowerCase();
            spacing = plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.15D);
            if (spacing < 0.5D) {
                spacing = 0.5D;
            }
            instantBuyMax = plugin.getConfig().getInt("modern-ui.world-panel.instant-buy-max", 50);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("world-panel config invalid — wall panel dormant.");
            return false;
        }
    }

    private boolean isLive() {
        return instance == this && plugin != null && plugin.isEnabled() && world != null;
    }

    private String activeCategory() {
        if (activeCategory == null || activeCategory.isBlank()) {
            String def = null;
            try {
                def = gui.defaultTab();
            } catch (Throwable ignored) {
            }
            activeCategory = def == null || def.isBlank() ? "materials" : def;
        }
        return activeCategory;
    }

    private void build() {
        try {
            if (!isLive()) {
                plugin.getLogger().info("BazaarPanel build skipped: not live (world=null or plugin disabled).");
                return;
            }
            purgeStale();
            removeAll();
            spawnHeader();
            spawnTabs();
            spawnGrid(activeCategory());
            plugin.getLogger().info("BazaarPanel built: " + trackedEntities.size() + " entities at "
                    + world.getName() + " " + wx + "," + wy + "," + wz + " facing " + facing);
        } catch (Throwable t) {
            plugin.getLogger().info("BazaarPanel build failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static void rebuild() {
        if (instance != null) {
            instance.build();
        }
    }

    private void flipTo(String category) {
        try {
            if (!isLive()) return;
            if (category != null && !category.isBlank()) {
                activeCategory = category;
            }
            lastFlip = System.currentTimeMillis();
        } catch (Throwable ignored) {
        }
        build();
    }

    private void refreshContents() {
        try {
            if (!isLive()) return;
            removeGrid();
            spawnGrid(activeCategory());
        } catch (Throwable t) {
            plugin.getLogger().fine("panel refresh failed: " + t.getClass().getSimpleName());
        }
    }

    private void purgeStale() {
        try {
            purgeStaleClass(ItemDisplay.class);
            purgeStaleClass(TextDisplay.class);
            purgeStaleClass(Interaction.class);
        } catch (Throwable t) {
            plugin.getLogger().fine("panel purge skipped: " + t.getClass().getSimpleName());
        }
    }

    private <T extends Entity> void purgeStaleClass(Class<T> type) {
        for (T entity : world.getEntitiesByClass(type)) {
            try {
                if (entity.getPersistentDataContainer().has(PANEL_KEY, PersistentDataType.STRING)) {
                    entity.remove();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private void removeAll() {
        for (UUID id : trackedEntities) {
            despawn(id);
        }
        trackedEntities.clear();
        gridEntities.clear();
    }

    private void removeGrid() {
        for (UUID id : gridEntities) {
            despawn(id);
            trackedEntities.remove(id);
        }
        gridEntities.clear();
    }

    private void despawn(UUID id) {
        try {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        } catch (Throwable ignored) {
        }
    }

    private void track(Entity entity, boolean grid) {
        if (entity == null) return;
        try {
            trackedEntities.add(entity.getUniqueId());
            if (grid) {
                gridEntities.add(entity.getUniqueId());
            }
        } catch (Throwable ignored) {
        }
    }

    private Location point(double off, double dy) {
        double ax = 0.0D;
        double az = 0.0D;
        if ("east".equals(facing)) {
            az = off;
        } else if ("west".equals(facing)) {
            az = -off;
        } else {
            ax = off;
        }
        return new Location(world, wx + ax, wy + dy, wz + az);
    }

    private float wallYaw() {
        switch (facing) {
            case "north": return 180.0F;
            case "east": return -90.0F;
            case "west": return 90.0F;
            default: return 0.0F;
        }
    }

    private void spawnHeader() {
        try {
            Location loc = point(0.0D, 3.4D);
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(UiKit.deserialized("\uE049 <gradient:#C084FC:#F0ABFC><bold>GRAND BAZAAR</bold></gradient>"));
                    styleShared(t);
                    t.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(1.2F, 1.2F, 1.2F),
                            new Quaternionf()));
                } catch (Throwable err) {
                    plugin.getLogger().fine("header styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d, false);
        } catch (Throwable t) {
            plugin.getLogger().fine("header spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void styleShared(TextDisplay t) {
        t.setBillboard(Display.Billboard.CENTER);
        t.setShadowed(true);
        t.setSeeThrough(false);
        t.setDefaultBackground(false);
        t.setBackgroundColor(Color.fromARGB(0x90000000));
        t.setAlignment(TextDisplay.TextAlignment.CENTER);
        t.setPersistent(false);
        t.setTeleportDuration(1);
    }

    private TextDisplay spawnText(Location loc, String mini, float scale, boolean grid) {
        try {
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(UiKit.deserialized(mini));
                    styleShared(t);
                    t.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(scale, scale, scale),
                            new Quaternionf()));
                } catch (Throwable err) {
                    plugin.getLogger().fine("text styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d, grid);
            return d;
        } catch (Throwable t) {
            plugin.getLogger().fine("text spawn failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private Interaction spawnHitbox(Location loc, float width, float height, String kind, String value, boolean grid) {
        try {
            Interaction hit = world.spawn(loc, Interaction.class, h -> {
                try {
                    h.setInteractionWidth(width);
                    h.setInteractionHeight(height);
                    h.setResponsive(true);
                    h.setPersistent(false);
                } catch (Throwable err) {
                    plugin.getLogger().fine("hitbox styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            tag(hit, kind, value);
            track(hit, grid);
            return hit;
        } catch (Throwable t) {
            plugin.getLogger().fine("hitbox spawn failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private void tag(Entity entity, String kind, String value) {
        try {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(PANEL_KEY, PersistentDataType.STRING, kind);
            pdc.set(VALUE_KEY, PersistentDataType.STRING, value);
        } catch (Throwable ignored) {
        }
    }

    private void spawnTabs() {
        try {
            List<String> tabs = gui.tabOrder();
            if (tabs == null || tabs.isEmpty()) return;
            int n = tabs.size();
            for (int i = 0; i < n; i++) {
                final String category = tabs.get(i);
                boolean active = category != null && category.equals(activeCategory());
                double off = (i - (n - 1) / 2.0D) * 1.3D;
                String mini = (active ? "<gold><bold>" : "<gray><bold>")
                        + gui.categoryLabel(category) + "</bold>";
                spawnText(point(off, 2.6D), mini, 0.8F, false);
                spawnHitbox(point(off, 2.6D), 1.1F, 0.6F, "tab", category, false);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("tabs spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnGrid(String category) {
        try {
            if ("gear".equals(category)) {
                spawnGearGrid();
            } else {
                spawnShopGrid(category);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("grid spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnShopGrid(String category) {
        List<String> ids = gui.stockIds(category);
        for (int idx = 0; idx < 21; idx++) {
            if (idx >= ids.size()) break;
            final String id = ids.get(idx);
            Integer price = gui.buyPriceFor(category, id);
            if (price == null) continue;
            int c = idx % 7;
            int r = idx / 7;
            double off = (c - 3) * spacing;
            double dy = (2 - r) * spacing * 0.9D + 1.2D;
            final ItemStack stack = buildStack(id);
            final String name = gui.displayNameOf(id);
            final String mini = "<white>" + name + "</white>\n<aqua>"
                    + UiKit.SHARD_GLYPH + " " + price + " Shards</aqua>";
            spawnItem(point(off, dy), stack);
            spawnText(point(off, dy - 0.45D), mini, 0.55F, true);
            spawnHitbox(point(off, dy), 0.9F, 1.0F, "item", category + "|" + id, true);
        }
    }

    private void spawnGearGrid() {
        List<ShopManager.GearStockEntry> stock = plugin.getShopManager().getGearStock();
        for (int i = 0; i < stock.size() && i < 21; i++) {
            ShopManager.GearStockEntry entry = stock.get(i);
            if (entry == null || entry.item() == null) continue;
            int c = i % 7;
            int r = i / 7;
            double off = (c - 3) * spacing;
            double dy = (2 - r) * spacing * 0.9D + 1.2D;
            final ItemStack stack = entry.item().clone();
            final int idx = i;
            final String mini = "<white>" + plainName(stack) + "</white>\n<aqua>"
                    + UiKit.SHARD_GLYPH + " " + entry.price() + " Shards</aqua>";
            spawnItem(point(off, dy), stack);
            spawnText(point(off, dy - 0.45D), mini, 0.55F, true);
            spawnHitbox(point(off, dy), 0.9F, 1.0F, "item", "gear|" + idx, true);
        }
    }

    private void spawnItem(Location loc, ItemStack stack) {
        final float yaw = wallYaw();
        try {
            ItemDisplay d = world.spawn(loc, ItemDisplay.class, disp -> {
                try {
                    disp.setItemStack(stack);
                    disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    disp.setBillboard(Display.Billboard.FIXED);
                    disp.setPersistent(false);
                    disp.setRotation(yaw, 0.0F);
                    disp.setTeleportDuration(1);
                    disp.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf().rotationY(-(float) Math.toRadians(yaw)),
                            new Vector3f(0.9F, 0.9F, 0.9F),
                            new Quaternionf()));
                } catch (Throwable err) {
                    plugin.getLogger().fine("item styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d, true);
        } catch (Throwable t) {
            plugin.getLogger().fine("item spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private ItemStack buildStack(String id) {
        try {
            ItemBuilder builder = OraxenItems.getItemById(id);
            if (builder == null) {
                return fallbackPaper();
            }
            ItemStack built = builder.build();
            return built == null ? fallbackPaper() : built.clone();
        } catch (Throwable t) {
            return fallbackPaper();
        }
    }

    private ItemStack fallbackPaper() {
        try {
            return new ItemStack(Material.PAPER);
        } catch (Throwable t) {
            return null;
        }
    }

    private String plainName(ItemStack stack) {
        try {
            if (stack.hasItemMeta()) {
                net.kyori.adventure.text.Component custom = stack.getItemMeta().customName();
                if (custom != null) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(custom);
                    if (!plain.isEmpty()) return plain;
                }
            }
            String mat = stack.getType().name().toLowerCase().replace('_', ' ');
            return Character.toUpperCase(mat.charAt(0)) + mat.substring(1);
        } catch (Throwable t) {
            return "gear";
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        try {
            if (!(event.getRightClicked() instanceof Interaction hit)) return;
            String kind;
            String value;
            try {
                kind = hit.getPersistentDataContainer().get(PANEL_KEY, PersistentDataType.STRING);
                value = hit.getPersistentDataContainer().get(VALUE_KEY, PersistentDataType.STRING);
            } catch (Throwable t) {
                return;
            }
            if (kind == null) return;
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) return;
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            Long prior = lastClick.get(player.getUniqueId());
            if (prior != null && now - prior < 400L) return;
            if (lastClick.size() > 512) {
                lastClick.clear();
            }
            lastClick.put(player.getUniqueId(), now);
            if ("tab".equals(kind)) {
                if (value == null) return;
                try {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.4F);
                } catch (Throwable ignored) {
                }
                flipTo(value);
                return;
            }
            if ("item".equals(kind)) {
                handleItemClick(player, value);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("panel click failed: " + t.getClass().getSimpleName());
        }
    }

    private void handleItemClick(Player player, String value) {
        if (value == null) return;
        int sep = value.indexOf('|');
        if (sep < 0) return;
        String category = value.substring(0, sep);
        String rest = value.substring(sep + 1);
        if ("gear".equals(category)) {
            int idx;
            try {
                idx = Integer.parseInt(rest.trim());
            } catch (NumberFormatException e) {
                return;
            }
            List<ShopManager.GearStockEntry> stock = plugin.getShopManager().getGearStock();
            if (idx < 0 || idx >= stock.size()) {
                refreshContents();
                return;
            }
            ShopManager.GearStockEntry entry = stock.get(idx);
            if (entry == null || entry.item() == null) {
                refreshContents();
                return;
            }
            enqueueBuy(() -> gui.buyGearFromDialog(player, idx));
            return;
        }
        Integer price = gui.buyPriceFor(category, rest);
        if (price == null) {
            refreshContents();
            return;
        }
        final String itemId = rest;
        if (price <= instantBuyMax) {
            enqueueBuy(() -> gui.buyFromDialog(player, category, itemId, 1));
        } else {
            DialogUI.openBuyConfirm(plugin, gui, player, category, itemId);
        }
    }

    private void enqueueBuy(Runnable action) {
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    plugin.getLogger().fine("panel buy failed: " + t.getClass().getSimpleName());
                }
            }, 1L);
        } catch (Throwable t) {
            plugin.getLogger().fine("panel schedule failed: " + t.getClass().getSimpleName());
        }
    }
}
