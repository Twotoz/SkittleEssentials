package twotoz.skittleEssentials.listeners;

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
}