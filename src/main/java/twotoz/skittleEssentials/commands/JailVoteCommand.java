package twotoz.skittleEssentials.commands;


import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.managers.JailVoteManager;
import twotoz.skittleEssentials.managers.JailbanManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JailVoteCommand implements CommandExecutor, TabCompleter {

    private final SkittleEssentials plugin;
    private final JailVoteManager voteManager;
    private final JailbanManager jailbanManager;
    private final Economy economy;

    public JailVoteCommand(SkittleEssentials plugin, JailVoteManager voteManager, JailbanManager jailbanManager, Economy economy) {
        this.plugin = plugin;
        this.voteManager = voteManager;
        this.jailbanManager = jailbanManager;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (command.getName().equalsIgnoreCase("startjailvote")) {
            return handleStartJailVote(sender, args);
        } else if (command.getName().equalsIgnoreCase("jailvote")) {
            return handleJailVote(sender, args);
        }

        return false;
    }

    private boolean handleStartJailVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can start a jail vote!");
            return true;
        }

        Player player = (Player) sender;

        // Check permission
        if (!player.hasPermission("skittle.jailvote.start")) {
            player.sendMessage("§cYou don't have permission to start a jail vote!");
            return true;
        }

        // Check if vote is already active
        if (voteManager.isVoteActive()) {
            player.sendMessage("§cA jail vote is already in progress!");
            player.sendMessage("§7Time remaining: §e" + voteManager.getTimeRemaining());
            return true;
        }

        // Check if player wants to confirm
        if (args.length == 0) {
            double cost = voteManager.getVoteCost();
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§c§l⚠ START JAIL VOTE?");
            player.sendMessage("");
            player.sendMessage("§7This will start a community vote to jail a player.");
            player.sendMessage("§7Cost: §c$" + String.format("%,.2f", cost));
            player.sendMessage("§7Duration: §e" + voteManager.getVoteDuration() + " seconds");
            player.sendMessage("");
            player.sendMessage("§eType §f/startjailvote confirm §eto proceed");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        if (!args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage("§cUsage: /startjailvote or /startjailvote confirm");
            return true;
        }

        // Check if economy is available
        if (economy == null) {
            player.sendMessage("§cEconomy system is not available!");
            return true;
        }

        // Check if player has enough money
        double cost = voteManager.getVoteCost();
        double balance = economy.getBalance(player);

        if (balance < cost) {
            player.sendMessage("§c§l✗ Insufficient funds!");
            player.sendMessage("§7You need: §c$" + String.format("%,.2f", cost));
            player.sendMessage("§7You have: §e$" + String.format("%,.2f", balance));
            player.sendMessage("§7Missing: §c$" + String.format("%,.2f", cost - balance));
            return true;
        }

        // Withdraw money
        economy.withdrawPlayer(player, cost);

        // Start the vote
        voteManager.startVote(player);

        // Confirmation message to starter
        player.sendMessage("§a§l✓ Jail vote started!");
        player.sendMessage("§7Paid: §c$" + String.format("%,.2f", cost));
        player.sendMessage("§7New balance: §a$" + String.format("%,.2f", economy.getBalance(player)));

        return true;
    }

    private boolean handleJailVote(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can vote!");
            return true;
        }

        Player player = (Player) sender;

        // Check if vote is active
        if (!voteManager.isVoteActive()) {
            player.sendMessage("§cThere is no active jail vote!");
            player.sendMessage("§7Someone needs to start one with §e/startjailvote");
            return true;
        }

        // Check if already voted
        if (voteManager.hasVoted(player)) {
            player.sendMessage("§cYou have already voted in this jail vote!");
            return true;
        }

        // If no args, show current standings
        if (args.length == 0) {
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("§c§l⚖ JAIL VOTE STATUS");
            player.sendMessage("§7Time remaining: §e" + voteManager.getTimeRemaining());
            player.sendMessage("§7Total votes: §e" + voteManager.getTotalVotes());
            player.sendMessage("");

            Map<String, Integer> standings = voteManager.getCurrentVoteStandings();
            if (standings.isEmpty()) {
                player.sendMessage("§7No votes cast yet. Be the first!");
            } else {
                player.sendMessage("§7Current standings:");
                standings.entrySet().stream()
                        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                        .limit(5)
                        .forEach(entry -> {
                            player.sendMessage("§e" + entry.getKey() + " §7- §f" + entry.getValue() + " votes");
                        });
            }

            player.sendMessage("");
            player.sendMessage("§7Vote with: §f/jailvote <player>");
            player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        // Vote for a player
        String targetName = args[0];
        voteManager.vote(player, targetName);

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("startjailvote")) {
            if (args.length == 1) {
                return List.of("confirm").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        } else if (command.getName().equalsIgnoreCase("jailvote")) {
            if (args.length == 1) {
                // Suggest online players
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return completions;
    }
}