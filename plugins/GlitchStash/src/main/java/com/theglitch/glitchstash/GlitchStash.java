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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.theglitch.glitchstash.extract.DynamicExtractionManager;
import com.theglitch.glitchstash.extract.ExtractionMarkers;

public final class GlitchStash extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static GlitchStash instance;
    private StashManager stashManager;
    private ExtractionVariantManager variantManager;
    // One instance-triple per configured red world (auto-extract.red-worlds) — each world
    // runs its own independent 31m cycle, 3 dynamic extraction points, and markers.
    private final Map<String, AutoExtractScheduler> autoExtractSchedulers = new ConcurrentHashMap<>();
    private final Map<String, ExtractionMarkers> extractionMarkersByWorld = new ConcurrentHashMap<>();
    private final Map<String, DynamicExtractionManager> dynamicExtractionManagers = new ConcurrentHashMap<>();
    private volatile List<String> redWorlds = List.of("glitch_red");
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

        // Automated extraction — one instance-triple per configured red world, each starting
        // ALL its own VelKoth arenas every 31m (30m raid + 1m scatter buffer), fully independent
        // of the other worlds' cycles. Folia-safe fixed-rate scheduler; discovers arenas
        // reflectively or via config allow-list. Dynamic mode (auto-extract.dynamic) picks
        // random validated spots per cycle instead. See AutoExtractScheduler.java:1 and
        // extraction-variants for zone design (ROADMAP 5.11.5)
        redWorlds = loadRedWorlds();
        startExtractionCycles();

        int totalSchedulers = autoExtractSchedulers.size();
        getLogger().info("GlitchStash enabled — " + stashManager.getStashCount() + " stashes loaded."
                + (totalSchedulers > 0 ? " AutoExtract active for " + totalSchedulers + " red world(s): " + redWorlds + "." : " AutoExtract disabled."));
    }

    /** Reads auto-extract.red-worlds (falls back to the deprecated singular red-world key, then a hard default). */
    private List<String> loadRedWorlds() {
        List<String> list = getConfig().getStringList("auto-extract.red-worlds");
        List<String> normalized = new ArrayList<>();
        if (list != null) {
            for (String w : list) {
                if (w != null && !w.isBlank()) normalized.add(w.trim());
            }
        }
        if (normalized.isEmpty()) {
            String legacy = getConfig().getString("auto-extract.red-world", "glitch_red");
            if (legacy != null && !legacy.isBlank()) normalized.add(legacy.trim());
        }
        if (normalized.isEmpty()) normalized.add("glitch_red");
        return List.copyOf(normalized);
    }

    /** Constructs and starts one (ExtractionMarkers, DynamicExtractionManager, AutoExtractScheduler) triple per red world. */
    private void startExtractionCycles() {
        for (String world : redWorlds) {
            String key = world.toLowerCase(java.util.Locale.ROOT);
            try {
                ExtractionMarkers markers = new ExtractionMarkers(this, world);
                DynamicExtractionManager dynamicManager = new DynamicExtractionManager(this, markers, world);
                extractionMarkersByWorld.put(key, markers);
                dynamicExtractionManagers.put(key, dynamicManager);
                AutoExtractScheduler scheduler = new AutoExtractScheduler(this, dynamicManager, world);
                scheduler.start();
                autoExtractSchedulers.put(key, scheduler);
            } catch (Throwable t) {
                getLogger().warning("Failed to start extraction cycle for world '" + world + "': " + t.getMessage());
            }
        }
    }

    /** Stops and clears every world's extraction-cycle instances. */
    private void stopExtractionCycles() {
        for (AutoExtractScheduler scheduler : autoExtractSchedulers.values()) {
            try { scheduler.shutdown(); } catch (Exception e) { getLogger().warning("Error shutting down AutoExtractScheduler: " + e.getMessage()); }
        }
        autoExtractSchedulers.clear();
        for (DynamicExtractionManager manager : dynamicExtractionManagers.values()) {
            try { manager.endCycle(); } catch (Exception e) { getLogger().warning("Error ending dynamic extraction cycle: " + e.getMessage()); }
        }
        dynamicExtractionManagers.clear();
        extractionMarkersByWorld.clear();
    }

    @Override
    public void onDisable() {
        try {
            com.theglitch.glitchstash.ui.StashPanel.shutdown(this);
        } catch (Throwable ignored) {
        }
        stopExtractionCycles();
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
        // Rebuild the per-world extraction cycles from scratch so an admin can add/remove
        // a red world via config + reload without a full server restart.
        stopExtractionCycles();
        redWorlds = loadRedWorlds();
        startExtractionCycles();
        try {
            com.theglitch.glitchstash.ui.StashPanel.reconfigureAndRebuild();
        } catch (Throwable ignored) {
        }
        getLogger().info("GlitchStash reloaded (payout=" + payoutEnabledCache
                + ", variants=" + variantEnabledCache + ", arm=" + variantArmDurationCache + "s"
                + ", autoExtract=" + autoExtractSchedulers.size() + " world(s) " + redWorlds + ").");
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

    /** @deprecated use {@link #getAutoExtractScheduler(String)} — returns the first configured red world's scheduler. */
    @Deprecated
    public AutoExtractScheduler getAutoExtractScheduler() {
        if (redWorlds.isEmpty()) return null;
        return autoExtractSchedulers.get(redWorlds.get(0).toLowerCase(java.util.Locale.ROOT));
    }

    public AutoExtractScheduler getAutoExtractScheduler(String world) {
        if (world == null) return null;
        return autoExtractSchedulers.get(world.toLowerCase(java.util.Locale.ROOT));
    }

    /** @deprecated use {@link #getDynamicExtractionManager(String)} — returns the first configured red world's manager. */
    @Deprecated
    public DynamicExtractionManager getDynamicExtractionManager() {
        if (redWorlds.isEmpty()) return null;
        return dynamicExtractionManagers.get(redWorlds.get(0).toLowerCase(java.util.Locale.ROOT));
    }

    public DynamicExtractionManager getDynamicExtractionManager(String world) {
        if (world == null) return null;
        return dynamicExtractionManagers.get(world.toLowerCase(java.util.Locale.ROOT));
    }

    /** @deprecated use {@link #getExtractionMarkers(String)} — returns the first configured red world's markers. */
    @Deprecated
    public ExtractionMarkers getExtractionMarkers() {
        if (redWorlds.isEmpty()) return null;
        return extractionMarkersByWorld.get(redWorlds.get(0).toLowerCase(java.util.Locale.ROOT));
    }

    public ExtractionMarkers getExtractionMarkers(String world) {
        if (world == null) return null;
        return extractionMarkersByWorld.get(world.toLowerCase(java.util.Locale.ROOT));
    }

    public List<String> getRedWorlds() {
        return redWorlds;
    }
}
