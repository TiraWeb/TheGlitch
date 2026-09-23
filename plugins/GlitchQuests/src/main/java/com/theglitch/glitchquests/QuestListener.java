package com.theglitch.glitchquests;

import com.theglitch.glitchquests.QuestDef.QuestType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.block.Container;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.Method;

/** Feeds game events into quest progress. */
public final class QuestListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchQuests plugin;
    private final QuestManager quests;

    // MythicMobs is optional — resolved lazily via reflection
    private Object mythicMobManager;
    private Method isMythicMob;
    private boolean mythicResolved;

    public QuestListener(GlitchQuests plugin, QuestManager quests) {
        this.plugin = plugin;
        this.quests = quests;
    }

    /** Hooks VelKoth's KothWinEvent (= successful extraction) without a compile dependency. */
    @SuppressWarnings("unchecked")
    public void hookExtraction() {
        try {
            Class<? extends Event> cls = (Class<? extends Event>) Class.forName("dev.velmax.velkoth.api.event.KothWinEvent");
            Method getWinner = cls.getMethod("getWinner");
            Bukkit.getPluginManager().registerEvent(cls, this, EventPriority.MONITOR, (l, e) -> {
                if (!cls.isInstance(e)) return;
                try {
                    if (getWinner.invoke(e) instanceof Player p) quests.progress(p, QuestType.EXTRACT, 1);
                } catch (ReflectiveOperationException ignored) {
                }
            }, plugin, true);
            plugin.getLogger().info("Hooked VelKoth extractions for EXTRACT quests");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            plugin.getLogger().warning("VelKoth not found — EXTRACT quests will never progress");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = quests.data(p);
        d.lastTravelCm = travelCm(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (quests.canClaimStreak(d) || quests.canClaimWeekly(d) || quests.canClaimMonthly(d)) {
                p.sendMessage(MM.deserialize("<dark_purple>✦</dark_purple> <gray>You have rewards waiting! "
                        + "<click:run_command:/rewards><hover:show_text:'<gray>Open rewards'>"
                        + "<gold><u>Claim them in /rewards</u></gold></hover></click></gray>"));
            }
        }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        quests.unload(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || killer.equals(dead)) return;
        if (dead instanceof Player) {
            quests.progress(killer, QuestType.KILL_PLAYER, 1);
            return;
        }
        boolean mythic = isMythic(dead);
        if (dead instanceof Enemy || mythic) quests.progress(killer, QuestType.KILL_MOB, 1);
        if (mythic) quests.progress(killer, QuestType.KILL_GLITCH_MOB, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        quests.progress(p, QuestType.MINE_BLOCK, 1);
        Material m = e.getBlock().getType();
        if (m.name().endsWith("_ORE") || m == Material.ANCIENT_DEBRIS) quests.progress(p, QuestType.MINE_ORE, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder(false) instanceof Container c)) return;
        Location l = c.getLocation();
        String key = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
        if (quests.data(p).lootedToday.add(key)) quests.progress(p, QuestType.LOOT_CONTAINER, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) quests.progress(e.getPlayer(), QuestType.FISH, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        if (e.getItem().getType().isEdible()) quests.progress(e.getPlayer(), QuestType.EAT, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int amount = e.getRecipe().getResult().getAmount();
        if (e.isShiftClick()) {
            // shift-craft makes as many as the smallest ingredient stack allows
            int min = Integer.MAX_VALUE;
            for (var item : e.getInventory().getMatrix()) {
                if (item != null && !item.getType().isAir()) min = Math.min(min, item.getAmount());
            }
            if (min != Integer.MAX_VALUE) amount *= min;
        }
        quests.progress(p, QuestType.CRAFT, amount);
    }

    /** Called every minute: PLAYTIME +1 and TRAVEL from the movement statistics delta. */
    public void tickMinute() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            quests.progress(p, QuestType.PLAYTIME, 1);
            PlayerData d = quests.data(p);
            long cm = travelCm(p);
            if (d.lastTravelCm >= 0 && cm > d.lastTravelCm) {
                quests.progress(p, QuestType.TRAVEL, (int) Math.min(Integer.MAX_VALUE, (cm - d.lastTravelCm) / 100));
            }
            d.lastTravelCm = cm;
        }
    }

    private static long travelCm(Player p) {
        return (long) p.getStatistic(Statistic.WALK_ONE_CM) + p.getStatistic(Statistic.SPRINT_ONE_CM)
                + p.getStatistic(Statistic.SWIM_ONE_CM) + p.getStatistic(Statistic.HORSE_ONE_CM)
                + p.getStatistic(Statistic.AVIATE_ONE_CM) + p.getStatistic(Statistic.BOAT_ONE_CM);
    }

    private boolean isMythic(Entity entity) {
        if (!mythicResolved) {
            mythicResolved = true;
            try {
                Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                Object inst = mb.getMethod("inst").invoke(null);
                mythicMobManager = inst.getClass().getMethod("getMobManager").invoke(inst);
                isMythicMob = mythicMobManager.getClass().getMethod("isMythicMob", Entity.class);
            } catch (Throwable t) {
                plugin.getLogger().warning("MythicMobs API unavailable — KILL_GLITCH_MOB quests will never progress");
            }
        }
        if (isMythicMob == null) return false;
        try {
            return (boolean) isMythicMob.invoke(mythicMobManager, entity);
        } catch (Throwable t) {
            return false;
        }
    }
}
