package com.theglitch.glitchraid;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
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
 * juggle two party systems. MythicDungeons (PartyPlugin: Default) reads a player's
 * party through {@code MythicPlayer.getDungeonParty()}: its own MythicParty when the
 * player used MD's /party (rerouted to /raid by {@link PartyCommandAlias}), otherwise
 * the {@code IDungeonParty} we attach to every online member here. Play, queue,
 * ready check and leader-only start all go through that interface.
 *
 * MythicDungeons is a licensed jar that is not on the build classpath, so the
 * interface is implemented with a {@link Proxy} and the calls are method handles
 * (plain reflection on its main class fails: it links Citizens, which isn't
 * installed). If MythicDungeons is missing or changes, the bridge stays inactive.
 */
public final class DungeonPartyBridge implements Listener {

    private static final String API = "net.playavalon.mythicdungeons.api.party.IDungeonParty";

    private final GlitchRaid plugin;
    private final PartyManager parties;
    private final Map<UUID, Object> proxies = new ConcurrentHashMap<>(); // party leader -> IDungeonParty proxy
    private Class<?> partyInterface;
    private MethodHandle inst;
    private MethodHandle getMythicPlayer;
    private MethodHandle setDungeonParty;
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
            Class<?> md = Class.forName("net.playavalon.mythicdungeons.MythicDungeons", false, cl);
            Class<?> mythicPlayer = Class.forName("net.playavalon.mythicdungeons.player.MythicPlayer", false, cl);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            inst = lookup.findStatic(md, "inst", MethodType.methodType(md));
            getMythicPlayer = lookup.findVirtual(md, "getMythicPlayer", MethodType.methodType(mythicPlayer, Player.class));
            setDungeonParty = lookup.findVirtual(mythicPlayer, "setDungeonParty", MethodType.methodType(void.class, partyInterface));
            active = true;
            for (Party party : parties.getAllParties()) sync(party, List.of());
            plugin.getLogger().info("MythicDungeons party bridge active — dungeons use /party.");
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
            for (UUID id : party.getMembers()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) setParty(p, proxy);
            }
        } catch (Throwable e) {
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

    private void setParty(Player player, Object party) throws Throwable {
        Object mythicPlayer = getMythicPlayer.invoke(inst.invoke(), player);
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
