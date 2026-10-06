package com.theglitch.glitchtutorial;

import com.theglitch.common.Bots;
import com.theglitch.common.TutorialItems;
import com.theglitch.common.Worlds;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.forget(event.getPlayer());
    }

    /** Leaving your own tutorial world (step done, /spawn, skip...) throws it away. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (Bots.isBot(p)) return;
        java.util.UUID owner = manager.instances().ownerOf(event.getFrom().getName());
        if (owner != null && owner.equals(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.getWorld().equals(event.getFrom())) manager.closeInstance(owner);
            });
        }
    }

    /** No raiding with lent gear: the Red Zone is closed while the tutorial is running. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player p = event.getPlayer();
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || !manager.isActive(p)) return;
        String w = to.getWorld().getName();
        if (w.startsWith(Worlds.GLITCH_RED)) {
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
        Location cp = manager.checkpoint(p.getWorld(), r.step);
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
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            // the off-hand pass of the same click: just keep the barrel closed
            if (event.getClickedBlock().getType() == Material.BARREL && manager.inTutorialWorld(event.getPlayer())) event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock().getType() != Material.BARREL || !manager.inTutorialWorld(event.getPlayer())) return;
        if (manager.onCrate(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    /**
     * Tutorial mobs and training rogues count for the newcomer they were spawned for, whoever
     * landed the killing blow — newcomers are hidden from each other but can still hit each
     * other's mobs, and Citizens NPC deaths don't always credit a killer.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!manager.instances().isTutorialWorld(dead.getWorld().getName())) return;
        boolean rogue = Bots.isBot(dead);
        if (dead instanceof Player && !rogue) return;
        if (!rogue) event.getDrops().clear(); // tutorial mobs drop nothing real
        java.util.UUID owner = rogue ? TutorialManager.traineeOf(dead) : manager.mobOwner(dead.getUniqueId());
        Player p = owner == null ? null : Bukkit.getPlayer(owner);
        if (p != null) manager.onKill(p, dead, rogue);
    }

    // ---- tutorial combat buff ----

    /**
     * Lent gear alone can't carry a newcomer through a boss built for geared parties (Goblin
     * Hollow's boss: 900 HP, 8-32 per hit), so active tutorial players hit harder and take less:
     * {@code combat.tutorial-world} inside their tutorial world, {@code combat.dungeon} during
     * the dungeon step (anywhere but the hub — the Red Zone is closed to them).
     * Runs after GlitchItems' gear modifiers (NORMAL) so it scales the final number.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && !Bots.isBot(victim)) {
            String zone = buffZone(victim);
            if (zone != null) event.setDamage(event.getDamage() * plugin.getConfig().getDouble("combat." + zone + ".damage-taken", 1.0));
        }
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            org.bukkit.entity.Entity d = byEntity.getDamager();
            if (d instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) d = shooter;
            if (d instanceof Player attacker && !Bots.isBot(attacker) && !(event.getEntity() instanceof Player victim && !Bots.isBot(victim))) {
                String zone = buffZone(attacker);
                if (zone != null) event.setDamage(event.getDamage() * plugin.getConfig().getDouble("combat." + zone + ".damage-dealt", 1.0));
            }
        }
    }

    /** "tutorial-world", "dungeon" or null (no buff). */
    private String buffZone(Player p) {
        TutorialStore.Record r = manager.record(p);
        if (r == null || r.status != TutorialStore.Status.ACTIVE) return null;
        String w = p.getWorld().getName();
        if (manager.inTutorialWorld(p)) return "tutorial-world";
        if (r.step == Step.DUNGEON && !w.equals("hub") && !w.startsWith(Worlds.GLITCH_RED)) return "dungeon";
        return null;
    }

    /** Tell them about the dungeon buff when they arrive. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnterDungeon(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (Bots.isBot(p) || !"dungeon".equals(buffZone(p))) return;
        String msg = plugin.getConfig().getString("combat.dungeon.message", "");
        if (msg.isBlank()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) manager.sayLines(p, java.util.List.of(msg), null, 0);
        }, 40L);
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
        // Bundles: a tutorial item tucked into a bundle would leave inside it
        ItemStack cur = event.getCurrentItem(), cursor = event.getCursor();
        if ((isBundle(cur) && TutorialItems.isTutorial(cursor)) || (isBundle(cursor) && TutorialItems.isTutorial(cur))) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getType() == InventoryType.CRAFTING) return; // own inventory only
        ItemStack hotbar = event.getHotbarButton() >= 0 ? p.getInventory().getItem(event.getHotbarButton()) : null;
        if (TutorialItems.isTutorial(event.getCurrentItem()) || TutorialItems.isTutorial(event.getCursor())
                || TutorialItems.isTutorial(hotbar)
                || (event.getClick().name().contains("OFFHAND") && TutorialItems.isTutorial(p.getInventory().getItemInOffHand()))) {
            event.setCancelled(true);
            p.sendActionBar(MM.deserialize("<gray>Tutorial items can't be stored, sold or insured.</gray>"));
        }
    }

    private static boolean isBundle(ItemStack it) {
        return it != null && it.getType().name().endsWith("BUNDLE");
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
