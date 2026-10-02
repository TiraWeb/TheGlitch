package com.theglitch.glitchtutorial;

import com.theglitch.common.Bots;
import com.theglitch.common.TutorialItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

/** Step triggers (crates, kills, deaths), privacy in the tutorial world, and tutorial-item guards. */
final class TutorialListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final GlitchTutorial plugin;
    private final TutorialManager manager;

    TutorialListener(GlitchTutorial plugin, TutorialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---- join / quit / worlds ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (Bots.isBot(p)) return;
        // after the other join handlers (raid reroutes etc.) have moved the player
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            manager.onJoin(p);
            updateVisibility(p);
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (Bots.isBot(p)) return;
        updateVisibility(p);
        World tw = manager.world();
        if (tw != null && event.getFrom().equals(tw)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                p.showPlayer(plugin, other);
                other.showPlayer(plugin, p);
            }
        }
    }

    /** Newcomers in the tutorial world can't see each other — it should feel like their own run. */
    private void updateVisibility(Player p) {
        World tw = manager.world();
        if (tw == null || !p.getWorld().equals(tw)) return;
        for (Player other : tw.getPlayers()) {
            if (other.equals(p) || Bots.isBot(other)) continue;
            p.hidePlayer(plugin, other);
            other.hidePlayer(plugin, p);
        }
    }

    /** No raiding with lent gear: the Red Zone is closed while the tutorial is running. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !manager.isActive(p)) return;
        String w = to.getWorld().getName();
        if (w.startsWith("glitch_red")) {
            event.setCancelled(true);
            p.sendMessage(MM.deserialize("<yellow>Finish the tutorial first</yellow> <gray>(or <yellow>/tutorial skip</yellow>) — then the Red Zone is all yours.</gray>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        TutorialStore.Record r = manager.record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE || !r.step.inTutorialWorld()) return;
        if (!manager.inTutorialWorld(p)) return;
        Location cp = manager.checkpoint(r.step);
        if (cp != null) event.setRespawnLocation(cp);
    }

    /** Lent items never drop on death (hub/dungeon deaths) — they just vanish. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(TutorialItems::isTutorial);
    }

    // ---- step triggers ----

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCrate(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.BARREL || !manager.inTutorialWorld(event.getPlayer())) return;
        if (manager.onCrate(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    /** Last real player to hit each tutorial mob/rogue — Citizens NPC deaths don't always credit a killer. */
    private final java.util.Map<java.util.UUID, java.util.UUID> lastHit = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        World tw = manager.world();
        if (tw == null || !event.getEntity().getWorld().equals(tw)) return;
        org.bukkit.entity.Entity d = event.getDamager();
        if (d instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) d = shooter;
        if (d instanceof Player hitter && !Bots.isBot(hitter)) lastHit.put(event.getEntity().getUniqueId(), hitter.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        World tw = manager.world();
        if (tw == null || !dead.getWorld().equals(tw)) return;
        Player killer = dead.getKiller();
        java.util.UUID hit = lastHit.remove(dead.getUniqueId());
        if ((killer == null || Bots.isBot(killer)) && hit != null) killer = Bukkit.getPlayer(hit);
        boolean rogue = Bots.isBot(dead);
        if (killer == null || Bots.isBot(killer)) {
            // a tutorial mob killed by something else still counts for its owner
            if (!rogue && !(dead instanceof Player)) {
                java.util.UUID owner = manager.mobOwner(dead.getUniqueId());
                Player p = owner == null ? null : Bukkit.getPlayer(owner);
                if (p != null) manager.onKill(p, dead, false);
            }
            return;
        }
        if (dead instanceof Player && !rogue) return;
        if (!rogue) event.getDrops().clear(); // tutorial mobs drop nothing real
        manager.onKill(killer, dead, rogue);
    }

    // ---- tutorial item guards ----

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (TutorialItems.isTutorial(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(MM.deserialize("<gray>That's a tutorial item — it goes back at the end.</gray>"));
        }
    }

    /**
     * With any GUI or container open (stash, Bazaar, insurance, chests…), tutorial items can't
     * be clicked, shift-clicked or hotbar-swapped — so they can't be stored, sold or insured.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return; // own inventory only
        ItemStack hotbar = event.getHotbarButton() >= 0 ? p.getInventory().getItem(event.getHotbarButton()) : null;
        if (TutorialItems.isTutorial(event.getCurrentItem()) || TutorialItems.isTutorial(event.getCursor())
                || TutorialItems.isTutorial(hotbar)
                || (event.getClick().name().contains("OFFHAND") && TutorialItems.isTutorial(p.getInventory().getItemInOffHand()))) {
            event.setCancelled(true);
            p.sendActionBar(MM.deserialize("<gray>Tutorial items can't be stored, sold or insured.</gray>"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!TutorialItems.isTutorial(event.getOldCursor())) return;
        int top = event.getView().getTopInventory().getSize();
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return;
        for (int slot : event.getRawSlots()) {
            if (slot < top) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFrame(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame)) return;
        if (TutorialItems.isTutorial(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (TutorialItems.isTutorial(event.getPlayerItem())) event.setCancelled(true);
    }
}
