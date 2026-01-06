package twotoz.skittleEssentials.listeners;


import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.BuildmodeManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class BuildmodeListener implements Listener {

    private final SkittleEssentials plugin;
    private final BuildmodeManager buildmodeManager;
    private List<String> allowedCommands;
    private List<String> blockedBlocks;

    public BuildmodeListener(SkittleEssentials plugin, BuildmodeManager buildmodeManager) {
        this.plugin = plugin;
        this.buildmodeManager = buildmodeManager;
        loadConfig();
    }

    public void loadConfig() {
        allowedCommands = plugin.getConfig().getStringList("buildmode.allowed-commands");
        blockedBlocks = plugin.getConfig().getStringList("buildmode.blocked-blocks");
    }

    // Auto-disable buildmode on join
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            // Clear inventory
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            // Set gamemode survival
            player.setGameMode(GameMode.SURVIVAL);

            // Remove from buildmode
            buildmodeManager.removePlayer(player);

            player.sendMessage("§c§l✗ Buildmode has been disabled!");
            player.sendMessage("§7You logged out while in buildmode. Your inventory has been cleared.");

            plugin.getLogger().info(player.getName() + " joined while in buildmode - automatically disabled and inventory cleared.");
        }
    }

    // Prevent interactions with containers (but allow breaking empty ones)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();

        // Block opening containers (right-click)
        if (isContainer(type)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.sendMessage("§cYou can't open containers while in build mode!");
                return;
            }
        }

        // Block interactions with restricted blocks (spawners, command blocks, etc.)
        if (isRestrictedBlock(type)) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                player.sendMessage("§cYou can't interact with this block while in build mode!");
            }
        }
    }

    // CRITICAL: Prevent middle-click and ANY item with NBT data
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreativeInventoryAction(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Check BOTH cursor AND current item (middle-click can set either one)
        ItemStack[] itemsToCheck = {event.getCursor(), event.getCurrentItem()};

        for (ItemStack item : itemsToCheck) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            // Block restricted blocks (command blocks, barriers, spawners, bedrock, etc.)
            if (isRestrictedBlock(item.getType())) {
                event.setCancelled(true);
                player.sendMessage("§c§lBLOCKED! §cYou can't obtain " + item.getType().name() + " in build mode!");
                plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain " + item.getType().name() + " via creative inventory");
                return;
            }

            // Block bedrock specifically (very common exploit item)
            if (item.getType() == Material.BEDROCK) {
                event.setCancelled(true);
                player.sendMessage("§c§lBLOCKED! §cYou can't obtain bedrock!");
                plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain bedrock");
                return;
            }

            // Block items from config blocked list
            if (isBlockedBlock(item.getType())) {
                event.setCancelled(true);
                player.sendMessage("§c§lBLOCKED! §cYou can't obtain " + item.getType().name() + " in build mode!");
                plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain blocked item: " + item.getType().name());
                return;
            }

            // CRITICAL: Block ALL items with ANY NBT data/metadata
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();

                // Block if item has display name, lore, enchants, or any other metadata
                if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants() ||
                        !meta.getItemFlags().isEmpty() || meta.isUnbreakable()) {
                    event.setCancelled(true);
                    player.sendMessage("§c§lBLOCKED! §cYou can't obtain items with NBT data in build mode!");
                    plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain item with NBT data: " + item.getType().name());
                    return;
                }
            }

            // CRITICAL: Block ALL shulker boxes (can transport items even as items)
            if (isShulkerBox(item.getType())) {
                event.setCancelled(true);
                player.sendMessage("§c§lBLOCKED! §cYou can't obtain shulker boxes in build mode!");
                plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain shulker box");
                return;
            }
        }
    }

    // CRITICAL: Block entity interactions completely (armor stands, item frames, villagers, etc.)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Block ALL entity interactions to prevent exploits
        event.setCancelled(true);
        player.sendMessage("§cYou can't interact with entities while in build mode!");

        // Log specific entity types for monitoring
        plugin.getLogger().info("BLOCKED INTERACTION: " + player.getName() + " tried to interact with " +
                event.getRightClicked().getType() + " in buildmode");
    }

    // CRITICAL: Block placing armor stands, item frames, and other item-holding entities
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();

        if (player == null || !buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Block placing item frames, paintings, etc.
        event.setCancelled(true);
        player.sendMessage("§cYou can't place " + event.getEntity().getType().name() + " in build mode!");
        plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to place " + event.getEntity().getType().name());
    }

    // CRITICAL: Block breaking hanging entities (item frames, paintings) to prevent item drops
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!(event.getRemover() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getRemover();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Block breaking item frames, paintings, etc.
        event.setCancelled(true);
        player.sendMessage("§cYou can't break " + event.getEntity().getType().name() + " in build mode!");
        plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to break " + event.getEntity().getType().name());
    }

    // CRITICAL: Block spawning entities (armor stands, minecarts, boats, etc.)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        // Check if a player in buildmode spawned this entity
        if (event.getEntity() instanceof Player) {
            return;
        }

        // Try to find the player who spawned this entity (within 5 blocks)
        Entity entity = event.getEntity();

        for (Entity nearby : entity.getNearbyEntities(5, 5, 5)) {
            if (nearby instanceof Player) {
                Player player = (Player) nearby;

                if (buildmodeManager.isInBuildmode(player)) {
                    // Block spawning entities that could hold items
                    EntityType type = entity.getType();

                    if (isItemHoldingEntity(type)) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou can't spawn " + type.name() + " in build mode!");
                        plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to spawn " + type.name());
                        return;
                    }
                }
            }
        }
    }

    // CRITICAL: Block placing armor stands specifically (they can hold items)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandPlace(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.ARMOR_STAND) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place armor stands in build mode!");
            plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to place armor stand");
        }
    }

    // CRITICAL: Block vehicle creation (minecarts, boats can be used to transport items)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicleCreate(VehicleCreateEvent event) {
        Vehicle vehicle = event.getVehicle();

        // Check if a player in buildmode created this vehicle
        for (Entity nearby : vehicle.getNearbyEntities(5, 5, 5)) {
            if (nearby instanceof Player) {
                Player player = (Player) nearby;

                if (buildmodeManager.isInBuildmode(player)) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou can't create vehicles in build mode!");
                    plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to create " + vehicle.getType().name());
                    return;
                }
            }
        }
    }

    // CRITICAL: Prevent damaging/breaking entities (armor stands, item frames via punch, etc.)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if attacker is a player in buildmode
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            if (buildmodeManager.isInBuildmode(attacker)) {
                // Block ALL entity damage (prevents breaking armor stands, item frames, etc.)
                event.setCancelled(true);

                // Only send message for non-player entities
                if (!(event.getEntity() instanceof Player)) {
                    attacker.sendMessage("§cYou can't damage entities while in build mode!");
                    plugin.getLogger().info("BLOCKED: " + attacker.getName() + " tried to damage " + event.getEntity().getType());
                }
                return;
            }
        }

        // Check if victim is a player in buildmode
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();

            if (buildmodeManager.isInBuildmode(victim)) {
                event.setCancelled(true);

                if (event.getDamager() instanceof Player) {
                    Player attacker = (Player) event.getDamager();
                    attacker.sendMessage("§cYou can't attack players in build mode!");
                }
            }
        }
    }

    // Players in buildmode take no damage
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (buildmodeManager.isInBuildmode(player)) {
                event.setCancelled(true);
            }
        }
    }

    // CRITICAL: Prevent player death while in buildmode (extra safety layer)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (buildmodeManager.isInBuildmode(player)) {
            // Clear all drops to prevent ANY items from escaping
            event.getDrops().clear();
            event.setKeepInventory(false);

            plugin.getLogger().warning("CRITICAL: " + player.getName() + " died in buildmode - all drops cleared!");

            // Force clear inventory after respawn
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().clear();
                    player.getInventory().setArmorContents(null);
                    player.getInventory().setItemInOffHand(null);
                }
            }, 1L);
        }
    }

    // CRITICAL: Prevent breaking containers with items, and block bedrock/restricted blocks
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();

        // CRITICAL: Prevent breaking bedrock
        if (type == Material.BEDROCK) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't break bedrock!");
            return;
        }

        // Prevent breaking restricted blocks (spawners, command blocks, etc.)
        if (isRestrictedBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't break this block type while in build mode!");
            return;
        }

        // Prevent breaking blocked blocks
        if (isBlockedBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't break this block type while in build mode!");
            return;
        }

        // CRITICAL: Always prevent breaking shulker boxes
        if (isShulkerBox(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't break shulker boxes in build mode!");
            return;
        }

        // Check if block is a container
        if (isContainer(type)) {
            // Check if container is empty
            BlockState state = block.getState();

            if (state instanceof InventoryHolder) {
                InventoryHolder holder = (InventoryHolder) state;
                Inventory inventory = holder.getInventory();

                // Check if inventory has any items
                if (!isInventoryEmpty(inventory)) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou can't break containers with items in them!");
                    player.sendMessage("§7The container must be empty first.");
                    return;
                }
            }
        }

        // CRITICAL: Check for item-holding blocks (lectern, jukebox, etc.)
        if (isItemHoldingBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't break this block type in build mode!");
            plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to break item-holding block: " + type.name());
        }
    }

    // CRITICAL: Prevent ANY items from dropping when a buildmode player breaks a block
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Cancel ALL item drops from blocks broken by buildmode players
        event.setCancelled(true);

        // Log for security monitoring
        if (!event.getItems().isEmpty()) {
            plugin.getLogger().warning("CRITICAL: Prevented item drops from " + player.getName() +
                    " breaking " + event.getBlockState().getType().name() +
                    " (" + event.getItems().size() + " items would have dropped)");
        }
    }

    // CRITICAL: Prevent placing restricted blocks and containers
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();

        // Prevent placing bedrock
        if (type == Material.BEDROCK) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place bedrock!");
            return;
        }

        // Prevent placing restricted blocks
        if (isRestrictedBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place this block type while in build mode!");
            return;
        }

        // Prevent placing blocked blocks
        if (isBlockedBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place this block type while in build mode!");
            return;
        }

        // CRITICAL: Prevent placing ALL shulker boxes
        if (isShulkerBox(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place shulker boxes in build mode!");
            plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to place shulker box");
            return;
        }

        // CRITICAL: Prevent placing containers (they could contain items)
        if (isContainer(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place containers in build mode!");
            plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to place container: " + type.name());
            return;
        }

        // CRITICAL: Prevent placing item-holding blocks
        if (isItemHoldingBlock(type)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't place this block type in build mode!");
            plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to place item-holding block: " + type.name());
        }
    }

    // Prevent picking up items
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (buildmodeManager.isInBuildmode(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't pick up items while in build mode!");
        }
    }

    // Prevent dropping items
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't drop items while in build mode!");
        }
    }

    // Block inventory clicking to prevent item manipulation
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Allow clicking in creative inventory menu, but check what they're getting
        if (event.getClickedInventory() != null) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() != Material.AIR) {
                // Prevent getting bedrock
                if (clicked.getType() == Material.BEDROCK) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou can't obtain bedrock!");
                    return;
                }

                // Prevent getting blocked items
                if (isBlockedBlock(clicked.getType())) {
                    event.setCancelled(true);
                    player.sendMessage("§cYou can't obtain this item in build mode!");
                }
            }
        }
    }

    // Block commands (allowlist system - only commands in the list are allowed)
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1); // Remove the /

        // Check if command is in the allowed list
        boolean isAllowed = false;
        for (String allowedCmd : allowedCommands) {
            String allowed = allowedCmd.toLowerCase().replace("/", "");

            // Check exact match
            if (command.equals(allowed)) {
                isAllowed = true;
                break;
            }
        }

        // If command is not in allowlist, block it
        if (!isAllowed) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't use this command in build mode!");
            plugin.getLogger().info("BLOCKED COMMAND: " + player.getName() + " tried to use /" + command + " in buildmode");
        }
    }

    // CRITICAL: Always disable buildmode when changing worlds
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            plugin.getLogger().info(player.getName() + " changed worlds while in buildmode - force disabling.");

            // Clear inventory
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            // Set gamemode survival
            player.setGameMode(GameMode.SURVIVAL);

            // Remove from buildmode
            buildmodeManager.removePlayer(player);

            player.sendMessage("§c§l✗ Buildmode has been disabled!");
            player.sendMessage("§7You changed worlds. Your inventory has been cleared.");
        }
    }

    // Auto-disable buildmode on quit
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            // Clear inventory
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);

            // Set gamemode survival
            player.setGameMode(GameMode.SURVIVAL);

            // Remove from buildmode
            buildmodeManager.removePlayer(player);

            plugin.getLogger().info(player.getName() + " has left the server in build mode - inventory cleared and set back to survival.");
        }
    }

    // Helper method to check if an inventory is empty
    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    // Helper method to check if a material is a container
    private boolean isContainer(Material material) {
        switch (material) {
            // Chests
            case CHEST:
            case TRAPPED_CHEST:
            case ENDER_CHEST:

                // Shulker boxes
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

                // Other containers
            case BARREL:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING_STAND:
                return true;

            default:
                return false;
        }
    }

    // Helper method to check if a block is in the blocked list
    private boolean isBlockedBlock(Material material) {
        String materialName = material.name();

        for (String blocked : blockedBlocks) {
            if (materialName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }

        return false;
    }

    // Helper method to check restricted blocks (valuable/exploitable - cannot be broken or placed)
    private boolean isRestrictedBlock(Material material) {
        switch (material) {
            case SPAWNER:
            case END_PORTAL_FRAME:
            case COMMAND_BLOCK:
            case CHAIN_COMMAND_BLOCK:
            case REPEATING_COMMAND_BLOCK:
            case STRUCTURE_BLOCK:
            case JIGSAW:
            case BARRIER:
            case LIGHT:
            case BEDROCK:
                return true;
            default:
                return false;
        }
    }

    // Helper method to check if material is a shulker box
    private boolean isShulkerBox(Material material) {
        switch (material) {
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
                return true;
            default:
                return false;
        }
    }

    // Helper method to check if block can hold items (lectern, jukebox, etc.)
    private boolean isItemHoldingBlock(Material material) {
        switch (material) {
            case LECTERN:
            case JUKEBOX:
            case FLOWER_POT:
                return true;
            default:
                return false;
        }
    }

    // Helper method to check if entity type can hold items
    private boolean isItemHoldingEntity(EntityType type) {
        switch (type) {
            case ARMOR_STAND:
            case ITEM_FRAME:
            case GLOW_ITEM_FRAME:
            case MINECART:
            case CHEST_MINECART:
            case HOPPER_MINECART:
            case FURNACE_MINECART:
            case BOAT:
            case CHEST_BOAT:
                return true;
            default:
                return false;
        }
    }
}