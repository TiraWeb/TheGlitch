package com.theglitch.glitchinsurance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlitchInsurance extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchInsurance instance;
    private InsuranceManager manager;
    private volatile Economy cachedEconomy;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        cacheMessages();

        manager = new InsuranceManager(this);

        Bukkit.getPluginManager().registerEvents(new InsuranceListener(this, manager), this);

        var insuranceCmd = new InsuranceCommand(this, manager);
        var adminCmd = new InsuranceAdminCommand(this, manager);
        if (getCommand("insurance") != null) {
            getCommand("insurance").setExecutor(insuranceCmd);
            getCommand("insurance").setTabCompleter(insuranceCmd);
        }
        if (getCommand("insuranceadmin") != null) {
            getCommand("insuranceadmin").setExecutor(adminCmd);
            getCommand("insuranceadmin").setTabCompleter(adminCmd);
        }

        getLogger().info("GlitchInsurance enabled — premium=" + manager.getPremiumPerItem()
                + ", max=" + manager.getMaxInsuredItems()
                + ", claimWindow=" + manager.getClaimWindowSeconds() + "s, cooldown=" + manager.getCooldownSeconds() + "s");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchInsurance disabled.");
    }

    public void reloadPlugin() {
        this.cachedEconomy = null;
        reloadConfig();
        cacheMessages();
        if (manager != null) {
            manager.reload();
        }
        getLogger().info("GlitchInsurance reloaded (premium=" + manager.getPremiumPerItem()
                + ", max=" + manager.getMaxInsuredItems()
                + ", claimWindow=" + manager.getClaimWindowSeconds() + "s, cooldown=" + manager.getCooldownSeconds() + "s).");
    }

    private void cacheMessages() {
        messageCache.clear();
        if (getConfig().getConfigurationSection("messages") == null) return;
        for (String key : getConfig().getConfigurationSection("messages").getKeys(false)) {
            String val = getConfig().getString("messages." + key);
            if (val != null) messageCache.put(key, val);
        }
    }

    public String getMessage(String key) {
        String cached = messageCache.get(key);
        if (cached != null) return cached;
        String msg = getConfig().getString("messages." + key);
        if (msg != null) return msg;
        // fallback to direct key
        msg = getConfig().getString(key);
        return msg != null ? msg : key;
    }

    public Component getComponent(String key) {
        return MM.deserialize(getMessage(key));
    }

    public Component getComponent(String key, String placeholder, String value) {
        return MM.deserialize(getMessage(key).replace(placeholder, value));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3, String ph4, String v4) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3).replace(ph4, v4));
    }

    public Economy getEconomy() {
        if (cachedEconomy != null) return cachedEconomy;
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider != null) {
            cachedEconomy = provider.getProvider();
            if (cachedEconomy != null) getLogger().info("Economy provider found: " + cachedEconomy.getName());
        }
        return cachedEconomy;
    }

    public void invalidateEconomy() {
        this.cachedEconomy = null;
    }

    public InsuranceManager getManager() {
        return manager;
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchInsurance getInstance() {
        return instance;
    }
}
