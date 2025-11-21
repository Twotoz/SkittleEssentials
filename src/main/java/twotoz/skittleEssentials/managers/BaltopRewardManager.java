package twotoz.skittleEssentials.managers;


import twotoz.skittleEssentials.SkittleEssentials;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class BaltopRewardManager {

    private final SkittleEssentials plugin;
    private final Essentials essentials;
    private final LuckPerms luckPerms;

    private BukkitTask checkTask;

    // Track current baltop holders
    private UUID currentRank1 = null;
    private UUID currentRank2 = null;
    private UUID currentRank3 = null;

    // Config settings
    private boolean enabled;
    private int checkIntervalMinutes;
    private String rank1Group;
    private String rank2Group;
    private String rank3Group;

    public BaltopRewardManager(SkittleEssentials plugin, Essentials essentials, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.essentials = essentials;
        this.luckPerms = luckPerms;
        loadConfig();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("baltop-rewards.enabled", true);
        checkIntervalMinutes = plugin.getConfig().getInt("baltop-rewards.check-interval-minutes", 30);
        rank1Group = plugin.getConfig().getString("baltop-rewards.rank-1-group", "baltop1");
        rank2Group = plugin.getConfig().getString("baltop-rewards.rank-2-group", "baltop2");
        rank3Group = plugin.getConfig().getString("baltop-rewards.rank-3-group", "baltop3");
    }
    
    /**
     * Restart the task (used during reload)
     */
    public void restart() {
        stop();
        start();
    }

    private void validateGroups() {
        GroupManager groupManager = luckPerms.getGroupManager();
        List<String> missingGroups = new ArrayList<>();

        // Check all groups silently
        if (groupManager.getGroup(rank1Group) == null) {
            missingGroups.add(rank1Group);
        }
        if (groupManager.getGroup(rank2Group) == null) {
            missingGroups.add(rank2Group);
        }
        if (groupManager.getGroup(rank3Group) == null) {
            missingGroups.add(rank3Group);
        }

        // Only log if there are missing groups (reduce spam)
        if (!missingGroups.isEmpty()) {
            plugin.getLogger().warning("[Baltop] Missing groups will be auto-created: " + String.join(", ", missingGroups));
        }
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("⚠ Baltop rewards disabled in config");
            return;
        }

        // Validate groups exist (silent)
        validateGroups();

        // Initial check after 10 seconds (give server time to load)
        Bukkit.getScheduler().runTaskLater(plugin, this::updateBaltopRewards, 200L);

        // Schedule periodic checks
        long intervalTicks = checkIntervalMinutes * 60 * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBaltopRewards, intervalTicks, intervalTicks);

        plugin.getLogger().info("✅ Baltop rewards enabled! Checking every " + checkIntervalMinutes + " minutes");
    }

    public void stop() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }

    private void updateBaltopRewards() {
        // Run async to avoid lag
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Get top 3 from essentials
                Map<UUID, Double> baltop = getTopBalances(3);

                if (baltop.isEmpty()) {
                    // Silently skip if no data available
                    return;
                }

                List<UUID> topPlayers = new ArrayList<>(baltop.keySet());

                UUID newRank1 = topPlayers.size() > 0 ? topPlayers.get(0) : null;
                UUID newRank2 = topPlayers.size() > 1 ? topPlayers.get(1) : null;
                UUID newRank3 = topPlayers.size() > 2 ? topPlayers.get(2) : null;

                boolean hasChanges = false;

                // Update rank 1
                if (!Objects.equals(currentRank1, newRank1)) {
                    if (currentRank1 != null) {
                        removeFromGroup(currentRank1, rank1Group);
                    }
                    if (newRank1 != null) {
                        addToGroup(newRank1, rank1Group);
                    }
                    currentRank1 = newRank1;
                    hasChanges = true;
                }

                // Update rank 2
                if (!Objects.equals(currentRank2, newRank2)) {
                    if (currentRank2 != null) {
                        removeFromGroup(currentRank2, rank2Group);
                    }
                    if (newRank2 != null) {
                        addToGroup(newRank2, rank2Group);
                    }
                    currentRank2 = newRank2;
                    hasChanges = true;
                }

                // Update rank 3
                if (!Objects.equals(currentRank3, newRank3)) {
                    if (currentRank3 != null) {
                        removeFromGroup(currentRank3, rank3Group);
                    }
                    if (newRank3 != null) {
                        addToGroup(newRank3, rank3Group);
                    }
                    currentRank3 = newRank3;
                    hasChanges = true;
                }

                // Only log if there were actual changes
                if (hasChanges) {
                    plugin.getLogger().info("Baltop permissions updated");
                }

            } catch (Exception e) {
                // Silently handle errors to avoid console spam
                plugin.getLogger().severe("[Baltop] Critical error: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("deprecation") // getUserMap is deprecated but no alternative exists
    private Map<UUID, Double> getTopBalances(int amount) {
        Map<UUID, Double> balances = new LinkedHashMap<>();

        try {
            // Get all users from Essentials
            Collection<UUID> allUsers = essentials.getUserMap().getAllUniqueUsers();

            // Create a list of users with their balances
            List<Map.Entry<UUID, Double>> userBalances = new ArrayList<>();

            for (UUID uuid : allUsers) {
                User user = essentials.getUser(uuid);
                if (user != null) {
                    double balance = user.getMoney().doubleValue();
                    userBalances.add(new AbstractMap.SimpleEntry<>(uuid, balance));
                }
            }

            // Sort by balance descending
            userBalances.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

            // Get top X
            for (int i = 0; i < Math.min(amount, userBalances.size()); i++) {
                Map.Entry<UUID, Double> entry = userBalances.get(i);
                balances.put(entry.getKey(), entry.getValue());
            }

        } catch (Exception e) {
            // Silently handle to avoid console spam
        }

        return balances;
    }

    private boolean ensureGroupExists(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return false;
        }

        GroupManager groupManager = luckPerms.getGroupManager();

        // Check if group exists
        if (groupManager.getGroup(groupName) != null) {
            return true;
        }

        // Group doesn't exist - create it silently
        try {
            groupManager.createAndLoadGroup(groupName).thenAcceptAsync(group -> {
                // Silently create group - no spam
            }).exceptionally(throwable -> {
                // Only log critical errors
                plugin.getLogger().severe("[Baltop] Failed to create group: " + groupName);
                return null;
            });
            return true; // Assume it will be created
        } catch (Exception e) {
            // Silently fail
            return false;
        }
    }

    private void addToGroup(UUID playerId, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return;
        }

        // Ensure the group exists first
        if (!ensureGroupExists(groupName)) {
            return;
        }

        UserManager userManager = luckPerms.getUserManager();

        userManager.loadUser(playerId).thenAcceptAsync(user -> {
            if (user == null) {
                // Silently skip if user can't be loaded
                return;
            }

            InheritanceNode node = InheritanceNode.builder(groupName).build();
            if (user.data().add(node).wasSuccessful()) {
                // Silently save
                userManager.saveUser(user);
            }
        }).exceptionally(throwable -> {
            // Silently handle errors
            return null;
        });
    }

    private void removeFromGroup(UUID playerId, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return;
        }

        // Check if group exists (but don't create it)
        GroupManager groupManager = luckPerms.getGroupManager();
        if (groupManager.getGroup(groupName) == null) {
            // Silently skip if group doesn't exist
            return;
        }

        UserManager userManager = luckPerms.getUserManager();

        userManager.loadUser(playerId).thenAcceptAsync(user -> {
            if (user == null) {
                // Silently skip if user can't be loaded
                return;
            }

            InheritanceNode node = InheritanceNode.builder(groupName).build();
            if (user.data().remove(node).wasSuccessful()) {
                // Silently save
                userManager.saveUser(user);
            }
        }).exceptionally(throwable -> {
            // Silently handle errors
            return null;
        });
    }

    public void forceUpdate() {
        plugin.getLogger().info("[Baltop] Forcing manual update...");
        updateBaltopRewards();
    }
}