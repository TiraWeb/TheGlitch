package com.theglitch.glitchshops;

import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class NpcListener implements Listener {

    private final GlitchShops plugin;
    private final ShopGUI shopGUI;

    public NpcListener(GlitchShops plugin, ShopGUI shopGUI) {
        this.plugin = plugin;
        this.shopGUI = shopGUI;
    }

    @EventHandler
    public void onNpcInteract(NpcInteractEvent event) {
        if (!event.isRightClick()) return;
        String npcName = event.getNpc().getData().getName();
        if (plugin.getConfig().getStringList("bazaar-npc-names").contains(npcName)) {
            shopGUI.open(event.getPlayer(), plugin.getConfig().getString("default-tab", "materials"));
        }
    }
}
