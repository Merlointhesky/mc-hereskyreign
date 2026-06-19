package com.hereskyreign.hereskyreign.generator;

import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SkyReignChunkGenerator extends ChunkGenerator {

    private final HereSkyReignPlugin plugin;
    private final BlockData cloudBlockData;

    public SkyReignChunkGenerator(HereSkyReignPlugin plugin) {
        this.plugin = plugin;

        // The Cloud Block Trick: use Material.BROWN_MUSHROOM_BLOCK with all faces set to false
        MultipleFacing multipleFacing = (MultipleFacing) Bukkit.createBlockData(Material.BROWN_MUSHROOM_BLOCK);
        for (BlockFace face : multipleFacing.getAllowedFaces()) {
            multipleFacing.setFace(face, false);
        }
        this.cloudBlockData = multipleFacing;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        SimplexNoiseGenerator noise = new SimplexNoiseGenerator(worldInfo.getSeed());
        double scale = plugin.getConfig().getDouble("generation.island-noise-scale", 0.015);
        double threshold = plugin.getConfig().getDouble("generation.island-noise-threshold", 0.1);
        int minY = plugin.getConfig().getInt("generation.island-min-y", 60);
        int maxY = plugin.getConfig().getInt("generation.island-max-y", 64);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double realX = (chunkX * 16) + x;
                double realZ = (chunkZ * 16) + z;

                // Evaluate 2D simplex noise
                double noiseValue = noise.noise(realX * scale, realZ * scale);

                if (noiseValue > threshold) {
                    // Generate flat cloud block plane
                    for (int y = minY; y <= maxY; y++) {
                        chunkData.setBlock(x, y, z, cloudBlockData);
                    }
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new SkyReignBiomeProvider();
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull org.bukkit.World world) {
        return Collections.singletonList(new SkyReignBlockPopulator(plugin));
    }
}
