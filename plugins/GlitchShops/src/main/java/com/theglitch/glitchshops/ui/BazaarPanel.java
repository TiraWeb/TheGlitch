package com.theglitch.glitchshops.ui;

import com.theglitch.glitchshops.GlitchShops;
import com.theglitch.glitchshops.ShopGUI;
import com.theglitch.glitchshops.ShopManager;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
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
    private static final double[] GRID_ROW_Y = {2.55D, 1.50D, 0.45D};
    private static final double LABEL_DY = 0.42D;
    private static final int LABEL_MAX = 14;

    // Fix 3: single yaw source = configured `modern-ui.world-panel.facing` (south/north/east/west)
    // mapped through wallYaw()/panelYaw(). Every TextDisplay + ItemDisplay of one build uses the
    // SAME yaw with FIXED billboard + setRotation(yaw, 0) so item and label always agree.
    // Interaction hitboxes cannot rotate — they stay axis-aligned, centered/sized to cover the
    // row at that yaw.
    // Layout: ROW_PITCH 1.05 between GRID_ROW_Y entries; hitbox height 0.9 leaves a 0.15 dead gap
    // between adjacent rows; hitbox width 1.2 (< default spacing 1.35) leaves a 0.15 dead gap
    // between columns (clamped to spacing - dead gap when spacing is tight). Hitbox center is
    // shifted down by HITBOX_DY_OFFSET so one box covers both the item icon (row anchor) and its
    // label below, plus margin, for easy 3-4 block clicks.
    private static final float PANEL_PITCH = 0.0F;
    private static final Display.Billboard PANEL_BILLBOARD = Display.Billboard.CENTER; // text + icons face the viewer
    private static final float HEADER_SCALE = 1.1F;
    private static final float TAB_TEXT_SCALE = 0.75F;
    private static final float ROW_TEXT_SCALE = 0.5F;
    private static final float ROW_ITEM_SCALE = 0.85F;
    private static final double ROW_PITCH = 1.05D;
    private static final double HITBOX_DY_OFFSET = -0.15D;
    private static final float ROW_HITBOX_WIDTH = 1.2F;
    private static final float ROW_HITBOX_HEIGHT = 0.9F;
    private static final float TAB_HITBOX_WIDTH = 1.2F;
    private static final float TAB_HITBOX_HEIGHT = 0.7F;
    private static final float HITBOX_DEAD_GAP = 0.15F;

    private static GlitchShops plugin;
    private static ShopGUI gui;
    private static BazaarPanel instance;
    private static BukkitTask buildTask;
    private static BukkitTask refreshTask;

    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();
    private final Set<UUID> gridEntities = new HashSet<>();

    private String activeCategory;

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;
    private boolean cfgEnabled;

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
        cancelTasks();
        buildTask = pl.getServer().getScheduler().runTaskLater(pl, panel::build, 20L);
        refreshTask = pl.getServer().getScheduler().runTaskTimer(pl, panel::refreshContents, period, period);
        pl.getLogger().info("Grand Bazaar wall panel armed at " + worldNameSafe(panel) + ".");
    }

    public static void reconfigureAndRebuild() {
        try {
            if (plugin == null) return;
            if (instance != null) {
                if (instance.loadConfig()) {
                    scheduleTasks();
                    instance.build();
                } else {
                    cancelTasks();
                    try {
                        instance.removeAll();
                        HandlerList.unregisterAll(instance);
                    } catch (Throwable ignored) {
                    }
                    instance = null;
                }
                return;
            }
            init(plugin, gui);
        } catch (Throwable t) {
            if (plugin != null) {
                plugin.getLogger().fine("panel reconfigure failed: " + t.getClass().getSimpleName());
            }
        }
    }

    private static void scheduleTasks() {
        long refreshSeconds;
        try {
            refreshSeconds = plugin.getConfig().getLong("modern-ui.world-panel.refresh-seconds", 600L);
        } catch (Throwable t) {
            refreshSeconds = 600L;
        }
        long period = Math.max(200L, refreshSeconds * 20L);
        cancelTasks();
        buildTask = plugin.getServer().getScheduler().runTaskLater(plugin, instance::build, 20L);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, instance::refreshContents, period, period);
    }

    public static void removeWall() {
        try {
            cancelTasks();
            if (instance != null) {
                instance.cfgEnabled = false;
                instance.removeAll();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void cancelTasks() {
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
    }

    public static void shutdown() {
        cancelTasks();
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
            cfgEnabled = plugin.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
            String name = plugin.getConfig().getString("modern-ui.world-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                plugin.getLogger().warning("world-panel world '" + name + "' not found — wall panel dormant.");
                return false;
            }
            if (!cfgEnabled) {
                return false;
            }
            world = w;
            wx = plugin.getConfig().getDouble("modern-ui.world-panel.x", 0.0D);
            wy = plugin.getConfig().getDouble("modern-ui.world-panel.y", 0.0D);
            wz = plugin.getConfig().getDouble("modern-ui.world-panel.z", 0.0D);
            String f = plugin.getConfig().getString("modern-ui.world-panel.facing", "south");
            facing = f == null ? "south" : f.toLowerCase();
            spacing = plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.35D);
            if (spacing < 0.5D) {
                spacing = 0.5D;
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("world-panel config invalid — wall panel dormant.");
            return false;
        }
    }

    private boolean isLive() {
        return instance == this && cfgEnabled && plugin != null && plugin.isEnabled() && world != null;
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
            forceLoadPanelChunks();
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

    private void forceLoadPanelChunks() {
        try {
            int minCX = ((int) Math.floor(wx) - 8) >> 4;
            int maxCX = ((int) Math.floor(wx) + 8) >> 4;
            int minCZ = ((int) Math.floor(wz) - 8) >> 4;
            int maxCZ = ((int) Math.floor(wz) + 8) >> 4;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    world.setChunkForceLoaded(cx, cz, true);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().info("BazaarPanel force-load skipped: " + t.getClass().getSimpleName());
        }
    }

    private void flipTo(String category) {
        try {
            if (!isLive()) return;
            if (category != null && !category.isBlank()) {
                activeCategory = category;
            }
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

    // Fix 3: single yaw source for the whole panel build. Currently the configured facing;
    // placer-yaw placement should persist through the same config key so all rows agree.
    private float panelYaw() {
        return wallYaw();
    }

    private void spawnHeader() {
        try {
            Location loc = point(0.0D, 4.3D);
            final float yaw = panelYaw();
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(UiKit.deserialized("\uE049 <gradient:#C084FC:#F0ABFC><bold>GRAND BAZAAR</bold></gradient>"));
                    styleShared(t);
                    t.setRotation(yaw, PANEL_PITCH);
                    t.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(HEADER_SCALE, HEADER_SCALE, HEADER_SCALE),
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
        t.setBillboard(PANEL_BILLBOARD);
        t.setShadowed(true);
        t.setSeeThrough(false);
        t.setDefaultBackground(false);
        t.setBackgroundColor(Color.fromARGB(0x90000000));
        t.setAlignment(TextDisplay.TextAlignment.CENTER);
        t.setPersistent(true);
        t.setTeleportDuration(1);
    }

    private TextDisplay spawnText(Location loc, String mini, float scale, boolean grid) {
        try {
            final float yaw = panelYaw();
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(UiKit.deserialized(mini));
                    styleShared(t);
                    t.setRotation(yaw, PANEL_PITCH);
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
                    h.setPersistent(true);
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
                double off = (i - (n - 1) / 2.0D) * spacing;
                String mini = (active ? "<gold><bold>" : "<gray><bold>")
                        + gui.categoryLabel(category) + "</bold>";
                spawnText(point(off, 3.3D), mini, TAB_TEXT_SCALE, false);
                float tabW = (float) Math.min(TAB_HITBOX_WIDTH, Math.max(0.6D, spacing - HITBOX_DEAD_GAP));
                spawnHitbox(point(off, 3.3D), tabW, TAB_HITBOX_HEIGHT, "tab", category, false);
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
            if (price == null || price <= 0) continue;
            int c = idx % 7;
            int r = idx / 7;
            double off = (c - 3) * spacing;
            double dy = GRID_ROW_Y[Math.min(r, 2)];
            final ItemStack stack = buildStack(id);
            final String name = truncateName(gui.displayNameOf(id));
            final String mini = "<white>" + name + "</white>\n<aqua>"
                    + UiKit.SHARD_GLYPH + " " + price + " Shards</aqua>";
            spawnItem(point(off, dy), stack);
            spawnText(point(off, dy - LABEL_DY), mini, ROW_TEXT_SCALE, true);
            float rowW = (float) Math.min(ROW_HITBOX_WIDTH, Math.max(0.6D, spacing - HITBOX_DEAD_GAP));
            spawnHitbox(point(off, dy + HITBOX_DY_OFFSET), rowW, ROW_HITBOX_HEIGHT, "item", category + "|" + id, true);
        }
    }

    private void spawnGearGrid() {
        List<ShopManager.GearStockEntry> stock = plugin.getShopManager().getGearStock();
        for (int i = 0; i < stock.size() && i < 21; i++) {
            ShopManager.GearStockEntry entry = stock.get(i);
            if (entry == null || entry.item() == null || entry.price() <= 0) continue;
            int c = i % 7;
            int r = i / 7;
            double off = (c - 3) * spacing;
            double dy = GRID_ROW_Y[Math.min(r, 2)];
            final ItemStack stack = entry.item().clone();
            final String mini = "<white>" + truncateName(plainName(stack)) + "</white>\n<aqua>"
                    + UiKit.SHARD_GLYPH + " " + entry.price() + " Shards</aqua>";
            spawnItem(point(off, dy), stack);
            spawnText(point(off, dy - LABEL_DY), mini, ROW_TEXT_SCALE, true);
            float rowW = (float) Math.min(ROW_HITBOX_WIDTH, Math.max(0.6D, spacing - HITBOX_DEAD_GAP));
            spawnHitbox(point(off, dy + HITBOX_DY_OFFSET), rowW, ROW_HITBOX_HEIGHT, "item", "gear|" + entry.id(), true);
        }
    }

    private String truncateName(String name) {
        if (name == null || name.isBlank()) return "???";
        String plain = name.trim();
        return plain.length() <= LABEL_MAX ? plain : plain.substring(0, LABEL_MAX) + "\u2026";
    }

    private void spawnItem(Location loc, ItemStack stack) {
        final float yaw = panelYaw();
        try {
            ItemDisplay d = world.spawn(loc, ItemDisplay.class, disp -> {
                try {
                    disp.setItemStack(stack);
                    disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    disp.setBillboard(PANEL_BILLBOARD);
                    disp.setPersistent(true);
                    disp.setRotation(yaw, PANEL_PITCH);
                    disp.setTeleportDuration(1);
                    disp.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(ROW_ITEM_SCALE, ROW_ITEM_SCALE, ROW_ITEM_SCALE),
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
            ItemBuilder builder = NexoItems.itemFromId(id);
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
                PersistentDataContainer pdc = hit.getPersistentDataContainer();
                kind = pdc.get(PANEL_KEY, PersistentDataType.STRING);
                value = pdc.get(VALUE_KEY, PersistentDataType.STRING);
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
            final String gearId = rest.trim();
            ShopManager.GearStockEntry entry = plugin.getShopManager().gearStockById(gearId);
            if (entry == null || entry.item() == null) {
                refreshContents();
                return;
            }
            // Chat [YES]/[NO] first so a stray click can't spend shards (2026-09-25).
            String gearName = entry.item().hasItemMeta() && entry.item().getItemMeta().hasCustomName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                            .serialize(entry.item().getItemMeta().customName())
                    : gearId;
            confirmBuy(player, gearName, entry.price(), () -> gui.buyGearFromDialog(player, gearId));
            return;
        }
        Integer price = gui.buyPriceFor(category, rest);
        if (price == null || price <= 0) {
            refreshContents();
            return;
        }
        final String itemId = rest;
        confirmBuy(player, gui.displayNameOf(itemId), price, () -> gui.buyFromDialog(player, category, itemId, 1));
    }

    private void confirmBuy(Player player, String name, int price, Runnable buy) {
        com.theglitch.common.ChatConfirm.ask(player,
                net.kyori.adventure.text.Component.text("Buy ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                        .append(net.kyori.adventure.text.Component.text(name, net.kyori.adventure.text.format.NamedTextColor.WHITE))
                        .append(net.kyori.adventure.text.Component.text(" for ", net.kyori.adventure.text.format.NamedTextColor.GRAY))
                        .append(net.kyori.adventure.text.Component.text(price + " shards", net.kyori.adventure.text.format.NamedTextColor.AQUA))
                        .append(net.kyori.adventure.text.Component.text("?", net.kyori.adventure.text.format.NamedTextColor.GRAY)),
                () -> enqueueBuy(buy));
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
