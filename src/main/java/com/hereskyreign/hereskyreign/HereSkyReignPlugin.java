package com.hereskyreign.hereskyreign;

import com.hereskyreign.hereskyreign.generator.SkyReignChunkGenerator;
import com.hereskyreign.hereskyreign.listener.TeleportationListener;
import com.hereskyreign.hereskyreign.coliseum.ColiseumManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class HereSkyReignPlugin extends JavaPlugin {

    private static HereSkyReignPlugin instance;
    private ColiseumManager coliseumManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Export cloud block resource pack for server admins
        try {
            saveResource("cloud_block_resourcepack.zip", false);
            getLogger().info("Extracted cloud block resource pack to: plugins/HereSkyReign/cloud_block_resourcepack.zip");
        } catch (IllegalArgumentException e) {
            // Already exists or resource not found
        }

        // Initialize Coliseum Manager
        this.coliseumManager = new ColiseumManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new TeleportationListener(this), this);
        getServer().getPluginManager().registerEvents(new com.hereskyreign.hereskyreign.listener.CloudBlockListener(this), this);

        // Load or create the Sky Reign world
        String worldName = getConfig().getString("world-name", "sky_reign");
        getLogger().info("Initializing Sky Reign world: " + worldName + "...");

        World skyReignWorld = Bukkit.getWorld(worldName);
        if (skyReignWorld == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generator(new SkyReignChunkGenerator(this));
            skyReignWorld = creator.createWorld();
            if (skyReignWorld != null) {
                getLogger().info("Sky Reign world created successfully.");
            } else {
                getLogger().severe("Failed to create Sky Reign world!");
            }
        } else {
            getLogger().info("Sky Reign world loaded.");
        }

        // BlueMap Integration
        if (skyReignWorld != null && getConfig().getBoolean("bluemap-integration.enabled", true)) {
            setupBlueMap(worldName);
        }
        getLogger().info("HereSkyReign enabled!");
    }

    private void setupBlueMap(String worldName) {
        java.io.File pluginsFolder = getDataFolder().getParentFile();
        java.io.File bluemapMapsFolder = new java.io.File(pluginsFolder, "BlueMap/maps");
        if (bluemapMapsFolder.exists() && bluemapMapsFolder.isDirectory()) {
            java.io.File mapConfigFile = new java.io.File(bluemapMapsFolder, worldName + ".conf");

            World world = Bukkit.getWorld(worldName);
            if (world == null) return;

            java.io.File worldFolder = world.getWorldFolder();
            String relativePath;
            try {
                relativePath = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize()
                        .relativize(worldFolder.toPath().toAbsolutePath().normalize())
                        .toString().replace('\\', '/');
            } catch (Exception e) {
                relativePath = worldName;
            }

            String dim = "minecraft:overworld";

            boolean needsUpdate = true;
            if (mapConfigFile.exists()) {
                try {
                    java.util.List<String> lines = java.nio.file.Files.readAllLines(mapConfigFile.toPath());
                    boolean hasCorrectWorld = false;
                    boolean hasCorrectDim = false;
                    for (String line : lines) {
                        if (line.trim().startsWith("world:") && line.contains("\"" + relativePath + "\"")) {
                            hasCorrectWorld = true;
                        }
                        if (line.trim().startsWith("dimension:") && line.contains("\"" + dim + "\"")) {
                            hasCorrectDim = true;
                        }
                    }
                    if (hasCorrectWorld && hasCorrectDim) {
                        needsUpdate = false;
                    }
                } catch (Exception e) {
                    // Ignore and overwrite
                }
            }

            if (needsUpdate) {
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(mapConfigFile))) {
                    writer.println("world: \"" + relativePath + "\"");
                    writer.println("dimension: \"" + dim + "\"");
                    writer.println("name: \"" + worldName + "\"");
                    writer.println("sorting: 300");
                    getLogger().info("Automatically created/updated BlueMap map configuration for " + worldName);

                    // Delay command run slightly to allow BlueMap initialization to complete
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bluemap reload");
                    }, 100L); // 5 seconds
                } catch (Exception e) {
                    getLogger().warning("Failed to create BlueMap configuration for " + worldName + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        if (coliseumManager != null) {
            coliseumManager.cleanup();
        }
        getLogger().info("HereSkyReign disabled!");
    }

    public static HereSkyReignPlugin getInstance() {
        return instance;
    }

    public ColiseumManager getColiseumManager() {
        return coliseumManager;
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new SkyReignChunkGenerator(this);
    }
}
