package twotoz.skittleEssentials.managers;

import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JailbanManager {

    private final SkittleEssentials plugin;
    private final JailDataManager jailDataManager;

    private Location pos1;
    private Location pos2;
    private Location jailSpawn;

    private final Set<UUID> jailbannedPlayers = ConcurrentHashMap.newKeySet();

    private double minX, maxX;
    private double minY, maxY;
    private double minZ, maxZ;
    private String jailWorld;

    private int boundaryCooldown;

    public JailbanManager(SkittleEssentials plugin, JailDataManager jailDataManager) {
        this.plugin = plugin;
        this.jailDataManager = jailDataManager;
        loadJailRegion();
    }

    public void loadJailRegion() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("jailban");
        if (section == null) {
            plugin.getLogger().warning("Jailban configuration not found!");
            return;
        }

        boundaryCooldown = section.getInt("boundary-message-cooldown", 3);

        ConfigurationSection regionSection = section.getConfigurationSection("region");
        if (regionSection == null) {
            plugin.getLogger().warning("Jailban region configuration not found!");
            return;
        }

        // Load pos1
        ConfigurationSection pos1Section = regionSection.getConfigurationSection("pos1");
        if (pos1Section != null) {
            String worldName = pos1Section.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                pos1 = new Location(
                        world,
                        pos1Section.getDouble("x"),
                        pos1Section.getDouble("y"),
                        pos1Section.getDouble("z")
                );
            }
        }

        // Load pos2
        ConfigurationSection pos2Section = regionSection.getConfigurationSection("pos2");
        if (pos2Section != null) {
            String worldName = pos2Section.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                pos2 = new Location(
                        world,
                        pos2Section.getDouble("x"),
                        pos2Section.getDouble("y"),
                        pos2Section.getDouble("z")
                );
            }
        }

        // Load spawn
        ConfigurationSection spawnSection = regionSection.getConfigurationSection("spawn");
        if (spawnSection != null) {
            String worldName = spawnSection.getString("world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                jailSpawn = new Location(
                        world,
                        spawnSection.getDouble("x"),
                        spawnSection.getDouble("y"),
                        spawnSection.getDouble("z"),
                        spawnSection.contains("yaw") ? (float) spawnSection.getDouble("yaw") : 0,
                        spawnSection.contains("pitch") ? (float) spawnSection.getDouble("pitch") : 0
                );
            }
        }

        // Calculate region bounds
        if (pos1 != null && pos2 != null) {
            jailWorld = pos1.getWorld().getName();
            minX = Math.min(pos1.getX(), pos2.getX());
            maxX = Math.max(pos1.getX(), pos2.getX());
            minY = Math.min(pos1.getY(), pos2.getY());
            maxY = Math.max(pos1.getY(), pos2.getY());
            minZ = Math.min(pos1.getZ(), pos2.getZ());
            maxZ = Math.max(pos1.getZ(), pos2.getZ());

            plugin.getLogger().info("Jail region loaded in world " + jailWorld);
        } else {
            plugin.getLogger().warning("Jail region coordinates not properly configured!");
        }
    }

    public boolean isInJailRegion(Location location) {
        if (pos1 == null || pos2 == null) return false;
        if (!location.getWorld().getName().equals(jailWorld)) return false;

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    public void jailban(Player player, String reason, double bailAmount) {
        jailbannedPlayers.add(player.getUniqueId());

        jailDataManager.setJailBailWithReason(player.getUniqueId(), bailAmount, reason)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to save jail data for " + player.getName());
                    throwable.printStackTrace();
                    return null;
                });
    }

    public void jailban(UUID playerId, String reason, double bailAmount) {
        jailbannedPlayers.add(playerId);

        jailDataManager.setJailBailWithReason(playerId, bailAmount, reason)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to save jail data for " + playerId);
                    throwable.printStackTrace();
                    return null;
                });
    }

    public void unjailban(Player player) {
        jailbannedPlayers.remove(player.getUniqueId());

        jailDataManager.removeJailData(player.getUniqueId())
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to remove jail data for " + player.getName());
                    throwable.printStackTrace();
                    return null;
                });
    }

    public void unjailban(UUID playerId) {
        jailbannedPlayers.remove(playerId);

        jailDataManager.removeJailData(playerId)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to remove jail data for " + playerId);
                    throwable.printStackTrace();
                    return null;
                });
    }

    public boolean isJailbanned(Player player) {
        return jailbannedPlayers.contains(player.getUniqueId());
    }

    public boolean isJailbanned(UUID playerId) {
        return jailbannedPlayers.contains(playerId);
    }

    public String getJailReason(Player player) {
        return jailDataManager.getJailReason(player.getUniqueId());
    }

    public double getBailRequired(Player player) {
        return jailDataManager.getBailRequired(player.getUniqueId());
    }

    public double getCurrentBalance(Player player) {
        return jailDataManager.getCurrentBalance(player.getUniqueId());
    }

    public void addBalance(Player player, double amount) {
        jailDataManager.addBalance(player.getUniqueId(), amount)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to add balance for " + player.getName());
                    throwable.printStackTrace();
                    return null;
                });
    }

    public boolean canAffordBail(Player player) {
        return jailDataManager.hasEnoughForBail(player.getUniqueId());
    }

    public void bailOut(Player player) {
        if (canAffordBail(player)) {
            unjailban(player);
        }
    }

    public List<String> getJailbannedPlayers() {
        return jailbannedPlayers.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(p -> p.getName() != null ? p.getName() : "Unknown")
                .collect(Collectors.toList());
    }

    public List<Player> getOnlineJailbannedPlayers() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : jailbannedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                players.add(player);
            }
        }
        return players;
    }

    public Location getJailSpawn() {
        return jailSpawn;
    }

    public boolean isConfigured() {
        return pos1 != null && pos2 != null && jailSpawn != null;
    }

    public void enforceJailBounds(Player player) {
        if (jailSpawn != null) player.teleport(jailSpawn);
    }

    public int getBoundaryCooldown() {
        return boundaryCooldown;
    }

    public boolean canUseCommand(Player player, String command) {
        if (!isJailbanned(player)) return true;
        if (player.hasPermission("skittle.jailban.bypass")) return true;

        List<String> allowedCommands = plugin.getConfig().getStringList("jailban.allowed-commands");
        for (String allowed : allowedCommands) {
            if (command.toLowerCase().startsWith(allowed.toLowerCase()))
                return true;
        }
        return false;
    }

    public boolean canSeeJailChat(Player player) {
        if (isJailbanned(player)) return true;
        if (isInJailRegion(player.getLocation())) return true;
        return player.hasPermission("skittle.jailban.notify");
    }

    public void loadJailedPlayersFromData() {
        jailbannedPlayers.clear();

        // IMPORTANT: reload() returns void → never dereference
        jailDataManager.reload();

        Set<UUID> jailedUUIDs = jailDataManager.getAllJailedPlayers();
        jailbannedPlayers.addAll(jailedUUIDs);

        if (!jailedUUIDs.isEmpty()) {
            plugin.getLogger().info("Loaded " + jailedUUIDs.size() + " jailed player(s) from data");
        }
    }

    public void shutdown() {
        clearAll();
    }

    public void clearAll() {
        jailbannedPlayers.clear();
    }
}