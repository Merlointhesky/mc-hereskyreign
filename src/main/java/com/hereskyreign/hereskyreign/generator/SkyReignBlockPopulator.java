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

                // Get coordinates relative to the chunk origin
                int originX = cx * 16 + chunkRandom.nextInt(16);
                int originZ = cz * 16 + chunkRandom.nextInt(16);

                // Verify there is actually a cloud island underneath this structure origin
                double originNoise = noise.noise(originX * scale, originZ * scale);
                if (originNoise <= threshold) {
                    continue; // Skip, since the origin would float in empty air
                }

                if (spawnColiseum) {
                    // Spawn single Coliseum
                    placeHouse(limitedRegion, chunkRandom, originX, surfaceY, originZ, scale, threshold, noise, "coliseum");
                } else {
                    // Spawn a Multi-Building Village around a central Well and Bell focal point
                    placeWell(limitedRegion, originX, surfaceY, originZ);
                    
                    // Spawn 3 central villagers around the well
                    for (int i = 0; i < 3; i++) {
                        int vx = originX + chunkRandom.nextInt(5) - 2;
                        int vz = originZ + chunkRandom.nextInt(5) - 2;
                        int vy = surfaceY + 1;
                        if (vx == originX && vz == originZ) {
                            vx += 1; // Don't spawn directly in the water center
                        }
                        
                        org.bukkit.Location loc = new org.bukkit.Location(null, vx, vy, vz);
                        if (limitedRegion.isInRegion(vx, vy, vz)) {
                            try {
                                org.bukkit.entity.Villager villager = limitedRegion.createEntity(loc, org.bukkit.entity.Villager.class);
                                if (villager != null) {
                                    org.bukkit.entity.Villager.Profession[] professions = org.bukkit.entity.Villager.Profession.values();
                                    villager.setProfession(professions[chunkRandom.nextInt(professions.length)]);
                                    villager.setVillagerLevel(chunkRandom.nextInt(3) + 1);
                                    villager.setCustomName("§bSky Villager");
                                    villager.setCustomNameVisible(true);
                                }
                            } catch (Exception e) {
                                // Suppress
                            }
                        }
                    }
                    
                    // Conforming village houses offsets (within safe limitedRegion bounds)
                    int[][] offsets = {
                        {13, -1},  // East
                        {-13, 1},  // West
                        {1, 13},   // South
                        {-1, -13}  // North
                    };
                    
                    String[] blueprints = {"village_house", "white_village_house"};
                    
                    for (int[] offset : offsets) {
                        int hx = originX + offset[0];
                        int hz = originZ + offset[1];
                        
                        // Noise check at each target house position to ensure they generate on solid clouds
                        if (noise.noise(hx * scale, hz * scale) > threshold) {
                            String bpName = blueprints[chunkRandom.nextInt(blueprints.length)];
                            placeHouse(limitedRegion, chunkRandom, hx, surfaceY, hz, scale, threshold, noise, bpName);
                            // Draw path from focal well to house
                            placePath(limitedRegion, originX, originZ, hx, hz, surfaceY - 1);
                        }
                    }
                }
            }
        }
    }

    private void placeWell(LimitedRegion limitedRegion, int x, int y, int z) {
        // Base cobblestone ring with water well center
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    // Water source in center
                    setBlockIfInRegion(limitedRegion, x + dx, y, z + dz, "minecraft:water");
                    // Dig hole down for water depth
                    setBlockIfInRegion(limitedRegion, x + dx, y - 1, z + dz, "minecraft:water");
                    setBlockIfInRegion(limitedRegion, x + dx, y - 2, z + dz, "minecraft:cobblestone");
                } else {
                    setBlockIfInRegion(limitedRegion, x + dx, y, z + dz, "minecraft:cobblestone");
                    // Place wall or fence posts at corners
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                        setBlockIfInRegion(limitedRegion, x + dx, y + 1, z + dz, "minecraft:oak_fence");
                        setBlockIfInRegion(limitedRegion, x + dx, y + 2, z + dz, "minecraft:oak_fence");
                    }
                }
            }
        }
        
        // Roof slabs
        setBlockIfInRegion(limitedRegion, x - 1, y + 3, z - 1, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x + 1, y + 3, z - 1, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x - 1, y + 3, z + 1, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x + 1, y + 3, z + 1, "minecraft:oak_slab[type=bottom]");
        
        setBlockIfInRegion(limitedRegion, x, y + 3, z - 1, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x, y + 3, z + 1, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x - 1, y + 3, z, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x + 1, y + 3, z, "minecraft:oak_slab[type=bottom]");
        setBlockIfInRegion(limitedRegion, x, y + 3, z, "minecraft:oak_slab[type=bottom]");
        
        // A ceiling bell hanging from the center roof
        setBlockIfInRegion(limitedRegion, x, y + 2, z, "minecraft:bell[attachment=ceiling,facing=north]");
    }

    private void placeHouse(LimitedRegion limitedRegion, Random random, int originX, int surfaceY, int originZ, double scale, double threshold, SimplexNoiseGenerator noise, String bpName) {
        // Roll rotation and mirror
        int rotIndex = random.nextInt(4);
        StructureRotation rotation = StructureRotation.values()[rotIndex];

        int mirrorIndex = random.nextInt(3);
        Mirror mirror = null;
        if (mirrorIndex == 1) {
            mirror = Mirror.LEFT_RIGHT;
        } else if (mirrorIndex == 2) {
            mirror = Mirror.FRONT_BACK;
        }

        // Load blueprint from HereEngy (uses our public loadBlueprint API)
        Blueprint bp = getOrLoadBlueprint(bpName);
        if (bp == null) {
            return;
        }

        // Apply mirror first, then rotation (mathematically correct order for absolute world-axis operations)
        Blueprint transformed = bp;
        if (mirror != null) {
            transformed = com.hereengy.hereengy.blueprint.BlueprintManager.mirrorBlueprint(transformed, mirror);
        }
        if (rotation != StructureRotation.NONE) {
            transformed = com.hereengy.hereengy.blueprint.BlueprintManager.rotateBlueprint(transformed, rotation);
        }

        // Paste blocks only within the loaded region
        for (BlueprintBlock block : transformed.getBlocks()) {
            int bx = originX + block.getX();
            int by = surfaceY + block.getY();
            int bz = originZ + block.getZ();
            setBlockIfInRegion(limitedRegion, bx, by, bz, block.getBlockData());
        }

        // Spawn a resident villager inside the house (at the floor level)
        if (bpName.equals("village_house") || bpName.equals("white_village_house")) {
            int vx = originX;
            int vz = originZ;
            int vy = surfaceY + 1;
            org.bukkit.Location loc = new org.bukkit.Location(null, vx, vy, vz);
            if (limitedRegion.isInRegion(vx, vy, vz)) {
                try {
                    org.bukkit.entity.Villager villager = limitedRegion.createEntity(loc, org.bukkit.entity.Villager.class);
                    if (villager != null) {
                        org.bukkit.entity.Villager.Profession[] professions = org.bukkit.entity.Villager.Profession.values();
                        villager.setProfession(professions[random.nextInt(professions.length)]);
                        villager.setVillagerLevel(random.nextInt(3) + 1);
                        villager.setCustomName("§bSky Villager");
                        villager.setCustomNameVisible(true);
                    }
                } catch (Exception e) {
                    // Suppress
                }
            }
        }
    }

    private void placePath(LimitedRegion limitedRegion, int x1, int z1, int x2, int z2, int y) {
        // Simple Bresenham's line algorithm to lay gravel/path block connections
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;
        
        int x = x1;
        int z = z1;
        
        while (true) {
            // Place path block, skipping immediate start and ends
            if ((x != x1 || z != z1) && (x != x2 || z != z2)) {
                setBlockIfInRegion(limitedRegion, x, y, z, "minecraft:dirt_path");
            }
            
            if (x == x2 && z == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                z += sz;
            }
        }
    }

    private void setBlockIfInRegion(LimitedRegion limitedRegion, int x, int y, int z, String blockDataStr) {
        if (limitedRegion.isInRegion(x, y, z)) {
            try {
                limitedRegion.setBlockData(x, y, z, Bukkit.createBlockData(blockDataStr));
            } catch (Exception e) {
                // Suppress placement failures
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
