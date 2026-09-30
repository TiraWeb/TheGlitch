package com.theglitch.glitchworldgen;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Void chunks + barrier walls on the faces that touch the imported map. See {@link GlitchWorldGen}. */
final class MapEdgeGenerator extends ChunkGenerator {

    private final Set<Long> map = new HashSet<>();

    MapEdgeGenerator(GlitchWorldGen plugin, String worldName) {
        Path file = plugin.getDataFolder().toPath().resolve(worldName + ".keep");
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file)) {
                    String[] p = line.trim().split("\s+");
                    if (p.length == 2) map.add(key(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
                }
            } catch (IOException | NumberFormatException e) {
                plugin.getLogger().warning("Could not read " + file + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info(worldName + ": map footprint " + map.size() + " chunks (void + edge walls outside it)");
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private boolean inMap(int x, int z) {
        return map.contains(key(x, z));
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
        if (map.isEmpty() || inMap(cx, cz)) return; // a missing map chunk stays void
        int y0 = info.getMinHeight(), y1 = info.getMaxHeight();
        if (inMap(cx - 1, cz)) data.setRegion(0, y0, 0, 1, y1, 16, Material.BARRIER);   // west face
        if (inMap(cx + 1, cz)) data.setRegion(15, y0, 0, 16, y1, 16, Material.BARRIER); // east face
        if (inMap(cx, cz - 1)) data.setRegion(0, y0, 0, 16, y1, 1, Material.BARRIER);   // north face
        if (inMap(cx, cz + 1)) data.setRegion(0, y0, 15, 16, y1, 16, Material.BARRIER); // south face
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo w, int x, int y, int z) {
                return Biome.THE_VOID;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo w) {
                return List.of(Biome.THE_VOID);
            }
        };
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
