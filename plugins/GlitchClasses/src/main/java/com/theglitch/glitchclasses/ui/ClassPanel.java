package com.theglitch.glitchclasses.ui;

import com.theglitch.glitchclasses.ClassGUI;
import com.theglitch.glitchclasses.GlitchClasses;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ClassPanel implements Listener {

    private static final NamespacedKey PANEL_KEY = new NamespacedKey("glitchclasses", "panel");
    private static final NamespacedKey VALUE_KEY = new NamespacedKey("glitchclasses", "value");

    private static final double HEADER_Y = 4.3D;
    private static final float HEADER_SCALE = 1.1F;
    private static final double GRID_Y = 2.55D;
    private static final double LABEL_DY = 0.42D;
    private static final float ITEM_SCALE = 0.85F;
    private static final float LABEL_SCALE = 0.5F;
    private static final float HITBOX_WIDTH = 0.85F;
    private static final float HITBOX_HEIGHT = 1.0F;
    private static final double CELL_CENTER = 2.5D;
    private static final int MAX_CLASS_CELLS = 4;

    private static final Map<String, String> COLOR_MINI = Map.of(
            "red", "red",
            "green", "green",
            "dark_purple", "dark_purple",
            "light_purple", "light_purple");

    private static GlitchClasses plugin;
    private static ClassPanel instance;
    private static BukkitTask buildTask;
    private static boolean warnedMissingWorld;

    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;
    private boolean configEnabled;

    private ClassPanel() {
    }

    public static void init(GlitchClasses pl) {
        plugin = pl;
        if (instance != null) {
            return;
        }
        boolean enabled;
        try {
            enabled = pl.getConfig().getBoolean("modern-ui.class-panel.enabled", true);
        } catch (Throwable t) {
            enabled = false;
        }
        if (!enabled) {
            return;
        }
        ClassPanel panel = new ClassPanel();
        if (!panel.loadConfig()) {
            return;
        }
        instance = panel;
        try {
            pl.getServer().getPluginManager().registerEvents(panel, pl);
        } catch (Throwable t) {
            pl.getLogger().warning("ClassPanel listener registration failed: " + t.getMessage());
            instance = null;
            return;
        }
        buildTask = pl.getServer().getScheduler().runTaskLater(pl, panel::build, 100L);
        pl.getLogger().info("Class wall panel armed at "
                + (panel.world == null ? "?" : panel.world.getName()) + ".");
    }

    public static void shutdown() {
        if (buildTask != null) {
            try {
                buildTask.cancel();
            } catch (Throwable ignored) {
            }
            buildTask = null;
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

    public static void rebuild() {
        if (instance != null) {
            instance.build();
        }
    }

    public static void reconfigureAndRebuild() {
        if (plugin == null) {
            return;
        }
        if (instance == null) {
            init(plugin);
            return;
        }
        if (!instance.loadConfig()) {
            try {
                instance.removeAll();
            } catch (Throwable ignored) {
            }
            return;
        }
        instance.build();
    }

    public static void hide() {
        if (instance != null) {
            try {
                instance.removeAll();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean loadConfig() {
        try {
            configEnabled = plugin.getConfig().getBoolean("modern-ui.class-panel.enabled", true);
            if (!configEnabled) {
                return false;
            }
            String name = plugin.getConfig().getString("modern-ui.class-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                if (!warnedMissingWorld) {
                    plugin.getLogger().warning("class-panel world '" + name + "' not found — wall panel dormant.");
                    warnedMissingWorld = true;
                }
                return false;
            }
            world = w;
            wx = plugin.getConfig().getDouble("modern-ui.class-panel.x", 177.0D);
            wy = plugin.getConfig().getDouble("modern-ui.class-panel.y", -33.5D);
            wz = plugin.getConfig().getDouble("modern-ui.class-panel.z", -59.4D);
            String f = plugin.getConfig().getString("modern-ui.class-panel.facing", "west");
            facing = f == null ? "west" : f.toLowerCase(Locale.ROOT);
            spacing = plugin.getConfig().getDouble("modern-ui.class-panel.spacing", 1.35D);
            if (spacing < 0.5D) {
                spacing = 0.5D;
            }
            return true;
        } catch (Throwable t) {
            if (!warnedMissingWorld) {
                plugin.getLogger().warning("class-panel config invalid — wall panel dormant.");
                warnedMissingWorld = true;
            }
            return false;
        }
    }

    private boolean isLive() {
        return instance == this && plugin != null && plugin.isEnabled() && world != null && configEnabled;
    }

    private void build() {
        try {
            if (!isLive()) {
                return;
            }
            purgeStale();
            removeAll();
            forceLoadPanelChunks();
            spawnHeader();
            spawnCells();
            plugin.getLogger().info("ClassPanel built: " + trackedEntities.size() + " entities at "
                    + world.getName() + " " + wx + "," + wy + "," + wz + " facing " + facing);
        } catch (Throwable t) {
            plugin.getLogger().info("ClassPanel build failed: "
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
            plugin.getLogger().fine("class-panel force-load skipped: " + t.getClass().getSimpleName());
        }
    }

    private void purgeStale() {
        try {
            purgeStaleClass(ItemDisplay.class);
            purgeStaleClass(TextDisplay.class);
            purgeStaleClass(Interaction.class);
        } catch (Throwable t) {
            plugin.getLogger().fine("class-panel purge skipped: " + t.getClass().getSimpleName());
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

    private void track(Entity entity) {
        if (entity == null) return;
        try {
            trackedEntities.add(entity.getUniqueId());
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

    private void styleShared(TextDisplay t) {
        try {
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setSeeThrough(false);
            t.setDefaultBackground(false);
            t.setBackgroundColor(Color.fromARGB(0x90000000));
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setPersistent(true);
            t.setTeleportDuration(1);
        } catch (Throwable err) {
            plugin.getLogger().fine("text styling incomplete: " + err.getClass().getSimpleName());
        }
    }

    private void spawnHeader() {
        try {
            Location loc = point(0.0D, HEADER_Y);
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(UiKit.deserialized(
                            "\uE049 <gradient:#C084FC:#F0ABFC><bold>CHOOSE YOUR CLASS</bold></gradient>"));
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
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("header spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private TextDisplay spawnText(Location loc, String mini, float scale) {
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
                    plugin.getLogger().fine("label styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d);
            return d;
        } catch (Throwable t) {
            plugin.getLogger().fine("label spawn failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private void spawnHitbox(Location loc, float width, float height, String kind, String value) {
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
            track(hit);
        } catch (Throwable t) {
            plugin.getLogger().fine("hitbox spawn failed: " + t.getClass().getSimpleName());
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
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("item spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnCells() {
        try {
            ClassGUI gui = plugin.getClassGUI();
            String[] order = gui != null ? gui.classOrder() : new String[0];
            for (int c = 0; c < MAX_CLASS_CELLS; c++) {
                double off = (c - CELL_CENTER) * spacing;
                if (c < order.length) {
                    final String className = order[c];
                    spawnCell(off, new ItemStack(iconOf(className)), labelMini(className),
                            "class|" + className);
                }
            }
            spawnCell((4 - CELL_CENTER) * spacing, new ItemStack(Material.BARRIER),
                    "<red><bold>RESET CLASS</bold></red>", "action|resetask");
            spawnCell((5 - CELL_CENTER) * spacing, new ItemStack(Material.KNOWLEDGE_BOOK),
                    "<yellow><bold>ABILITY KEYS</bold></yellow>", "action|keys");
        } catch (Throwable t) {
            plugin.getLogger().fine("cells spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnCell(double off, ItemStack stack, String labelMini, String value) {
        spawnItem(point(off, GRID_Y), stack);
        spawnText(point(off, GRID_Y - LABEL_DY), labelMini, LABEL_SCALE);
        spawnHitbox(point(off, GRID_Y), HITBOX_WIDTH, HITBOX_HEIGHT, "cell", value);
    }

    private Material iconOf(String className) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String raw = cls != null ? cls.getString("icon", "") : "";
        try {
            return Material.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
        } catch (Throwable t) {
            return Material.SHIELD;
        }
    }

    private String labelMini(String className) {
        ConfigurationSection cls = plugin.getConfig().getConfigurationSection("classes." + className);
        String display = cls != null ? cls.getString("display-name", "") : "";
        if (display != null && !display.isEmpty()) {
            try {
                String plain = PlainTextComponentSerializer.plainText()
                        .serialize(UiKit.deserialized(display));
                if (plain.length() <= 14) {
                    return display;
                }
            } catch (Throwable ignored) {
                return display;
            }
        }
        String upper = className == null ? "" : className.toUpperCase(Locale.ROOT);
        if (upper.length() > 14) {
            upper = upper.substring(0, 14) + "\u2026";
        }
        String tag = COLOR_MINI.getOrDefault(
                cls == null || cls.getString("color", "") == null
                        ? ""
                        : cls.getString("color", "").toLowerCase(Locale.ROOT),
                "white");
        return "<" + tag + ">" + upper + "</" + tag + ">";
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
            if ("cell".equals(kind)) {
                dispatch(player, value);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("class panel click failed: " + t.getClass().getSimpleName());
        }
    }

    private void dispatch(Player player, String value) {
        if (value == null) return;
        int sep = value.indexOf('|');
        if (sep < 0) return;
        String type = value.substring(0, sep);
        String arg = value.substring(sep + 1);
        ClassGUI gui = plugin.getClassGUI();
        if ("class".equals(type)) {
            if (gui == null || !isKnown(gui, arg)) return;
            playClick(player);
            try {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    try {
                        DialogUI.openClass(plugin, plugin.getClassGUI(), player, arg,
                                () -> {
                                    var g = plugin.getClassGUI();
                                    if (g != null) g.openClassMenu(player, arg);
                                });
                    } catch (Throwable t) {
                        plugin.getLogger().fine("panel class dialog failed: " + t.getClass().getSimpleName());
                    }
                }, 1L);
            } catch (Throwable t) {
                plugin.getLogger().fine("panel schedule failed: " + t.getClass().getSimpleName());
            }
            return;
        }
        if ("action".equals(type)) {
            if ("resetask".equals(arg)) {
                if (gui == null) return;
                playClick(player);
                DialogUI.openResetConfirm(plugin, gui, player, () -> gui.openMainMenu(player));
            } else if ("keys".equals(arg)) {
                playClick(player);
                openKeys(player);
            }
        }
    }

    private void openKeys(Player player) {
        if (!DialogUI.supported()) {
            ClassGUI g = plugin.getClassGUI();
            if (g != null) g.openMainMenu(player);
            return;
        }
        try {
            FloatingBanner.show(plugin, player, UiKit.title("ABILITY KEYS"), 60L);
            DialogUI.show(plugin, player, DialogUI.multiAction("ABILITY KEYS", "gold",
                    "F = Prime\nSneak+F = Tactical\nSneak+Q = Ultimate",
                    DialogUI.button("OK", "yellow", null, "classui noop"), 1, null));
        } catch (Throwable t) {
            plugin.getLogger().fine("keys dialog failed: " + t.getClass().getSimpleName());
        }
    }

    private boolean isKnown(ClassGUI gui, String className) {
        if (className == null || className.isEmpty()) return false;
        for (String c : gui.classOrder()) {
            if (c.equals(className)) return true;
        }
        return false;
    }

    private void playClick(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.4F);
        } catch (Throwable ignored) {
        }
    }
}
