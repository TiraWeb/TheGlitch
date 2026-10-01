package com.theglitch.glitchraid;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes the GlitchRaid party THE party for MythicDungeons too, so players never
 * juggle two party systems. MythicDungeons supports a custom provider: with
 * {@code General.PartyPlugin: GlitchRaid} in its config, every party member's
 * MythicPlayer gets an {@code IDungeonParty} that we implement here.
 *
 * MythicDungeons is a licensed jar that is not on the build classpath, so the
 * interface is implemented with a {@link Proxy} and everything else is
 * reflection. If MythicDungeons is missing, too old, or set to another party
 * plugin, the bridge stays inactive and nothing else changes.
 */
public final class DungeonPartyBridge implements Listener {

    private static final String API = "net.playavalon.mythicdungeons.api.party.IDungeonParty";

    private final GlitchRaid plugin;
    private final PartyManager parties;
    private final Map<UUID, Object> proxies = new ConcurrentHashMap<>(); // party leader -> IDungeonParty proxy
    private Class<?> partyInterface;
    private Method inst;
    private Method getMythicPlayer;
    private Method setDungeonParty;
    private Method initDungeonParty;
    private boolean active;

    public DungeonPartyBridge(GlitchRaid plugin, PartyManager parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    /** Resolves the MythicDungeons API; call once MythicDungeons has enabled. */
    public void enable() {
        if (Bukkit.getPluginManager().getPlugin("MythicDungeons") == null) return;
        try {
            ClassLoader cl = Bukkit.getPluginManager().getPlugin("MythicDungeons").getClass().getClassLoader();
            partyInterface = Class.forName(API, true, cl);
            Class<?> md = Class.forName("net.playavalon.mythicdungeons.MythicDungeons", true, cl);
            inst = md.getMethod("inst");
            getMythicPlayer = md.getMethod("getMythicPlayer", Player.class);
            Class<?> mythicPlayer = getMythicPlayer.getReturnType();
            setDungeonParty = mythicPlayer.getMethod("setDungeonParty", partyInterface);
            initDungeonParty = partyInterface.getMethod("initDungeonParty", org.bukkit.plugin.Plugin.class);
            String configured = String.valueOf(md.getMethod("getPartyPluginName").invoke(inst.invoke(null)));
            if (!configured.equalsIgnoreCase(plugin.getName())) {
                plugin.getLogger().warning("MythicDungeons uses party plugin '" + configured
                        + "' — set General.PartyPlugin: " + plugin.getName() + " so dungeons use raid parties.");
                return;
            }
            active = true;
            for (Party party : parties.getAllParties()) sync(party, List.of());
            plugin.getLogger().info("MythicDungeons party bridge active — dungeons use raid parties.");
        } catch (ReflectiveOperationException | LinkageError e) {
            plugin.getLogger().warning("MythicDungeons party bridge unavailable: " + e);
        }
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Pushes a party's current membership to MythicDungeons. {@code removed} are
     * players who just left (or whose party was disbanded) and lose their dungeon party.
     */
    public void sync(Party party, Collection<UUID> removed) {
        if (!active) return;
        try {
            for (UUID id : removed) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) setParty(p, null);
            }
            if (party == null) return;
            if (!parties.isLeader(party.getLeader()) || parties.getPartyAsLeader(party.getLeader()) != party) {
                proxies.remove(party.getLeader()); // disbanded
                return;
            }
            Object proxy = proxies.computeIfAbsent(party.getLeader(), id -> newProxy(id));
            initDungeonParty.invoke(proxy, plugin);
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Dungeon party sync failed: " + e);
        }
    }

    /** MythicDungeons builds a fresh MythicPlayer on every join — re-attach the party. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!active) return;
        UUID id = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Party party = parties.getParty(id);
            if (party != null) sync(party, List.of());
        }, 10L);
    }

    private void setParty(Player player, Object party) throws ReflectiveOperationException {
        Object mythicPlayer = getMythicPlayer.invoke(inst.invoke(null), player);
        if (mythicPlayer != null) setDungeonParty.invoke(mythicPlayer, party);
    }

    private Object newProxy(UUID leader) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "getPlayers": {
                        List<Player> online = new ArrayList<>();
                        Party party = parties.getPartyAsLeader(leader);
                        if (party != null) {
                            for (UUID id : party.getMembers()) {
                                Player p = Bukkit.getPlayer(id);
                                if (p != null) online.add(p);
                            }
                        }
                        return online;
                    }
                    case "getLeader": {
                        OfflinePlayer l = Bukkit.getOfflinePlayer(leader);
                        return l;
                    }
                    // Membership is owned by /party — MythicDungeons leaving or joining
                    // an instance must not change who is in the party.
                    case "addPlayer":
                    case "removePlayer":
                        return null;
                    case "equals":
                        return proxy == args[0];
                    case "hashCode":
                        return System.identityHashCode(proxy);
                    case "toString":
                        return "GlitchRaidParty[" + leader + "]";
                    default:
                        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                        return null;
                }
            }
        };
        return Proxy.newProxyInstance(partyInterface.getClassLoader(), new Class<?>[]{partyInterface}, handler);
    }
}
