package twotoz.skittleEssentials.managers;


import twotoz.skittleEssentials.SkittleEssentials;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FakePlayersManager {

    private final SkittleEssentials plugin;
    private ProtocolManager protocolManager;

    private boolean enabled;
    private int fakePlayerCount;
    private List<String> fakePlayerNames;

    public FakePlayersManager(SkittleEssentials plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        enabled = plugin.getConfig().getBoolean("fake-players.enabled", false);
        fakePlayerCount = plugin.getConfig().getInt("fake-players.count", 5);
        fakePlayerNames = plugin.getConfig().getStringList("fake-players.names");

        // Validation
        if (fakePlayerNames.isEmpty()) {
            plugin.getLogger().warning("No fake player names configured!");
            fakePlayerNames = new ArrayList<>();
            fakePlayerNames.add("Steve");
            fakePlayerNames.add("Alex");
        }
    }

    public void start() {
        if (!enabled) {
            plugin.getLogger().info("⚠ Fake players disabled in config");
            return;
        }

        // Check if ProtocolLib is available
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.getLogger().severe("⚠ ProtocolLib not found! Fake players disabled.");
            return;
        }

        try {
            protocolManager = ProtocolLibrary.getProtocolManager();
            setupPacketListener();
            plugin.getLogger().info("✅ Fake players enabled! Adding " + fakePlayerCount + " fake players");
        } catch (Exception e) {
            plugin.getLogger().severe("⚠ Failed to setup fake players:");
            e.printStackTrace();
        }
    }

    private void setupPacketListener() {
        protocolManager.addPacketListener(new PacketAdapter(
                plugin,
                PacketType.Status.Server.SERVER_INFO
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    // Get the server ping packet
                    WrappedServerPing ping = event.getPacket().getServerPings().read(0);

                    // Get current real player count
                    int realPlayers = Bukkit.getOnlinePlayers().size();

                    // Set fake player count
                    ping.setPlayersOnline(realPlayers + fakePlayerCount);

                    // Create list of player profiles (real + fake)
                    List<WrappedGameProfile> profiles = new ArrayList<>();

                    // Add real players first
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        profiles.add(new WrappedGameProfile(player.getUniqueId(), player.getName()));
                    }

                    // Add fake players
                    for (int i = 0; i < fakePlayerNames.size() && i < fakePlayerCount; i++) {
                        String fakeName = fakePlayerNames.get(i);
                        UUID fakeUUID = UUID.randomUUID();
                        profiles.add(new WrappedGameProfile(fakeUUID, fakeName));
                    }

                    // Set the player list
                    ping.setPlayers(profiles);

                    // Write back to packet
                    event.getPacket().getServerPings().write(0, ping);

                } catch (Exception e) {
                    plugin.getLogger().warning("Error in fake players packet listener:");
                    e.printStackTrace();
                }
            }
        });
    }

    public void stop() {
        if (protocolManager != null) {
            protocolManager.removePacketListeners(plugin);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getFakePlayerCount() {
        return fakePlayerCount;
    }

    public List<String> getFakePlayerNames() {
        return new ArrayList<>(fakePlayerNames);
    }
}