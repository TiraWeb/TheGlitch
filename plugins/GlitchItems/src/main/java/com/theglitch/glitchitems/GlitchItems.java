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
    private ContainerManager containerManager;
    private Economy economy;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        economy = getEconomy();
        if (economy == null) {
            getLogger().warning("No Vault economy provider registered — identify fees disabled until an economy plugin is present.");
        }

        gearManager = new GearManager(this);
        glitchManager = new ResidualGlitchManager(this);
        identifyManager = new IdentifyManager(this, gearManager);
        containerManager = new ContainerManager(this);

        Bukkit.getPluginManager().registerEvents(new CombatListener(this, gearManager, glitchManager), this);
        Bukkit.getPluginManager().registerEvents(new ContainerListener(containerManager), this);
        glitchManager.start();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new GlitchExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered (%glitchitems_*%).");
        }

        getCommand("identify").setExecutor(new IdentifyCommand(identifyManager));
        getCommand("glitchitems").setExecutor(new GlitchItemsCommand(this, gearManager, glitchManager));
        getCommand("glitchcontainers").setExecutor(new ContainerCommand(this, containerManager));

        getLogger().info("GlitchItems enabled.");
    }

    @Override
    public void onDisable() {
        if (glitchManager != null) {
            glitchManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchItems disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (containerManager != null) {
            containerManager.reload();
        }
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

    public ContainerManager getContainerManager() {
        return containerManager;
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
}
