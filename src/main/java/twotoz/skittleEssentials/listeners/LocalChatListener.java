package twotoz.skittleEssentials.listeners;

import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.JailbanManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public class LocalChatListener implements Listener, CommandExecutor {

    private final SkittleEssentials plugin;
    private final JailbanManager jailbanManager;
    private final StaffChatListener staffChatListener;

    // Config values
    private boolean enabled;
    private String localPrefix;
    private String spyPrefix;
    private String chatFormat;
    private double chatRadius;
    private double chatRadiusSquared;

    // Thread-safe maps for toggles
    private final Map<UUID, Boolean> localChatToggle = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spyToggle = new ConcurrentHashMap<>();

    public LocalChatListener(SkittleEssentials plugin, JailbanManager jailbanManager, StaffChatListener staffChatListener) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;
        this.staffChatListener = staffChatListener;
        loadConfig();

        // Register command executors
        plugin.getCommand("localchat").setExecutor(this);
        plugin.getCommand("localchatspy").setExecutor(this);
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("localchat.enabled", true);
        localPrefix = plugin.getConfig().getString("localchat.prefix", "&e[&6LocalChat&e]");
        spyPrefix = plugin.getConfig().getString("localchat.spy-prefix", "&6[LocalSpy]");
        chatFormat = plugin.getConfig().getString("localchat.format", "&f{player} &8: &7{message}");
        chatRadius = plugin.getConfig().getDouble("localchat.radius", 20.0);
        chatRadiusSquared = chatRadius * chatRadius; // Pre-calculate for performance
    }

    /**
     * Check if local chat module is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Handle /localchat and /localchatspy commands
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // Check if module is enabled
        if (!enabled) {
            player.sendMessage("§cLocal chat is currently disabled!");
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        // Route to appropriate handler
        if (cmdName.equals("localchatspy") || cmdName.equals("lcspy") || cmdName.equals("lspy")) {
            return handleSpyToggle(player);
        }

        return handleLocalToggle(player, label);
    }

    /**
     * Handle /localchat toggle
     */
    private boolean handleLocalToggle(Player player, String label) {
        if (!player.hasPermission("skittle.localchat.use")) {
            player.sendMessage("§cYou don't have permission to use local chat!");
            return true;
        }

        UUID playerId = player.getUniqueId();
        boolean currentStatus = localChatToggle.getOrDefault(playerId, false);
        boolean newStatus = !currentStatus;

        localChatToggle.put(playerId, newStatus);

        if (newStatus) {
            player.sendMessage("§eLocal chat mode §2§lENABLED§e! Your messages will be sent within " + ((int) chatRadius) + " blocks.");
            player.sendMessage("§7Use §f/" + label + " §7again to disable.");
        } else {
            player.sendMessage("§eLocal chat mode §c§lDISABLED§e! Your messages will now be sent globally.");
        }

        return true;
    }

    /**
     * Handle /localchatspy toggle
     */
    private boolean handleSpyToggle(Player player) {
        if (!player.hasPermission("skittle.localchat.spy")) {
            player.sendMessage("§cYou don't have permission to use local chat spy!");
            return true;
        }

        UUID playerId = player.getUniqueId();
        boolean currentStatus = spyToggle.getOrDefault(playerId, false);
        boolean newStatus = !currentStatus;

        spyToggle.put(playerId, newStatus);

        if (newStatus) {
            player.sendMessage("§6Local Chat Spy §2§lENABLED§6! You will see all local messages globally.");
        } else {
            player.sendMessage("§6Local Chat Spy §c§lDISABLED§6!");
        }

        return true;
    }

    /**
     * Handle local chat with "?" prefix or toggle mode
     * Priority LOW: After StaffChat (LOWEST), before JailChat (HIGH)
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncChatEvent event) {
        // Skip if module is disabled
        if (!enabled) {
            return;
        }

        Player sender = event.getPlayer();

        // Skip if already cancelled by higher priority handler (e.g., StaffChat)
        if (event.isCancelled()) {
            return;
        }

        // Get message from Adventure API
        String message;
        if (event.message() instanceof TextComponent) {
            message = ((TextComponent) event.message()).content();
        } else {
            message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message());
        }

        // Check triggers: toggle or "?" prefix
        boolean hasToggle = localChatToggle.getOrDefault(sender.getUniqueId(), false);
        boolean hasPrefix = message.startsWith("?");

        if (!hasToggle && !hasPrefix) {
            return; // Not a local chat message
        }

        // CRITICAL: Local chat doesn't work in jail region
        // Players in jail should use jail chat instead
        if (jailbanManager != null && jailbanManager.isInJailRegion(sender.getLocation())) {
            if (hasPrefix) {
                // Only show error if they explicitly tried with "?"
                // If toggle is on, let it fall through to jail chat
                sender.sendMessage("§cLocal chat does not work in jail!");
                event.setCancelled(true);
            }
            return; // Let jail chat handle it
        }

        // Permission check - if no permission, let message pass through as normal chat
        if (!sender.hasPermission("skittle.localchat.use")) {
            return; // Message will be sent as normal chat with "?" prefix
        }

        // Extract message content BEFORE canceling event
        String localMessageContent = hasPrefix ? message.substring(1).trim() : message.trim();

        // If empty message, let it pass through as normal chat (just "?" in chat)
        if (localMessageContent.isEmpty()) {
            return; // Don't cancel - "?" will be sent as normal chat
        }

        // Now we know it's a valid local chat message - cancel event to prevent global broadcast
        event.setCancelled(true);

        // Format messages
        String rawFormat = chatFormat
                .replace("{player}", sender.getName())
                .replace("{message}", localMessageContent);

        String localFormatted = (localPrefix + " " + rawFormat).replace("&", "§");
        String spyFormatted = (spyPrefix + " " + rawFormat).replace("&", "§");

        // Distribute messages (synchronously to avoid threading issues)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location senderLoc = sender.getLocation();

            for (Player recipient : Bukkit.getOnlinePlayers()) {
                // Check if recipient has spy permission
                boolean isSpy = spyToggle.getOrDefault(recipient.getUniqueId(), false)
                        && recipient.hasPermission("skittle.localchat.spy");

                // Check if recipient is in range
                boolean inRange = isInRange(senderLoc, recipient.getLocation());

                if (inRange) {
                    // Recipient must have permission to see local messages
                    if (recipient.hasPermission("skittle.localchat.use")) {
                        recipient.sendMessage(localFormatted);
                    }
                } else if (isSpy) {
                    // Spy can see all local messages (even outside range)
                    recipient.sendMessage(spyFormatted);
                }
            }
        });

        // Log to console
        plugin.getLogger().info("[LocalChat] " + sender.getName() + ": " + localMessageContent);
    }

    /**
     * Efficiently check if recipient is in range using squared distance
     * Avoids expensive Math.sqrt() call
     */
    private boolean isInRange(Location senderLoc, Location recipientLoc) {
        // Must be in same world
        if (!senderLoc.getWorld().equals(recipientLoc.getWorld())) {
            return false;
        }

        // Use squared distance for performance
        return senderLoc.distanceSquared(recipientLoc) <= chatRadiusSquared;
    }

    /**
     * Check if player has local chat toggle enabled
     * Used by other listeners to check state
     */
    public boolean hasLocalChatToggle(UUID playerId) {
        return localChatToggle.getOrDefault(playerId, false);
    }

    /**
     * Check if player has local chat toggle enabled
     */
    public boolean hasLocalChatToggle(Player player) {
        return hasLocalChatToggle(player.getUniqueId());
    }

    /**
     * Cleanup HashMaps when players leave to prevent memory leaks
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        localChatToggle.remove(playerId);
        spyToggle.remove(playerId);
    }
}