package com.hereskyreign.hereskyreign.listener;

import com.hereskyreign.hereskyreign.HereSkyReignPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class CloudBlockListener implements Listener {

    private final HereSkyReignPlugin plugin;
    private final NamespacedKey cloudBlockKey;

    public CloudBlockListener(HereSkyReignPlugin plugin) {
        this.plugin = plugin;
        this.cloudBlockKey = new NamespacedKey(plugin, "cloud_block");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BROWN_MUSHROOM_BLOCK) {
            return;
        }

        if (block.getBlockData() instanceof MultipleFacing mf) {
            // A cloud block has all 6 directional faces set to false
            boolean isCloud = true;
            for (BlockFace face : mf.getAllowedFaces()) {
                if (mf.hasFace(face)) {
                    isCloud = false;
                    break;
                }
            }

            if (isCloud) {
                Player player = event.getPlayer();
                ItemStack tool = player.getInventory().getItemInMainHand();

                if (tool.getType() != Material.AIR && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    event.setDropItems(false); // Prevent standard mushroom drops

                    // Drop custom cloud block item
                    ItemStack cloudItem = new ItemStack(Material.BROWN_MUSHROOM_BLOCK);
                    ItemMeta meta = cloudItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(net.kyori.adventure.text.Component.text("Cloud Block")
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE));

                        // Set custom 1.21.4 item model reference
                        meta.setItemModel(new NamespacedKey("minecraft", "cloud_block"));

                        // Tag with PDC so we can detect it when placed
                        meta.getPersistentDataContainer().set(cloudBlockKey, PersistentDataType.BYTE, (byte) 1);

                        cloudItem.setItemMeta(meta);
                    }

                    block.getWorld().dropItemNaturally(block.getLocation(), cloudItem);
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.BROWN_MUSHROOM_BLOCK || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(cloudBlockKey, PersistentDataType.BYTE)) {
            Block block = event.getBlockPlaced();
            if (block.getBlockData() instanceof MultipleFacing mf) {
                // Force all faces to false to render the pore texture (custom cloud) on all sides
                for (BlockFace face : mf.getAllowedFaces()) {
                    mf.setFace(face, false);
                }
                block.setBlockData(mf, false); // Set data without physics updates to prevent resetting
            }
        }
    }
}
