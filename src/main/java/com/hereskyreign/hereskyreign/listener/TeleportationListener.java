package com.hereskyreign.hereskyreign.listener;

import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportationListener implements Listener {

    private final HereSkyReignPlugin plugin;
    private final Map<UUID, Long> teleportCooldowns = new ConcurrentHashMap<>();

    public TeleportationListener(HereSkyReignPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Player player = event.getPlayer();

        // Perform fast threshold checks first to avoid extra object lookups/calculations
        double y = to.getY();
        World world = to.getWorld();
        if (world == null) return;

        long now = System.currentTimeMillis();
        Long cooldown = teleportCooldowns.get(player.getUniqueId());
        if (cooldown != null && cooldown > now) {
            return;
        }

        String skyReignName = plugin.getConfig().getString("world-name", "sky_reign");
        if (world.getName().equals(skyReignName)) {
            // Sky Reign -> Overworld
            double thresholdY = plugin.getConfig().getDouble("teleportation.skyreign-to-overworld-y", 0.0);
            if (y < thresholdY) {
                World overworld = Bukkit.getWorlds().get(0); // Standard primary overworld
                if (overworld != null) {
                    Location target = to.clone();
                    target.setWorld(overworld);
                    // Teleport to the Overworld ceiling
                    double overworldY = plugin.getConfig().getDouble("teleportation.overworld-to-skyreign-y", 320.0);
                    target.setY(overworldY);

                    // Maintain momentum
                    Vector velocity = player.getVelocity();

                    player.teleport(target);
                    player.setVelocity(velocity);

                    long cooldownTicks = plugin.getConfig().getLong("teleportation.cooldown-ticks", 100);
                    teleportCooldowns.put(player.getUniqueId(), now + (cooldownTicks * 50L));
                }
            }
        } else if (world.getEnvironment() == World.Environment.NORMAL) {
            // Overworld -> Sky Reign
            double thresholdY = plugin.getConfig().getDouble("teleportation.overworld-to-skyreign-y", 320.0);
            if (y > thresholdY) {
                World skyReign = Bukkit.getWorld(skyReignName);
                if (skyReign != null) {
                    Location target = to.clone();
                    target.setWorld(skyReign);
                    // Teleport to the Sky Reign floor (just above threshold to prevent loop)
                    double skyReignY = plugin.getConfig().getDouble("teleportation.skyreign-to-overworld-y", 0.0) + 2.0;
                    target.setY(skyReignY);

                    Vector velocity = player.getVelocity();

                    player.teleport(target);
                    player.setVelocity(velocity);

                    long cooldownTicks = plugin.getConfig().getLong("teleportation.cooldown-ticks", 100);
                    teleportCooldowns.put(player.getUniqueId(), now + (cooldownTicks * 50L));
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        teleportCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
