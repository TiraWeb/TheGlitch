package com.theglitch.glitchitems;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlitchItems extends JavaPlugin {

    private static GlitchItems instance;
    private GearManager gearManager;
    private ResidualGlitchManager glitchManager;
    private IdentifyManager identifyManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        economy = setupEconomy();
        if (economy == null) {
            getLogger().warning("Vault economy not found — identify fees disabled until an economy plugin is present.");
        }

        gearManager = new GearManager(this);
        glitchManager = new ResidualGlitchManager(this);
        identifyManager = new IdentifyManager(this, gearManager);

        Bukkit.getPluginManager().registerEvents(new CombatListener(this, gearManager, glitchManager), this);
        glitchManager.start();

        getCommand("identify").setExecutor(new IdentifyCommand(identifyManager));
        getCommand("glitchitems").setExecutor(new GlitchItemsCommand(this, gearManager, glitchManager));

        getLogger().info("GlitchItems enabled.");
    }

    @Override
    public void onDisable() {
        instance = null;
        getLogger().info("GlitchItems disabled.");
    }

    private Economy setupEconomy() {
        if (getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);
        return provider == null ? null : provider.getProvider();
    }

    public void reloadPlugin() {
        reloadConfig();
        getLogger().info("GlitchItems reloaded.");
    }

    public static GlitchItems getInstance() {
        return instance;
    }

    public GearManager getGearManager() {
        return gearManager;
    }

    public ResidualGlitchManager getGlitchManager() {
        return glitchManager;
    }

    public IdentifyManager getIdentifyManager() {
        return identifyManager;
    }

    public Economy getEconomy() {
        return economy;
    }
}
