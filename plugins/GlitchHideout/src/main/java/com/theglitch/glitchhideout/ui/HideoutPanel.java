package com.theglitch.glitchhideout.ui;

import com.theglitch.glitchhideout.GlitchHideout;
import com.theglitch.glitchhideout.HideoutManager;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HideoutPanel implements Listener {

    private static final NamespacedKey PANEL_KEY = new NamespacedKey("glitchhideout", "panel");
    private static final NamespacedKey VALUE_KEY = new NamespacedKey("glitchhideout", "value");
    // Resolved Material.matchMaterial results — config icon strings are a tiny fixed set
    private static final Map<String, Material> ICON_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final double HEADER_Y = 4.3D;
    private static final float HEADER_SCALE = 1.1F;
    private static final double[] ROW_Y = {2.55D, 1.5D, 0.45D};
    private static final float ITEM_SCALE = 0.85F;
    private static final float LABEL_SCALE = 0.5F;
    private static final int MAX_CELLS = 7;
    private static final String HEADER_TEXT =
            "\uE049 <gradient:#C084FC:#F0ABFC><bold>THE HIDEOUT</bold></gradient>";

    private static GlitchHideout plugin;
    private static HideoutPanel instance;
    private static BukkitTask buildTask;
    private static BukkitTask refreshTask;

    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();
    private final Set<UUID> gridEntities = new HashSet<>();
    private final Map<String, Integer> anchorLevels = new HashMap<>();

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;
    private String lastSignature = "";

    private HideoutPanel() {
    }

    public static void init(GlitchHideout pl) {
        plugin = pl;
        boolean enabled;
        try {
            enabled = pl.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
        } catch (Throwable t) {
            enabled = false;
        }
        if (!enabled) return;
        ensureRunning();
    }

    public static void shutdown() {
        disableAll();
    }

    public static void reconfigureAndRebuild() {
        if (plugin == null || !plugin.isEnabled()) return;
        boolean enabled;
        try {
            enabled = plugin.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
        } catch (Throwable t) {
            enabled = false;
        }
        if (!enabled) {
            disableAll();
            return;
        }
        if (instance == null) {
            ensureRunning();
            return;
        }
        if (!instance.loadConfig()) {
            disableAll();
            return;
        }
        instance.build();
    }

    public static void rebuild() {
        if (plugin == null || !plugin.isEnabled()) return;
        if (instance == null) {
            ensureRunning();
            return;
        }
        instance.build();
    }

    private static void ensureRunning() {
        if (instance != null || plugin == null || !plugin.isEnabled()) return;
        HideoutPanel panel = new HideoutPanel();
        if (!panel.loadConfig()) return;
        instance = panel;
        panel.anchorSnapshot();
        try {
            plugin.getServer().getPluginManager().registerEvents(panel, plugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("HideoutPanel listener registration failed: " + t.getMessage());
            instance = null;
            return;
        }
        buildTask = plugin.getServer().getScheduler().runTaskLater(plugin, panel::build, 100L);
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                panel::refreshPersonalization, 140L, 40L);
    }

    private static void disableAll() {
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

    private boolean loadConfig() {
        try {
            String name = plugin.getConfig().getString("modern-ui.world-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                plugin.getLogger().warning("world-panel world '" + name + "' not found — hideout wall panel dormant.");
                return false;
            }
            world = w;
            wx = plugin.getConfig().getDouble("modern-ui.world-panel.x", 90.0D);
            wy = plugin.getConfig().getDouble("modern-ui.world-panel.y", -33.5D);
            wz = plugin.getConfig().getDouble("modern-ui.world-panel.z", 10.0D);
            String f = plugin.getConfig().getString("modern-ui.world-panel.facing", "west");
            facing = f == null ? "west" : f.toLowerCase(Locale.ROOT);
            spacing = plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.35D);
            if (spacing < 0.5D) spacing = 0.5D;
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("world-panel config invalid — hideout wall panel dormant.");
            return false;
        }
    }

    private boolean isLive() {
        return instance == this && plugin != null && plugin.isEnabled() && world != null;
    }

    private void build() {
        try {
            if (!isLive()) {
                plugin.getLogger().info("HideoutPanel build skipped: not live.");
                return;
            }
            purgeStale();
            removeAll();
            forceLoadPanelChunks();
            anchorSnapshot();
            spawnHeader();
            spawnGrid();
            plugin.getLogger().info("HideoutPanel built: " + trackedEntities.size() + " entities at "
                    + world.getName() + " " + wx + "," + wy + "," + wz + " facing " + facing);
        } catch (Throwable t) {
            plugin.getLogger().info("HideoutPanel build failed: "
                    + t.getClass().getSimpleName() + ": " + t.getMessage());
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
            plugin.getLogger().info("HideoutPanel force-load skipped: " + t.getClass().getSimpleName());
        }
    }

    private void refreshPersonalization() {
        try {
            if (!isLive()) return;
            Player anchor = nearestViewer();
            String signature = signatureOf(anchor);
            if (signature.equals(lastSignature)) return;
            lastSignature = signature;
            removeGrid();
            spawnGrid();
        } catch (Throwable t) {
            plugin.getLogger().fine("HideoutPanel refresh failed: " + t.getClass().getSimpleName());
        }
    }

    private Player nearestViewer() {
        Player best = null;
        double bestDistSq = 256.0D;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;
            Location loc = p.getLocation();
            double dx = loc.getX() - wx;
            double dy = loc.getY() - wy;
            double dz = loc.getZ() - wz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = p;
            }
        }
        return best;
    }

    private String signatureOf(Player anchor) {
        StringBuilder sb = new StringBuilder();
        List<HideoutManager.Station> stations = plugin.getHideoutManager().getStations();
        for (int i = 0; i < stations.size(); i++) {
            HideoutManager.Station station = stations.get(i);
            int level;
            if (anchor != null) {
                level = Math.min(plugin.getHideoutManager().getLevel(anchor.getUniqueId(), station.id()),
                        station.costs().length);
                anchorLevels.put(station.id(), level);
            } else {
                level = anchorLevels.getOrDefault(station.id(), 0);
            }
            if (i > 0) sb.append(';');
            sb.append(station.id()).append(':').append(level);
        }
        return sb.toString();
    }

    private void anchorSnapshot() {
        anchorLevels.clear();
        lastSignature = signatureOf(nearestViewer());
    }

    private void purgeStale() {
        try {
            purgeStaleClass(ItemDisplay.class);
            purgeStaleClass(TextDisplay.class);
            purgeStaleClass(Interaction.class);
        } catch (Throwable t) {
            plugin.getLogger().fine("HideoutPanel purge skipped: " + t.getClass().getSimpleName());
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
            if (e != null) e.remove();
        } catch (Throwable ignored) {
        }
    }

    private void track(Entity entity, boolean grid) {
        if (entity == null) return;
        try {
            trackedEntities.add(entity.getUniqueId());
            if (grid) gridEntities.add(entity.getUniqueId());
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
            Location loc = point(0.0D, HEADER_Y);
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(GlitchHideout.mm().deserialize(HEADER_TEXT));
                    styleShared(t);
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
        t.setBillboard(Display.Billboard.CENTER);
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
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(GlitchHideout.mm().deserialize(mini));
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

    private Interaction spawnHitbox(Location loc, String kind, String value, boolean grid) {
        try {
            Interaction hit = world.spawn(loc, Interaction.class, h -> {
                try {
                    h.setInteractionWidth(0.85F);
                    h.setInteractionHeight(1.0F);
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

    private void spawnItem(Location loc, ItemStack stack) {
        final float yaw = wallYaw();
        try {
            ItemDisplay d = world.spawn(loc, ItemDisplay.class, disp -> {
                try {
                    disp.setItemStack(stack);
                    disp.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    disp.setBillboard(Display.Billboard.FIXED);
                    disp.setPersistent(true);
                    disp.setRotation(yaw, 0.0F);
                    disp.setTeleportDuration(1);
                    disp.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf().rotationY(-(float) Math.toRadians(yaw)),
                            new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
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

    private void spawnGrid() {
        try {
            List<HideoutManager.Station> stations = plugin.getHideoutManager().getStations();
            float yaw = wallYaw();
            for (int i = 0; i < stations.size() && i < MAX_CELLS; i++) {
                HideoutManager.Station station = stations.get(i);
                int c = i % MAX_CELLS;
                double colOff = (c - 3) * spacing;
                double rowY = ROW_Y[0];
                Material icon = resolveIcon(station);
                ItemStack stack = icon == null ? null : new ItemStack(icon);
                if (stack != null) {
                    spawnItem(point(colOff, rowY), stack);
                }
                int lvl = anchorLevels.containsKey(station.id())
                        ? Math.max(0, anchorLevels.get(station.id()))
                        : 0;
                String mini = "<white>" + truncate(plainName(station.display()), 14)
                        + "</white>\n<gold>Lv " + lvl + "/" + station.costs().length + "</gold>";
                spawnText(point(colOff, rowY - 0.42D), mini, LABEL_SCALE, true);
                spawnHitbox(point(colOff, rowY), "cell", "station|" + station.id(), true);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("grid spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private Material resolveIcon(HideoutManager.Station station) {
        Material configured = null;
        try {
            String raw = station.icon();
            if (raw != null) {
                String key = raw.toUpperCase(Locale.ROOT);
                if (ICON_CACHE.containsKey(key)) {
                    configured = ICON_CACHE.get(key);
                } else {
                    configured = Material.matchMaterial(raw);
                    if (configured != null) ICON_CACHE.put(key, configured);
                }
            }
        } catch (Throwable ignored) {
        }
        if (configured != null) return configured;
        switch (station.id() == null ? "" : station.id().toLowerCase(Locale.ROOT)) {
            case "arcane_core":
            case "core": return Material.BEACON;
            case "workbench": return Material.CRAFTING_TABLE;
            case "med": return Material.BREWING_STAND;
            case "stash": return Material.CHEST;
            case "intel": return Material.SPYGLASS;
            case "trainer": return Material.BOOK;
            case "armory": return Material.ARMOR_STAND;
            default: return Material.STONE;
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
            if ("cell".equals(kind)) {
                handleCellClick(player, value);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("panel click failed: " + t.getClass().getSimpleName());
        }
    }

    private void handleCellClick(Player player, String value) {
        if (value == null || !value.startsWith("station|")) return;
        String stationId = value.substring("station|".length());
        if (plugin.getHideoutManager().getStation(stationId) == null) {
            rebuild();
            return;
        }
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.4F);
        } catch (Throwable ignored) {
        }
        enqueue(() -> DialogUI.openStation(plugin, player, stationId, "hideoutui noop"));
    }

    private void enqueue(Runnable action) {
        try {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    action.run();
                } catch (Throwable t) {
                    plugin.getLogger().fine("panel action failed: " + t.getClass().getSimpleName());
                }
            }, 1L);
        } catch (Throwable t) {
            plugin.getLogger().fine("panel schedule failed: " + t.getClass().getSimpleName());
        }
    }

    private String plainName(String miniMessage) {
        if (miniMessage == null || miniMessage.isEmpty()) return "";
        try {
            String plain = PlainTextComponentSerializer.plainText()
                    .serialize(GlitchHideout.mm().deserialize(miniMessage));
            return plain == null ? "" : plain.strip();
        } catch (Throwable t) {
            return miniMessage.replaceAll("<[^>]*>", "");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "\u2026";
    }
}
