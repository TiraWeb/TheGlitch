package com.theglitch.glitchinsurance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        if (getCommand("insureui") != null) {
            getCommand("insureui").setExecutor(new InsuranceUICommand(this));
        }

        com.theglitch.glitchinsurance.ui.InsurancePanel.init(this);

        getLogger().info("GlitchInsurance enabled — premium=" + manager.getPremiumPerItem()
                + ", max=" + manager.getMaxInsuredItems()
                + ", claimWindow=" + manager.getClaimWindowSeconds() + "s, cooldown=" + manager.getCooldownSeconds() + "s");
    }

    @Override
    public void onDisable() {
        com.theglitch.glitchinsurance.ui.InsurancePanel.shutdown();
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
        com.theglitch.glitchinsurance.ui.InsurancePanel.rebuild();
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

    public InsuranceManager getManager() {
        return manager;
    }

    public boolean uiBuy(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            player.sendMessage(getComponent("hold-item"));
            return false;
        }
        InsuranceManager.InsureResult result;
        try {
            result = manager.insureItem(player, held);
        } catch (Throwable t) {
            getLogger().warning("uiBuy failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
            return false;
        }
        switch (result) {
            case SUCCESS -> {
                String itemName = uiDisplayName(held);
                int count = manager.countInsured(player.getUniqueId());
                player.sendMessage(getComponent("insured",
                        "<item>", itemName,
                        "<premium>", String.valueOf(manager.getPremiumPerItem()),
                        "<count>", String.valueOf(count),
                        "<max>", String.valueOf(manager.getMaxInsuredItems())));
                return true;
            }
            case ALREADY_INSURED -> player.sendMessage(getComponent("already-insured"));
            case MAX_REACHED -> player.sendMessage(getComponent("max-reached",
                    "<max>", String.valueOf(manager.getMaxInsuredItems())));
            case NOT_ENOUGH_SHARDS -> player.sendMessage(getComponent("not-enough-shards",
                    "<premium>", String.valueOf(manager.getPremiumPerItem())));
            case COOLDOWN -> player.sendMessage(getComponent("cooldown",
                    "<seconds>", String.valueOf(manager.getCooldownRemaining(player.getUniqueId()))));
            case AIR -> player.sendMessage(getComponent("hold-item"));
            case NO_ECONOMY -> player.sendMessage(Component.text("Economy unavailable — try again later.", NamedTextColor.RED));
        }
        return false;
    }

    public boolean uiClaim(Player player, int index) {
        try {
            var snapshot = manager.getInsured(player.getUniqueId());
            if (index < 0 || index >= snapshot.size()) {
                player.sendMessage(getComponent("no-insurance"));
                return false;
            }
        } catch (Throwable t) {
            getLogger().warning("uiClaim snapshot failed: " + t.getClass().getSimpleName());
            player.sendMessage(getComponent("no-insurance"));
            return false;
        }
        ItemStack claimedItem;
        try {
            claimedItem = manager.claimOrdinal(player.getUniqueId(), index);
        } catch (Throwable t) {
            getLogger().warning("uiClaim failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            player.sendMessage(getComponent("no-insurance"));
            return false;
        }
        if (claimedItem == null) {
            player.sendMessage(getComponent("no-insurance"));
            return false;
        }
        var leftover = player.getInventory().addItem(claimedItem);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                player.sendMessage(getComponent("inventory-full"));
            }
        }
        player.sendMessage(getComponent("claimed", "<count>", String.valueOf(1)));
        return true;
    }

    private static String uiDisplayName(ItemStack stack) {
        if (stack == null) return "AIR";
        try {
            var meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                var comp = meta.displayName();
                if (comp != null) {
                    return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
                }
            }
        } catch (Throwable ignored) {
        }
        return stack.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    public static MiniMessage mm() {
        return MM;
    }

    public static GlitchInsurance getInstance() {
        return instance;
    }
}
