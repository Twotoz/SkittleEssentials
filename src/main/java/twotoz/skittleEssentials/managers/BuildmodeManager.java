package twotoz.skittleEssentials.managers;


import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildmodeManager {

    private final SkittleEssentials plugin;
    // Thread-safe collections
    private final Map<UUID, Long> buildmodePlayers = new ConcurrentHashMap<>();
    private BukkitTask verificationTask;

    public BuildmodeManager(SkittleEssentials plugin) {
        this.plugin = plugin;
        startVerificationTask();
    }

    public void addPlayer(Player player) {
        buildmodePlayers.put(player.getUniqueId(), System.currentTimeMillis());
        plugin.getLogger().info("Added " + player.getName() + " to buildmode tracking");
    }

    public void removePlayer(Player player) {
        buildmodePlayers.remove(player.getUniqueId());
        plugin.getLogger().info("Removed " + player.getName() + " from buildmode tracking");
    }

    public boolean isInBuildmode(Player player) {
        return buildmodePlayers.containsKey(player.getUniqueId());
    }

    public void clear() {
        buildmodePlayers.clear();
    }

    /**
     * Start periodic verification task to check if players in buildmode are still in creative
     * Runs every second (20 ticks)
     */
    private void startVerificationTask() {
        verificationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Create snapshot to avoid ConcurrentModificationException
            Set<UUID> snapshot = new HashSet<>(buildmodePlayers.keySet());
            
            for (UUID uuid : snapshot) {
                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline()) {
                    // Player is offline, will be handled by quit event
                    continue;
                }

                // Check if player is still in creative mode
                if (player.getGameMode() != GameMode.CREATIVE) {
                    plugin.getLogger().warning("EXPLOIT DETECTED: " + player.getName() + " is in buildmode but not in creative mode!");
                    plugin.getLogger().warning("Gamemode: " + player.getGameMode() + " - Force disabling buildmode");

                    // Force disable buildmode
                    forceDisableBuildmode(player);
                }
            }
        }, 20L, 20L); // Run every second (20 ticks delay, 20 ticks period)
    }

    /**
     * Force disable buildmode for a player (used when exploit detected)
     */
    private void forceDisableBuildmode(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Set gamemode survival
        player.setGameMode(GameMode.SURVIVAL);

        // Remove from buildmode
        removePlayer(player);

        player.sendMessage("§c§l⚠ BUILDMODE EXPLOIT DETECTED!");
        player.sendMessage("§cYour buildmode has been forcefully disabled.");
        player.sendMessage("§cYour inventory has been cleared for security.");
        player.sendMessage("§7If this was an error, please contact an administrator.");
    }

    /**
     * Stop the verification task (called on plugin disable)
     */
    public void shutdown() {
        if (verificationTask != null) {
            verificationTask.cancel();
        }

        // Force disable buildmode for all online players
        for (UUID uuid : buildmodePlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                player.getInventory().setItemInOffHand(null);
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage("§c§l✗ Buildmode disabled (server reload/shutdown)");
            }
        }

        buildmodePlayers.clear();
    }

    /**
     * Get the number of players currently in buildmode
     */
    public int getBuildmodeCount() {
        return buildmodePlayers.size();
    }
}