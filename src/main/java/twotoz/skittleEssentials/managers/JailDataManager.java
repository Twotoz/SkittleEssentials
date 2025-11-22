package twotoz.skittleEssentials.managers;


import twotoz.skittleEssentials.SkittleEssentials;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe SQLite-based jail data manager
 * Stores jail bail amounts, balances, and reasons
 */
public class JailDataManager {

    private final SkittleEssentials plugin;
    private final File databaseFile;
    private final ExecutorService executor;
    private Connection connection;

    public JailDataManager(SkittleEssentials plugin) {
        this.plugin = plugin;

        // Create data folder if it doesn't exist
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        this.databaseFile = new File(dataFolder, "jaildata.db");

        // Single-threaded executor for sequential database operations
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "JailData-DB-Thread");
            t.setDaemon(true);
            return t;
        });

        initializeDatabase();
    }

    /**
     * Initialize SQLite database connection and create tables
     */
    private void initializeDatabase() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");

            // Create connection with thread-safe settings
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrency
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
                stmt.execute("PRAGMA busy_timeout=5000;"); // 5 second timeout
                stmt.execute("PRAGMA cache_size=10000;");
            }

            // Create table if not exists
            createTables();

            plugin.getLogger().info("✅ JailData SQLite database initialized at data/jaildata.db");

        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("❌ SQLite JDBC driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Failed to initialize JailData database!");
            e.printStackTrace();
        }
    }

    /**
     * Create database tables
     */
    private void createTables() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS jail_data (
                player_uuid TEXT PRIMARY KEY,
                bail_required REAL NOT NULL DEFAULT 0.0,
                current_balance REAL NOT NULL DEFAULT 0.0,
                reason TEXT NOT NULL DEFAULT 'Jailed',
                jailed_at INTEGER NOT NULL DEFAULT 0
            );
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);

            // Create index for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_jailed_at ON jail_data(jailed_at);");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create jail_data table!");
            e.printStackTrace();
        }
    }

    /**
     * Set jail bail amount for a player (async)
     */
    public CompletableFuture<Void> setJailBail(UUID playerId, double bailAmount) {
        return setJailBailWithReason(playerId, bailAmount, "Jailed");
    }

    /**
     * Set jail bail amount with reason for a player (async)
     */
    public CompletableFuture<Void> setJailBailWithReason(UUID playerId, double bailAmount, String reason) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                INSERT OR REPLACE INTO jail_data (player_uuid, bail_required, current_balance, reason, jailed_at)
                VALUES (?, ?, 0.0, ?, ?)
                """;

            synchronized (this) {
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, playerId.toString());
                    pstmt.setDouble(2, bailAmount);
                    pstmt.setString(3, reason);
                    pstmt.setLong(4, System.currentTimeMillis());
                    pstmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to set jail bail for " + playerId);
                    e.printStackTrace();
                }
            }
        }, executor);
    }

    /**
     * Get bail required for a player (sync, safe to call from main thread)
     */
    public double getBailRequired(UUID playerId) {
        String sql = "SELECT bail_required FROM jail_data WHERE player_uuid = ?";

        synchronized (this) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("bail_required");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get bail required for " + playerId);
                e.printStackTrace();
            }
        }

        return 0.0;
    }

    /**
     * Get current balance for a player (sync)
     */
    public double getCurrentBalance(UUID playerId) {
        String sql = "SELECT current_balance FROM jail_data WHERE player_uuid = ?";

        synchronized (this) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble("current_balance");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get current balance for " + playerId);
                e.printStackTrace();
            }
        }

        return 0.0;
    }

    /**
     * Add balance to a player's jail account (async)
     */
    public CompletableFuture<Void> addBalance(UUID playerId, double amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                UPDATE jail_data 
                SET current_balance = current_balance + ?
                WHERE player_uuid = ?
                """;

            synchronized (this) {
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setDouble(1, amount);
                    pstmt.setString(2, playerId.toString());
                    pstmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to add balance for " + playerId);
                    e.printStackTrace();
                }
            }
        }, executor);
    }

    /**
     * Check if player has enough balance for bail (sync)
     */
    public boolean hasEnoughForBail(UUID playerId) {
        double current = getCurrentBalance(playerId);
        double required = getBailRequired(playerId);
        return current >= required && required > 0;
    }

    /**
     * Get jail reason for a player (sync)
     */
    public String getJailReason(UUID playerId) {
        String sql = "SELECT reason FROM jail_data WHERE player_uuid = ?";

        synchronized (this) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("reason");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get jail reason for " + playerId);
                e.printStackTrace();
            }
        }

        return "No reason given";
    }

    /**
     * Remove jail data for a player (async)
     */
    public CompletableFuture<Void> removeJailData(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM jail_data WHERE player_uuid = ?";

            synchronized (this) {
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, playerId.toString());
                    pstmt.executeUpdate();

                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to remove jail data for " + playerId);
                    e.printStackTrace();
                }
            }
        }, executor);
    }

    /**
     * Check if player has jail data (sync)
     */
    public boolean hasJailData(UUID playerId) {
        String sql = "SELECT 1 FROM jail_data WHERE player_uuid = ? LIMIT 1";

        synchronized (this) {
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerId.toString());

                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to check jail data for " + playerId);
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Get all jailed players (sync)
     */
    public Set<UUID> getAllJailedPlayers() {
        Set<UUID> jailedPlayers = new HashSet<>();
        String sql = "SELECT player_uuid FROM jail_data";

        synchronized (this) {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        jailedPlayers.add(uuid);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in jail_data: " + rs.getString("player_uuid"));
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to get all jailed players");
                e.printStackTrace();
            }
        }

        return jailedPlayers;
    }

    /**
     * Reload the database connection (used during plugin reload)
     */
    public void reload() {
        // SQLite with WAL mode is always live, no need to reload
        plugin.getLogger().info("JailData database reloaded (SQLite is always live)");
    }

    /**
     * Force save - checkpoint WAL to ensure all data is written
     */
    public void forceSave() {
        executor.submit(() -> {
            synchronized (this) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    plugin.getLogger().info("JailData WAL checkpoint completed");
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to checkpoint WAL");
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Shutdown database and executor
     */
    public void shutdown() {
        // Shutdown executor gracefully
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("JailData executor did not terminate in time, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close database connection
        try {
            if (connection != null && !connection.isClosed()) {
                synchronized (this) {
                    // Final WAL checkpoint
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    }

                    connection.close();
                    plugin.getLogger().info("JailData database closed");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error closing JailData database!");
            e.printStackTrace();
        }
    }

    /**
     * Migrate old YAML data to SQLite (call once if needed)
     */
    public void migrateFromYAML(File oldYamlFile) {
        if (!oldYamlFile.exists()) {
            return;
        }

        plugin.getLogger().info("Migrating jail data from YAML to SQLite...");

        // Run migration async to avoid blocking
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                org.bukkit.configuration.file.FileConfiguration config =
                        org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(oldYamlFile);

                int migrated = 0;
                for (String uuidString : config.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidString);
                        double bailRequired = config.getDouble(uuidString + ".bail_required", 0.0);
                        double currentBalance = config.getDouble(uuidString + ".current_balance", 0.0);
                        String reason = config.getString(uuidString + ".reason", "Jailed");

                        // Insert into database
                        String sql = """
                            INSERT OR REPLACE INTO jail_data (player_uuid, bail_required, current_balance, reason, jailed_at)
                            VALUES (?, ?, ?, ?, ?)
                            """;

                        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                            pstmt.setString(1, uuid.toString());
                            pstmt.setDouble(2, bailRequired);
                            pstmt.setDouble(3, currentBalance);
                            pstmt.setString(4, reason);
                            pstmt.setLong(5, System.currentTimeMillis());
                            pstmt.executeUpdate();
                            migrated++;
                        }

                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skipping invalid UUID during migration: " + uuidString);
                    }
                }

                plugin.getLogger().info("✅ Migrated " + migrated + " jail entries from YAML to SQLite");

                // Rename old file to .old
                File backup = new File(oldYamlFile.getParentFile(), oldYamlFile.getName() + ".old");
                if (oldYamlFile.renameTo(backup)) {
                    plugin.getLogger().info("Old YAML file backed up as " + backup.getName());
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to migrate YAML data!");
                e.printStackTrace();
            }
        });
    }
}