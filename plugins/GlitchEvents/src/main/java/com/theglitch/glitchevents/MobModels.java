package com.theglitch.glitchevents;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rides pack-model ItemDisplays on top of vanilla/MythicMobs mobs.
 *
 * <p>MythicMobs without ModelEngine cannot wear skeletal Blockbench models, so
 * we hide the base mob (invisibility) and mount an ItemDisplay holding a
 * resource-pack item model (e.g. {@code myrlin:asset_...} on paper). Matching
 * is by entity type + custom-name substring, so it works no matter what spawns
 * the mob (MythicMobs spawners, random spawns, admin commands).</p>
 *
 * <p>Displays are tracked mob-UUID -&gt; display-UUID and swept every few seconds
 * (plus immediate cleanup on death and a full sweep on disable), so a dead or
 * despawned mob can never leave a floating model behind.</p>
 */
public final class MobModels {

    /** Scoreboard tag marking our model displays (also used by the sweeps). */
    public static final String MODEL_TAG = "glitch_mob_model";

    private record Entry(String matchName, EntityType type, Material material, Key model,
                         float scale, float yOffset, boolean hideBase) {
    }

    private final GlitchEvents plugin;

    private volatile boolean enabled = true;
    private volatile List<Entry> entries = List.of();
    private volatile long sweepPeriodTicks = 100L;

    /** mob UUID -> display UUID for every live attachment. */
    private final Map<UUID, UUID> attached = new ConcurrentHashMap<>();
    private volatile BukkitTask sweepTask;

    public MobModels(GlitchEvents plugin) {
        this.plugin = plugin;
        reload();
        startSweep();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Re-reads the mob-models config section (safe to call on /glitchevents reload). */
    public void reload() {
        boolean on = plugin.getConfig().getBoolean("mob-models.enabled", true);
        long period = plugin.getConfig().getLong("mob-models.sweep-period-ticks", 100L);
        List<Entry> parsed = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("mob-models.models");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                String match = s.getString("match-name", "").trim();
                String typeName = s.getString("entity-type", "").trim().toUpperCase(java.util.Locale.ROOT);
                String modelId = s.getString("item-model", "").trim();
                if (match.isEmpty() || typeName.isEmpty() || modelId.isEmpty()) {
                    plugin.getLogger().warning("[MobModels] Skipping entry '" + key + "' — match-name/entity-type/item-model are all required.");
                    continue;
                }
                EntityType type;
                try {
                    type = EntityType.valueOf(typeName);
                } catch (IllegalArgumentException bad) {
                    plugin.getLogger().warning("[MobModels] Skipping entry '" + key + "' — unknown entity-type '" + typeName + "'.");
                    continue;
                }
                Material material = Material.matchMaterial(s.getString("item-material", "PAPER"));
                if (material == null) {
                    plugin.getLogger().warning("[MobModels] Entry '" + key + "' — unknown item-material, defaulting to PAPER.");
                    material = Material.PAPER;
                }
                Key model;
                try {
                    model = Key.key(modelId);
                } catch (Exception bad) {
                    plugin.getLogger().warning("[MobModels] Skipping entry '" + key + "' — invalid item-model key '" + modelId + "'.");
                    continue;
                }
                float scale = (float) s.getDouble("scale", 2.3);
                float yOffset = (float) s.getDouble("y-offset", -0.8);
                boolean hide = s.getBoolean("hide-base", true);
                parsed.add(new Entry(match.toLowerCase(java.util.Locale.ROOT), type, material, model, scale, yOffset, hide));
            }
        }
        this.entries = List.copyOf(parsed);
        this.enabled = on;
        this.sweepPeriodTicks = Math.max(20L, period);
        plugin.getLogger().info("[MobModels] Config reloaded — enabled=" + on + ", entries=" + parsed.size());
        restartSweep();
    }

    /**
     * Attempts to attach a pack model to the mob. Safe to call repeatedly —
     * already-attached mobs are skipped.
     */
    public void tryAttach(LivingEntity mob) {
        if (!enabled || mob == null || !mob.isValid() || mob.isDead()) return;
        if (attached.containsKey(mob.getUniqueId())) return;
        Entry entry = match(mob);
        if (entry == null) return;

        if (entry.hideBase()) {
            try {
                mob.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                        PotionEffect.INFINITE_DURATION, 0, false, false, false));
            } catch (Exception e) {
                plugin.getLogger().warning("[MobModels] Could not hide base mob " + mob.getUniqueId() + ": " + e.getMessage());
            }
        }

        ItemStack stack;
        try {
            stack = ItemStack.of(entry.material());
            //noinspection UnstableApiUsage
            stack.setData(DataComponentTypes.ITEM_MODEL, entry.model());
        } catch (Exception e) {
            plugin.getLogger().warning("[MobModels] Could not build model item for " + mob.getName() + ": " + e.getMessage());
            return;
        }

        Location at = mob.getLocation().clone();
        ItemDisplay display;
        try {
            display = at.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(stack);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(new Transformation(
                        new Vector3f(0f, entry.yOffset(), 0f),
                        new Quaternionf(),
                        new Vector3f(entry.scale(), entry.scale(), entry.scale()),
                        new Quaternionf()));
                d.setPersistent(false);
                d.setViewRange(64.0f);
                d.addScoreboardTag(MODEL_TAG);
                try {
                    d.getPersistentDataContainer().set(
                            new org.bukkit.NamespacedKey(plugin, "mob_owner"),
                            PersistentDataType.STRING, mob.getUniqueId().toString());
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("[MobModels] Could not spawn model display for " + mob.getName() + ": " + e.getMessage());
            return;
        }

        boolean mounted;
        try {
            mounted = mob.addPassenger(display);
        } catch (Exception e) {
            mounted = false;
        }
        if (!mounted) {
            try {
                display.remove();
            } catch (Exception ignored) {
            }
            plugin.getLogger().warning("[MobModels] Could not mount model display on " + mob.getName() + " — display removed.");
            return;
        }

        attached.put(mob.getUniqueId(), display.getUniqueId());
        plugin.getLogger().info("[MobModels] Attached model '" + entry.model().asString()
                + "' to " + mob.getName() + " (" + mob.getType() + ")");
    }

    /** Removes the model display riding the given mob, if any. */
    public void detach(Entity mob) {
        if (mob == null) return;
        UUID displayId = attached.remove(mob.getUniqueId());
        if (displayId == null) return;
        for (World world : Bukkit.getWorlds()) {
            Entity display;
            try {
                display = Bukkit.getEntity(displayId);
            } catch (Exception e) {
                continue;
            }
            if (display == null || !display.isValid()) continue;
            if (!world.equals(display.getWorld())) continue;
            try {
                display.remove();
            } catch (Exception ignored) {
            }
        }
    }

    /** Removes every tracked model display (called on disable). */
    public void removeAll() {
        for (UUID displayId : attached.values()) {
            try {
                Entity display = Bukkit.getEntity(displayId);
                if (display != null) display.remove();
            } catch (Exception ignored) {
            }
        }
        attached.clear();
        // Belt-and-braces: sweep every world for stragglers carrying our tag.
        for (World world : Bukkit.getWorlds()) {
            List<Entity> tagged;
            try {
                tagged = new ArrayList<>(world.getEntitiesByClass(ItemDisplay.class));
            } catch (Exception e) {
                continue;
            }
            for (Entity e : tagged) {
                try {
                    if (e.getScoreboardTags().contains(MODEL_TAG)) e.remove();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Entry match(LivingEntity mob) {
        String name = mob.getCustomName();
        if (name == null) {
            // MythicMobs Display may arrive as a plain String custom name.
            return null;
        }
        String plain;
        try {
            plain = ChatColor.stripColor(name);
        } catch (Exception e) {
            plain = name;
        }
        if (plain == null) return null;
        String lower = plain.toLowerCase(java.util.Locale.ROOT);
        for (Entry entry : entries) {
            if (mob.getType() == entry.type() && lower.contains(entry.matchName())) {
                return entry;
            }
        }
        return null;
    }

    private void startSweep() {
        restartSweep();
    }

    private void restartSweep() {
        BukkitTask old = sweepTask;
        if (old != null) {
            try {
                old.cancel();
            } catch (Exception ignored) {
            }
        }
        try {
            sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, sweepPeriodTicks, sweepPeriodTicks);
        } catch (Exception e) {
            plugin.getLogger().warning("[MobModels] Could not schedule orphan sweep: " + e.getMessage());
        }
    }

    /** Drops dead/invalid pairs, removes orphans, and picks up unattached matches. */
    private void sweep() {
        if (attached.isEmpty() && !enabled) return;
        for (Map.Entry<UUID, UUID> pair : new ArrayList<>(attached.entrySet())) {
            Entity mob = null;
            try {
                mob = Bukkit.getEntity(pair.getKey());
            } catch (Exception ignored) {
            }
            if (mob != null && mob.isValid()) continue;
            attached.remove(pair.getKey());
            try {
                Entity display = Bukkit.getEntity(pair.getValue());
                if (display != null) display.remove();
            } catch (Exception ignored) {
            }
        }
        if (enabled) {
            scanForCandidates();
        }
    }

    /**
     * Retro-scan: attaches models to matching mobs the spawn listener missed
     * (name applied after the event, plugin reload with mobs already out,
     * spawners that bypass Bukkit events). Cheap — only entity types we match on.
     */
    private void scanForCandidates() {
        if (entries.isEmpty()) return;
        java.util.EnumSet<EntityType> types = java.util.EnumSet.noneOf(EntityType.class);
        for (Entry entry : entries) {
            types.add(entry.type());
        }
        for (World world : Bukkit.getWorlds()) {
            for (EntityType type : types) {
                List<Entity> found;
                try {
                    found = new ArrayList<>(world.getEntitiesByClass(
                            (Class<? extends Entity>) type.getEntityClass()));
                } catch (Exception e) {
                    continue;
                }
                for (Entity entity : found) {
                    if (!(entity instanceof LivingEntity mob)) continue;
                    if (!mob.isValid() || mob.isDead()) continue;
                    if (attached.containsKey(mob.getUniqueId())) continue;
                    try {
                        tryAttach(mob);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MobModels] scan attach failed: " + e.getMessage());
                    }
                }
            }
        }
    }
}
