package com.theglitch.glitchhud;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GlitchHUD extends JavaPlugin implements Listener {

    private PlaceholderResolver placeholders;
    private HudManager hudManager;
    private BelowNameManager belowNameManager;
    private boolean bossbarPatched = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        placeholders = new PlaceholderResolver(this);
        hudManager = new HudManager(this, placeholders);
        belowNameManager = new BelowNameManager(this, placeholders, hudManager);

        hudManager.reload();
        belowNameManager.reload();

        // Hook placeholder refresh on enable (wait a tick for expansions to register)
        FoliaScheduler.runLaterGlobal(this, () -> placeholders.refresh(), 20L);

        getServer().getPluginManager().registerEvents(this, this);

        hudManager.start();
        belowNameManager.start();

        // Ensure all online players get board (reload case)
        FoliaScheduler.runLaterGlobal(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                hudManager.ensureBoard(p);
                belowNameManager.syncViewer(p);
            }
        }, 10L);

        // Best-effort patch residual bossbar overlay
        FoliaScheduler.runLaterGlobal(this, this::patchResidualBossbar, 40L);

        getLogger().info("GlitchHUD enabled (refresh=" + getConfig().getInt("hud.refresh-ticks", 20) + " ticks, below-name=" + getConfig().getBoolean("below-name.enabled", true) + ").");
    }

    @Override
    public void onDisable() {
        if (hudManager != null) hudManager.shutdown();
        if (belowNameManager != null) belowNameManager.shutdown();
        getLogger().info("GlitchHUD disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        placeholders.refresh();
        if (hudManager != null) {
            hudManager.reload();
            // Rebuild boards to apply new title
            for (Player p : Bukkit.getOnlinePlayers()) hudManager.ensureBoard(p);
        }
        if (belowNameManager != null) {
            belowNameManager.reload();
            belowNameManager.start();
        }
        patchResidualBossbar();
        getLogger().info("GlitchHUD reloaded.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        placeholders.refresh();
        FoliaScheduler.runLaterGlobal(this, () -> {
            hudManager.ensureBoard(p);
            belowNameManager.syncViewer(p);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        hudManager.removeBoard(e.getPlayer());
        belowNameManager.onQuit(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        FoliaScheduler.runLaterGlobal(this, () -> {
            hudManager.ensureBoard(p);
            hudManager.refresh(p);
            belowNameManager.onWorldChange(p);
            belowNameManager.syncViewer(p);
        }, 2L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(java.util.Locale.ROOT);
        if ("glitchhud".equals(cmd)) {
            if (!sender.hasPermission("glitchhud.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
                sender.sendMessage("§dGlitchHUD §7— §f/glitchhud reload §7| §f/glitchhud toggle §7| §f/glitchhud debug");
                return true;
            }
            if ("reload".equalsIgnoreCase(args[0])) {
                reloadPlugin();
                sender.sendMessage("§aGlitchHUD reloaded.");
                return true;
            }
            if ("toggle".equalsIgnoreCase(args[0])) {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cPlayers only.");
                    return true;
                }
                boolean visible = hudManager.toggle(p);
                sender.sendMessage(visible ? "§aHUD shown." : "§7HUD hidden. §f/sb §7to show.");
                return true;
            }
            if ("debug".equalsIgnoreCase(args[0])) {
                boolean dbg = !getConfig().getBoolean("debug", false);
                getConfig().set("debug", dbg);
                saveConfig();
                sender.sendMessage("§7Debug: " + (dbg ? "§aON" : "§cOFF"));
                // Also trigger immediate refresh dump
                if (sender instanceof Player p) {
                    hudManager.refresh(p);
                    sender.sendMessage("§7Refreshed HUD for " + p.getName());
                }
                return true;
            }
            sender.sendMessage("§cUnknown subcommand. §7/glitchhud help");
            return true;
        }
        if ("sb".equals(cmd)) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (!p.hasPermission("glitchhud.toggle")) {
                p.sendMessage("§cNo permission.");
                return true;
            }
            boolean visible = hudManager.toggle(p);
            p.sendMessage(visible ? "§aScoreboard shown." : "§7Scoreboard hidden. §f/sb §7to toggle back.");
            return true;
        }
        return false;
    }

    private void patchResidualBossbar() {
        if (bossbarPatched) return;
        try {
            String overlayName = getConfig().getString("bossbar.residual-overlay", "NOTCHED_10");
            boolean darken = getConfig().getBoolean("bossbar.darken-at-max", true);
            // Reflect into GlitchItems' ResidualGlitchManager if present
            var itemsPlugin = Bukkit.getPluginManager().getPlugin("GlitchItems");
            if (itemsPlugin == null || !itemsPlugin.isEnabled()) return;
            // We don't have compile-time dep, so use reflection to poke config-driven? Instead, we directly edit ResidualGlitchManager.java file at build time.
            // For runtime, we just log that overlay config exists; actual patch is applied via file edit in next step.
            // This stub keeps the hook for future live patch without restart.
            bossbarPatched = true;
            if (getConfig().getBoolean("debug", false)) {
                getLogger().info("Bossbar patch probe: overlay=" + overlayName + " darken=" + darken);
            }
        } catch (Exception e) {
            if (getConfig().getBoolean("debug", false)) getLogger().warning("Bossbar patch failed: " + e.getMessage());
        }
    }
}
