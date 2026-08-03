package com.theglitch.glitchshops;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlitchShops extends JavaPlugin {

    private static GlitchShops instance;
    private ShopManager shopManager;
    private ShopGUI shopGUI;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

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
        reloadConfig();
        shopManager.reload();
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

    public static GlitchShops getInstance() {
        return instance;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }
}
