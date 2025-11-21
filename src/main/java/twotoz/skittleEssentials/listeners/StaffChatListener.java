package twotoz.skittleEssentials.listeners;


import twotoz.skittleEssentials.SkittleEssentials;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StaffChatListener implements Listener, CommandExecutor {

    private final SkittleEssentials plugin;
    private String staffPrefix;
    private String staffFormat;

    // Thread-safe HashMap for staff chat toggle
    private final Map<UUID, Boolean> staffChatToggle = new ConcurrentHashMap<>();

    public StaffChatListener(SkittleEssentials plugin) {
        this.plugin = plugin;
        loadConfig();

        // Register command executor
        plugin.getCommand("staffchat").setExecutor(this);
    }

    public void loadConfig() {
        staffPrefix = plugin.getConfig().getString("staffchat.prefix", "&7[&c&lSTAFF&7]");
        staffFormat = plugin.getConfig().getString("staffchat.format", "&f{player} &8» &7{message}");
    }

    /**
     * Handle /staffchat, /sct, /sc command
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check if sender is a player
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("skittle.staffchat")) {
            player.sendMessage("§cYou don't have permission to use staff chat!");
            return true;
        }

        // Toggle staff chat mode
        UUID playerId = player.getUniqueId();
        boolean currentStatus = staffChatToggle.getOrDefault(playerId, false);
        boolean newStatus = !currentStatus;

        staffChatToggle.put(playerId, newStatus);

        // Send feedback message
        if (newStatus) {
            player.sendMessage("§aStaff chat mode §2§lENABLED§a! All your messages will now go to staff chat.");
            player.sendMessage("§7Use §f/" + label + " §7again to disable.");
        } else {
            player.sendMessage("§cStaff chat mode §4§lDISABLED§c! Your messages will now be sent to public chat.");
        }

        return true;
    }

    /**
     * Handle staff chat with "!" prefix or toggle mode
     * LOWEST priority so we catch it FIRST before other chat plugins (like jail chat)
     * Using modern Paper AsyncChatEvent (not deprecated)
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        // Get message from the new Adventure API
        String message;
        if (event.message() instanceof TextComponent) {
            message = ((TextComponent) event.message()).content();
        } else {
            message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message());
        }

        // Check if player has staff permission
        if (!sender.hasPermission("skittle.staffchat")) {
            return;
        }

        boolean hasToggle = staffChatToggle.getOrDefault(sender.getUniqueId(), false);
        boolean hasPrefix = message.startsWith("!");

        // If they have toggle enabled OR used "!" prefix, send to staff chat
        if (!hasToggle && !hasPrefix) {
            return;
        }

        // Cancel the event so DiscordSRV and other plugins don't see it
        event.setCancelled(true);

        // Remove the "!" prefix if it exists
        String staffMessage = hasPrefix ? message.substring(1).trim() : message.trim();

        // Don't send empty messages
        if (staffMessage.isEmpty()) {
            // Synchronize back to main thread for safety
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage("§cYou can't send an empty staff message!");
            });
            return;
        }

        // Format the staff message
        String formattedMessage = (staffPrefix + " " + staffFormat)
                .replace("&", "§")
                .replace("{player}", sender.getName())
                .replace("{message}", staffMessage);

        // Send to all staff members (synchronously to avoid async issues)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("skittle.staffchat")) {
                    staff.sendMessage(formattedMessage);
                }
            }
        });

        // Log to console
        plugin.getLogger().info("[STAFF CHAT] " + sender.getName() + ": " + staffMessage);
    }

    /**
     * Cleanup HashMap when players leave to prevent memory leaks
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        staffChatToggle.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Check if player has staff chat toggle enabled
     */
    public boolean hasStaffChatToggle(UUID playerId) {
        return staffChatToggle.getOrDefault(playerId, false);
    }

    /**
     * Check if player has staff chat toggle enabled
     */
    public boolean hasStaffChatToggle(Player player) {
        return hasStaffChatToggle(player.getUniqueId());
    }
}