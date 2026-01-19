package twotoz.skittleEssentials.managers;

import twotoz.skittleEssentials.SkittleEssentials;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class BaltopRewardManager {

    private final SkittleEssentials plugin;
    private final Essentials essentials;
    private final LuckPerms luckPerms;
    private boolean isFolia = false;

    private Object checkTask; // BukkitTask or ScheduledTask

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

        // Detect Folia
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

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

        if (groupManager.getGroup(rank1Group) == null) {
            missingGroups.add(rank1Group);
        }
        if (groupManager.getGroup(rank2Group) == null) {
            missingGroups.add(rank2Group);
        }
        if (groupManager.getGroup(rank3Group) == null) {
            missingGroups.add(rank3Group);
        }

        if (!missingGroups.isEmpty()) {
            plugin.getLogger().warning("[Baltop] Missing groups will be auto-created: " + String.join(", ", missingGroups));
        }
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("⚠ Baltop rewards disabled in config");
            return;
        }

        validateGroups();

        // Initial check after 10 seconds (Folia-safe)
        if (isFolia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, (task) -> {
                updateBaltopRewards();
            }, 200L, TimeUnit.MILLISECONDS); // 200 * 50ms = 10 seconds
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, this::updateBaltopRewards, 200L);
        }

        // Schedule periodic checks (Folia-safe)
        long intervalMs = checkIntervalMinutes * 60 * 1000L;

        if (isFolia) {
            checkTask = Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (task) -> {
                updateBaltopRewards();
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        } else {
            long intervalTicks = checkIntervalMinutes * 60 * 20L;
            checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateBaltopRewards, intervalTicks, intervalTicks);
        }

        plugin.getLogger().info("✅ Baltop rewards enabled! Checking every " + checkIntervalMinutes + " minutes");
    }

    public void stop() {
        if (checkTask != null) {
            if (isFolia) {
                try {
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) checkTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Folia baltop task: " + e.getMessage());
                }
            } else {
                try {
                    ((org.bukkit.scheduler.BukkitTask) checkTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Bukkit baltop task: " + e.getMessage());
                }
            }
        }
    }

    private void updateBaltopRewards() {
        // Already async from scheduler
        try {
            Map<UUID, Double> baltop = getTopBalances(3);

            if (baltop.isEmpty()) {
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

            if (hasChanges) {
                plugin.getLogger().info("Baltop permissions updated");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("[Baltop] Critical error: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private Map<UUID, Double> getTopBalances(int amount) {
        Map<UUID, Double> balances = new LinkedHashMap<>();

        try {
            Collection<UUID> allUsers = essentials.getUserMap().getAllUniqueUsers();
            List<Map.Entry<UUID, Double>> userBalances = new ArrayList<>();

            for (UUID uuid : allUsers) {
                User user = essentials.getUser(uuid);
                if (user != null) {
                    double balance = user.getMoney().doubleValue();
                    userBalances.add(new AbstractMap.SimpleEntry<>(uuid, balance));
                }
            }

            userBalances.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

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

        if (groupManager.getGroup(groupName) != null) {
            return true;
        }

        try {
            groupManager.createAndLoadGroup(groupName).thenAcceptAsync(group -> {
                // Silently create group
            }).exceptionally(throwable -> {
                plugin.getLogger().severe("[Baltop] Failed to create group: " + groupName);
                return null;
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void addToGroup(UUID playerId, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return;
        }

        if (!ensureGroupExists(groupName)) {
            return;
        }

        UserManager userManager = luckPerms.getUserManager();

        userManager.loadUser(playerId).thenAcceptAsync(user -> {
            if (user == null) {
                return;
            }

            InheritanceNode node = InheritanceNode.builder(groupName).build();
            if (user.data().add(node).wasSuccessful()) {
                userManager.saveUser(user);
            }
        }).exceptionally(throwable -> {
            return null;
        });
    }

    private void removeFromGroup(UUID playerId, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            return;
        }

        GroupManager groupManager = luckPerms.getGroupManager();
        if (groupManager.getGroup(groupName) == null) {
            return;
        }

        UserManager userManager = luckPerms.getUserManager();

        userManager.loadUser(playerId).thenAcceptAsync(user -> {
            if (user == null) {
                return;
            }

            InheritanceNode node = InheritanceNode.builder(groupName).build();
            if (user.data().remove(node).wasSuccessful()) {
                userManager.saveUser(user);
            }
        }).exceptionally(throwable -> {
            return null;
        });
    }

    public void forceUpdate() {
        plugin.getLogger().info("[Baltop] Forcing manual update...");
        updateBaltopRewards();
    }
}