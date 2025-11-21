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

    public NewPlayerFilter(SkittleEssentials plugin, Essentials essentials) {
        this.plugin = plugin;
        this.essentials = essentials;
        loadConfig();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("new-player-filter.enabled", true);
        playtimeThresholdHours = plugin.getConfig().getDouble("new-player-filter.playtime-threshold-hours", 2.0);
        blockedCommands = plugin.getConfig().getStringList("new-player-filter.blocked-commands");

        plugin.getLogger().info("NewPlayerFilter loaded: " + blockedCommands.size() + " blocked commands");
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

            // Playtime is in milliseconds
            long playtimeMillis = user.getBase().getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            double playtimeHours = playtimeMillis / (1000.0 * 60.0 * 60.0 * 20.0); // Ticks to hours

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
}