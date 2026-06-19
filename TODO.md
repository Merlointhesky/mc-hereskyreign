# Project: Sky Reign Dimension Plugin

## Context
I have two Spigot/Paper plugin projects in this workspace:
1. **The Blueprint Plugin:** My existing custom plugin that handles saving, loading, rotating, flipping, and mirroring structure blueprints (metadata files).
2. **The Sky Reign Plugin:** The new plugin we are building now.

## Core Objective
Write the complete implementation for the "Sky Reign" plugin. This plugin creates a custom floating-island dimension, handles seamless vertical teleportation from the Overworld, and procedurally generates custom structures using my existing Blueprint API. 

## Technical Requirements

### 1. Seamless Teleportation
* Create an optimized event listener (`PlayerMoveEvent` or a repeating task) to monitor player Y-levels.
* **Overworld -> Sky Reign:** If a player in the Overworld goes above Y=320, teleport them seamlessly to the bottom of the Sky Reign dimension, maintaining their X and Z coordinates.
* **Sky Reign -> Overworld:** If a player falls below the minimum Y build height in the Sky Reign, teleport them to Y=320 in the Overworld so they fall out of the sky.

### 2. World Generation (The Cloud Planes)
* Implement a custom `ChunkGenerator` for the Sky Reign dimension. 
* The terrain should generate as flat-plane floating islands.
* **The Cloud Block Trick:** The primary block for this terrain must be a Mushroom Block with a specific, unused block state. Use `Material.BROWN_MUSHROOM_BLOCK` and force all 6 directional block state properties (up, down, north, south, east, west) to `false` (meaning the pore texture shows on all sides). This will serve as our custom Cloud Block via resource pack.

### 3. Structure Generation (Villages & Coliseums)
* Analyze my Blueprint plugin in the workspace to understand how to load and paste blueprint metadata files.
* Create a Spigot `BlockPopulator` for the Sky Reign generator.
* During chunk generation, roll a chance to spawn either a "Sky Village" or a "Coliseum" blueprint on the surface of the cloud planes.
* **API Integration:** Use the Blueprint plugin's API to randomly apply rotation, mirroring, or flipping to the structures before pasting them so they look organic.
* **Lag Prevention:** Implement strict safety checks in the `BlockPopulator` to ensure blocks are only pasted within loaded/generated chunks to absolutely prevent cascading chunk generation lag. (Do not include any boss spawning logic).

## Expected Artifacts
1. The `ChunkGenerator` and dimension registration logic.
2. The `BlockPopulator` integrating the Blueprint plugin's API.
3. The Player Y-level teleportation listener.