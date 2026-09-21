package com.theglitch.glitchitems;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Sweeps each chunk for orphaned Nexo furniture containers as it loads — see
 * {@link ContainerManager#sweepOrphans(org.bukkit.Chunk)} javadoc for why
 * these can exist despite {@link ContainerManager#clear} and
 * {@link ContainerManager#clearAll}. Self-healing: as players explore, every
 * chunk that loads gets checked once, so leftover furniture from before this
 * fix (or any future desync) is cleaned up without needing a full-map sweep.
 */
public record ContainerOrphanSweepListener(ContainerManager manager) implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        manager.sweepOrphans(event.getChunk());
    }
}
