package com.theglitch.glitchhideout;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.Set;

public final class GlitchHideout extends JavaPlugin {

    private static final Set<String> GAME_WORLDS = Set.of("glitch_pve", "glitch_red");

    private static GlitchHideout instance;
    private HideoutManager manager;
    private HideoutGUI gui;
    private FileConfiguration messagesConfig;
    private File messagesFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadMessages();

        manager = new HideoutManager(this);
        gui = new HideoutGUI(this, manager);

        Bukkit.getPluginManager().registerEvents(gui, this);

        getCommand("hideout").setExecutor(this::onHideoutCommand);
        getCommand("hideoutadmin").setExecutor(this::onAdminCommand);

        startIntelTicker();

        getLogger().info("GlitchHideout enabled.");
    }

    @Override
    public void onDisable() {
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
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                if (manager.intelLevel(player.getUniqueId()) < 1) continue;
                if (!GAME_WORLDS.contains(player.getWorld().getName())) continue;
                for (Entity entity : player.getNearbyEntities(20, 20, 20)) {
                    if (entity instanceof Monster || isGlitchMob(entity)) {
                        ((LivingEntity) entity).addPotionEffect(
                                new PotionEffect(PotionEffectType.GLOWING, 30, 0));
                    }
                }
            }
        }, 60L, 10L);
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
    }

    public void reloadPlugin() {
        reloadConfig();
        loadMessages();
        if (manager != null) {
            manager.reload();
        }
        getLogger().info("GlitchHideout reloaded.");
    }

    public String getMessage(String key) {
        return messagesConfig.getString(key, key);
    }

    public Component getComponent(String key) {
        return MiniMessage.miniMessage().deserialize(getMessage(key));
    }

    public Component getComponent(String key, String ph1, String v1) {
        return MiniMessage.miniMessage().deserialize(getMessage(key).replace(ph1, v1));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2) {
        return MiniMessage.miniMessage().deserialize(
                getMessage(key).replace(ph1, v1).replace(ph2, v2));
    }

    public Component getComponent(String key, String ph1, String v1, String ph2, String v2, String ph3, String v3) {
        return MiniMessage.miniMessage().deserialize(
                getMessage(key).replace(ph1, v1).replace(ph2, v2).replace(ph3, v3));
    }

    public static GlitchHideout getInstance() {
        return instance;
    }

    public HideoutManager getHideoutManager() {
        return manager;
    }
}
