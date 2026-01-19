package twotoz.skittleEssentials.managers;

import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JailVoteManager {

    private final SkittleEssentials plugin;
    private final JailbanManager jailbanManager;
    private boolean isFolia = false;

    // Active vote state
    private volatile boolean voteActive = false;
    private UUID voteStarter;
    private long voteStartTime;
    private Object voteTask; // Can be BukkitTask or ScheduledTask

    // Vote tracking: voter UUID -> voted player name - Thread-safe
    private final Map<UUID, String> votes = new ConcurrentHashMap<>();

    // Settings from config
    private double voteCost;
    private int voteDuration;
    private double jailBailAmount;

    public JailVoteManager(SkittleEssentials plugin, JailbanManager jailbanManager) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;

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
        voteCost = plugin.getConfig().getDouble("jailvote.cost", 1000000.0);
        voteDuration = plugin.getConfig().getInt("jailvote.duration-seconds", 120);
        jailBailAmount = plugin.getConfig().getDouble("jailvote.bail-amount", 500.0);
    }

    public boolean isVoteActive() {
        return voteActive;
    }

    public double getVoteCost() {
        return voteCost;
    }

    public int getVoteDuration() {
        return voteDuration;
    }

    public boolean hasVoted(Player player) {
        return votes.containsKey(player.getUniqueId());
    }

    public void startVote(Player starter) {
        if (voteActive) {
            return;
        }

        voteActive = true;
        voteStarter = starter.getUniqueId();
        voteStartTime = System.currentTimeMillis();
        votes.clear();

        // Broadcast to all players
        broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastToAll("§c§l⚖ JAIL VOTE STARTED!");
        broadcastToAll("§7Started by: §e" + starter.getName());
        broadcastToAll("§7Duration: §e" + voteDuration + " seconds");
        broadcastToAll("");
        broadcastToAll("§eVote for who should go to jail:");
        broadcastToAll("§7Use: §f/jailvote <player>");
        broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Schedule vote end (Folia-safe)
        if (isFolia) {
            // Folia: Use global region scheduler with delay
            long delayMillis = voteDuration * 1000L;
            voteTask = Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> {
                endVote();
            }, delayMillis / 50); // Convert ms to ticks (50ms per tick)
        } else {
            // Paper: Traditional scheduler
            voteTask = Bukkit.getScheduler().runTaskLater(plugin, this::endVote, voteDuration * 20L);
        }
    }

    public void vote(Player voter, String targetName) {
        if (!voteActive) {
            voter.sendMessage("§cThere is no active jail vote!");
            return;
        }

        if (hasVoted(voter)) {
            voter.sendMessage("§cYou have already voted for: §e" + votes.get(voter.getUniqueId()));
            return;
        }

        Player target = Bukkit.getPlayer(targetName);
        String finalTargetName;

        if (target != null) {
            finalTargetName = target.getName();
        } else {
            if (Bukkit.getOfflinePlayer(targetName).hasPlayedBefore()) {
                finalTargetName = Bukkit.getOfflinePlayer(targetName).getName();
            } else {
                voter.sendMessage("§cPlayer not found!");
                return;
            }
        }

        votes.put(voter.getUniqueId(), finalTargetName);
        voter.sendMessage("§a§l✓ You voted to jail: §e" + finalTargetName);

        int voteCount = getVoteCount(finalTargetName);
        broadcastToAll("§7[§c⚖§7] §e" + voter.getName() + " §7voted to jail §c" + finalTargetName + " §7(§e" + voteCount + " votes§7)");
    }

    private int getVoteCount(String playerName) {
        int count = 0;
        for (String voted : votes.values()) {
            if (voted.equalsIgnoreCase(playerName)) {
                count++;
            }
        }
        return count;
    }

    private void endVote() {
        if (!voteActive) {
            return;
        }

        voteActive = false;

        Map<String, Integer> voteCounts = new HashMap<>();
        for (String playerName : votes.values()) {
            voteCounts.put(playerName, voteCounts.getOrDefault(playerName, 0) + 1);
        }

        if (voteCounts.isEmpty()) {
            broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            broadcastToAll("§c§l⚖ JAIL VOTE ENDED!");
            broadcastToAll("§7No votes were cast.");
            broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        String winner = null;
        int maxVotes = 0;
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                winner = entry.getKey();
            }
        }

        if (winner == null) {
            broadcastToAll("§cJail vote failed - no winner determined!");
            return;
        }

        broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        broadcastToAll("§c§l⚖ JAIL VOTE RESULTS!");
        broadcastToAll("§7Total voters: §e" + votes.size());
        broadcastToAll("");
        broadcastToAll("§c§l➤ Winner: §e" + winner + " §7(§c" + maxVotes + " votes§7)");

        List<Map.Entry<String, Integer>> sortedVotes = new ArrayList<>(voteCounts.entrySet());
        sortedVotes.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        broadcastToAll("");
        broadcastToAll("§7Top voted players:");
        int position = 1;
        for (Map.Entry<String, Integer> entry : sortedVotes) {
            if (position > 3) break;
            String emoji = position == 1 ? "§c🥇" : position == 2 ? "§6🥈" : "§e🥉";
            broadcastToAll(emoji + " §e" + entry.getKey() + " §7- §f" + entry.getValue() + " votes");
            position++;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlineWinner = Bukkit.getOfflinePlayer(winner);
        UUID winnerUUID = offlineWinner.getUniqueId();
        Player targetPlayer = offlineWinner.getPlayer();

        if (jailbanManager.isJailbanned(winnerUUID)) {
            broadcastToAll("");
            broadcastToAll("§c" + winner + " is already in jail!");
        } else {
            jailbanManager.jailban(winnerUUID, "Voted into jail by the community", jailBailAmount);

            if (targetPlayer != null && targetPlayer.isOnline()) {
                // Player is online - teleport them (Folia-safe)
                if (isFolia) {
                    targetPlayer.getScheduler().run(plugin, (task) -> {
                        if (jailbanManager.getJailSpawn() != null) {
                            targetPlayer.teleport(jailbanManager.getJailSpawn());
                        }
                    }, null);
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (jailbanManager.getJailSpawn() != null) {
                            targetPlayer.teleport(jailbanManager.getJailSpawn());
                        }
                    });
                }

                broadcastToAll("");
                broadcastToAll("§a§l✓ " + winner + " has been sent to jail!");
                broadcastToAll("§7Bail Amount: §a$" + String.format("%.2f", jailBailAmount));
            } else {
                broadcastToAll("");
                broadcastToAll("§a§l✓ " + winner + " has been jailed!");
                broadcastToAll("§7They are offline and will be teleported to jail when they join.");
                broadcastToAll("§7Bail Amount: §a$" + String.format("%.2f", jailBailAmount));
            }
        }

        broadcastToAll("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        votes.clear();
    }

    public void cancelVote() {
        if (voteTask != null) {
            if (isFolia) {
                try {
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) voteTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Folia vote task: " + e.getMessage());
                }
            } else {
                try {
                    ((org.bukkit.scheduler.BukkitTask) voteTask).cancel();
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to cancel Bukkit vote task: " + e.getMessage());
                }
            }
        }
        voteActive = false;
        votes.clear();
    }

    @SuppressWarnings("deprecation")
    private void broadcastToAll(String message) {
        Bukkit.broadcastMessage(message);
    }

    public String getTimeRemaining() {
        if (!voteActive) {
            return "No active vote";
        }

        long elapsed = System.currentTimeMillis() - voteStartTime;
        long remaining = (voteDuration * 1000L) - elapsed;

        if (remaining <= 0) {
            return "Ending soon...";
        }

        long seconds = remaining / 1000;
        return seconds + " seconds";
    }

    public int getTotalVotes() {
        return votes.size();
    }

    public Map<String, Integer> getCurrentVoteStandings() {
        Map<String, Integer> voteCounts = new HashMap<>();
        for (String playerName : votes.values()) {
            voteCounts.put(playerName, voteCounts.getOrDefault(playerName, 0) + 1);
        }
        return voteCounts;
    }
}