package twotoz.skittleEssentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.JailbanManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JailbanCommand implements CommandExecutor, TabCompleter {

    private final SkittleEssentials plugin;
    private final JailbanManager jailbanManager;
    private boolean isFolia = false;

    public JailbanCommand(SkittleEssentials plugin, JailbanManager jailbanManager) {
        this.plugin = plugin;
        this.jailbanManager = jailbanManager;

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

        if (command.getName().equalsIgnoreCase("jailban")) {
            return handleJailban(sender, args);
        } else if (command.getName().equalsIgnoreCase("unjailban")) {
            return handleUnjailban(sender, args);
        } else if (command.getName().equalsIgnoreCase("jaillist")) {
            return handleJaillist(sender);
        } else if (command.getName().equalsIgnoreCase("jailbal")) {
            return handleJailbal(sender);
        } else if (command.getName().equalsIgnoreCase("bail")) {
            return handleBail(sender, args);
        }

        return false;
    }

    private boolean handleJailban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skittle.jailban")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /jailban <player> <bail_amount> [reason]");
            sender.sendMessage("§7Example: /jailban Steve 500 Griefing");
            return true;
        }

        String targetName = args[0];

        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            sender.sendMessage("§cPlayer not found! They have never played on this server.");
            return true;
        }

        UUID targetUUID = offlineTarget.getUniqueId();
        String targetDisplayName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;

        if (jailbanManager.isJailbanned(targetUUID)) {
            sender.sendMessage("§c" + targetDisplayName + " is already jailed!");
            return true;
        }

        double bailAmount;
        try {
            bailAmount = Double.parseDouble(args[1]);
            if (bailAmount < 0) {
                sender.sendMessage("§cBail amount must be positive!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid bail amount!");
            return true;
        }

        String reason = "No reason given";
        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        Location jailSpawn = jailbanManager.getJailSpawn();
        if (jailSpawn == null) {
            sender.sendMessage("§cJail spawn is not properly configured!");
            return true;
        }

        jailbanManager.jailban(targetUUID, reason, bailAmount);

        String senderName = sender instanceof Player ? sender.getName() : "Console";
        Player onlineTarget = offlineTarget.getPlayer();

        if (onlineTarget != null && onlineTarget.isOnline()) {
            // Player is online - teleport them (Folia-safe)
            if (isFolia) {
                onlineTarget.getScheduler().run(plugin, (task) -> {
                    onlineTarget.teleport(jailSpawn);
                    sendJailNotification(onlineTarget, reason, bailAmount, senderName);
                }, null);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    onlineTarget.teleport(jailSpawn);
                    sendJailNotification(onlineTarget, reason, bailAmount, senderName);
                });
            }

            sender.sendMessage("§a§l✓ " + targetDisplayName + " has been jailed!");
        } else {
            sender.sendMessage("§a§l✓ " + targetDisplayName + " has been jailed!");
            sender.sendMessage("§7They are offline and will be teleported to jail when they join.");
        }

        sender.sendMessage("§7Reason: §e" + reason);
        sender.sendMessage("§7Bail Amount: §a$" + String.format("%.2f", bailAmount));

        // Broadcast to staff
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("skittle.jailban.notify") && !staff.equals(sender)) {
                String status = onlineTarget != null ? "online" : "offline";
                staff.sendMessage("§7[§c⚖§7] " + targetDisplayName + " (" + status + ") was jailed by " + senderName + " (Bail: $" + String.format("%.2f", bailAmount) + ")");
            }
        }

        return true;
    }

    private void sendJailNotification(Player player, String reason, double bailAmount, String senderName) {
        player.sendMessage("§c§l⚖ You have been jailed!");
        player.sendMessage("§7Reason: §e" + reason);
        player.sendMessage("§7Bail Amount: §a$" + String.format("%.2f", bailAmount));
        player.sendMessage("§7By: §e" + senderName);
        player.sendMessage("§7");
        player.sendMessage("§7Kill mobs to earn money and use §e/bail §7to check your progress!");
    }

    private boolean handleUnjailban(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skittle.jailban")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /unjailban <player>");
            return true;
        }

        String targetName = args[0];

        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);

        if (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline()) {
            sender.sendMessage("§cPlayer not found! They have never played on this server.");
            return true;
        }

        UUID targetUUID = offlineTarget.getUniqueId();
        String targetDisplayName = offlineTarget.getName() != null ? offlineTarget.getName() : targetName;

        if (!jailbanManager.isJailbanned(targetUUID)) {
            sender.sendMessage("§c" + targetDisplayName + " is not jailed!");
            return true;
        }

        jailbanManager.unjailban(targetUUID);

        String senderName = sender instanceof Player ? sender.getName() : "Console";
        Player onlineTarget = offlineTarget.getPlayer();

        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage("§a§l✓ You have been released from jail!");
            onlineTarget.sendMessage("§7By: §e" + senderName);
            sender.sendMessage("§a§l✓ " + targetDisplayName + " has been released from jail!");
        } else {
            sender.sendMessage("§a§l✓ " + targetDisplayName + " has been released from jail!");
            sender.sendMessage("§7They are offline and will be notified when they join.");
        }

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("skittle.jailban.notify") && !staff.equals(sender)) {
                String status = onlineTarget != null ? "online" : "offline";
                staff.sendMessage("§7[§a✓§7] " + targetDisplayName + " (" + status + ") was unjailed by " + senderName);
            }
        }

        return true;
    }

    private boolean handleJaillist(CommandSender sender) {
        if (!sender.hasPermission("skittle.jailban")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        List<String> jailedPlayers = jailbanManager.getJailbannedPlayers();

        if (jailedPlayers.isEmpty()) {
            sender.sendMessage("§7There are currently no jailed players.");
            return true;
        }

        sender.sendMessage("§6§l=== Jailed Players ===");
        for (String playerName : jailedPlayers) {
            Player player = Bukkit.getPlayer(playerName);
            if (player != null && player.isOnline()) {
                double current = jailbanManager.getCurrentBalance(player);
                double required = jailbanManager.getBailRequired(player);
                String progress = String.format("$%.2f / $%.2f", current, required);
                sender.sendMessage("§e- " + playerName + " §a(Online) §7- " + progress);
            } else {
                sender.sendMessage("§e- " + playerName + " §7(Offline)");
            }
        }
        sender.sendMessage("§7Total: §e" + jailedPlayers.size() + " §7player(s)");

        return true;
    }

    private boolean handleJailbal(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!jailbanManager.isJailbanned(player)) {
            sender.sendMessage("§cYou are not in jail!");
            return true;
        }

        double current = jailbanManager.getCurrentBalance(player);
        double required = jailbanManager.getBailRequired(player);
        double remaining = required - current;

        sender.sendMessage("§6§l=== Your Jail Balance ===");
        sender.sendMessage("§7Current Balance: §a$" + String.format("%.2f", current));
        sender.sendMessage("§7Bail Required: §e$" + String.format("%.2f", required));
        sender.sendMessage("§7Remaining: §c$" + String.format("%.2f", remaining));
        sender.sendMessage("");

        if (current >= required) {
            sender.sendMessage("§a§l✓ You can bail out! Use §e/bail confirm");
        } else {
            double percentage = (current / required) * 100;
            sender.sendMessage("§7Progress: §e" + String.format("%.1f", percentage) + "%");
            sender.sendMessage("§7Keep killing mobs to earn money!");
        }

        return true;
    }

    private boolean handleBail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (!jailbanManager.isJailbanned(player)) {
            sender.sendMessage("§cYou are not in jail!");
            return true;
        }

        if (args.length == 0) {
            return handleJailbal(sender);
        }

        if (args[0].equalsIgnoreCase("confirm")) {
            if (!jailbanManager.canAffordBail(player)) {
                double current = jailbanManager.getCurrentBalance(player);
                double required = jailbanManager.getBailRequired(player);
                sender.sendMessage("§cYou don't have enough money to bail out!");
                sender.sendMessage("§7You have §a$" + String.format("%.2f", current) + " §7but need §e$" + String.format("%.2f", required));
                return true;
            }

            jailbanManager.bailOut(player);

            player.sendMessage("§a§l✓ You have been released from jail!");
            player.sendMessage("§7You paid your bail and are now free!");

            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("skittle.jailban.notify") && !staff.equals(player)) {
                    staff.sendMessage("§7[§a✓§7] " + player.getName() + " bailed themselves out of jail!");
                }
            }

            return true;
        }

        sender.sendMessage("§cUsage: /bail or /bail confirm");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("skittle.jailban")) {
            return completions;
        }

        if (command.getName().equalsIgnoreCase("jailban")) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 2) {
                return Arrays.asList("100", "250", "500", "1000", "2500")
                        .stream()
                        .filter(d -> d.startsWith(args[1]))
                        .collect(Collectors.toList());
            }
        } else if (command.getName().equalsIgnoreCase("unjailban")) {
            if (args.length == 1) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (command.getName().equalsIgnoreCase("bail")) {
            if (args.length == 1) {
                return Arrays.asList("confirm")
                        .stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}