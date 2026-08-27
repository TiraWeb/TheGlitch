package com.theglitch.glitchstash.ui;

import com.theglitch.glitchstash.GlitchStash;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StashPanel implements Listener {

    private static final NamespacedKey PANEL_KEY = new NamespacedKey("glitchstash", "panel");
    private static final NamespacedKey VALUE_KEY = new NamespacedKey("glitchstash", "value");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final double HEADER_Y = 4.3D;
    private static final float HEADER_SCALE = 1.1F;
    private static final float ITEM_SCALE = 0.85F;
    private static final float LABEL_SCALE = 0.5F;
    private static final long BUILD_DELAY_TICKS = 100L;

    private static GlitchStash plugin;
    private static StashPanel instance;
    private static BukkitTask buildTask;

    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Set<UUID> trackedEntities = new HashSet<>();

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;

    private StashPanel() {
    }

    public static void init(GlitchStash pl) {
        plugin = pl;
        if (instance != null) {
            return;
        }
        if (!enabled(pl)) {
            return;
        }
        arm(pl);
    }

    public static void shutdown(GlitchStash pl) {
        disarm();
        plugin = null;
    }

    public static void reconfigureAndRebuild() {
        GlitchStash pl = plugin;
        if (pl == null || !pl.isEnabled()) {
            return;
        }
        if (!enabled(pl)) {
            disarm();
            return;
        }
        if (instance == null) {
            arm(pl);
            return;
        }
        if (instance.loadConfig()) {
            instance.build();
        }
    }

    private static boolean enabled(GlitchStash pl) {
        try {
            return pl.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
        } catch (Throwable t) {
            return true;
        }
    }

    private static synchronized void arm(GlitchStash pl) {
        if (instance != null) {
            return;
        }
        StashPanel panel = new StashPanel();
        if (!panel.loadConfig()) {
            return;
        }
        instance = panel;
        try {
            pl.getServer().getPluginManager().registerEvents(panel, pl);
        } catch (Throwable t) {
            pl.getLogger().warning("StashPanel listener registration failed: " + t.getMessage());
            instance = null;
            return;
        }
        panel.buildTask = pl.getServer().getScheduler().runTaskLater(pl, panel::build, BUILD_DELAY_TICKS);
        pl.getLogger().info("Stash kiosk wall panel armed at "
                + (panel.world == null ? "?" : panel.world.getName()) + ".");
    }

    private static synchronized void disarm() {
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

    private boolean loadConfig() {
        try {
            String name = plugin.getConfig().getString("modern-ui.world-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                plugin.getLogger().warning("world-panel world '" + name + "' not found — stash kiosk dormant.");
                return false;
            }
            world = w;
            wx = plugin.getConfig().getDouble("modern-ui.world-panel.x", 67.5D);
            wy = plugin.getConfig().getDouble("modern-ui.world-panel.y", -43.5D);
            wz = plugin.getConfig().getDouble("modern-ui.world-panel.z", -5.5D);
            String f = plugin.getConfig().getString("modern-ui.world-panel.facing", "west");
            facing = f == null ? "west" : f.toLowerCase();
            spacing = plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.35D);
            if (spacing < 0.5D) {
                spacing = 0.5D;
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("world-panel config invalid — stash kiosk dormant.");
            return false;
        }
    }

    private boolean isLive() {
        return instance == this && plugin != null && plugin.isEnabled() && world != null;
    }

    private void build() {
        try {
            if (!isLive()) {
                plugin.getLogger().info("StashPanel build skipped: not live.");
                return;
            }
            purgeStale();
            removeAll();
            forceLoadPanelChunks();
            spawnHeader();
            double half = 0.75D * spacing;
            spawnBigCell(Material.CHEST, -half, "<white>OPEN STASH</white>\n<aqua>Chest menu</aqua>",
                    "action", "chest");
            spawnBigCell(Material.ENDER_EYE, half, "<white>STASH REMOTE</white>\n<gold>Rank perk</gold>",
                    "action", "dialog");
            spawnDecoBarrel();
            plugin.getLogger().info("StashPanel built: " + trackedEntities.size() + " entities at "
                    + world.getName() + " " + wx + "," + wy + "," + wz + " facing " + facing);
        } catch (Throwable t) {
            plugin.getLogger().info("StashPanel build failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
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
            plugin.getLogger().info("StashPanel force-load skipped: " + t.getClass().getSimpleName());
        }
    }

    private void purgeStale() {
        try {
            purgeStaleClass(ItemDisplay.class);
            purgeStaleClass(TextDisplay.class);
            purgeStaleClass(Interaction.class);
        } catch (Throwable t) {
            plugin.getLogger().fine("stash panel purge skipped: " + t.getClass().getSimpleName());
        }
    }

    private <T extends Entity> void purgeStaleClass(Class<T> type) {
        List<T> stale = new ArrayList<>();
        for (T entity : world.getEntitiesByClass(type)) {
            try {
                if (entity.getPersistentDataContainer().has(PANEL_KEY, PersistentDataType.STRING)) {
                    stale.add(entity);
                }
            } catch (Throwable ignored) {
            }
        }
        for (T entity : stale) {
            try {
                entity.remove();
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
        if (entity == null) {
            return;
        }
        try {
            trackedEntities.add(entity.getUniqueId());
        } catch (Throwable ignored) {
        }
    }

    private Location point(double colOff, double rowY) {
        double ax = 0.0D;
        double az = 0.0D;
        if ("east".equals(facing)) {
            az = colOff;
        } else if ("west".equals(facing)) {
            az = -colOff;
        } else {
            ax = colOff;
        }
        return new Location(world, wx + ax, wy + rowY, wz + az);
    }

    private float wallYaw() {
        switch (facing) {
            case "south": return 0.0F;
            case "west": return 90.0F;
            case "north": return 180.0F;
            case "east": return -90.0F;
            default: return 0.0F;
        }
    }

    private void spawnHeader() {
        try {
            Location loc = point(0.0D, HEADER_Y);
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(MM.deserialize("\uE049 <gradient:#C084FC:#F0ABFC><bold>STASH KIOSK</bold></gradient>"));
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
            tag(d, "decor", "header");
            track(d);
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

    private TextDisplay spawnLabel(Location loc, String mini, float scale) {
        try {
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(MM.deserialize(mini));
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
            tag(d, "decor", "label");
            track(d);
            return d;
        } catch (Throwable t) {
            plugin.getLogger().fine("label spawn failed: " + t.getClass().getSimpleName());
            return null;
        }
    }

    private void spawnBigCell(Material material, double colOff, String labelText, String kind, String value) {
        double rowY = 1.5D;
        spawnItem(point(colOff, rowY), new ItemStack(material));
        spawnLabel(point(colOff, rowY - 0.42D), labelText, LABEL_SCALE);
        spawnHitbox(point(colOff, rowY), 0.85F, 1.0F, kind, value);
    }

    private void spawnDecoBarrel() {
        double colOff = 0.0D;
        double rowY = 0.45D;
        spawnItem(point(colOff, rowY), new ItemStack(Material.BARREL));
        spawnLabel(point(colOff, rowY - 0.42D), "<gray>Extract to fill\nyour stash</gray>", LABEL_SCALE);
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
                    plugin.getLogger().fine("icon styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            tag(d, "decor", "icon");
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("icon spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private Interaction spawnHitbox(Location loc, float width, float height, String kind, String value) {
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

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        try {
            if (!(event.getRightClicked() instanceof Interaction hit)) {
                return;
            }
            String kind;
            String value;
            try {
                kind = hit.getPersistentDataContainer().get(PANEL_KEY, PersistentDataType.STRING);
                value = hit.getPersistentDataContainer().get(VALUE_KEY, PersistentDataType.STRING);
            } catch (Throwable t) {
                return;
            }
            if (!"action".equals(kind)) {
                return;
            }
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Player player = event.getPlayer();
            long now = System.currentTimeMillis();
            Long prior = lastClick.get(player.getUniqueId());
            if (prior != null && now - prior < 400L) {
                return;
            }
            if (lastClick.size() > 512) {
                lastClick.clear();
            }
            lastClick.put(player.getUniqueId(), now);
            if ("chest".equals(value)) {
                enqueue(player, () -> Bukkit.dispatchCommand(player, "stash"));
            } else if ("dialog".equals(value)) {
                enqueue(player, () -> {
                    if (DialogUI.canRemote(player)) {
                        DialogUI.openStash(plugin, player, () -> Bukkit.dispatchCommand(player, "stash"));
                    } else {
                        player.sendMessage(MM.deserialize(
                                "<gray>Remote stash is a rank perk — use the chest menu.</gray>"));
                    }
                });
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("stash panel click failed: " + t.getClass().getSimpleName());
        }
    }

    private void enqueue(Player player, Runnable action) {
        try {
            if (player.isOnline()) {
                player.getScheduler().run(plugin, s -> action.run(), null);
            }
        } catch (Throwable fallbackErr) {
            try {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        action.run();
                    }
                }, 1L);
            } catch (Throwable t) {
                plugin.getLogger().fine("stash panel action failed: " + t.getClass().getSimpleName());
            }
        }
    }
}
