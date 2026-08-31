package com.theglitch.glitchstash.extract;

/**
 * One validated extraction spot for a dynamic cycle. Shared between
 * SpotPicker, DynamicExtractionManager and ExtractionMarkers.
 *
 * @param arenaId        VelKoth arena id serving this point
 * @param world          world name
 * @param x              capture center X (block)
 * @param y              first air block above ground (block)
 * @param z              capture center Z (block)
 * @param radiusBlocks   capture square half-width in blocks
 * @param openUntilEpochMs when the extraction window closes
 * @param index          slot index within the cycle (0..N-1), stable per arena-prefix
 */
public record ExtractionPoint(String arenaId, String world, int x, int y, int z, int radiusBlocks, long openUntilEpochMs, int index) {}
