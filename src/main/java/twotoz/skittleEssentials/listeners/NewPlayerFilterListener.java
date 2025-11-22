package twotoz.skittleEssentials.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.filters.NewPlayerFilter;

public class NewPlayerFilterListener implements Listener {

    private final SkittleEssentials plugin;
    private final NewPlayerFilter filter;

    public NewPlayerFilterListener(SkittleEssentials plugin, NewPlayerFilter filter) {
        this.plugin = plugin;
        this.filter = filter;
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
     * Filter chat berichten per ontvanger:
     * - Nieuwe spelers zien blocked words als ***
     * - Ervaren spelers zien het origineel
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!filter.isEnabled() || !filter.isChatFilterEnabled()) {
            return;
        }

        // Cancel de standaard broadcast om zelf te handelen
        event.setCancelled(true);

        Player sender = event.getPlayer();
        String originalMessage = event.getMessage();
        String format = event.getFormat();  // Behoudt ranks, kleuren en formatting (inclusief aanpassingen van andere plugins zoals EssentialsX)

        // Stuur naar elke recipient
        for (Player recipient : event.getRecipients()) {
            String messageToSend = originalMessage;

            // Filter alleen voor nieuwe spelers (ontvangers met te weinig playtime), maar skip voor de sender zelf
            if (filter.isNewPlayer(recipient) && recipient != sender) {
                messageToSend = filter.filterMessageContent(originalMessage);
            }

            // Stuur met behouden formatting en ranks
            recipient.sendMessage(String.format(format, sender.getDisplayName(), messageToSend));
        }
    }
}