package twotoz.skittleEssentials.listeners;

import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.JailbanManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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

public class JailbanListener implements Listener, CommandExecutor {

    private final SkittleEssentials plugin;
    private final JailbanManager jailbanManager;
    private final StaffChatListener staffChatListener;
    private boolean isFolia = false;

    // Track last block position to avoid checking every tiny movement - Thread-safe
    private final Map<UUID, Location> lastBlockPosition = new ConcurrentHashMap<>();

    // Track staff who have Jail Spy toggled ON
    private final Map<UUID, Boolean> jailSpyToggle = new ConcurrentHashMap<>();

    public JailbanListener(SkittleEssentials plugin, JailbanManager jailbanManager) {
        this(plugin, jailbanManager, null);
    }

    public JailbanListener(SkittleEssentials plugin, JailbanManager jailbanManager, StaffChatListener staffChatListener) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;
        this.staffChatListener = staffChatListener;

        // Detect Folia
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    /**
     * Handle /jailchatspy command
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("skittle.jailban.spy")) {
            player.sendMessage("§cYou don't have permission to use jail chat spy!");
            return true;
        }

        UUID playerId = player.getUniqueId();
        boolean currentStatus = jailSpyToggle.getOrDefault(playerId, false);
        boolean newStatus = !currentStatus;

        jailSpyToggle.put(playerId, newStatus);

        if (newStatus) {
            player.sendMessage("§c§l🕵 Jail Chat Spy §2§lENABLED§c! You will now see messages from jailed players.");
        } else {
            player.sendMessage("§c§l🕵 Jail Chat Spy §c§lDISABLED§c!");
        }

        return true;
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
     * Handle jail chat system - Folia-safe
     */
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String message = event.getMessage();

        // Skip jail chat if player is using staff chat
        if (sender.hasPermission("skittle.staffchat")) {
            if (message.startsWith("!")) {
                return;
            }

            if (staffChatListener != null && staffChatListener.hasStaffChatToggle(sender)) {
                return;
            }
        }

        boolean senderInJail = jailbanManager.isInJailRegion(sender.getLocation());
        boolean senderJailbanned = jailbanManager.isJailbanned(sender);

        // IF SENDER IS IN JAIL (PRISONER CHAT)
        if (senderInJail || senderJailbanned) {
            event.setCancelled(true);

            // Schedule on appropriate scheduler (Folia-safe)
            Runnable chatTask = () -> {
                ConfigurationSection chatSection = plugin.getConfig().getConfigurationSection("jailban.chat");
                String prefix = chatSection != null ? chatSection.getString("prefix", "&7[&cJail&7]") : "&7[&cJail&7]";
                String format = chatSection != null ? chatSection.getString("format", "&f{player} &8» &7{message}") : "&f{player} &8» &7{message}";

                String formattedMessage = (prefix + " " + format)
                        .replace("&", "§")
                        .replace("{player}", sender.getName())
                        .replace("{message}", message);

                plugin.getLogger().info("[JailChat] " + sender.getName() + ": " + message);

                for (Player recipient : Bukkit.getOnlinePlayers()) {
                    boolean isRecipientJailbanned = jailbanManager.isJailbanned(recipient);
                    boolean isRecipientInJail = jailbanManager.isInJailRegion(recipient.getLocation());
                    boolean hasSpy = recipient.hasPermission("skittle.jailban.spy") && jailSpyToggle.getOrDefault(recipient.getUniqueId(), false);

                    if (isRecipientJailbanned || isRecipientInJail || hasSpy) {
                        recipient.sendMessage(formattedMessage);
                    }
                }
            };

            if (isFolia) {
                // Folia: Use global region scheduler for chat broadcast
                Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> chatTask.run());
            } else {
                // Paper: Use traditional scheduler
                Bukkit.getScheduler().runTask(plugin, chatTask);
            }

            return;
        }

        // IF SENDER IS NOT IN JAIL (NORMAL CHAT)
        event.getRecipients().removeIf(recipient -> {
            return jailbanManager.isJailbanned(recipient) ||
                    jailbanManager.isInJailRegion(recipient.getLocation());
        });
    }

    /**
     * Teleport jailed players to jail on join - Folia-safe
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (jailbanManager.isJailbanned(player)) {
            Location jailSpawn = jailbanManager.getJailSpawn();
            if (jailSpawn != null) {
                if (isFolia) {
                    // Folia: Schedule teleport on player's region
                    player.getScheduler().run(plugin, (task) -> {
                        player.teleport(jailSpawn);
                        sendJailWelcomeMessage(player);
                    }, null);
                } else {
                    // Paper: Direct teleport
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.teleport(jailSpawn);
                        sendJailWelcomeMessage(player);
                    });
                }
            }
        }
    }

    private void sendJailWelcomeMessage(Player player) {
        double current = jailbanManager.getCurrentBalance(player);
        double required = jailbanManager.getBailRequired(player);

        player.sendMessage("§c§l⚖ You are in jail!");
        player.sendMessage("§7Reason: §e" + jailbanManager.getJailReason(player));
        player.sendMessage("§7Bail Required: §e$" + String.format("%.2f", required));
        player.sendMessage("§7Current Balance: §a$" + String.format("%.2f", current));
        player.sendMessage("§7Kill mobs to earn money! Use §e/jailbal §7to check progress.");
    }

    /**
     * Clean up tracking data on quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastBlockPosition.remove(event.getPlayer().getUniqueId());
        jailSpyToggle.remove(event.getPlayer().getUniqueId());
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

        if (plugin.getConfig().getBoolean("jailban.block-interactions", true)) {
            if (event.getClickedBlock() != null) {
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

        if (killer == null) {
            return;
        }

        if (!jailbanManager.isJailbanned(killer)) {
            return;
        }

        if (entity instanceof Player) {
            return;
        }

        double earnAmount = 1.0;
        jailbanManager.addBalance(killer, earnAmount);

        double current = jailbanManager.getCurrentBalance(killer);
        double required = jailbanManager.getBailRequired(killer);

        killer.sendMessage("§a+$" + String.format("%.2f", earnAmount) + " §7(§a$" + String.format("%.2f", current) + "§7/§e$" + String.format("%.2f", required) + "§7)");

        if (jailbanManager.canAffordBail(killer)) {
            killer.sendMessage("§a§l✓ You have enough money to bail out! Use §e/bail confirm");
        }
    }
}