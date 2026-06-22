package com.hereskyreign.hereskyreign.generator;

import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class SkyReignBlockPopulator extends BlockPopulator {

    private final HereSkyReignPlugin plugin;

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

                if (spawnColiseum) {
                    // Coliseum footprint: 30x30 centered at (originX, originZ)
                    // Check all four outer corners of the 30x30 footprint to prevent spilling over void (radius 15)
                    boolean fits = checkFootprintFits(noise, originX, originZ, 15, scale, threshold);
                    if (!fits) {
                        continue;
                    }

                    generateColiseum(limitedRegion, originX, surfaceY, originZ, chunkRandom);
                } else {
                    // Check if well center fits on the cloud
                    double originNoise = noise.noise(originX * scale, originZ * scale);
                    if (originNoise <= threshold) {
                        continue; // Skip
                    }

                    // Spawn a Multi-Building Village around a central Well and Bell focal point
                    placeWell(limitedRegion, originX, surfaceY, originZ);
                    
                    // Spawn villagers near the well on the main server thread safely after generation
                    if (cx == chunkX && cz == chunkZ) {
                        spawnVillagerDelayed(worldInfo.getName(), originX, surfaceY + 1, originZ, 3, chunkRandom);
                    }

                    // House placements and directions
                    for (int dir = 0; dir < 4; dir++) {
                        int hx, hz;
                        String doorFacing;
                        int pathStartX = originX, pathStartZ = originZ;
                        int pathEndX, pathEndZ;
                        boolean isCottage = (dir % 2 == 0); // Alternate house types
                        int size = isCottage ? 8 : 7;
                        int halfSize = size / 2; // For centering

                        if (dir == 0) { // East
                            hx = originX + 13;
                            hz = originZ - halfSize;
                            doorFacing = "west";
                            pathStartX = originX + 2;
                            pathEndX = hx - 1;
                            pathStartZ = originZ;
                            pathEndZ = originZ;
                        } else if (dir == 1) { // West
                            hx = originX - 13 - (size - 1);
                            hz = originZ - halfSize;
                            doorFacing = "east";
                            pathStartX = originX - 2;
                            pathEndX = hx + size;
                            pathStartZ = originZ;
                            pathEndZ = originZ;
                        } else if (dir == 2) { // South
                            hx = originX - halfSize;
                            hz = originZ + 13;
                            doorFacing = "north";
                            pathStartX = originX;
                            pathEndX = originX;
                            pathStartZ = originZ + 2;
                            pathEndZ = hz - 1;
                        } else { // North
                            hx = originX - halfSize;
                            hz = originZ - 13 - (size - 1);
                            doorFacing = "south";
                            pathStartX = originX;
                            pathEndX = originX;
                            pathStartZ = originZ - 2;
                            pathEndZ = hz + size;
                        }

                        // Check if house footprint fits on the cloud island (radius = halfSize + 1)
                        boolean houseFits = checkFootprintFits(noise, hx + halfSize, hz + halfSize, halfSize + 1, scale, threshold);
                        if (houseFits) {
                            // Draw straight path from well to the door step (2-3 blocks wide, cleared of grass)
                            drawStraightPath(limitedRegion, pathStartX, pathStartZ, pathEndX, pathEndZ, surfaceY - 1, chunkRandom);

                            if (isCottage) {
                                generateCottage(limitedRegion, hx, surfaceY, hz, doorFacing, chunkRandom);
                            } else {
                                generateStonebrickHouse(limitedRegion, hx, surfaceY, hz, doorFacing, chunkRandom);
                            }

                            // Spawn resident villager inside the house
                            int vx = hx + halfSize;
                            int vz = hz + halfSize;
                            if ((vx >> 4) == chunkX && (vz >> 4) == chunkZ) {
                                spawnVillagerDelayed(worldInfo.getName(), vx, surfaceY + 1, vz, 1, chunkRandom);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkFootprintFits(SimplexNoiseGenerator noise, int cx, int cz, int radius, double scale, double threshold) {
        // Checks four corners at the given footprint radius to guarantee the structure does not spill into void
        return noise.noise((cx - radius) * scale, (cz - radius) * scale) > threshold
            && noise.noise((cx + radius) * scale, (cz - radius) * scale) > threshold
            && noise.noise((cx - radius) * scale, (cz + radius) * scale) > threshold
            && noise.noise((cx + radius) * scale, (cz + radius) * scale) > threshold;
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

    private void generateCottage(LimitedRegion limitedRegion, int x, int y, int z, String doorFacing, Random random) {
        // Footprint: 8x8. Base starts at y.
        // 1. Floor
        for (int dx = 0; dx < 8; dx++) {
            for (int dz = 0; dz < 8; dz++) {
                setBlockIfInRegion(limitedRegion, x + dx, y, z + dz, "minecraft:spruce_planks");
            }
        }
        
        // 2. Pillars at corners (Oak Log axis=y)
        for (int dy = 1; dy <= 4; dy++) {
            setBlockIfInRegion(limitedRegion, x, y + dy, z, "minecraft:oak_log[axis=y]");
            setBlockIfInRegion(limitedRegion, x + 7, y + dy, z, "minecraft:oak_log[axis=y]");
            setBlockIfInRegion(limitedRegion, x, y + dy, z + 7, "minecraft:oak_log[axis=y]");
            setBlockIfInRegion(limitedRegion, x + 7, y + dy, z + 7, "minecraft:oak_log[axis=y]");
        }
        
        // 3. Walls (Oak planks)
        for (int dy = 1; dy <= 4; dy++) {
            for (int i = 1; i < 7; i++) {
                setBlockIfInRegion(limitedRegion, x + i, y + dy, z, "minecraft:oak_planks");
                setBlockIfInRegion(limitedRegion, x + i, y + dy, z + 7, "minecraft:oak_planks");
                setBlockIfInRegion(limitedRegion, x, y + dy, z + i, "minecraft:oak_planks");
                setBlockIfInRegion(limitedRegion, x + 7, y + dy, z + i, "minecraft:oak_planks");
            }
        }
        
        // 4. Windows (glass blocks)
        if (!doorFacing.equals("north")) {
            setBlockIfInRegion(limitedRegion, x + 3, y + 2, z, "minecraft:glass");
            setBlockIfInRegion(limitedRegion, x + 4, y + 2, z, "minecraft:glass");
        }
        if (!doorFacing.equals("south")) {
            setBlockIfInRegion(limitedRegion, x + 3, y + 2, z + 7, "minecraft:glass");
            setBlockIfInRegion(limitedRegion, x + 4, y + 2, z + 7, "minecraft:glass");
        }
        if (!doorFacing.equals("west")) {
            setBlockIfInRegion(limitedRegion, x, y + 2, z + 3, "minecraft:glass");
            setBlockIfInRegion(limitedRegion, x, y + 2, z + 4, "minecraft:glass");
        }
        if (!doorFacing.equals("east")) {
            setBlockIfInRegion(limitedRegion, x + 7, y + 2, z + 3, "minecraft:glass");
            setBlockIfInRegion(limitedRegion, x + 7, y + 2, z + 4, "minecraft:glass");
        }
        
        // 5. Door & Stairs to ground
        int doorX = 0, doorZ = 0;
        String stairBlock = "minecraft:oak_stairs";
        if (doorFacing.equals("west")) {
            doorX = x; doorZ = z + 3;
            setBlockIfInRegion(limitedRegion, doorX - 1, y, doorZ, stairBlock + "[facing=east]");
            setBlockIfInRegion(limitedRegion, doorX - 1, y + 1, doorZ, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX - 1, y + 2, doorZ, "minecraft:air");
        } else if (doorFacing.equals("east")) {
            doorX = x + 7; doorZ = z + 3;
            setBlockIfInRegion(limitedRegion, doorX + 1, y, doorZ, stairBlock + "[facing=west]");
            setBlockIfInRegion(limitedRegion, doorX + 1, y + 1, doorZ, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX + 1, y + 2, doorZ, "minecraft:air");
        } else if (doorFacing.equals("north")) {
            doorX = x + 3; doorZ = z;
            setBlockIfInRegion(limitedRegion, doorX, y, doorZ - 1, stairBlock + "[facing=south]");
            setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ - 1, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ - 1, "minecraft:air");
        } else { // south
            doorX = x + 3; doorZ = z + 7;
            setBlockIfInRegion(limitedRegion, doorX, y, doorZ + 1, stairBlock + "[facing=north]");
            setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ + 1, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ + 1, "minecraft:air");
        }
        
        setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ, "minecraft:oak_door[facing=" + doorFacing + ",half=lower]");
        setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ, "minecraft:oak_door[facing=" + doorFacing + ",half=upper]");
        
        // 6. Bed (aligned properly facing north, head at z + 1, foot at z + 2)
        setBlockIfInRegion(limitedRegion, x + 2, y + 1, z + 1, "minecraft:red_bed[part=head,facing=north]");
        setBlockIfInRegion(limitedRegion, x + 2, y + 1, z + 2, "minecraft:red_bed[part=foot,facing=north]");
        
        // 7. Roof (sloped oak stairs facing north/south or east/west)
        for (int dz = 0; dz < 8; dz++) {
            setBlockIfInRegion(limitedRegion, x, y + 5, z + dz, "minecraft:oak_stairs[facing=east]");
            setBlockIfInRegion(limitedRegion, x + 1, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 2, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 3, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 4, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 5, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 6, y + 5, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 7, y + 5, z + dz, "minecraft:oak_stairs[facing=west]");
        }
        for (int dz = 0; dz < 8; dz++) {
            setBlockIfInRegion(limitedRegion, x + 1, y + 6, z + dz, "minecraft:oak_stairs[facing=east]");
            setBlockIfInRegion(limitedRegion, x + 2, y + 6, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 3, y + 6, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 4, y + 6, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 5, y + 6, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 6, y + 6, z + dz, "minecraft:oak_stairs[facing=west]");
        }
        for (int dz = 0; dz < 8; dz++) {
            setBlockIfInRegion(limitedRegion, x + 2, y + 7, z + dz, "minecraft:oak_stairs[facing=east]");
            setBlockIfInRegion(limitedRegion, x + 3, y + 7, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 4, y + 7, z + dz, "minecraft:oak_planks");
            setBlockIfInRegion(limitedRegion, x + 5, y + 7, z + dz, "minecraft:oak_stairs[facing=west]");
        }
        for (int dz = 0; dz < 8; dz++) {
            setBlockIfInRegion(limitedRegion, x + 3, y + 8, z + dz, "minecraft:oak_slab[type=bottom]");
            setBlockIfInRegion(limitedRegion, x + 4, y + 8, z + dz, "minecraft:oak_slab[type=bottom]");
        }
    }

    private void generateStonebrickHouse(LimitedRegion limitedRegion, int x, int y, int z, String doorFacing, Random random) {
        // Footprint: 7x7. Base starts at y.
        // 1. Floor
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                setBlockIfInRegion(limitedRegion, x + dx, y, z + dz, "minecraft:stone_bricks");
            }
        }
        
        // 2. Walls (Stone bricks and cracked stone bricks mixed)
        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = 0; dx < 7; dx++) {
                for (int dz = 0; dz < 7; dz++) {
                    if (dx == 0 || dx == 6 || dz == 0 || dz == 6) {
                        String mat = (random.nextDouble() < 0.25) ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks";
                        setBlockIfInRegion(limitedRegion, x + dx, y + dy, z + dz, mat);
                    }
                }
            }
        }
        
        // 3. Windows (glass blocks)
        if (!doorFacing.equals("north")) {
            setBlockIfInRegion(limitedRegion, x + 3, y + 2, z, "minecraft:glass");
        }
        if (!doorFacing.equals("south")) {
            setBlockIfInRegion(limitedRegion, x + 3, y + 2, z + 6, "minecraft:glass");
        }
        if (!doorFacing.equals("west")) {
            setBlockIfInRegion(limitedRegion, x, y + 2, z + 3, "minecraft:glass");
        }
        if (!doorFacing.equals("east")) {
            setBlockIfInRegion(limitedRegion, x + 6, y + 2, z + 3, "minecraft:glass");
        }
        
        // 4. Door & Stairs to ground
        int doorX = 0, doorZ = 0;
        String stairBlock = "minecraft:stone_brick_stairs";
        if (doorFacing.equals("west")) {
            doorX = x; doorZ = z + 3;
            setBlockIfInRegion(limitedRegion, doorX - 1, y, doorZ, stairBlock + "[facing=east]");
            setBlockIfInRegion(limitedRegion, doorX - 1, y + 1, doorZ, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX - 1, y + 2, doorZ, "minecraft:air");
        } else if (doorFacing.equals("east")) {
            doorX = x + 6; doorZ = z + 3;
            setBlockIfInRegion(limitedRegion, doorX + 1, y, doorZ, stairBlock + "[facing=west]");
            setBlockIfInRegion(limitedRegion, doorX + 1, y + 1, doorZ, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX + 1, y + 2, doorZ, "minecraft:air");
        } else if (doorFacing.equals("north")) {
            doorX = x + 3; doorZ = z;
            setBlockIfInRegion(limitedRegion, doorX, y, doorZ - 1, stairBlock + "[facing=south]");
            setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ - 1, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ - 1, "minecraft:air");
        } else { // south
            doorX = x + 3; doorZ = z + 6;
            setBlockIfInRegion(limitedRegion, doorX, y, doorZ + 1, stairBlock + "[facing=north]");
            setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ + 1, "minecraft:air");
            setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ + 1, "minecraft:air");
        }
        
        setBlockIfInRegion(limitedRegion, doorX, y + 1, doorZ, "minecraft:spruce_door[facing=" + doorFacing + ",half=lower]");
        setBlockIfInRegion(limitedRegion, doorX, y + 2, doorZ, "minecraft:spruce_door[facing=" + doorFacing + ",half=upper]");
        
        // 5. Bed (aligned properly facing north, head at z + 1, foot at z + 2)
        setBlockIfInRegion(limitedRegion, x + 2, y + 1, z + 1, "minecraft:red_bed[part=head,facing=north]");
        setBlockIfInRegion(limitedRegion, x + 2, y + 1, z + 2, "minecraft:red_bed[part=foot,facing=north]");
        
        // 6. Roof: Flat Stone Brick Slabs with cobblestone stairs trim
        for (int dx = -1; dx <= 7; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                boolean isEdge = (dx == -1 || dx == 7 || dz == -1 || dz == 7);
                if (isEdge) {
                    String stairData = "minecraft:cobblestone_stairs";
                    if (dx == -1) stairData += "[facing=east]";
                    else if (dx == 7) stairData += "[facing=west]";
                    else if (dz == -1) stairData += "[facing=south]";
                    else if (dz == 7) stairData += "[facing=north]";
                    
                    setBlockIfInRegion(limitedRegion, x + dx, y + 5, z + dz, stairData);
                } else {
                    setBlockIfInRegion(limitedRegion, x + dx, y + 5, z + dz, "minecraft:stone_brick_slab[type=bottom]");
                }
            }
        }
    }

    private void generateColiseum(LimitedRegion limitedRegion, int x, int y, int z, Random random) {
        // Footprint: 30x30 circular colosseum centered at (x, z)
        // Outer wall radius: 15
        
        String cloudBlock = "minecraft:brown_mushroom_block[up=false,down=false,north=false,south=false,east=false,west=false]";
        
        for (int dx = -15; dx <= 15; dx++) {
            for (int dz = -15; dz <= 15; dz++) {
                int px = x + dx;
                int pz = z + dz;
                int d2 = dx * dx + dz * dz;
                
                if (d2 > 225) continue; // Outside 30x30 circle
                
                // 1. Clear air space above the entire colosseum area
                for (int dy = 1; dy <= 8; dy++) {
                    setBlockIfInRegion(limitedRegion, px, y + dy, pz, "minecraft:air");
                }
                
                if (d2 >= 196) {
                    // Outer Wall & Pillars (Y = y to y + 5)
                    boolean isPillar = (dx * dz) % 3 == 0;
                    if (isPillar) {
                        for (int dy = 0; dy <= 5; dy++) {
                            String mat = (random.nextDouble() < 0.2) ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks";
                            setBlockIfInRegion(limitedRegion, px, y + dy, pz, mat);
                        }
                        // Cloud rim on top of pillars
                        setBlockIfInRegion(limitedRegion, px, y + 6, pz, cloudBlock);
                    } else {
                        setBlockIfInRegion(limitedRegion, px, y, pz, "minecraft:cobblestone");
                        setBlockIfInRegion(limitedRegion, px, y + 1, pz, "minecraft:stone_brick_wall");
                        setBlockIfInRegion(limitedRegion, px, y + 2, pz, "minecraft:stone_brick_wall");
                        setBlockIfInRegion(limitedRegion, px, y + 3, pz, "minecraft:stone_brick_wall");
                        setBlockIfInRegion(limitedRegion, px, y + 4, pz, "minecraft:stone_brick_slab[type=bottom]");
                        // Cloud rim on top of walls
                        setBlockIfInRegion(limitedRegion, px, y + 5, pz, cloudBlock);
                    }
                } else if (d2 >= 64) {
                    // Seating Areas (Stone Brick Stairs facing inwards)
                    String dir;
                    if (Math.abs(dx) > Math.abs(dz)) {
                        dir = (dx < 0) ? "east" : "west";
                    } else {
                        dir = (dz < 0) ? "south" : "north";
                    }
                    
                    if (d2 >= 144) {
                        // Tier 3
                        setBlockIfInRegion(limitedRegion, px, y, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 1, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 2, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 3, pz, "minecraft:stone_brick_stairs[facing=" + dir + "]");
                    } else if (d2 >= 100) {
                        // Tier 2
                        setBlockIfInRegion(limitedRegion, px, y, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 1, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 2, pz, "minecraft:stone_brick_stairs[facing=" + dir + "]");
                    } else {
                        // Tier 1
                        setBlockIfInRegion(limitedRegion, px, y, pz, "minecraft:stone_bricks");
                        setBlockIfInRegion(limitedRegion, px, y + 1, pz, "minecraft:stone_brick_stairs[facing=" + dir + "]");
                    }
                } else {
                    // Arena Floor (Y = y) - replaced with Cloud Blocks
                    // Center 4x4 has polished stone bricks decoration
                    if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) {
                        String centerMat = (dx == 0 || dz == 0) ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite";
                        setBlockIfInRegion(limitedRegion, px, y, pz, centerMat);
                    } else {
                        setBlockIfInRegion(limitedRegion, px, y, pz, cloudBlock);
                    }
                }
            }
        }
        
        // Entrances: Clear a 3-block wide passage at East/West and North/South axes
        for (int dy = 0; dy <= 5; dy++) {
            for (int dAxis = -15; dAxis <= 15; dAxis++) {
                for (int dOff = -1; dOff <= 1; dOff++) {
                    int dist2_ew = dAxis * dAxis + dOff * dOff;
                    if (dist2_ew >= 64) {
                        setBlockIfInRegion(limitedRegion, x + dAxis, y + dy, z + dOff, "minecraft:air");
                    }
                    int dist2_ns = dOff * dOff + dAxis * dAxis;
                    if (dist2_ns >= 64) {
                        setBlockIfInRegion(limitedRegion, x + dOff, y + dy, z + dAxis, "minecraft:air");
                    }
                }
            }
        }

        // Place stairs at the entrance thresholds going up into the arena (ground is y - 1, arena floor is y)
        for (int dOff = -1; dOff <= 1; dOff++) {
            // West entrance (dx = -15)
            setBlockIfInRegion(limitedRegion, x - 15, y, z + dOff, "minecraft:stone_brick_stairs[facing=east]");
            setBlockIfInRegion(limitedRegion, x - 15, y + 1, z + dOff, "minecraft:air");

            // East entrance (dx = 15)
            setBlockIfInRegion(limitedRegion, x + 15, y, z + dOff, "minecraft:stone_brick_stairs[facing=west]");
            setBlockIfInRegion(limitedRegion, x + 15, y + 1, z + dOff, "minecraft:air");

            // North entrance (dz = -15)
            setBlockIfInRegion(limitedRegion, x + dOff, y, z - 15, "minecraft:stone_brick_stairs[facing=south]");
            setBlockIfInRegion(limitedRegion, x + dOff, y + 1, z - 15, "minecraft:air");

            // South entrance (dz = 15)
            setBlockIfInRegion(limitedRegion, x + dOff, y, z + 15, "minecraft:stone_brick_stairs[facing=north]");
            setBlockIfInRegion(limitedRegion, x + dOff, y + 1, z + 15, "minecraft:air");
        }
    }

    private void drawStraightPath(LimitedRegion limitedRegion, int x1, int z1, int x2, int z2, int y, Random random) {
        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        if (minX == maxX) {
            // Vertical path along Z axis
            for (int z = minZ; z <= maxZ; z++) {
                int width = random.nextInt(2) + 2; // 2 or 3 blocks wide
                if (width == 2) {
                    setPathBlock(limitedRegion, minX, y, z);
                    setPathBlock(limitedRegion, minX + 1, y, z);
                } else {
                    setPathBlock(limitedRegion, minX - 1, y, z);
                    setPathBlock(limitedRegion, minX, y, z);
                    setPathBlock(limitedRegion, minX + 1, y, z);
                }
            }
        } else if (minZ == maxZ) {
            // Horizontal path along X axis
            for (int x = minX; x <= maxX; x++) {
                int width = random.nextInt(2) + 2; // 2 or 3 blocks wide
                if (width == 2) {
                    setPathBlock(limitedRegion, x, y, minZ);
                    setPathBlock(limitedRegion, x, y, minZ + 1);
                } else {
                    setPathBlock(limitedRegion, x, y, minZ - 1);
                    setPathBlock(limitedRegion, x, y, minZ);
                    setPathBlock(limitedRegion, x, y, minZ + 1);
                }
            }
        }
    }

    private void setPathBlock(LimitedRegion limitedRegion, int x, int y, int z) {
        setBlockIfInRegion(limitedRegion, x, y, z, "minecraft:dirt_path");
        setBlockIfInRegion(limitedRegion, x, y + 1, z, "minecraft:air"); // Clear foliage
    }

    private void spawnVillagerDelayed(String worldName, int x, int y, int z, int count, Random random) {
        // Schedules entity spawning on the main thread shortly after chunk loads to avoid entity persistence bugs
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
                    java.util.List<Villager.Profession> professions = org.bukkit.Registry.VILLAGER_PROFESSION.stream().toList();
                    for (int i = 0; i < count; i++) {
                        int vx = x + (count > 1 ? random.nextInt(3) - 1 : 0);
                        int vz = z + (count > 1 ? random.nextInt(3) - 1 : 0);
                        Location loc = new Location(world, vx, y, vz);
                        
                        try {
                            Villager villager = world.spawn(loc, Villager.class);
                            if (villager != null) {
                                villager.setProfession(professions.get(random.nextInt(professions.size())));
                                villager.setVillagerLevel(random.nextInt(3) + 1);
                            }
                        } catch (Exception e) {
                            // Suppress spawning failures
                        }
                    }
                });
            }
        }, 20L); // 1 second delay
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
}
