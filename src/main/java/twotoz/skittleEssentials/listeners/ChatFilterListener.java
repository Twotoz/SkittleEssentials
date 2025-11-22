package twotoz.skittleEssentials.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.filters.NewPlayerFilter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Thread-safe chat filter using ProtocolLib
 * Filters chat messages for new players by replacing blocked words with ***
 */
public class ChatFilterListener extends PacketAdapter {

    private final SkittleEssentials plugin;
    private final NewPlayerFilter filter;
    
    // Thread-safe pattern cache (compiled once, used many times)
    private volatile List<FilterPattern> filterPatterns;
    
    /**
     * Represents a blocked word pattern with its replacement
     */
    private static class FilterPattern {
        final Pattern pattern;
        final String replacement;
        
        FilterPattern(String word) {
            // Create case-insensitive pattern with word boundaries
            // \b ensures we match whole words, not parts
            this.pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
            this.replacement = "***";
        }
        
        String filter(String message) {
            return pattern.matcher(message).replaceAll(replacement);
        }
    }

    public ChatFilterListener(SkittleEssentials plugin, NewPlayerFilter filter) {
        // LOWEST priority to intercept before other plugins
        super(plugin, ListenerPriority.LOWEST, PacketType.Play.Client.CHAT);
        this.plugin = plugin;
        this.filter = filter;
        loadFilterPatterns();
    }

    /**
     * Load and compile filter patterns from config
     * Thread-safe: volatile write ensures visibility across threads
     */
    public void loadFilterPatterns() {
        List<String> blockedWords = plugin.getConfig().getStringList("new-player-filter.blocked-words");
        
        List<FilterPattern> patterns = new ArrayList<>();
        for (String word : blockedWords) {
            if (word != null && !word.trim().isEmpty()) {
                patterns.add(new FilterPattern(word.trim()));
            }
        }
        
        // Atomic replacement of pattern list
        this.filterPatterns = patterns;
        
        plugin.getLogger().info("Loaded " + patterns.size() + " chat filter patterns");
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Early exit if filtering disabled
        if (!filter.isEnabled()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Early exit if player is temporary (e.g., during login phase)
        if (event.isPlayerTemporary()) {
            return;
        }
        
        // Only filter for new players (bypass permission check included)
        if (!filter.isNewPlayer(player)) {
            return;
        }
        
        // Get the packet
        PacketContainer packet = event.getPacket();
        
        try {
            // Read the chat message (index 0 is the message string)
            String originalMessage = packet.getStrings().read(0);
            
            // Filter the message
            String filteredMessage = filterMessage(originalMessage);
            
            // Only modify packet if message changed
            if (!originalMessage.equals(filteredMessage)) {
                // Write the filtered message back
                packet.getStrings().write(0, filteredMessage);
                
                // Optional: Log filtered messages (debug mode)
                if (plugin.getConfig().getBoolean("new-player-filter.log-filtered-messages", false)) {
                    plugin.getLogger().info("Filtered chat from " + player.getName() + 
                        ": '" + originalMessage + "' -> '" + filteredMessage + "'");
                }
            }
            
        } catch (Exception e) {
            // Never crash the packet handler - log error and continue
            plugin.getLogger().warning("Error filtering chat for " + player.getName() + ": " + e.getMessage());
            // Don't modify packet if error occurs
        }
    }

    /**
     * Filter a message by replacing blocked words
     * Thread-safe: uses immutable pattern list
     * 
     * @param message Original message
     * @return Filtered message with blocked words replaced by ***
     */
    private String filterMessage(String message) {
        // Quick null/empty check
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        // Get current pattern list (thread-safe volatile read)
        List<FilterPattern> patterns = this.filterPatterns;
        
        if (patterns == null || patterns.isEmpty()) {
            return message;
        }
        
        // Apply all filter patterns
        String filtered = message;
        for (FilterPattern pattern : patterns) {
            filtered = pattern.filter(filtered);
        }
        
        return filtered;
    }
}
