package com.hereskyreign.hereskyreign.coliseum;

import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ColiseumManager {

    private final HereSkyReignPlugin plugin;
    private final File dataFile;
    private final List<ColiseumInstance> colosseums = new ArrayList<>();
    private final NamespacedKey guardKey;
    private final NamespacedKey bossKey;

    public ColiseumManager(HereSkyReignPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/colosseums.yml");
        this.guardKey = new NamespacedKey(plugin, "coliseum_guard");
        this.bossKey = new NamespacedKey(plugin, "coliseum_boss");

        loadColosseums();
        startUpdateTask();
    }

    public void addColiseum(Location loc) {
        for (ColiseumInstance instance : colosseums) {
            if (instance.center.getBlockX() == loc.getBlockX() &&
                instance.center.getBlockZ() == loc.getBlockZ()) {
                return;
            }
        }
        colosseums.add(new ColiseumInstance(loc));
        saveColosseums();
        plugin.getLogger().info("Registered new coliseum at " + loc.getBlockX() + ", " + loc.getBlockZ());
    }

    private void loadColosseums() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        List<String> list = config.getStringList("colosseums");
        for (String entry : list) {
            String[] parts = entry.split(",");
            if (parts.length == 4) {
                World world = Bukkit.getWorld(parts[0]);
                if (world != null) {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    colosseums.add(new ColiseumInstance(new Location(world, x, y, z)));
                }
            }
        }
        plugin.getLogger().info("Loaded " + colosseums.size() + " coliseum locations.");
    }

    private void saveColosseums() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> list = new ArrayList<>();
        for (ColiseumInstance instance : colosseums) {
            list.add(instance.center.getWorld().getName() + "," +
                     instance.center.getX() + "," +
                     instance.center.getY() + "," +
                     instance.center.getZ());
        }
        config.set("colosseums", list);
        try {
            config.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cleanup() {
        for (ColiseumInstance instance : colosseums) {
            if (instance.bossBar != null) {
                for (UUID uuid : instance.viewers) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.hideBossBar(instance.bossBar);
                    }
                }
                instance.viewers.clear();
                instance.bossBar = null;
            }
            if (instance.center.getWorld() != null && instance.center.getChunk().isLoaded()) {
                for (UUID uuid : instance.activeGuards) {
                    Entity entity = Bukkit.getEntity(uuid);
                    if (entity != null) entity.remove();
                }
                if (instance.bossPhantomUuid != null) {
                    Entity boss = Bukkit.getEntity(instance.bossPhantomUuid);
                    if (boss != null) boss.remove();
                }
            }
        }
    }

    private void startUpdateTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            long cooldownMillis = (long) (plugin.getConfig().getDouble("combat.respawn-cooldown-hours", 2.0) * 3600000L);
            int guardCount = plugin.getConfig().getInt("combat.guard-count", 4);

            for (ColiseumInstance instance : colosseums) {
                World world = instance.center.getWorld();
                if (world == null) continue;

                int cx = instance.center.getBlockX() >> 4;
                int cz = instance.center.getBlockZ() >> 4;
                if (!world.isChunkLoaded(cx, cz)) {
                    if (instance.bossBar != null) {
                        for (UUID uuid : instance.viewers) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                player.hideBossBar(instance.bossBar);
                            }
                        }
                        instance.viewers.clear();
                        instance.bossBar = null;
                    }
                    continue;
                }

                // --- 1. Manage Coliseum Guards ---
                instance.activeGuards.removeIf(uuid -> {
                    Entity entity = Bukkit.getEntity(uuid);
                    return entity == null || !entity.isValid() || entity.isDead();
                });

                if (instance.activeGuards.isEmpty()) {
                    if (instance.respawnTime == 0) {
                        instance.respawnTime = now + cooldownMillis;
                        plugin.getLogger().info("Coliseum at " + instance.center.getBlockX() + ", " + instance.center.getBlockZ() + " guards defeated. Respawning in " + plugin.getConfig().getDouble("combat.respawn-cooldown-hours", 2.0) + " hours.");
                    } else if (now >= instance.respawnTime) {
                        spawnGuards(instance, guardCount);
                        instance.respawnTime = 0;
                    }
                } else {
                    for (UUID uuid : instance.activeGuards) {
                        Entity entity = Bukkit.getEntity(uuid);
                        if (entity instanceof Zombie zombie) {
                            if (zombie.getLocation().distanceSquared(instance.center) > 196) { // radius > 14 blocks
                                zombie.teleport(instance.center.clone().add(0, 1, 0));
                                zombie.getWorld().spawnParticle(Particle.PORTAL, zombie.getLocation(), 10);
                            }
                        }
                    }
                }

                // --- 2. Manage Boss Phantom ---
                Entity boss = instance.bossPhantomUuid != null ? Bukkit.getEntity(instance.bossPhantomUuid) : null;
                boolean bossAlive = boss instanceof Phantom && boss.isValid() && !boss.isDead();

                if (!bossAlive) {
                    if (instance.bossBar != null) {
                        for (UUID uuid : instance.viewers) {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                player.hideBossBar(instance.bossBar);
                            }
                        }
                        instance.viewers.clear();
                        instance.bossBar = null;
                    }
                    spawnBoss(instance);
                } else {
                    Phantom phantom = (Phantom) boss;
                    if (instance.bossBar == null) {
                        instance.bossBar = BossBar.bossBar(
                            Component.text("Colosseum Stormbringer", NamedTextColor.RED, TextDecoration.BOLD),
                            1.0f,
                            BossBar.Color.RED,
                            BossBar.Overlay.PROGRESS
                        );
                    }

                    Set<Player> nearby = new HashSet<>();
                    for (Player player : world.getPlayers()) {
                        if (player.getLocation().distanceSquared(instance.center) <= 900) { // Radius 30
                            nearby.add(player);
                        }
                    }

                    instance.viewers.removeIf(uuid -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null || !player.isOnline() || !nearby.contains(player)) {
                            if (player != null && player.isOnline()) {
                                player.hideBossBar(instance.bossBar);
                            }
                            return true;
                        }
                        return false;
                    });

                    for (Player player : nearby) {
                        if (!instance.viewers.contains(player.getUniqueId())) {
                            player.showBossBar(instance.bossBar);
                            instance.viewers.add(player.getUniqueId());
                        }
                    }

                    double progress = phantom.getHealth() / phantom.getMaxHealth();
                    instance.bossBar.progress((float) Math.max(0.0, Math.min(1.0, progress)));

                    if (now >= instance.nextSpellTime) {
                        castSpell(instance, phantom, nearby);
                        long cooldownTicks = plugin.getConfig().getLong("combat.boss-spell-cooldown-ticks", 200L);
                        instance.nextSpellTime = now + (cooldownTicks * 50L);
                    }
                }
            }
        }, 40L, 40L);
    }

    private void spawnGuards(ColiseumInstance instance, int count) {
        World world = instance.center.getWorld();
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            double r = random.nextDouble() * 5.0;
            Location loc = instance.center.clone().add(Math.cos(angle) * r, 1.0, Math.sin(angle) * r);
            loc.setY(world.getHighestBlockYAt(loc) + 1.0);

            Zombie zombie = world.spawn(loc, Zombie.class, entity -> {
                entity.customName(Component.text("Colosseum Guard", NamedTextColor.RED));
                entity.setCustomNameVisible(true);
                entity.setRemoveWhenFarAway(false);
                entity.getPersistentDataContainer().set(guardKey, PersistentDataType.BYTE, (byte) 1);

                ItemStack helmet = new ItemStack(Material.DIAMOND_HELMET);
                ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
                ItemStack leggings = new ItemStack(Material.DIAMOND_LEGGINGS);
                ItemStack boots = new ItemStack(Material.DIAMOND_BOOTS);
                ItemStack weapon = new ItemStack(random.nextBoolean() ? Material.DIAMOND_SWORD : Material.DIAMOND_AXE);

                var equip = entity.getEquipment();
                if (equip != null) {
                    equip.setHelmet(helmet);
                    equip.setChestplate(chestplate);
                    equip.setLeggings(leggings);
                    equip.setBoots(boots);
                    equip.setItemInMainHand(weapon);

                    equip.setHelmetDropChance(0.0f);
                    equip.setChestplateDropChance(0.0f);
                    equip.setLeggingsDropChance(0.0f);
                    equip.setBootsDropChance(0.0f);
                    equip.setItemInMainHandDropChance(0.0f);
                }
            });

            if (zombie != null) {
                instance.activeGuards.add(zombie.getUniqueId());
            }
        }
    }

    private void spawnBoss(ColiseumInstance instance) {
        World world = instance.center.getWorld();
        Location loc = instance.center.clone().add(0, 12, 0);

        Phantom phantom = world.spawn(loc, Phantom.class, entity -> {
            entity.customName(Component.text("Colosseum Stormbringer", NamedTextColor.RED, TextDecoration.BOLD));
            entity.setCustomNameVisible(true);
            entity.setRemoveWhenFarAway(false);
            entity.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);

            int size = plugin.getConfig().getInt("combat.boss-phantom-size", 12);
            entity.setSize(size);

            entity.setMaxHealth(100.0);
            entity.setHealth(100.0);
        });

        if (phantom != null) {
            instance.bossPhantomUuid = phantom.getUniqueId();
            instance.nextSpellTime = System.currentTimeMillis() + 6000L;
        }
    }

    private void castSpell(ColiseumInstance instance, Phantom boss, Set<Player> players) {
        if (players.isEmpty()) return;

        List<Player> targetList = new ArrayList<>(players);
        Player target = targetList.get(new Random().nextInt(targetList.size()));

        boolean castWind = new Random().nextBoolean();
        World world = boss.getWorld();

        if (castWind) {
            Location pLoc = target.getLocation();
            Location center = instance.center;
            
            Vector dir = pLoc.toVector().subtract(center.toVector());
            dir.setY(0);
            if (dir.lengthSquared() > 0) {
                dir.normalize();
            } else {
                dir = new Vector(1, 0, 0);
            }

            Vector push = dir.multiply(2.2).setY(0.6);
            target.setVelocity(push);

            world.playSound(pLoc, Sound.ENTITY_WIND_CHARGE_THROW, 2f, 0.5f);
            world.spawnParticle(Particle.CLOUD, pLoc, 30, 1.0, 1.0, 1.0, 0.2);
            target.sendMessage(Component.text("💨 The Stormbringer casts a Gale Blast!", NamedTextColor.RED));
        } else {
            Location pLoc = target.getLocation();
            world.strikeLightning(pLoc);
            target.sendMessage(Component.text("⚡ The Stormbringer summons a lightning strike!", NamedTextColor.RED));
        }
    }

    private static class ColiseumInstance {
        private final Location center;
        private final List<UUID> activeGuards = new ArrayList<>();
        private final List<UUID> viewers = new ArrayList<>();
        private UUID bossPhantomUuid = null;
        private BossBar bossBar = null;
        private long respawnTime = 0;
        private long nextSpellTime = 0;

        public ColiseumInstance(Location center) {
            this.center = center;
        }
    }
}
