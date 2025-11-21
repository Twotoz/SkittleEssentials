package twotoz.skittleEssentials.managers;


import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class JailDataManager {

    private final SkittleEssentials plugin;
    private File jailDataFolder;
    private File jailBalanceFile;
    private FileConfiguration jailBalanceConfig;
    
    // Async save management
    private BukkitTask saveTask = null;
    private volatile boolean isDirty = false;

    public JailDataManager(SkittleEssentials plugin) {
        this.plugin = plugin;
        setupFiles();
    }

    private void setupFiles() {
        // Maak jaildata folder
        jailDataFolder = new File(plugin.getDataFolder(), "jaildata");
        if (!jailDataFolder.exists()) {
            jailDataFolder.mkdirs();
        }

        // Maak jailbalance.yml
        jailBalanceFile = new File(jailDataFolder, "jailbalance.yml");
        if (!jailBalanceFile.exists()) {
            try {
                jailBalanceFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create jailbalance.yml!");
                e.printStackTrace();
            }
        }

        jailBalanceConfig = YamlConfiguration.loadConfiguration(jailBalanceFile);
    }

    public void setJailBail(UUID playerId, double bailAmount) {
        String path = playerId.toString();
        jailBalanceConfig.set(path + ".bail_required", bailAmount);
        jailBalanceConfig.set(path + ".current_balance", 0.0);
        jailBalanceConfig.set(path + ".reason", "Jailed");
        saveJailBalance();
    }

    public void setJailBailWithReason(UUID playerId, double bailAmount, String reason) {
        String path = playerId.toString();
        jailBalanceConfig.set(path + ".bail_required", bailAmount);
        jailBalanceConfig.set(path + ".current_balance", 0.0);
        jailBalanceConfig.set(path + ".reason", reason);
        saveJailBalance();
    }

    public double getBailRequired(UUID playerId) {
        String path = playerId.toString() + ".bail_required";
        return jailBalanceConfig.getDouble(path, 0.0);
    }

    public double getCurrentBalance(UUID playerId) {
        String path = playerId.toString() + ".current_balance";
        return jailBalanceConfig.getDouble(path, 0.0);
    }

    public void addBalance(UUID playerId, double amount) {
        String path = playerId.toString() + ".current_balance";
        double current = getCurrentBalance(playerId);
        jailBalanceConfig.set(path, current + amount);
        saveJailBalance();
    }

    public boolean hasEnoughForBail(UUID playerId) {
        double current = getCurrentBalance(playerId);
        double required = getBailRequired(playerId);
        return current >= required && required > 0;
    }

    public String getJailReason(UUID playerId) {
        String path = playerId.toString() + ".reason";
        return jailBalanceConfig.getString(path, "No reason given");
    }

    public void removeJailData(UUID playerId) {
        String path = playerId.toString();
        jailBalanceConfig.set(path, null);
        saveJailBalance();
    }

    public boolean hasJailData(UUID playerId) {
        return jailBalanceConfig.contains(playerId.toString());
    }

    private void saveJailBalance() {
        isDirty = true;
        
        // Cancel previous save task (debouncing)
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }
        
        // Schedule async save after 1 second delay
        // This prevents excessive disk I/O when multiple changes happen rapidly
        saveTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (isDirty) {
                try {
                    jailBalanceConfig.save(jailBalanceFile);
                    isDirty = false;
                    plugin.getLogger().fine("Saved jailbalance.yml asynchronously");
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save jailbalance.yml!");
                    e.printStackTrace();
                }
            }
        }, 20L); // 1 second delay for debouncing
    }
    
    /**
     * Force immediate save (use sparingly, e.g., on plugin disable)
     */
    public void forceSave() {
        if (saveTask != null && !saveTask.isCancelled()) {
            saveTask.cancel();
        }
        
        try {
            jailBalanceConfig.save(jailBalanceFile);
            isDirty = false;
            plugin.getLogger().info("Force saved jailbalance.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not force save jailbalance.yml!");
            e.printStackTrace();
        }
    }

    public void reload() {
        jailBalanceConfig = YamlConfiguration.loadConfiguration(jailBalanceFile);
    }

    public Set<UUID> getAllJailedPlayers() {
        Set<UUID> jailedPlayers = new HashSet<>();
        if (jailBalanceConfig != null) {
            for (String key : jailBalanceConfig.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    jailedPlayers.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in jailbalance.yml: " + key);
                }
            }
        }
        return jailedPlayers;
    }
}