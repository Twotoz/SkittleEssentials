package twotoz.skittleEssentials.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
    public void onPlayerChat(AsyncChatEvent event) {
        if (!filter.isEnabled() || !filter.isChatFilterEnabled()) {
            return;
        }

        // Cancel de standaard broadcast om zelf te handelen
        event.setCancelled(true);

        Player sender = event.getPlayer();
        Component originalMessage = event.message();

        // Stuur naar elke recipient
        for (Audience audience : event.viewers()) {
            if (!(audience instanceof Player recipient)) {
                continue;
            }

            Component messageToSend = originalMessage;

            // Filter alleen voor nieuwe spelers (ontvangers met te weinig playtime), maar skip voor de sender zelf
            if (filter.isNewPlayer(recipient) && !recipient.equals(sender)) {
                messageToSend = filter.filterMessageContent(originalMessage);
            }

            // Render met de originele renderer om formatting en ranks te behouden (compatibel met EssentialsX etc.)
            Component rendered = event.renderer().render(sender, sender.displayName(), messageToSend, recipient);

            // Stuur het gerenderde bericht
            recipient.sendMessage(rendered);
        }
    }
}