package com.theglitch.glitchworldgen;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Generator for the imported Red Zone maps. Multiverse worlds.yml sets
 * {@code generator: GlitchWorldGen} on each red world; every chunk that is
 * NOT part of the imported map (i.e. anything the server would otherwise
 * fill with vanilla terrain) is generated as empty void in the_void biome,
 * with a full-height barrier wall on any side facing a map chunk — so the
 * map's own edge is the playable edge and nothing new ever appears past it.
 *
 * The map footprint comes from {@code plugins/GlitchWorldGen/<world>.keep}
 * ("x z" chunk coords per line), written by scripts/map-footprint.py from the
 * chunks carrying blending_data (= chunks from the imported save). Without a
 * .keep file the world just gets plain void.
 */
public final class GlitchWorldGen extends JavaPlugin {

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new MapEdgeGenerator(this, worldName);
    }
}
