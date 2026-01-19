package twotoz.skittleEssentials.commands;

import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.BuildmodeManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildmodeCommand implements CommandExecutor, TabCompleter {

    private final SkittleEssentials plugin;
    private final BuildmodeManager buildmodeManager;
    private final Map<UUID, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT = 30000; // 30 seconds
    private boolean isFolia = false;

    public BuildmodeCommand(SkittleEssentials plugin, BuildmodeManager buildmodeManager) {
        this.plugin = plugin;
        this.buildmodeManager = buildmodeManager;

        // Detect Folia
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be executed by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("skittle.buildmode")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            if (buildmodeManager.isInBuildmode(player)) {
                player.sendMessage("§eYou are currently in §6§lBUILDMODE§e!");
                player.sendMessage("§7Use §e/buildmode off §7to deactivate.");
            } else {
                player.sendMessage("§7Buildmode is currently §cdisabled§7.");
                player.sendMessage("§7Use §e/buildmode on §7to activate.");
            }
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "on":
                handleBuildmodeOn(player, args);
                break;

            case "off":
                handleBuildmodeOff(player);
                break;

            default:
                player.sendMessage("§cUsage: /buildmode <on|off>");
                break;
        }

        return true;
    }

    private void handleBuildmodeOn(Player player, String[] args) {
        if (buildmodeManager.isInBuildmode(player)) {
            player.sendMessage("§cYou are already in buildmode!");
            return;
        }

        // Check for confirmation
        if (args.length == 1) {
            // Request confirmation
            pendingConfirmations.put(player.getUniqueId(), System.currentTimeMillis());

            player.sendMessage("§c§l⚠ WARNING! ⚠");
            player.sendMessage("§eAre you sure you want to enable buildmode?");
            player.sendMessage("§c§lWARNING: Your inventory will be cleared!");
            player.sendMessage("");
            player.sendMessage("§7Type §e/buildmode on confirm §7to confirm.");
            player.sendMessage("§7This confirmation expires in 30 seconds.");
            return;
        }

        // Check if they typed "confirm"
        if (args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
            Long pendingTime = pendingConfirmations.get(player.getUniqueId());

            if (pendingTime == null) {
                player.sendMessage("§cYou must first type §e/buildmode on §cbefore confirming!");
                return;
            }

            // Check timeout
            if (System.currentTimeMillis() - pendingTime > CONFIRMATION_TIMEOUT) {
                pendingConfirmations.remove(player.getUniqueId());
                player.sendMessage("§cConfirmation expired! Type §e/buildmode on §cagain.");
                return;
            }

            // Confirmation successful - schedule on player's region (Folia-safe)
            pendingConfirmations.remove(player.getUniqueId());

            if (isFolia) {
                player.getScheduler().run(plugin, (task) -> {
                    enableBuildmode(player);
                }, null);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> enableBuildmode(player));
            }
        } else {
            player.sendMessage("§cUsage: /buildmode on confirm");
        }
    }

    private void enableBuildmode(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Set gamemode creative
        player.setGameMode(GameMode.CREATIVE);

        // Register in buildmode
        buildmodeManager.addPlayer(player);

        player.sendMessage("§a§l✓ Buildmode enabled!");
        player.sendMessage("§7Your inventory has been cleared and you are in creative mode.");
        player.sendMessage("§c§lIMPORTANT: §7You cannot place/break containers or items with NBT data!");
        player.sendMessage("§7Use §e/buildmode off §7to deactivate.");
    }

    private void handleBuildmodeOff(Player player) {
        if (!buildmodeManager.isInBuildmode(player)) {
            player.sendMessage("§cYou are not in buildmode!");
            return;
        }

        // Schedule on player's region (Folia-safe)
        if (isFolia) {
            player.getScheduler().run(plugin, (task) -> {
                disableBuildmode(player);
            }, null);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> disableBuildmode(player));
        }
    }

    private void disableBuildmode(Player player) {
        // Clear inventory
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);

        // Set gamemode survival
        player.setGameMode(GameMode.SURVIVAL);

        // Remove from buildmode
        buildmodeManager.removePlayer(player);

        player.sendMessage("§c§l✗ Buildmode disabled!");
        player.sendMessage("§7Your inventory has been cleared and you are in survival mode.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if ("on".startsWith(args[0].toLowerCase())) {
                completions.add("on");
            }
            if ("off".startsWith(args[0].toLowerCase())) {
                completions.add("off");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("on")) {
            if ("confirm".startsWith(args[1].toLowerCase())) {
                completions.add("confirm");
            }
        }

        return completions;
    }
}