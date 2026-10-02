package com.theglitch.glitchtutorial;

import com.theglitch.common.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One private tutorial world per player, like MythicDungeons instances: the template world
 * ({@code world} in config, built by scripts/setup-tutorial.sh) is copied on disk to
 * {@code tutorial_<n>}, loaded with GlitchWorldGen (void outside the slice), and unloaded +
 * deleted once the player leaves it — nothing from a run is kept. Gamerules, border and
 * weather come with the copy; the WorldGuard flags are copied from the template's regions file.
 */
final class TutorialInstances {

    private final GlitchTutorial plugin;
    /** player -> instance world name */
    private final Map<UUID, String> byPlayer = new ConcurrentHashMap<>();
    /** players whose instance is being copied right now -> callbacks waiting for it */
    private final Map<UUID, java.util.List<Consumer<World>>> pending = new HashMap<>();
    private final Set<String> reserved = ConcurrentHashMap.newKeySet();
    /** Echo etc.: called right after an instance loads / right before it unloads. */
    private Consumer<World> onOpen = w -> { };
    private Consumer<World> onClose = w -> { };

    void hooks(Consumer<World> onOpen, Consumer<World> onClose) {
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    TutorialInstances(GlitchTutorial plugin) {
        this.plugin = plugin;
    }

    String template() {
        return plugin.getConfig().getString("world", Worlds.TUTORIAL);
    }

    boolean isInstance(String world) {
        return world != null && world.matches(java.util.regex.Pattern.quote(template()) + "_[0-9]+");
    }

    /** The template or any instance. */
    boolean isTutorialWorld(String world) {
        return world != null && (world.equals(template()) || isInstance(world));
    }

    /** The player's loaded instance, or null. */
    World of(UUID player) {
        String name = byPlayer.get(player);
        return name == null ? null : Bukkit.getWorld(name);
    }

    UUID ownerOf(String world) {
        for (Map.Entry<UUID, String> e : byPlayer.entrySet()) {
            if (e.getValue().equals(world)) return e.getKey();
        }
        return null;
    }

    int count() {
        return byPlayer.size();
    }

    /** Folder holding the dimension folders ({@code <level>/dimensions/minecraft} on 26.x). */
    private File root() {
        World t = Bukkit.getWorld(template());
        if (t != null && new File(t.getWorldFolder(), "region").isDirectory()) return t.getWorldFolder().getParentFile();
        String level = Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
        return new File(Bukkit.getWorldContainer(), level + "/dimensions/minecraft");
    }

    private File wgFolder(String world) {
        return new File(plugin.getDataFolder().getParentFile(), "WorldGuard/worlds/" + world);
    }

    boolean templateReady() {
        return new File(new File(root(), template()), "region").isDirectory();
    }

    /** Gets (or builds) the player's instance, then runs {@code ready} on the main thread (null = failed). */
    void open(Player p, Consumer<World> ready) {
        open(p.getUniqueId(), p.getName(), ready);
    }

    void open(UUID id, String who, Consumer<World> ready) {
        World have = of(id);
        if (have != null) {
            ready.accept(have);
            return;
        }
        synchronized (pending) {
            java.util.List<Consumer<World>> waiting = pending.get(id);
            if (waiting != null) { // already copying — just wait for it too
                waiting.add(ready);
                return;
            }
            pending.put(id, new java.util.ArrayList<>(java.util.List.of(ready)));
        }
        int max = plugin.getConfig().getInt("max-instances", 30);
        if (byPlayer.size() >= max) {
            plugin.getLogger().warning("Tutorial instance limit reached (" + max + ")");
            done(id, null);
            return;
        }
        File root = root();
        File src = new File(root, template());
        String name = freeName(root);
        File dst = new File(root, name);
        File wgSrc = new File(wgFolder(template()), "regions.yml");
        File wgDst = new File(wgFolder(name), "regions.yml");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok;
            try {
                copyTree(src.toPath(), dst.toPath());
                if (wgSrc.isFile()) {
                    wgDst.getParentFile().mkdirs();
                    Files.copy(wgSrc.toPath(), wgDst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                ok = true;
            } catch (IOException e) {
                plugin.getLogger().warning("Couldn't copy the tutorial world to " + name + ": " + e);
                ok = false;
            }
            boolean copied = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                World w = null;
                if (copied) {
                    try {
                        w = new WorldCreator(name).generator("GlitchWorldGen").generateStructures(false).createWorld();
                    } catch (Exception e) {
                        plugin.getLogger().warning("Couldn't load tutorial instance " + name + ": " + e);
                    }
                }
                if (w == null) {
                    reserved.remove(name);
                    deleteLater(name);
                    done(id, null);
                    return;
                }
                w.setAutoSave(false); // thrown away afterwards — never worth a disk write
                byPlayer.put(id, name);
                reserved.remove(name);
                plugin.getLogger().info("Tutorial instance " + name + " opened for " + who + " (" + byPlayer.size() + " running)");
                try {
                    onOpen.accept(w);
                } catch (Exception e) {
                    plugin.getLogger().warning("Tutorial instance setup failed: " + e);
                }
                done(id, w);
            });
        });
    }

    private void done(UUID id, World w) {
        java.util.List<Consumer<World>> waiting;
        synchronized (pending) {
            waiting = pending.remove(id);
        }
        if (waiting != null) for (Consumer<World> c : waiting) c.accept(w);
    }

    private String freeName(File root) {
        for (int n = 1; ; n++) {
            String name = template() + "_" + n;
            if (Bukkit.getWorld(name) == null && !new File(root, name).exists() && reserved.add(name)) return name;
        }
    }

    /** Unloads and deletes the player's instance (anyone still inside goes to the hub first). */
    void close(UUID player) {
        close(player, 0);
    }

    private void close(UUID player, int attempt) {
        String name = byPlayer.get(player);
        if (name == null) return;
        World w = Bukkit.getWorld(name);
        if (w != null) {
            Location hub = hubSpawn();
            for (Player in : w.getPlayers()) {
                if (hub != null) in.teleport(hub);
            }
            try {
                onClose.accept(w);
            } catch (Exception ignored) {
            }
            if (!Bukkit.unloadWorld(w, false)) {
                // a player mid-teleport is still in it — retry shortly
                if (attempt < 15) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> close(player, attempt + 1), 40L);
                } else {
                    plugin.getLogger().warning("Tutorial instance " + name + " won't unload; it gets deleted on the next restart");
                    byPlayer.remove(player);
                }
                return;
            }
        }
        byPlayer.remove(player);
        plugin.getLogger().info("Tutorial instance " + name + " closed (" + byPlayer.size() + " running)");
        deleteLater(name);
    }

    /** Server stop / plugin disable: close everything synchronously. */
    void closeAll() {
        for (UUID id : new java.util.ArrayList<>(byPlayer.keySet())) {
            String name = byPlayer.remove(id);
            World w = name == null ? null : Bukkit.getWorld(name);
            if (w != null) {
                Location hub = hubSpawn();
                for (Player in : w.getPlayers()) {
                    if (hub != null) in.teleport(hub);
                }
                try {
                    onClose.accept(w);
                } catch (Exception ignored) {
                }
                Bukkit.unloadWorld(w, false);
            }
            if (name != null) deleteNow(name);
        }
    }

    /** Startup: delete instance folders left behind by a crash. */
    void deleteLeftovers() {
        File[] dirs = root().listFiles(File::isDirectory);
        if (dirs == null) return;
        int n = 0;
        for (File d : dirs) {
            if (isInstance(d.getName()) && Bukkit.getWorld(d.getName()) == null) {
                deleteNow(d.getName());
                n++;
            }
        }
        if (n > 0) plugin.getLogger().info("Deleted " + n + " leftover tutorial instance(s)");
    }

    private void deleteLater(String name) {
        // WorldGuard / the region writer can still be flushing for a moment after the unload
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> deleteNow(name), 100L);
    }

    private void deleteNow(String name) {
        if (!isInstance(name)) return; // never the template or anything else
        try {
            deleteTree(new File(root(), name).toPath());
            deleteTree(wgFolder(name).toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't delete tutorial instance " + name + ": " + e);
        }
    }

    private Location hubSpawn() {
        World hub = Bukkit.getWorld("hub");
        return hub == null ? null : hub.getSpawnLocation();
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String n = file.getFileName().toString();
                if (n.equals("session.lock") || n.equals("chunk_tickets.dat")) return FileVisitResult.CONTINUE;
                Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
