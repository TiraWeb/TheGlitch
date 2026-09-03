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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.theglitch.glitchstash.extract.DynamicExtractionManager;
import com.theglitch.glitchstash.extract.ExtractionMarkers;

public final class GlitchStash extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchStash instance;
    private StashManager stashManager;
    private ExtractionVariantManager variantManager;
    private AutoExtractScheduler autoExtractScheduler;
    private ExtractionMarkers extractionMarkers;
    private DynamicExtractionManager dynamicExtractionManager;
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

        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new ExtractionListener(this, stashManager), this);
        pm.registerEvents(new StashGUI(), this);
        pm.registerEvents(new ExtractionVariantListener(this, variantManager), this);

        getCommand("stash").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashtp").setExecutor(new StashCommand(this, stashManager));
        getCommand("stashadmin").setExecutor(new StashAdminCommand(this, stashManager));
        getCommand("extractadmin").setExecutor(new ExtractionVariantCommand(this, variantManager));
        if (getCommand("stashui") != null) {
            getCommand("stashui").setExecutor(new com.theglitch.glitchstash.StashUICommand(this));
        }

        try {
            com.theglitch.glitchstash.ui.StashPanel.init(this);
        } catch (Throwable t) {
            getLogger().warning("Failed to init StashPanel: " + t.getMessage());
        }

        // Automated extraction — starts ALL VelKoth arenas every 31m (30m raid + 1m scatter buffer)
        // Folia-safe fixed-rate scheduler; discovers arenas reflectively or via config allow-list.
        // Dynamic mode (auto-extract.dynamic) picks random validated spots per cycle instead.
        // See AutoExtractScheduler.java:1 and extraction-variants for zone design (ROADMAP 5.11.5)
        try {
            extractionMarkers = new ExtractionMarkers(this);
            dynamicExtractionManager = new DynamicExtractionManager(this, extractionMarkers);
        } catch (Throwable t) {
            getLogger().warning("Dynamic extraction unavailable: " + t.getMessage());
            extractionMarkers = null;
            dynamicExtractionManager = null;
        }
        try {
            autoExtractScheduler = new AutoExtractScheduler(this, dynamicExtractionManager);
            autoExtractScheduler.start();
        } catch (Exception e) {
            getLogger().warning("Failed to start AutoExtractScheduler: " + e.getMessage());
            e.printStackTrace();
        }

        getLogger().info("GlitchStash enabled — " + stashManager.getStashCount() + " stashes loaded."
                + (autoExtractScheduler != null && autoExtractScheduler.isEnabled() ? " AutoExtract every " + autoExtractScheduler.getIntervalMinutes() + "m active." : " AutoExtract disabled."));
    }

    @Override
    public void onDisable() {
        try {
            com.theglitch.glitchstash.ui.StashPanel.shutdown(this);
        } catch (Throwable ignored) {
        }
        if (autoExtractScheduler != null) {
            try { autoExtractScheduler.shutdown(); } catch (Exception e) { getLogger().warning("Error shutting down AutoExtractScheduler: " + e.getMessage()); }
            autoExtractScheduler = null;
        }
        if (dynamicExtractionManager != null) {
            try { dynamicExtractionManager.endCycle(); } catch (Exception e) { getLogger().warning("Error ending dynamic extraction cycle: " + e.getMessage()); }
            dynamicExtractionManager = null;
            extractionMarkers = null;
        }
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
            if (display.contains("theglitch:ui")) {
                display = display.replace("<font:theglitch:ui>", "")
                        .replace("<font:minecraft:default>", "")
                        .replace("</font>", "");
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
        if (autoExtractScheduler != null) {
            try {
                autoExtractScheduler.reload();
                // Restart fixed-rate with new timings if enabled; otherwise it cancels inside start()
                autoExtractScheduler.start();
            } catch (Exception e) {
                getLogger().warning("Failed to reload AutoExtractScheduler: " + e.getMessage());
            }
        }
        try {
            com.theglitch.glitchstash.ui.StashPanel.reconfigureAndRebuild();
        } catch (Throwable ignored) {
        }
        getLogger().info("GlitchStash reloaded (payout=" + payoutEnabledCache
                + ", variants=" + variantEnabledCache + ", arm=" + variantArmDurationCache + "s"
                + ", autoExtract=" + (autoExtractScheduler != null ? autoExtractScheduler.isEnabled() + " " + autoExtractScheduler.getIntervalMinutes() + "m" : "n/a") + ").");
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

    public AutoExtractScheduler getAutoExtractScheduler() {
        return autoExtractScheduler;
    }

    public DynamicExtractionManager getDynamicExtractionManager() {
        return dynamicExtractionManager;
    }

    public ExtractionMarkers getExtractionMarkers() {
        return extractionMarkers;
    }
}
