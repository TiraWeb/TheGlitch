package com.theglitch.common;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crash-safe file writes for per-player data (stash, hideout, classes, insurance).
 * <p>
 * Content goes to a temp file in the target's directory and is then moved over the
 * target, atomically where the filesystem supports it. A crash mid-write leaves the
 * previous file intact instead of a truncated one.
 */
public final class AtomicFiles {

    private AtomicFiles() {
    }

    /** Atomically writes {@code yaml} to {@code target}; failures are logged, never thrown. */
    public static void save(YamlConfiguration yaml, Path target, Logger logger) {
        write(yaml.saveToString(), target, logger);
    }

    /** Atomically writes {@code content} (UTF-8) to {@code target}; failures are logged, never thrown. */
    public static void write(String content, Path target, Logger logger) {
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, target.getFileName().toString() + "-", ".tmp");
            try {
                Files.writeString(tmp, content);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to atomically save " + target, e);
        }
    }
}
