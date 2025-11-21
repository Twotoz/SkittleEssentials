package twotoz.skittleEssentials.listeners;


import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.JailbanManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JailbanListener implements Listener {

    private final SkittleEssentials plugin;
    private final JailbanManager jailbanManager;
    private final StaffChatListener staffChatListener;

    // Track last block position to avoid checking every tiny movement - Thread-safe
    private final Map<UUID, Location> lastBlockPosition = new ConcurrentHashMap<>();

    public JailbanListener(SkittleEssentials plugin, JailbanManager jailbanManager) {
        this(plugin, jailbanManager, null);
    }

    public JailbanListener(SkittleEssentials plugin, JailbanManager jailbanManager, StaffChatListener staffChatListener) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;
        this.staffChatListener = staffChatListener;
    }

    /**
     * Efficient movement checking - only check when player moves to a different block
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Quick return if not jailbanned
        if (!jailbanManager.isJailbanned(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // Only check if player moved to a different block
        if (from.getBlockX() == to.getBlockX() &&
                from.getBlockY() == to.getBlockY() &&
                from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Check if player is trying to leave jail region
        if (!jailbanManager.isInJailRegion(to)) {
            event.setCancelled(true);
            jailbanManager.enforceJailBounds(player);
        }
    }

    /**
     * Handle teleports (prevent escaping via /tp, /home, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!jailbanManager.isJailbanned(player)) {
            return;
        }

        // Staff bypass
        if (player.hasPermission("skittle.jailban.bypass")) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Allow teleport if staying within jail region
        if (jailbanManager.isInJailRegion(to)) {
            return;
        }

        // Cancel any teleport trying to leave jail
        event.setCancelled(true);
        player.sendMessage("§c§l⚠ You can't leave jail via teleportation!");
    }

    /**
     * Block commands for jailed players
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!jailbanManager.isJailbanned(player)) {
            return;
        }

        // Staff bypass
        if (player.hasPermission("skittle.jailban.bypass")) {
            return;
        }

        String command = event.getMessage().substring(1).split(" ")[0].toLowerCase();

        // Check if command is allowed
        if (!jailbanManager.canUseCommand(player, command)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't use commands while in jail!");
        }
    }

    /**
     * Handle jail chat system
     */
    @SuppressWarnings("deprecation") // Using AsyncPlayerChatEvent for compatibility
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String message = event.getMessage();

        // Skip jail chat if player is using staff chat
        if (sender.hasPermission("skittle.staffchat")) {
            // Check for ! prefix
            if (message.startsWith("!")) {
                return; // Let StaffChatListener handle it
            }

            // Check for staff chat toggle (if StaffChatListener is available)
            if (staffChatListener != null && staffChatListener.hasStaffChatToggle(sender)) {
                return; // Let StaffChatListener handle it
            }
        }

        // Check if sender is in jail region OR jailbanned
        boolean senderInJail = jailbanManager.isInJailRegion(sender.getLocation());
        boolean senderJailbanned = jailbanManager.isJailbanned(sender);

        // If sender is in jail, convert to jail chat
        if (senderInJail || senderJailbanned) {
            event.setCancelled(true);

            // Send jail message (synchronously)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                jailbanManager.sendJailMessage(sender, message);
            });

            return;
        }

        // If sender is not in jail, remove jailed players from recipients
        event.getRecipients().removeIf(recipient -> {
            // Keep staff
            if (recipient.hasPermission("skittle.jailban.notify")) {
                return false;
            }

            // Remove if jailbanned or in jail region
            return jailbanManager.isJailbanned(recipient) ||
                    jailbanManager.isInJailRegion(recipient.getLocation());
        });
    }

    /**
     * Teleport jailed players to jail on join
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (jailbanManager.isJailbanned(player)) {
            // Teleport to jail spawn
            Location jailSpawn = jailbanManager.getJailSpawn();
            if (jailSpawn != null) {
                player.teleport(jailSpawn);
            }

            double current = jailbanManager.getCurrentBalance(player);
            double required = jailbanManager.getBailRequired(player);

            player.sendMessage("§c§l⚖ You are in jail!");
            player.sendMessage("§7Reason: §e" + jailbanManager.getJailReason(player));
            player.sendMessage("§7Bail Required: §e$" + String.format("%.2f", required));
            player.sendMessage("§7Current Balance: §a$" + String.format("%.2f", current));
            player.sendMessage("§7Kill mobs to earn money! Use §e/jailbal §7to check progress.");
        }
    }

    /**
     * Clean up tracking data on quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastBlockPosition.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Prevent jailed players from dropping items
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (jailbanManager.isJailbanned(player)) {
            event.setCancelled(true);
            player.sendMessage("§cYou can't drop items while in jail!");
        }
    }

    /**
     * Prevent jailed players from picking up items
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();

        if (jailbanManager.isJailbanned(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent jailed players from interacting with certain things
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!jailbanManager.isJailbanned(player)) {
            return;
        }

        // Check config for blocked interactions
        if (plugin.getConfig().getBoolean("jailban.block-interactions", true)) {
            if (event.getClickedBlock() != null) {
                // Allow walking but block certain interactions
                switch (event.getClickedBlock().getType()) {
                    case CHEST:
                    case TRAPPED_CHEST:
                    case ENDER_CHEST:
                    case FURNACE:
                    case CRAFTING_TABLE:
                    case ANVIL:
                    case ENCHANTING_TABLE:
                        event.setCancelled(true);
                        player.sendMessage("§cYou can't interact with this while in jail!");
                        break;
                    default:
                        break;
                }
            }
        }
    }

    /**
     * Handle entity deaths - give jailed players money for killing mobs
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        // Check if killed by a player
        if (killer == null) {
            return;
        }

        // Check if killer is jailbanned
        if (!jailbanManager.isJailbanned(killer)) {
            return;
        }

        // Don't give money for killing other players
        if (entity instanceof Player) {
            return;
        }

        // Give $1 per mob kill
        double earnAmount = 1.0;
        jailbanManager.addBalance(killer, earnAmount);

        double current = jailbanManager.getCurrentBalance(killer);
        double required = jailbanManager.getBailRequired(killer);

        // Send message to player
        killer.sendMessage("§a+$" + String.format("%.2f", earnAmount) + " §7(§a$" + String.format("%.2f", current) + "§7/§e$" + String.format("%.2f", required) + "§7)");

        // Check if they can now bail out
        if (jailbanManager.canAffordBail(killer)) {
            killer.sendMessage("§a§l✓ You have enough money to bail out! Use §e/bail confirm");
        }
    }
}