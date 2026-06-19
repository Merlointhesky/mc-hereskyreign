# HereSkyReign

A premium [Paper](https://papermc.io) Minecraft plugin that creates a floating-island dimension (**Sky Reign**) with seamless vertical teleportation, custom cloud block terrain, and organic procedural structure spawning using the [HereEngy](file:///d:/Development/Java/Minecraft%20Mods/HereEngy) Blueprint API.

## Features

- **Seamless Vertical Teleportation** — Teleports players vertically between the Overworld and the Sky Reign dimension automatically:
  - **Overworld → Sky Reign:** Reaching Y > 320 in the Overworld teleports players to Y = 2 in the Sky Reign dimension, maintaining horizontal coordinates and momentum.
  - **Sky Reign → Overworld:** Falling below Y < 0 in Sky Reign teleports players to Y = 320 in the Overworld, sending them falling out of the sky.
  - **Anti-Loop Cooldown:** A 100-tick (5-second) teleportation cooldown protects players from getting stuck in an infinite vertical loop.
- **Custom Cloud Block Terrain** — Features flat-plane organic floating islands generated using 2D Simplex Noise.
  - **Cloud Block Trick:** The islands are constructed out of `BROWN_MUSHROOM_BLOCK` with all 6 directional face properties set to `false` (meaning the inner pore texture is shown on all sides), which serves as a custom Cloud Block via your server's resource pack.
- **Procedural Structure Spawning** — Integrates with the `HereEngy` Blueprint API to populate islands with **Sky Villages** and **Coliseums**:
  - **Organic Transformations:** Randomly applies rotation and mirroring to structures so they spawn dynamically.
  - **Lag Prevention:** Utilizes `LimitedRegion` constraints and deterministic noise checks to guarantee zero cascading chunk generation lag.
  - **Void Prevention:** Checks the terrain height noise at the structure's origin to prevent structures from spawning floating in mid-air voids.
- **BlueMap Integration** — Automatically generates and updates BlueMap map rendering configuration files on startup to render the Sky Reign dimension.

---

## Configuration

Settings can be customized in `plugins/HereSkyReign/config.yml`:

```yaml
world-name: "sky_reign"

teleportation:
  overworld-to-skyreign-y: 320.0
  skyreign-to-overworld-y: 0.0
  cooldown-ticks: 100

generation:
  island-noise-scale: 0.015
  island-noise-threshold: 0.1
  island-min-y: 60
  island-max-y: 64

structures:
  village-chance: 0.005
  coliseum-chance: 0.002

bluemap-integration:
  enabled: true
```

---

## Requirements

- **Paper / Spigot 1.21.4** or higher.
- **Java 21** or higher.
- **HereEngy (Blueprint Plugin)** installed on the server.

## Resource Pack Setup

To render the custom Cloud Block correctly instead of the default brown mushroom block texture, a resource pack is required.

Upon the first startup, HereSkyReign automatically extracts a pre-configured resource pack file to:
`plugins/HereSkyReign/cloud_block_resourcepack.zip`

### How to use:
1. **Merge/Apply:** Unzip this file and merge its `assets/` folder with your server's existing resource pack.
2. **Server Distribution:** Upload the `cloud_block_resourcepack.zip` to a public web host, and add the download URL to the `resource-pack` setting in your server's `server.properties`.

---

## License

This project is licensed under the GPLv3 License - see the [LICENSE](file:///d:/Development/Java/Minecraft%20Mods/HereSkyReign/LICENSE) file for details.
