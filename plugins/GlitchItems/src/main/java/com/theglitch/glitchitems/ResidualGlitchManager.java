package com.theglitch.glitchitems;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ResidualGlitchManager {

    private final GlitchItems plugin;
    private final NamespacedKey stacksKey;
    private final NamespacedKey lastKey;

    public ResidualGlitchManager(GlitchItems plugin) {
        this.plugin = plugin;
        this.stacksKey = new NamespacedKey(plugin, "glitch_stacks");
        this.lastKey = new NamespacedKey(plugin, "glitch_last");
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isEnabledWorld(player.getWorld().getName())) continue;
            long now = System.currentTimeMillis();
            long last = getLast(player);
            if (last == 0L) {
                setLast(player, now);
                sendActionBar(player, getStacks(player));
                continue;
            }
            int due = (int) ((now - last) / (intervalMinutes() * 60_000L));
            if (due > 0) {
                int oldStacks = getStacks(player);
                int stacks = Math.min(oldStacks + due, maxStacks());
                setStacks(player, stacks);
                setLast(player, now);
                if (oldStacks < eliteHuntStacks() && stacks >= eliteHuntStacks()) {
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<dark_red>You have " + stacks + " stacks of Residual Glitch — something elite is hunting you.</dark_red>"));
                }
            }
            sendActionBar(player, getStacks(player));
        }
    }

    public int getStacks(Player player) {
        return player.getPersistentDataContainer().getOrDefault(stacksKey, PersistentDataType.INTEGER, 0);
    }

    public void setStacks(Player player, int stacks) {
        player.getPersistentDataContainer().set(stacksKey, PersistentDataType.INTEGER, stacks);
    }

    private long getLast(Player player) {
        return player.getPersistentDataContainer().getOrDefault(lastKey, PersistentDataType.LONG, 0L);
    }

    private void setLast(Player player, long time) {
        player.getPersistentDataContainer().set(lastKey, PersistentDataType.LONG, time);
    }

    public void clear(Player player) {
        setStacks(player, 0);
        setLast(player, 0L);
    }

    public double getPayoutMultiplier(Player player) {
        return 1.0 + getStacks(player) * payoutPerStack();
    }

    public double getDamageTakenMultiplier(Player player) {
        return 1.0 + getStacks(player) * damageTakenPerStack() / 100.0;
    }

    public int lootLuckBonus(Player player) {
        return getStacks(player) * lootLuckPerStack();
    }

    private void sendActionBar(Player player, int stacks) {
        String template = plugin.getConfig().getString("residual-glitch.actionbar",
                "<dark_red>Residual Glitch: <red>{stacks}/{max}</red>");
        String message = template
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{max}", String.valueOf(maxStacks()))
                .replace("{dmg}", String.valueOf(stacks * damageTakenPerStack()))
                .replace("{payout}", String.valueOf((int) (stacks * payoutPerStack() * 100)));
        player.sendActionBar(MiniMessage.miniMessage().deserialize(message));
    }

    private boolean isEnabledWorld(String world) {
        return enabledWorlds().contains(world);
    }

    private List<String> enabledWorlds() {
        return plugin.getConfig().getStringList("residual-glitch.enabled-worlds");
    }

    private int intervalMinutes() {
        return plugin.getConfig().getInt("residual-glitch.stack-interval-minutes", 5);
    }

    private int maxStacks() {
        return plugin.getConfig().getInt("residual-glitch.max-stacks", 8);
    }

    private int damageTakenPerStack() {
        return plugin.getConfig().getInt("residual-glitch.damage-taken-per-stack", 5);
    }

    private int lootLuckPerStack() {
        return plugin.getConfig().getInt("residual-glitch.loot-luck-per-stack", 5);
    }

    private int eliteHuntStacks() {
        return plugin.getConfig().getInt("residual-glitch.elite-hunt-stacks", 5);
    }

    private double payoutPerStack() {
        return plugin.getConfig().getDouble("residual-glitch.payout-per-stack", 0.10);
    }
}
