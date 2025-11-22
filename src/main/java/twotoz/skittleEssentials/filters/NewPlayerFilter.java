package twotoz.skittleEssentials.filters;


import twotoz.skittleEssentials.SkittleEssentials;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NewPlayerFilter {

    private final SkittleEssentials plugin;
    private final Essentials essentials;

    private boolean enabled;
    private double playtimeThresholdHours;
    private List<String> blockedCommands;

    // Chat filter settings
    private boolean chatFilterEnabled;
    private String replacementString;
    private List<String> blockedWords;
    private boolean logFilteredMessages;

    public NewPlayerFilter(SkittleEssentials plugin, Essentials essentials) {
        this.plugin = plugin;
        this.essentials = essentials;
        loadConfig();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("new-player-filter.enabled", true);
        playtimeThresholdHours = plugin.getConfig().getDouble("new-player-filter.playtime-threshold-hours", 2.0);
        blockedCommands = plugin.getConfig().getStringList("new-player-filter.blocked-commands");

        // Chat filter settings
        chatFilterEnabled = plugin.getConfig().getBoolean("new-player-filter.chat-filter.enabled", true);
        replacementString = plugin.getConfig().getString("new-player-filter.chat-filter.replacement", "***");
        blockedWords = plugin.getConfig().getStringList("new-player-filter.chat-filter.blocked-words");
        logFilteredMessages = plugin.getConfig().getBoolean("new-player-filter.chat-filter.log-filtered-messages", false);

        plugin.getLogger().info("NewPlayerFilter loaded: " + blockedCommands.size() + " blocked commands, " + blockedWords.size() + " filtered words");
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if a player is a "new player" based on playtime
     */
    public boolean isNewPlayer(Player player) {
        if (!enabled) {
            return false;
        }

        // OPs and players with bypass permission are never "new"
        if (player.isOp() || player.hasPermission("skittle.newplayerfilter.bypass")) {
            return false;
        }

        try {
            User user = essentials.getUser(player.getUniqueId());
            if (user == null) {
                return true; // If we have no data, treat as new
            }

            // PLAY_ONE_MINUTE statistic is in TICKS (20 ticks per second)
            long playtimeTicks = user.getBase().getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            // Convert ticks to hours: ticks / 20 (ticks per second) / 60 (seconds per minute) / 60 (minutes per hour)
            double playtimeHours = playtimeTicks / 72000.0; // 72000 ticks = 1 hour

            return playtimeHours < playtimeThresholdHours;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get playtime for " + player.getName() + ": " + e.getMessage());
            return false; // On error, don't filter
        }
    }

    /**
     * Check of een command geblokkeerd is voor nieuwe spelers
     * Supports both "command" and "command args" formats
     */
    public boolean isCommandBlocked(String command) {
        if (!enabled || blockedCommands.isEmpty()) {
            return false;
        }

        // Verwijder leading slash
        String cmd = command.toLowerCase();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }

        // Check alle blocked commands
        for (String blocked : blockedCommands) {
            String blockedCmd = blocked.toLowerCase();
            if (blockedCmd.startsWith("/")) {
                blockedCmd = blockedCmd.substring(1);
            }

            // Als blocked command spaties heeft (bijv "warp shop")
            if (blockedCmd.contains(" ")) {
                // Split beide in parts
                String[] cmdParts = cmd.split(" ");
                String[] blockedParts = blockedCmd.split(" ");

                // Check of command exact matched of langer is met dezelfde start
                if (cmdParts.length >= blockedParts.length) {
                    boolean matches = true;
                    for (int i = 0; i < blockedParts.length; i++) {
                        if (!cmdParts[i].equals(blockedParts[i])) {
                            matches = false;
                            break;
                        }
                    }
                    if (matches) {
                        return true; // "warp shop" blocks "warp shop" and "warp shop anything"
                    }
                }
            } else {
                // Geen spaties - block hele base command
                String baseCmd = cmd.split(" ")[0];
                if (baseCmd.equals(blockedCmd)) {
                    return true; // "warp" blocks all warp commands
                }
            }
        }

        return false;
    }

    public double getPlaytimeThresholdHours() {
        return playtimeThresholdHours;
    }

    public List<String> getBlockedCommands() {
        return new ArrayList<>(blockedCommands);
    }

    /**
     * Check if chat filter is enabled
     */
    public boolean isChatFilterEnabled() {
        return enabled && chatFilterEnabled;
    }

    /**
     * Filter a chat message for blocked words
     * Returns the filtered message with blocked words replaced
     */
    public String filterMessage(Player player, String message) {
        if (!isChatFilterEnabled() || blockedWords.isEmpty()) {
            return message;
        }

        // Don't filter for players with bypass permission
        if (player.hasPermission("skittle.newplayerfilter.bypass")) {
            return message;
        }

        // Don't filter if player is not new
        if (!isNewPlayer(player)) {
            return message;
        }

        return filterMessageContent(message);
    }

    /**
     * Filter message content without player checks
     * Used by chat listener which already checked if filtering is needed
     */
    public String filterMessageContent(String message) {
        if (blockedWords.isEmpty()) {
            return message;
        }

        String filtered = message;
        boolean wasFiltered = false;

        // Check each blocked word (case-insensitive)
        for (String blockedWord : blockedWords) {
            if (blockedWord.isEmpty()) continue;

            // Use word boundaries to match whole words only
            // (?i) makes it case-insensitive
            String regex = "(?i)\\b" + java.util.regex.Pattern.quote(blockedWord) + "\\b";

            if (filtered.matches(".*" + regex + ".*")) {
                filtered = filtered.replaceAll(regex, replacementString);
                wasFiltered = true;
            }
        }

        // Log if message was filtered and logging is enabled
        if (wasFiltered && logFilteredMessages) {
            plugin.getLogger().info("[ChatFilter] Message filtered: " + message + " -> " + filtered);
        }

        return filtered;
    }

    /**
     * Get the replacement string for blocked words
     */
    public String getReplacementString() {
        return replacementString;
    }

    /**
     * Get list of blocked words
     */
    public List<String> getBlockedWords() {
        return new ArrayList<>(blockedWords);
    }
}