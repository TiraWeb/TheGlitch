package com.theglitch.glitchhideout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GlitchHideout extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> GAME_WORLDS = Set.of("glitch_pve", "glitch_red");

    private static GlitchHideout instance;
    private HideoutManager manager;
    private HideoutGUI gui;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    // Cached hot-path & economy
    private volatile int medCooldownSeconds = 30;
    private volatile int intelGlowTicks = 30;
    private volatile int intelRange = 20;
    private volatile Economy cachedEconomy;
    private final Map<String, String> messageCache = new ConcurrentHashMap<>();
    private BukkitTask intelTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();
        cacheConfig();

        manager = new HideoutManager(this);
        gui = new HideoutGUI(this, manager);

        Bukkit.getPluginManager().registerEvents(gui, this);

        getCommand("hideout").setExecutor(this::onHideoutCommand);
        getCommand("hideoutadmin").setExecutor(this::onAdminCommand);
        if (getCommand("hideoutui") != null) {
            getCommand("hideoutui").setExecutor(new HideoutUICommand(this));
        }

        try {
            com.theglitch.glitchhideout.ui.HideoutPanel.init(this);
        } catch (Throwable t) {
            getLogger().warning("HideoutPanel init failed: " + t.getMessage());
        }

        startIntelTicker();

        getLogger().info("GlitchHideout enabled.");
    }

    @Override
    public void onDisable() {
        if (intelTask != null) {
            intelTask.cancel();
            intelTask = null;
        }
        try {
            com.theglitch.glitchhideout.ui.HideoutPanel.shutdown();
        } catch (Throwable ignored) {
        }
        if (manager != null) {
            manager.saveAll();
        }
        instance = null;
        getLogger().info("GlitchHideout disabled.");
    }

    public boolean onHideoutCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only."));
            return true;
        }
        if (args.length == 0
                && getConfig().getBoolean("modern-ui.dialogs", true)
                && com.theglitch.glitchhideout.ui.DialogBridge.dialogsRuntime()
                && com.theglitch.glitchhideout.ui.DialogUI.canRemote(this, player)) {
            com.theglitch.glitchhideout.ui.DialogUI.openRoot(this, player, () -> gui.openMain(player));
            return true;
        }
        gui.openMain(player);
        return true;
    }

    public boolean onAdminCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /hideoutadmin <reload|set <player> <station> <level>|reset <player>>"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadPlugin();
                sender.sendMessage(getComponent("admin-reloaded"));
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage(Component.text("Usage: /hideoutadmin set <player> <station> <level>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found."));
                    return true;
                }
                if (manager.getStation(args[2].toLowerCase()) == null) {
                    sender.sendMessage(Component.text("Unknown station."));
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Level must be a number."));
                    return true;
                }
                manager.setLevel(target.getUniqueId(), args[2].toLowerCase(), Math.max(0, level));
                sender.sendMessage(getComponent("admin-set",
                        "<player>", target.getName(),
                        "<station>", args[2].toLowerCase(),
                        "<level>", String.valueOf(level)));
            }
            case "reset" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /hideoutadmin reset <player>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found."));
                    return true;
                }
                manager.resetPlayer(target.getUniqueId());
                sender.sendMessage(getComponent("admin-reset", "<player>", target.getName()));
            }
            default -> sender.sendMessage(Component.text(
                    "Usage: /hideoutadmin <reload|set <player> <station> <level>|reset <player>>"));
        }
        return true;
    }

    private void startIntelTicker() {
        if (intelTask != null) intelTask.cancel();
        intelTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (manager == null) return;
            int glowTicks = intelGlowTicks; // cached — no getConfig() per tick
            int range = intelRange; // cached — no getConfig() per tick
            // Single-pass candidate collection — skip tick if no candidates
            java.util.List<Player> candidates = null;
            for (Player p : getServer().getOnlinePlayers()) {
                if (manager.intelLevel(p.getUniqueId()) >= 1 && GAME_WORLDS.contains(p.getWorld().getName())) {
                    if (candidates == null) candidates = new java.util.ArrayList<>(4);
                    candidates.add(p);
                }
            }
            if (candidates == null || candidates.isEmpty()) return;
            for (Player player : candidates) {
                // y=10 cheaper than 20x20x20 cube — intel is horizontal scouting
                for (Entity entity : player.getNearbyEntities(range, 10, range)) {
                    if (entity instanceof Monster || isGlitchMob(entity)) {
                        LivingEntity living = (LivingEntity) entity;
                        // Skip if already glowing with >10 ticks remaining — avoids re-adding effect
                        PotionEffect existing = living.getPotionEffect(PotionEffectType.GLOWING);
                        if (existing != null && existing.getDuration() > 10) continue;
                        living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0));
                    }
                }
            }
        }, 60L, 20L);
    }

    private boolean isGlitchMob(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (living.getCustomName() == null) return false;
        String name = living.getCustomName();
        return name.contains("Glitch") || name.contains("Corrupted");
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        messageCache.clear();
        for (String key : messagesConfig.getKeys(false)) {
            String v = messagesConfig.getString(key);
            if (v != null) messageCache.put(key, v);
        }
        for (String key : messagesConfig.getKeys(true)) {
            if (!messageCache.containsKey(key)) {
                String v = messagesConfig.getString(key);
                if (v != null) messageCache.put(key, v);
            }
        }
    }

    private void cacheConfig() {
        try {
            int cd = getConfig().getInt("med-cooldown-seconds", 30);
            if (cd < 1 || cd > 3600) {
                getLogger().warning("Invalid med-cooldown-seconds " + cd + " — clamped to 30.");
                cd = Math.max(1, Math.min(cd, 3600));
            }
            medCooldownSeconds = cd;

            int glow = getConfig().getInt("intel-glow-ticks", 30);
            if (glow < 5 || glow > 200) {
                getLogger().warning("Invalid intel-glow-ticks " + glow + " — clamped to 30.");
                glow = 30;
            }
            intelGlowTicks = glow;

            int range = getConfig().getInt("intel-range", 20);
            if (range < 5 || range > 64) range = 20;
            intelRange = range;

            // Invalidate economy cache
            cachedEconomy = null;
        } catch (Exception e) {
            getLogger().warning("Failed to cache GlitchHideout config: " + e.getMessage());
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        loadMessages();
        cacheConfig();
        if (manager != null) {
            manager.reload();
            manager.invalidateEconomy();
        }
        startIntelTicker(); // restart with new cached periods
        getLogger().info("GlitchHideout reloaded (medCooldown=" + medCooldownSeconds + "s, glow=" + intelGlowTicks + ").");
    }

    public String getMessage(String key) {
        String cached = messageCache.get(key);
        if (cached != null) return cached;
        return messagesConfig.getString(key, key);
    }

    public Component getComponent(String key) {
        return MM.deserialize(getMessage(key));
    }

    public Component getComponent(String key, String ph1, String v1) {
        return MM.deserialize(getMessage(key).replace(ph1, v1));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3) {
        return MM.deserialize(getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3));
    }

    public Economy getEconomy() {
        if (cachedEconomy != null) return cachedEconomy;
        RegisteredServiceProvider<Economy> prov = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (prov != null) {
            cachedEconomy = prov.getProvider();
            if (cachedEconomy != null) getLogger().info("Economy provider found: " + cachedEconomy.getName());
        }
        return cachedEconomy;
    }

    public int getMedCooldownSeconds() {
        return medCooldownSeconds;
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchHideout getInstance() {
        return instance;
    }

    public HideoutManager getHideoutManager() {
        return manager;
    }

    public HideoutGUI getGui() {
        return gui;
    }
}
