package twotoz.skittleEssentials.managers;

import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class BuildmodeManager {

    private final SkittleEssentials plugin;
    private final Map<UUID, Long> buildmodePlayers = new ConcurrentHashMap<>();
    private Object verificationTask;
    private boolean isFolia = false;

    public BuildmodeManager(SkittleEssentials plugin) {
        this.plugin = plugin;

        // Detect Folia
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;
            plugin.getLogger().info("Folia detected - using regional scheduling");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Paper/Spigot detected - using legacy scheduling");
        }

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
     * Start periodic verification task - Folia-safe
     */
    private void startVerificationTask() {
        if (isFolia) {
            // Folia: Schedule global repeating task (1 second = 1000ms)
            verificationTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (task) -> {
                verifyAllPlayers();
            }, 1000L, 1000L, TimeUnit.MILLISECONDS);
        } else {
            // Paper/Spigot: Traditional scheduler (20 ticks = 1 second)
            verificationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::verifyAllPlayers, 20L, 20L);
        }
    }

    private void verifyAllPlayers() {
        Set<UUID> snapshot = new HashSet<>(buildmodePlayers.keySet());

        for (UUID uuid : snapshot) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                continue;
            }

            // Schedule check on player's region (Folia-safe)
            if (isFolia) {
                player.getScheduler().run(plugin, (scheduledTask) -> {
                    checkPlayerGamemode(player);
                }, null);
            } else {
                // Paper: Run sync
                Bukkit.getScheduler().runTask(plugin, () -> checkPlayerGamemode(player));
            }
        }
    }

    private void checkPlayerGamemode(Player player) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            plugin.getLogger().warning("EXPLOIT DETECTED: " + player.getName() +
                    " is in buildmode but not in creative mode!");
            plugin.getLogger().warning("Gamemode: " + player.getGameMode() +
                    " - Force disabling buildmode");

            forceDisableBuildmode(player);
        }
    }

    /**
     * Force disable buildmode for a player (Folia-safe)
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
     * Stop the verification task (Folia-safe)
     */
    public void shutdown() {
        if (verificationTask != null) {
            if (isFolia) {
                try {
                    // Folia: Cancel scheduled task
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) verificationTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Folia task: " + e.getMessage());
                }
            } else {
                // Paper/Spigot: Cancel BukkitTask
                try {
                    ((org.bukkit.scheduler.BukkitTask) verificationTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Bukkit task: " + e.getMessage());
                }
            }
        }

        // Force disable buildmode for all online players
        for (UUID uuid : buildmodePlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (isFolia) {
                    player.getScheduler().run(plugin, (task) -> {
                        disablePlayerBuildmode(player);
                    }, null);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> disablePlayerBuildmode(player));
                }
            }
        }

        buildmodePlayers.clear();
    }

    private void disablePlayerBuildmode(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.sendMessage("§c§l✗ Buildmode disabled (server reload/shutdown)");
    }

    /**
     * Get the number of players currently in buildmode
     */
    public int getBuildmodeCount() {
        return buildmodePlayers.size();
    }
}