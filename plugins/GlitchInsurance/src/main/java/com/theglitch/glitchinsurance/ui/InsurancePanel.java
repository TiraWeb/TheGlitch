package com.theglitch.glitchinsurance.ui;

import com.theglitch.glitchinsurance.GlitchInsurance;
import com.theglitch.glitchinsurance.InsuranceManager;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InsurancePanel implements Listener {

    private static final NamespacedKey PANEL_KEY = new NamespacedKey("glitchinsurance", "panel");
    private static final NamespacedKey VALUE_KEY = new NamespacedKey("glitchinsurance", "value");
    private static final String HEADER = "\uE049 <gradient:#C084FC:#F0ABFC><bold>INSURANCE OFFICE</bold></gradient>";
    private static final String SUBROW = "<gray>pay shards \u00b7 protect your gear</gray>";
    private static final float[] ROW_Y = {2.55F, 1.5F, 0.45F};
    private static final float ITEM_SCALE = 0.85F;
    private static final int TRUNC = 14;

    private static GlitchInsurance plugin;
    private static InsurancePanel instance;
    private static BukkitTask buildTask;

    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();

    private World world;
    private double wx;
    private double wy;
    private double wz;
    private String facing;
    private double spacing;
    private boolean live;

    private InsurancePanel() {
    }

    public static void init(GlitchInsurance pl) {
        plugin = pl;
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
        arm(pl);
    }

    private static void arm(GlitchInsurance pl) {
        if (instance != null) {
            return;
        }
        InsurancePanel panel = new InsurancePanel();
        if (!panel.loadConfig(pl)) {
            return;
        }
        instance = panel;
        try {
            pl.getServer().getPluginManager().registerEvents(panel, pl);
        } catch (Throwable t) {
            pl.getLogger().warning("InsurancePanel listener registration failed: " + t.getMessage());
            instance = null;
            return;
        }
        try {
            buildTask = pl.getServer().getScheduler().runTaskLater(pl, panel::build, 100L);
        } catch (Throwable ignored) {
        }
        pl.getLogger().info("Insurance wall panel armed at " + panel.world.getName() + ".");
    }

    public static synchronized boolean placeHere(Player player) {
        try {
            if (plugin == null || player == null) {
                return false;
            }
            Location loc = player.getLocation();
            World w = loc.getWorld();
            String face = facingFromYaw(loc.getYaw());
            if (w == null || face == null) {
                return false;
            }
            plugin.getConfig().set("modern-ui.world-panel.world", w.getName());
            plugin.getConfig().set("modern-ui.world-panel.x", loc.getBlockX() + 0.5D);
            plugin.getConfig().set("modern-ui.world-panel.y", loc.getBlockY() + 1.0D);
            plugin.getConfig().set("modern-ui.world-panel.z", loc.getBlockZ() + 0.5D);
            plugin.getConfig().set("modern-ui.world-panel.facing", face);
            plugin.getConfig().set("modern-ui.world-panel.enabled", true);
            plugin.saveConfig();
            reconfigureAndRebuild();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized boolean undo() {
        try {
            cancelBuildTask();
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
            setEnabledConfig(false);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized boolean showAt() {
        try {
            if (plugin == null) {
                return false;
            }
            setEnabledConfig(true);
            reconfigureAndRebuild();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static synchronized void reconfigureAndRebuild() {
        try {
            if (plugin == null) {
                return;
            }
            cancelBuildTask();
            if (instance == null) {
                arm(plugin);
                return;
            }
            if (!instance.loadConfig(plugin)) {
                instance.removeAll();
                return;
            }
            instance.build();
        } catch (Throwable ignored) {
        }
    }

    private static void cancelBuildTask() {
        if (buildTask != null) {
            try {
                buildTask.cancel();
            } catch (Throwable ignored) {
            }
            buildTask = null;
        }
    }

    private static void setEnabledConfig(boolean value) {
        try {
            if (plugin == null) {
                return;
            }
            plugin.getConfig().set("modern-ui.world-panel.enabled", value);
            plugin.saveConfig();
        } catch (Throwable ignored) {
        }
    }

    public static void rebuild() {
        try {
            if (plugin == null) {
                return;
            }
            if (instance == null) {
                boolean enabled = plugin.getConfig().getBoolean("modern-ui.world-panel.enabled", true);
                if (enabled) {
                    arm(plugin);
                }
                return;
            }
            instance.build();
        } catch (Throwable ignored) {
        }
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

    private boolean loadConfig(GlitchInsurance pl) {
        try {
            String name = pl.getConfig().getString("modern-ui.world-panel.world", "hub");
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w == null) {
                pl.getLogger().warning("world-panel world '" + name + "' not found — InsurancePanel dormant.");
                return false;
            }
            world = w;
            wx = pl.getConfig().getDouble("modern-ui.world-panel.x", 106.0D);
            wy = pl.getConfig().getDouble("modern-ui.world-panel.y", -33.5D);
            wz = pl.getConfig().getDouble("modern-ui.world-panel.z", 62.5D);
            String f = pl.getConfig().getString("modern-ui.world-panel.facing", "west");
            facing = f == null ? "west" : f.toLowerCase(java.util.Locale.ROOT);
            spacing = Math.max(0.5D, configSpacing());
            live = true;
            return true;
        } catch (Throwable t) {
            pl.getLogger().warning("world-panel config invalid — InsurancePanel dormant.");
            return false;
        }
    }

    private static double configSpacing() {
        try {
            return plugin.getConfig().getDouble("modern-ui.world-panel.spacing", 1.35D);
        } catch (Throwable t) {
            return 1.35D;
        }
    }

    private boolean isLive() {
        return plugin != null && plugin.isEnabled() && live && world != null;
    }

    private void build() {
        try {
            if (!isLive()) {
                plugin.getLogger().fine("InsurancePanel build skipped: not live.");
                return;
            }
            purgeStale();
            removeAll();
            forceLoadChunks();
            spawnHeader();
            spawnCells();
            plugin.getLogger().info("InsurancePanel built: " + trackedEntities.size() + " entities at "
                    + world.getName() + " " + wx + "," + wy + "," + wz + " facing " + facing);
        } catch (Throwable t) {
            plugin.getLogger().info("InsurancePanel build failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void forceLoadChunks() {
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
            plugin.getLogger().fine("InsurancePanel force-load skipped: " + t.getClass().getSimpleName());
        }
    }

    private void purgeStale() {
        try {
            purgeClass(ItemDisplay.class);
            purgeClass(TextDisplay.class);
            purgeClass(Interaction.class);
        } catch (Throwable t) {
            plugin.getLogger().fine("InsurancePanel purge skipped: " + t.getClass().getSimpleName());
        }
    }

    private <T extends Entity> void purgeClass(Class<T> type) {
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
        if (entity == null) {
            return;
        }
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

    private static String facingFromYaw(float yaw) {
        float norm = yaw % 360.0F;
        if (norm >= 180.0F) norm -= 360.0F;
        if (norm < -180.0F) norm += 360.0F;
        if (norm >= -45.0F && norm < 45.0F) return "south";
        if (norm >= 45.0F && norm < 135.0F) return "west";
        if (norm >= -135.0F && norm < -45.0F) return "east";
        return "north";
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

    private void spawnHeader() {
        try {
            TextDisplay d = world.spawn(point(0.0D, 4.3D), TextDisplay.class, t -> {
                try {
                    t.text(GlitchInsurance.mm().deserialize(HEADER));
                    styleShared(t);
                    t.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(1.1F, 1.1F, 1.1F),
                            new Quaternionf()));
                } catch (Throwable err) {
                    plugin.getLogger().fine("header styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("header spawn failed: " + t.getClass().getSimpleName());
        }
        try {
            spawnSubRow();
        } catch (Throwable ignored) {
        }
    }

    private void spawnSubRow() {
        try {
            TextDisplay d = world.spawn(point(0.0D, 3.3D), TextDisplay.class, t -> {
                try {
                    t.text(GlitchInsurance.mm().deserialize(SUBROW));
                    styleShared(t);
                    t.setTransformation(new Transformation(
                            new Vector3f(0.0F, 0.0F, 0.0F),
                            new Quaternionf(),
                            new Vector3f(0.75F, 0.75F, 0.75F),
                            new Quaternionf()));
                } catch (Throwable err) {
                    plugin.getLogger().fine("subrow styling incomplete: " + err.getClass().getSimpleName());
                }
            });
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("subrow spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnText(Location loc, String mini, float scale) {
        try {
            TextDisplay d = world.spawn(loc, TextDisplay.class, t -> {
                try {
                    t.text(GlitchInsurance.mm().deserialize(mini));
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
            track(d);
        } catch (Throwable t) {
            plugin.getLogger().fine("text spawn failed: " + t.getClass().getSimpleName());
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

    private void spawnHitbox(Location loc, float width, float height, String value) {
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
            try {
                PersistentDataContainer pdc = hit.getPersistentDataContainer();
                pdc.set(PANEL_KEY, PersistentDataType.STRING, "action");
                pdc.set(VALUE_KEY, PersistentDataType.STRING, value);
            } catch (Throwable ignored) {
            }
            track(hit);
        } catch (Throwable t) {
            plugin.getLogger().fine("hitbox spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private void spawnCells() {
        try {
            float rowY = ROW_Y[1];
            int premium = configInt("premium", 100);
            int window = configInt("window", 300);
            for (int c = 0; c < 3; c++) {
                double off = (c - 1) * spacing;
                Material mat = switch (c) {
                    case 0 -> Material.ANVIL;
                    case 1 -> Material.PAPER;
                    default -> Material.GOLDEN_CHESTPLATE;
                };
                String value = switch (c) {
                    case 0 -> "action|buy";
                    case 1 -> "action|list";
                    default -> "action|claims";
                };
                String line1 = switch (c) {
                    case 0 -> "INSURE HELD";
                    case 1 -> "MY POLICIES";
                    default -> "CLAIM WINDOW";
                };
                String line2 = switch (c) {
                    case 0 -> "<aqua>" + trunc(premium + " Shards") + "</aqua>";
                    case 1 -> "<gray>" + trunc("list + claim") + "</gray>";
                    default -> "<gray>" + trunc(window + "s after death") + "</gray>";
                };
                spawnItem(point(off, rowY), new ItemStack(mat));
                spawnText(point(off, rowY - 0.42D), trunc(line1) + "\n" + line2, 0.5F);
                spawnHitbox(point(off, rowY), 0.85F, 1.0F, value);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("cells spawn failed: " + t.getClass().getSimpleName());
        }
    }

    private int configInt(String kind, int def) {
        try {
            InsuranceManager m = plugin.getManager();
            if (m == null) return def;
            if ("premium".equals(kind)) return m.getPremiumPerItem();
            if ("window".equals(kind)) return m.getClaimWindowSeconds();
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static String trunc(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= TRUNC) {
            return s;
        }
        return s.substring(0, TRUNC) + "\u2026";
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
            if (kind == null) {
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
            if ("action".equals(kind)) {
                handleAction(player, value);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("panel click failed: " + t.getClass().getSimpleName());
        }
    }

    private void handleAction(Player player, String value) {
        if (value == null) {
            return;
        }
        String act = value.startsWith("action|") ? value.substring("action|".length()) : value;
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.4F);
        } catch (Throwable ignored) {
        }
        switch (act) {
            case "buy" -> enqueue(() -> {
                try {
                    Bukkit.dispatchCommand(player, "insurance buy");
                } catch (Throwable ignored) {
                }
            });
            case "list", "claims" -> enqueue(() -> DialogUI.openRoot(plugin, player, () -> {
                try {
                    player.performCommand("insurance list");
                } catch (Throwable ignored) {
                }
            }));
            default -> {
            }
        }
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
}
