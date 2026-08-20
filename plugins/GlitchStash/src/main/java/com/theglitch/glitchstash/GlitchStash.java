package com.theglitch.glitchstash;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GlitchStash extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchStash instance;
    private StashManager stashManager;
    private ExtractionVariantManager variantManager;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    // Cached hot-path config & economy
    private volatile Economy cachedEconomy;
    private volatile boolean payoutEnabledCache = true;
    private volatile boolean variantEnabledCache = true;
    private volatile boolean variantEnforceKeyCache = true;
    private volatile int variantArmDurationCache = 180;
    private volatile String cachedDisplayName = "<dark_purple>YOUR STASH</dark_purple>";
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();
        cacheConfig();

        stashManager = new StashManager(this);
        variantManager = new ExtractionVariantManager(this);

        Bukkit.getPluginManager().registerEvents(new ExtractionListener(this, stashManager), this);
        Bukkit.getPluginManager().registerEvents(new StashGUI(), this);
        Bukkit.getPluginManager().registerEvents(new ExtractionVariantListener(this, variantManager), this);

        getCommand("stash").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashtp").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashadmin").setExecutor(new StashAdminCommand(this, stashManager));
        getCommand("extractadmin").setExecutor(new ExtractionVariantCommand(this, variantManager));

        getLogger().info("GlitchStash enabled — " + stashManager.getStashCount() + " stashes loaded.");
    }

    @Override
    public void onDisable() {
        if (stashManager != null) {
            stashManager.shutdown();
        }
        instance = null;
        getLogger().info("GlitchStash disabled.");
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        // Cache all messages for hot-path lookups (single map get vs YAML traversal)
        messageCache.clear();
        for (String key : messagesConfig.getKeys(false)) {
            String val = messagesConfig.getString(key);
            if (val != null) messageCache.put(key, val);
        }
        // For nested? messages.yml is flat, but support nested anyway
        for (String key : messagesConfig.getKeys(true)) {
            if (!messageCache.containsKey(key)) {
                String val = messagesConfig.getString(key);
                if (val != null) messageCache.put(key, val);
            }
        }
    }

    private void cacheConfig() {
        try {
            payoutEnabledCache = getConfig().getBoolean("payout-enabled", true);
            String display = getConfig().getString("display-name", "<dark_purple>YOUR STASH</dark_purple>");
            if (display == null || display.isBlank()) {
                getLogger().warning("Invalid display-name — falling back to default.");
                display = "<dark_purple>YOUR STASH</dark_purple>";
            }
            cachedDisplayName = display;
            variantEnabledCache = getConfig().getBoolean("extraction-variants.enabled", true);
            variantEnforceKeyCache = getConfig().getBoolean("extraction-variants.enforce-key", true);
            int arm = getConfig().getInt("extraction-variants.arm-duration-seconds", 180);
            if (arm < 1 || arm > 3600) {
                getLogger().warning("Invalid extraction-variants.arm-duration-seconds " + arm + " — clamped to 180.");
                arm = Math.max(1, Math.min(arm, 3600));
            }
            variantArmDurationCache = arm;
        } catch (Exception e) {
            getLogger().warning("Failed to cache GlitchStash config: " + e.getMessage());
        }
    }

    public void reloadPlugin() {
        this.cachedEconomy = null;
        reloadConfig();
        loadMessages();
        cacheConfig();
        if (variantManager != null) {
            variantManager.reload();
        }
        getLogger().info("GlitchStash reloaded (payout=" + payoutEnabledCache
                + ", variants=" + variantEnabledCache + ", arm=" + variantArmDurationCache + "s).");
    }

    public String getMessage(String key) {
        String cached = messageCache.get(key);
        if (cached != null) return cached;
        String msg = messagesConfig.getString(key, key);
        return msg;
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

    public boolean isPayoutEnabled() {
        return payoutEnabledCache;
    }

    public boolean isVariantEnabled() {
        return variantEnabledCache;
    }

    public boolean isVariantEnforceKey() {
        return variantEnforceKeyCache;
    }

    public int getVariantArmDuration() {
        return variantArmDurationCache;
    }

    public String getCachedDisplayName() {
        return cachedDisplayName;
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchStash getInstance() {
        return instance;
    }

    public StashManager getStashManager() {
        return stashManager;
    }

    public ExtractionVariantManager getExtractionVariantManager() {
        return variantManager;
    }
}
