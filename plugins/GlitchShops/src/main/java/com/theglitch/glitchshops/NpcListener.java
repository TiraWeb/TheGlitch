package com.theglitch.glitchshops;

import com.theglitch.glitchshops.ui.DialogBridge;
import com.theglitch.glitchshops.ui.DialogUI;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import org.bukkit.entity.Player;
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
        String npcName = event.getNpc().getData().getName();
        // Use cached HashSet lookup — no getConfig() polling per interact
        if (plugin.getBazaarNpcNames().contains(npcName)) {
            Player player = event.getPlayer();
            String category = plugin.getDefaultTab();
            if (shopGUI.dialogsEnabled() && DialogBridge.dialogsRuntime()) {
                DialogUI.openRoot(plugin, shopGUI, player, () -> shopGUI.open(player, category));
            } else {
                shopGUI.open(player, category);
            }
        }
    }
}
