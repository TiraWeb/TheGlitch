package com.theglitch.glitchshops;

import com.theglitch.glitchshops.ui.DialogBridge;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public final class GlitchShops extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchShops instance;
    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private Economy economy;

    // Cached config — refreshed on reload, read without getConfig() polling on hot paths
    private Set<String> bazaarNpcNames = new HashSet<>();
    private String defaultTab = "materials";

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cacheConfig();

        shopManager = new ShopManager(this);
        shopManager.reload();

        shopGUI = new ShopGUI(this, shopManager);
        Bukkit.getPluginManager().registerEvents(shopGUI, this);

        if (Bukkit.getPluginManager().getPlugin("FancyNpcs") != null) {
            Bukkit.getPluginManager().registerEvents(new NpcListener(this, shopGUI), this);
            getLogger().info("FancyNpcs hook enabled.");
        } else {
            getLogger().warning("FancyNpcs not found — bazaar opens via /shop only.");
        }

        getCommand("shop").setExecutor(new ShopCommand(this, shopGUI));

        if (getCommand("shop") != null && getConfig().getBoolean("modern-ui.enabled", true)) {
            getLogger().info(DialogBridge.runtimeSummary());
        }

        if (getEconomy() == null) {
            getLogger().warning("No Vault economy provider yet — transactions will work once one registers.");
        }

        getLogger().info("GlitchShops enabled.");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("GlitchShops disabled.");
    }

    public void reloadPlugin() {
        this.economy = null; // invalidate cached Vault provider
        reloadConfig();
        cacheConfig();
        shopManager.reload();
        if (shopGUI != null) {
            shopGUI.refreshCache();
        }
        // Re-resolve economy eagerly so next transaction is lock-free
        getEconomy();
        getLogger().info("GlitchShops reloaded (bazaarNpcs=" + bazaarNpcNames.size()
                + ", defaultTab=" + defaultTab + ").");
    }

    private void cacheConfig() {
        try {
            bazaarNpcNames = new HashSet<>(getConfig().getStringList("bazaar-npc-names"));
            String tab = getConfig().getString("default-tab", "materials");
            if (tab == null || tab.isBlank()) {
                getLogger().warning("Invalid default-tab '" + tab + "' — falling back to materials.");
                tab = "materials";
            }
            defaultTab = tab;
        } catch (Exception e) {
            getLogger().warning("Failed to cache GlitchShops config: " + e.getMessage());
            bazaarNpcNames = new HashSet<>();
            defaultTab = "materials";
        }
    }

    public Economy getEconomy() {
        if (economy != null) {
            return economy;
        }
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            economy = provider.getProvider();
            if (economy != null) {
                getLogger().info("Economy provider found: " + economy.getName());
            }
        }
        return economy;
    }

    /** Invalidate cached economy — called on reload or when provider changes. */
    public void invalidateEconomy() {
        this.economy = null;
    }

    public Set<String> getBazaarNpcNames() {
        return bazaarNpcNames;
    }

    public String getDefaultTab() {
        return defaultTab;
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchShops getInstance() {
        return instance;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}
