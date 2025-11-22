package twotoz.skittleEssentials.listeners;


import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.filters.NewPlayerFilter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class NewPlayerFilterListener implements Listener {

    private final SkittleEssentials plugin;
    private final NewPlayerFilter filter;

    public NewPlayerFilterListener(SkittleEssentials plugin, NewPlayerFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
        
        // Register ProtocolLib packet listener voor chat filtering
        if (filter.isChatFilterEnabled()) {
            registerChatPacketListener();
        }
    }

    /**
     * Blokkeer commands voor nieuwe spelers
     * LOWEST priority zodat we vroeg intercepten
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!filter.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        String command = event.getMessage();

        // Check of speler nieuw is
        if (filter.isNewPlayer(player)) {
            // Check of dit een geblokkeerd commando is
            if (filter.isCommandBlocked(command)) {
                event.setCancelled(true);
                player.sendMessage("§cNo permission.");
            }
        }
    }
    
    /**
     * Register ProtocolLib packet listener om chat berichten te filteren
     * voor nieuwe spelers (ze zien blocked words van anderen als ***)
     */
    private void registerChatPacketListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(plugin, ListenerPriority.NORMAL, PacketType.Play.Server.SYSTEM_CHAT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    if (event.isCancelled()) {
                        return;
                    }
                    
                    Player receiver = event.getPlayer();
                    
                    // Alleen filteren voor nieuwe spelers (die het bericht ONTVANGEN)
                    if (!filter.isNewPlayer(receiver)) {
                        return;
                    }
                    
                    PacketContainer packet = event.getPacket();
                    
                    try {
                        // Get chat component
                        WrappedChatComponent chatComponent = packet.getChatComponents().read(0);
                        if (chatComponent == null) {
                            return;
                        }
                        
                        // Get JSON string
                        String json = chatComponent.getJson();
                        
                        // Filter blocked words in the JSON
                        String filteredJson = filterJsonMessage(json);
                        
                        // Update packet met gefilterde content
                        if (!json.equals(filteredJson)) {
                            packet.getChatComponents().write(0, WrappedChatComponent.fromJson(filteredJson));
                        }
                        
                    } catch (Exception e) {
                        // Fail silently - don't break chat
                        plugin.getLogger().fine("Failed to filter chat packet: " + e.getMessage());
                    }
                }
            }
        );
    }
    
    /**
     * Filter blocked words in JSON chat component
     * Preserves all formatting, colors, ranks, etc.
     */
    private String filterJsonMessage(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        String filtered = json;
        
        // Filter elk blocked word (case-insensitive, whole words only)
        for (String blockedWord : filter.getBlockedWords()) {
            if (blockedWord.isEmpty()) continue;
            
            // Create regex for whole word matching, case-insensitive
            // This works in JSON text fields
            String regex = "(?i)\\b" + java.util.regex.Pattern.quote(blockedWord) + "\\b";
            filtered = filtered.replaceAll(regex, filter.getReplacementString());
        }
        
        return filtered;
    }
}
