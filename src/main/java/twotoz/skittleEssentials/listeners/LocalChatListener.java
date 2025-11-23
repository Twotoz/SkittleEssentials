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

    private String localPrefix;
    private String spyPrefix;
    private String chatFormat;
    private double chatRadius;
    private double chatRadiusSquared;

    // Thread-safe maps for toggles
    // Stores players who have local chat toggled ON
    private final Map<UUID, Boolean> localChatToggle = new ConcurrentHashMap<>();
    // Stores staff who have spy mode toggled ON
    private final Map<UUID, Boolean> spyToggle = new ConcurrentHashMap<>();

    public LocalChatListener(SkittleEssentials plugin, JailbanManager jailbanManager, StaffChatListener staffChatListener) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;
        this.staffChatListener = staffChatListener;
        loadConfig();
    }

    public void loadConfig() {
        localPrefix = plugin.getConfig().getString("localchat.prefix", "&e[LocalChat]");
        spyPrefix = plugin.getConfig().getString("localchat.spy-prefix", "&6[LocalChatSpy]");
        chatFormat = plugin.getConfig().getString("localchat.format", "&f{player} &8: &7{message}");
        chatRadius = plugin.getConfig().getDouble("localchat.radius", 100.0);
        // Calculate squared radius for efficient distance checks
        chatRadiusSquared = chatRadius * chatRadius;
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
        String cmdName = command.getName().toLowerCase();

        // Handle separated commands
        if (cmdName.equals("localchatspy") || cmdName.equals("lcspy") || cmdName.equals("lspy")) {
            return handleSpyToggle(player);
        }

        // Default to local chat toggle
        return handleLocalToggle(player, label);
    }

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
            player.sendMessage("§eLocal chat mode §2§lENABLED§e! Your messages will be sent locally.");
            player.sendMessage("§7Use §f/" + label + " §7again to disable.");
        } else {
            player.sendMessage("§eLocal chat mode §c§lDISABLED§e! Your messages will be sent globally.");
        }

        return true;
    }

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
            player.sendMessage("§6Local Chat Spy §2§lENABLED§6! You will see all local messages.");
        } else {
            player.sendMessage("§6Local Chat Spy §c§lDISABLED§6!");
        }

        return true;
    }

    /**
     * Handle local chat with "?" prefix or toggle mode
     * Priority LOW:
     * - Runs AFTER StaffChat (Lowest), so we don't pick up staff messages.
     * - Runs BEFORE JailbanListener (High), but we manually check jail status.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncChatEvent event) {
        Player sender = event.getPlayer();

        // 1. Skip if already cancelled (e.g., by StaffChat)
        if (event.isCancelled()) {
            return;
        }

        // Get message text
        String message;
        if (event.message() instanceof TextComponent) {
            message = ((TextComponent) event.message()).content();
        } else {
            message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message());
        }

        // 2. Check Triggers (Prefix or Toggle)
        boolean hasToggle = localChatToggle.getOrDefault(sender.getUniqueId(), false);
        boolean hasPrefix = message.startsWith("?");

        if (!hasToggle && !hasPrefix) {
            return; // Not a local chat message
        }

        // 3. Jail Check - Local chat is disabled in jail region
        if (jailbanManager.isInJailRegion(sender.getLocation())) {
            if (hasPrefix) {
                // Only send error if they explicitly tried to use local chat with ?
                // If they have toggle on, we just let it fall through to regular jail chat (handled by JailbanListener)
                sender.sendMessage("§cLocal chat does not work in jail!");
                event.setCancelled(true);
            }
            // If toggle is on, we do nothing here. The event proceeds to JailbanListener (HIGH priority)
            // which will convert it to [Jail] chat.
            return;
        }

        // 4. Permission Check
        if (!sender.hasPermission("skittle.localchat.use")) {
            sender.sendMessage("§cYou don't have permission to use local chat!");
            event.setCancelled(true);
            return;
        }

        // 5. Process Local Chat
        event.setCancelled(true); // Cancel global chat

        String localMessageContent = hasPrefix ? message.substring(1).trim() : message.trim();

        if (localMessageContent.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> sender.sendMessage("§cCannot send empty local message!"));
            return;
        }

        // Format messages
        String rawFormat = chatFormat
                .replace("{player}", sender.getName())
                .replace("{message}", localMessageContent);

        String localFormatted = (localPrefix + " " + rawFormat).replace("&", "§");
        String spyFormatted = (spyPrefix + " " + rawFormat).replace("&", "§");

        // 6. Distribute Messages (Thread-safe iteration)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location senderLoc = sender.getLocation();

            for (Player recipient : Bukkit.getOnlinePlayers()) {
                // Skip if recipient is ignoring sender (if you have an ignore system, otherwise skip)

                boolean isSpy = spyToggle.getOrDefault(recipient.getUniqueId(), false) && recipient.hasPermission("skittle.localchat.spy");
                boolean inRange = isRecipientInRange(senderLoc, recipient);

                if (inRange) {
                    // Normal local chat
                    recipient.sendMessage(localFormatted);
                } else if (isSpy) {
                    // Spy chat (only if NOT in range) - prevents double messages
                    recipient.sendMessage(spyFormatted);
                }
            }
        });

        // 7. Log to Console
        plugin.getLogger().info("[LocalChat] " + sender.getName() + ": " + localMessageContent);
    }

    /**
     * Efficiently checks if recipient is in range using squared distance
     */
    private boolean isRecipientInRange(Location senderLoc, Player recipient) {
        if (!senderLoc.getWorld().equals(recipient.getWorld())) {
            return false;
        }
        // Use distanceSquared to avoid expensive Math.sqrt calls
        return senderLoc.distanceSquared(recipient.getLocation()) <= chatRadiusSquared;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        localChatToggle.remove(event.getPlayer().getUniqueId());
        spyToggle.remove(event.getPlayer().getUniqueId());
    }
}