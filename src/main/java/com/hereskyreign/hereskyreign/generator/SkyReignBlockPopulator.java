package com.hereskyreign.hereskyreign.generator;

import com.hereengy.hereengy.HereEngyPlugin;
import com.hereengy.hereengy.blueprint.Blueprint;
import com.hereengy.hereengy.blueprint.BlueprintBlock;
import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import org.bukkit.Bukkit;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class SkyReignBlockPopulator extends BlockPopulator {

    private final HereSkyReignPlugin plugin;
    private final Map<String, Blueprint> blueprintCache = new ConcurrentHashMap<>();

    public SkyReignBlockPopulator(HereSkyReignPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void populate(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull LimitedRegion limitedRegion) {
        double coliseumChance = plugin.getConfig().getDouble("structures.coliseum-chance", 0.002);
        double villageChance = plugin.getConfig().getDouble("structures.village-chance", 0.005);
        int surfaceY = plugin.getConfig().getInt("generation.island-max-y", 64) + 1;

        double scale = plugin.getConfig().getDouble("generation.island-noise-scale", 0.015);
        double threshold = plugin.getConfig().getDouble("generation.island-noise-threshold", 0.1);
        SimplexNoiseGenerator noise = new SimplexNoiseGenerator(worldInfo.getSeed());

        // Check a 5x5 chunk grid around the current chunk (radius = 2 chunks)
        // structures are placed relative to their origin chunk's deterministic random choices
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;

                // Deterministic seed based on chunk coordinates and world seed
                long seed = worldInfo.getSeed() ^ ((long) cx * 341873128712L + (long) cz * 132897987541L);
                Random chunkRandom = new Random(seed);

                // Determine structure type to spawn
                boolean spawnColiseum = chunkRandom.nextDouble() < coliseumChance;
                boolean spawnVillage = false;
                if (!spawnColiseum) {
                    spawnVillage = chunkRandom.nextDouble() < villageChance;
                }

                if (!spawnColiseum && !spawnVillage) {
                    continue;
                }

                String bpName = spawnColiseum ? "coliseum" : "sky_village";

                // Get coordinates relative to the chunk origin
                int originX = cx * 16 + chunkRandom.nextInt(16);
                int originZ = cz * 16 + chunkRandom.nextInt(16);

                // Verify there is actually a cloud island underneath this structure origin
                double originNoise = noise.noise(originX * scale, originZ * scale);
                if (originNoise <= threshold) {
                    continue; // Skip, since the origin would float in empty air
                }

                // Roll rotation and mirror
                int rotIndex = chunkRandom.nextInt(4);
                StructureRotation rotation = StructureRotation.values()[rotIndex];

                int mirrorIndex = chunkRandom.nextInt(3);
                Mirror mirror = null;
                if (mirrorIndex == 1) {
                    mirror = Mirror.LEFT_RIGHT;
                } else if (mirrorIndex == 2) {
                    mirror = Mirror.FRONT_BACK;
                }

                // Load blueprint from HereEngy (uses our public loadBlueprint API)
                Blueprint bp = getOrLoadBlueprint(bpName);
                if (bp == null) {
                    continue; // Warning already logged
                }

                // Apply rotation and mirroring
                Blueprint transformed = bp;
                if (rotation != StructureRotation.NONE) {
                    transformed = com.hereengy.hereengy.blueprint.BlueprintManager.rotateBlueprint(transformed, rotation);
                }
                if (mirror != null) {
                    transformed = com.hereengy.hereengy.blueprint.BlueprintManager.mirrorBlueprint(transformed, mirror);
                }

                // Paste blocks only within the loaded region
                for (BlueprintBlock block : transformed.getBlocks()) {
                    int bx = originX + block.getX();
                    int by = surfaceY + block.getY();
                    int bz = originZ + block.getZ();

                    if (limitedRegion.isInRegion(bx, by, bz)) {
                        try {
                            limitedRegion.setBlockData(bx, by, bz, Bukkit.createBlockData(block.getBlockData()));
                        } catch (Exception e) {
                            // Suppress placement failures if block data is unsupported/malformed
                        }
                    }
                }
            }
        }
    }

    private Blueprint getOrLoadBlueprint(String name) {
        return blueprintCache.computeIfAbsent(name.toLowerCase(), key -> {
            try {
                HereEngyPlugin engy = HereEngyPlugin.getInstance();
                if (engy != null && engy.getBlueprintManager() != null) {
                    Blueprint bp = engy.getBlueprintManager().loadBlueprint(key);
                    if (bp == null) {
                        plugin.getLogger().warning("Blueprint '" + key + "' was not found in HereEngy/blueprints folder!");
                    }
                    return bp;
                } else {
                    plugin.getLogger().warning("HereEngy plugin is not active or initialized yet!");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch blueprint '" + name + "': " + e.getMessage());
            }
            return null;
        });
    }
}
