package twotoz.skittleEssentials.listeners;

import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.BuildmodeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.HashSet;
import java.util.Set;

public class BuildmodeListener implements Listener {

    private final SkittleEssentials plugin;
    private final BuildmodeManager buildmodeManager;
    private Set<String> allowedCommands;
    private Set<Material> blockedBlocks;
    private boolean isFolia = false;

    public BuildmodeListener(SkittleEssentials plugin, BuildmodeManager buildmodeManager) {
        this.plugin = plugin;
        this.buildmodeManager = buildmodeManager;

        // Detect Folia
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        loadConfig();
    }

    public void loadConfig() {
        allowedCommands = new HashSet<>(plugin.getConfig().getStringList("buildmode.allowed-commands"));

        blockedBlocks = new HashSet<>();
        for (String materialName : plugin.getConfig().getStringList("buildmode.blocked-blocks")) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                blockedBlocks.add(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in blocked-blocks: " + materialName);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();
        ItemStack itemInHand = event.getItemInHand();

        // CRITICAL: Block ALL container blocks (prevents duping via stored items)
        if (isContainerBlock(blockType)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✗ You cannot place container blocks in buildmode!");
            player.sendMessage("§7Reason: Can be used to duplicate items");
            return;
        }

        // CRITICAL: Block items with BlockStateMeta (NBT data containers)
        if (itemInHand != null && itemInHand.hasItemMeta()) {
            if (itemInHand.getItemMeta() instanceof BlockStateMeta) {
                BlockStateMeta meta = (BlockStateMeta) itemInHand.getItemMeta();

                // Check if the BlockStateMeta contains a container state
                if (meta.hasBlockState()) {
                    BlockState state = meta.getBlockState();

                    // Block if it's any inventory holder
                    if (state instanceof org.bukkit.block.Container ||
                            state instanceof org.bukkit.block.Chest ||
                            state instanceof org.bukkit.block.ShulkerBox ||
                            state instanceof org.bukkit.block.Barrel ||
                            state instanceof org.bukkit.block.Furnace ||
                            state instanceof org.bukkit.block.Hopper ||
                            state instanceof org.bukkit.block.Dropper ||
                            state instanceof org.bukkit.block.Dispenser) {

                        event.setCancelled(true);
                        player.sendMessage("§c§l✗ You cannot place blocks with stored items in buildmode!");
                        player.sendMessage("§7Reason: NBT data contains inventory - possible dupe exploit");

                        plugin.getLogger().warning("DUPE ATTEMPT: " + player.getName() +
                                " tried to place " + blockType + " with NBT inventory data");
                        return;
                    }
                }
            }

            // Additional check: Block any item with custom NBT that might contain items
            if (hasInventoryNBT(itemInHand)) {
                event.setCancelled(true);
                player.sendMessage("§c§l✗ You cannot place items with NBT inventory data in buildmode!");
                player.sendMessage("§7Reason: Possible dupe exploit");

                plugin.getLogger().warning("DUPE ATTEMPT: " + player.getName() +
                        " tried to place item with NBT inventory: " + itemInHand.getType());
                return;
            }
        }

        // Block specific materials from config
        if (blockedBlocks.contains(blockType)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✗ You cannot place §e" + blockType.name() + " §cin buildmode!");
            return;
        }

        // Success message (optional, can be removed if too spammy)
        // player.sendMessage("§7Block placed in buildmode");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();

        // Block breaking container blocks (prevents item extraction)
        if (isContainerBlock(blockType)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✗ You cannot break container blocks in buildmode!");
            player.sendMessage("§7Reason: Can be used to obtain items");
            return;
        }

        // Block breaking specific materials
        if (blockedBlocks.contains(blockType)) {
            event.setCancelled(true);
            player.sendMessage("§c§l✗ You cannot break §e" + blockType.name() + " §cin buildmode!");
            return;
        }

        // Success - no drops in creative anyway
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1); // Remove '/'

        // Check if command is allowed
        boolean allowed = false;
        for (String allowedCommand : allowedCommands) {
            if (command.equalsIgnoreCase(allowedCommand)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("§c§l✗ You cannot use this command in buildmode!");
            player.sendMessage("§7Use §e/buildmode off §7to exit buildmode first.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            // Schedule on player's region (Folia-safe)
            if (isFolia) {
                player.getScheduler().run(plugin, (task) -> {
                    disableBuildmodeOnQuit(player);
                }, null);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> disableBuildmodeOnQuit(player));
            }
        }
    }

    private void disableBuildmodeOnQuit(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        buildmodeManager.removePlayer(player);
        plugin.getLogger().info("Removed " + player.getName() + " from buildmode (quit)");
    }

    /**
     * Check if a material is a container block
     */
    private boolean isContainerBlock(Material material) {
        switch (material) {
            // Storage containers
            case CHEST:
            case TRAPPED_CHEST:
            case ENDER_CHEST:
            case BARREL:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:

                // Item handlers
            case HOPPER:
            case DROPPER:
            case DISPENSER:

                // Furnaces
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:

                // Other containers
            case BREWING_STAND:
            case LECTERN:
            case JUKEBOX:
                return true;

            default:
                return false;
        }
    }

    /**
     * Check if an item has inventory NBT data (advanced dupe protection)
     */
    private boolean hasInventoryNBT(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        // Check for BlockStateMeta with inventory
        if (item.getItemMeta() instanceof BlockStateMeta) {
            return true;
        }

        // For additional safety, you could add NBT tag checking here
        // This would require NMS or a library like NBT-API

        return false;
    }
}