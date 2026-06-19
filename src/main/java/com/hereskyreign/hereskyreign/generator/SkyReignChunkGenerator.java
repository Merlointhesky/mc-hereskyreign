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

        BlockData dirt = Bukkit.createBlockData(Material.DIRT);
        BlockData grass = Bukkit.createBlockData(Material.GRASS_BLOCK);

        // Adjust threshold for inset land
        double landThreshold = threshold + 0.03;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double realX = (chunkX * 16) + x;
                double realZ = (chunkZ * 16) + z;

                // Evaluate main 2D simplex noise
                double noiseValue = noise.noise(realX * scale, realZ * scale);
                // Evaluate high-frequency fluff noise for cloud edges
                double fluffNoise = noise.noise(realX * 0.15, realZ * 0.15);

                boolean hasLand = noiseValue > landThreshold;

                // Cloud placement checks (flared and fluffy)
                boolean hasCloudY60 = noiseValue > threshold;
                boolean hasCloudY59 = (noiseValue > (threshold - 0.04)) || (noiseValue > (threshold - 0.08) && fluffNoise > 0.0);
                boolean hasCloudY58 = (noiseValue > (threshold - 0.08)) || (noiseValue > (threshold - 0.12) && fluffNoise > 0.0);
                boolean hasCloudY57 = (noiseValue > (threshold - 0.08)) && fluffNoise > 0.3; // Hanging fluff clumps

                // 1. Generate Land (Grass at maxY, Dirt from minY + 1 to maxY - 1)
                if (hasLand) {
                    chunkData.setBlock(x, maxY, z, grass);
                    for (int y = minY + 1; y < maxY; y++) {
                        chunkData.setBlock(x, y, z, dirt);
                    }
                }

                // 2. Generate Cloud Layer (3 rows + fluff underneath)
                if (hasCloudY60) {
                    chunkData.setBlock(x, minY, z, cloudBlockData); // Cloud Top (Y=60)
                }
                if (hasCloudY59) {
                    chunkData.setBlock(x, minY - 1, z, cloudBlockData); // Cloud Middle (Y=59)
                }
                if (hasCloudY58) {
                    chunkData.setBlock(x, minY - 2, z, cloudBlockData); // Cloud Bottom (Y=58)
                }
                if (hasCloudY57) {
                    chunkData.setBlock(x, minY - 3, z, cloudBlockData); // Cloud Fluff (Y=57)
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
        return true;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return true;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false; // Disable vanilla structures to prevent mineshafts in the void
    }

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull org.bukkit.World world) {
        return Collections.singletonList(new SkyReignBlockPopulator(plugin));
    }
}
