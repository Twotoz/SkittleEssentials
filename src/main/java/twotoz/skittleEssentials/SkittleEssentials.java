package twotoz.skittleEssentials;

import com.earth2me.essentials.Essentials;
import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

// Import all subpackage classes
import twotoz.skittleEssentials.commands.*;
import twotoz.skittleEssentials.listeners.*;
import twotoz.skittleEssentials.managers.*;
import twotoz.skittleEssentials.filters.*;

public final class SkittleEssentials extends JavaPlugin {

    private static SkittleEssentials instance;

    private BuildmodeManager buildmodeManager;
    private BuildmodeListener buildmodeListener;
    private JailDataManager jailDataManager;
    private JailbanManager jailbanManager;
    private JailbanListener jailbanListener;
    private StaffChatListener staffChatListener;
    private LocalChatListener localChatListener;
    private JailVoteManager jailVoteManager;
    private BaltopRewardManager baltopRewardManager;
    private FakePlayersManager fakePlayersManager;
    private NewPlayerFilter newPlayerFilter;
    private NewPlayerFilterListener newPlayerFilterListener;
    private Economy economy;
    private Essentials essentials;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Setup Economy (Vault)
        if (!setupEconomy()) {
            getLogger().warning("⚠ Vault or Economy plugin not found! Jail vote will be disabled.");
        } else {
            getLogger().info("✅ Economy system hooked!");
        }

        // Setup Essentials
        if (!setupEssentials()) {
            getLogger().warning("⚠ Essentials not found! Baltop rewards will be disabled.");
        } else {
            getLogger().info("✅ Essentials hooked!");
        }

        // Setup NewPlayerFilter (Command Filter Only) - Requires Essentials
        if (essentials != null) {
            newPlayerFilter = new NewPlayerFilter(this, essentials);
            newPlayerFilterListener = new NewPlayerFilterListener(this, newPlayerFilter);
            getServer().getPluginManager().registerEvents(newPlayerFilterListener, this);

            getLogger().info("✅ NewPlayerFilter (Command Blocker) loaded!");
        } else {
            getLogger().warning("⚠ NewPlayerFilter disabled - Essentials not available!");
        }

        // Setup LuckPerms
        if (!setupLuckPerms()) {
            getLogger().warning("⚠ LuckPerms not found! Baltop rewards will be disabled.");
        } else {
            getLogger().info("✅ LuckPerms hooked!");
        }

        // Register commands
        getCommand("sizer").setExecutor(new SizerCommand(this));
        getCommand("skittle").setExecutor(new SkittleCommand(this));

        // Setup Buildmode
        buildmodeManager = new BuildmodeManager(this);
        buildmodeListener = new BuildmodeListener(this, buildmodeManager);
        getServer().getPluginManager().registerEvents(buildmodeListener, this);
        getCommand("buildmode").setExecutor(new BuildmodeCommand(this, buildmodeManager));
        getLogger().info("✅ Buildmode loaded with exploit protection!");

        // Setup Jailban with Bail System
        jailDataManager = new JailDataManager(this);

        // Migrate old YAML data if exists
        java.io.File oldJailData = new java.io.File(new java.io.File(getDataFolder(), "jaildata"), "jailbalance.yml");
        if (oldJailData.exists()) {
            jailDataManager.migrateFromYAML(oldJailData);
        }

        jailbanManager = new JailbanManager(this, jailDataManager);

        // Define staffChatListener before others
        staffChatListener = new StaffChatListener(this);
        getServer().getPluginManager().registerEvents(staffChatListener, this);
        getLogger().info("✅ Staff chat enabled!");

        // Initialize Local Chat if enabled (needs JailbanManager to check regions)
        if (getConfig().getBoolean("localchat.enabled", true)) {
            localChatListener = new LocalChatListener(this, jailbanManager, staffChatListener);
            getServer().getPluginManager().registerEvents(localChatListener, this);
            getCommand("localchat").setExecutor(localChatListener);
            getCommand("localchatspy").setExecutor(localChatListener);
            getLogger().info("✅ Local chat enabled!");
        } else {
            getLogger().info("⚠ Local chat disabled in config!");
        }

        if (jailbanManager.isConfigured()) {
            // Load jailed players from data file
            jailbanManager.loadJailedPlayersFromData();

            jailbanListener = new JailbanListener(this, jailbanManager, staffChatListener);
            getServer().getPluginManager().registerEvents(jailbanListener, this);

            JailbanCommand jailbanCommand = new JailbanCommand(this, jailbanManager);
            getCommand("jailban").setExecutor(jailbanCommand);
            getCommand("jailban").setTabCompleter(jailbanCommand);
            getCommand("unjailban").setExecutor(jailbanCommand);
            getCommand("unjailban").setTabCompleter(jailbanCommand);
            getCommand("jaillist").setExecutor(jailbanCommand);
            getCommand("jaillist").setTabCompleter(jailbanCommand);
            getCommand("jailbal").setExecutor(jailbanCommand);
            getCommand("jailbal").setTabCompleter(jailbanCommand);
            getCommand("bail").setExecutor(jailbanCommand);
            getCommand("bail").setTabCompleter(jailbanCommand);

            // Register Jail Chat Spy
            getCommand("jailchatspy").setExecutor(jailbanListener);

            getLogger().info("✅ Jailban bail system loaded!");

            // Setup Jail Vote System
            if (economy != null) {
                jailVoteManager = new JailVoteManager(this, jailbanManager);
                JailVoteCommand jailVoteCommand = new JailVoteCommand(this, jailVoteManager, jailbanManager, economy);
                getCommand("startjailvote").setExecutor(jailVoteCommand);
                getCommand("startjailvote").setTabCompleter(jailVoteCommand);
                getCommand("jailvote").setExecutor(jailVoteCommand);
                getCommand("jailvote").setTabCompleter(jailVoteCommand);
                getLogger().info("✅ Jail vote system loaded!");
            } else {
                getLogger().warning("⚠ Jail vote disabled - economy not available!");
            }
        } else {
            getLogger().warning("⚠ Jailban region not properly configured!");
        }

        // Setup Baltop Rewards
        if (essentials != null && luckPerms != null) {
            baltopRewardManager = new BaltopRewardManager(this, essentials, luckPerms);
            baltopRewardManager.start();
        } else {
            getLogger().warning("⚠ Baltop rewards disabled - Essentials or LuckPerms not available!");
        }

        // Setup Fake Players
        fakePlayersManager = new FakePlayersManager(this);
        fakePlayersManager.start();

        getLogger().info("✅ SkittleEssentials enabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    private boolean setupEssentials() {
        if (getServer().getPluginManager().getPlugin("Essentials") == null) {
            return false;
        }

        essentials = (Essentials) getServer().getPluginManager().getPlugin("Essentials");
        return essentials != null;
    }

    private boolean setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        }
        return luckPerms != null;
    }

    public void reloadSettings() {
        reloadConfig();

        // Reload buildmode config
        if (buildmodeListener != null) {
            buildmodeListener.loadConfig();
        }

        // Reload jailban config
        if (jailbanManager != null) {
            jailbanManager.loadJailRegion();
        }

        // Reload jail data
        if (jailDataManager != null) {
            jailDataManager.reload();
        }

        // Reload staff chat config
        if (staffChatListener != null) {
            staffChatListener.loadConfig();
        }

        // Reload local chat config
        if (localChatListener != null) {
            localChatListener.loadConfig();
        }

        // Reload jail vote config
        if (jailVoteManager != null) {
            jailVoteManager.loadConfig();
        }

        // Reload baltop rewards config and restart task
        if (baltopRewardManager != null) {
            baltopRewardManager.loadConfig();
            baltopRewardManager.restart(); // Restart timer to apply new settings
        }

        // Reload new player filter config
        if (newPlayerFilter != null) {
            newPlayerFilter.loadConfig();
        }

        // Reload fake players config
        if (fakePlayersManager != null) {
            fakePlayersManager.stop();
            fakePlayersManager.loadConfig();
            fakePlayersManager.start();
        }
    }

    public BaltopRewardManager getBaltopRewardManager() {
        return baltopRewardManager;
    }

    public JailbanManager getJailbanManager() {
        return jailbanManager;
    }

    public FakePlayersManager getFakePlayersManager() {
        return fakePlayersManager;
    }

    public NewPlayerFilter getNewPlayerFilter() {
        return newPlayerFilter;
    }

    public Essentials getEssentials() {
        return essentials;
    }

    public static SkittleEssentials getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        // Shutdown jail database connection
        if (jailDataManager != null) {
            jailDataManager.shutdown();
        }

        if (buildmodeManager != null) {
            buildmodeManager.shutdown();
        }
        if (jailbanManager != null) {
            jailbanManager.shutdown();
        }
        if (jailVoteManager != null) {
            jailVoteManager.cancelVote();
        }
        if (baltopRewardManager != null) {
            baltopRewardManager.stop();
        }
        if (fakePlayersManager != null) {
            fakePlayersManager.stop();
        }
        getLogger().info("❌ SkittleEssentials disabled.");
    }
}