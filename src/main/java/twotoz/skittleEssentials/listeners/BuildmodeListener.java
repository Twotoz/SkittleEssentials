package twotoz.skittleEssentials.listeners;


import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.BuildmodeManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDamageEvent;


import java.util.List;

public class BuildmodeListener implements Listener {

    private final SkittleEssentials plugin;
    private final BuildmodeManager buildmodeManager;
    private List<String> blockedCommands;
    private List<String> blockedBlocks;

    public BuildmodeListener(SkittleEssentials plugin, BuildmodeManager buildmodeManager) {
        this.plugin = plugin;
        this.buildmodeManager = buildmodeManager;
        loadConfig();
    }

    public void loadConfig() {
        blockedCommands = plugin.getConfig().getStringList("buildmode.blocked-commands");
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

    // IMPROVED: Prevent middle-click (pick block) in creative mode - this copies NBT data and allows getting restricted items!
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

            // Block items with NBT data (from middle-clicking containers with items)
            if (item.hasItemMeta()) {
                event.setCancelled(true);
                player.sendMessage("§c§lBLOCKED! §cYou can't obtain items with NBT data in build mode!");
                plugin.getLogger().warning("BLOCKED: " + player.getName() + " tried to obtain item with NBT data");
                return;
            }
        }
    }

    // Prevent interacting with ALL entities that could be exploited
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        // Block ALL entity interactions in buildmode to prevent ANY exploit
        event.setCancelled(true);
        player.sendMessage("§cYou can't interact with entities while in build mode!");

        // Log for debugging/monitoring
        plugin.getLogger().info(player.getName() + " tried to interact with " +
                event.getRightClicked().getType() + " in buildmode (blocked)");
    }

    // Prevent entity damage to block mob farms and entity killing exploits
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            // Players in buildmode take no damage
            if (buildmodeManager.isInBuildmode(player)) {
                event.setCancelled(true);
            }
        }
    }

    // Prevent breaking containers with items, and block bedrock/restricted blocks
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
    }

    // Prevent placing restricted blocks
    @EventHandler(priority = EventPriority.HIGH)
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

    // Prevent PvP
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if attacker is a player in buildmode
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();

            if (buildmodeManager.isInBuildmode(attacker)) {
                event.setCancelled(true);
                attacker.sendMessage("§cYou can't PvP while in build mode!");
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

    // Block certain commands
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!buildmodeManager.isInBuildmode(player)) {
            return;
        }

        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1); // Remove the /

        // Check if command is blocked
        for (String blockedCmd : blockedCommands) {
            String blocked = blockedCmd.toLowerCase();

            // Check exact match or with /
            if (command.equals(blocked) || command.equals(blocked.replace("/", ""))) {
                event.setCancelled(true);
                player.sendMessage("§cYou can't use this command in build mode!");
                return;
            }
        }
    }

    // CRITICAL: Force check gamemode when changing worlds to prevent exploits
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (buildmodeManager.isInBuildmode(player)) {
            plugin.getLogger().info(player.getName() + " changed worlds while in buildmode.");

            // Force them back to creative if they're not in creative anymore
            if (player.getGameMode() != GameMode.CREATIVE) {
                plugin.getLogger().warning("EXPLOIT ATTEMPT: " + player.getName() + " changed worlds and lost creative mode!");
                plugin.getLogger().warning("Forcing back to creative mode...");
                player.setGameMode(GameMode.CREATIVE);
            }
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
}