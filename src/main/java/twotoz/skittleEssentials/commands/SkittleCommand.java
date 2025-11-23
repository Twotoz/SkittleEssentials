package twotoz.skittleEssentials.commands;

import twotoz.skittleEssentials.SkittleEssentials;
import twotoz.skittleEssentials.filters.NewPlayerFilter;
import twotoz.skittleEssentials.managers.BaltopRewardManager;
import twotoz.skittleEssentials.managers.FakePlayersManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SkittleCommand implements CommandExecutor, TabCompleter {

    private final SkittleEssentials plugin;

    public SkittleCommand(SkittleEssentials plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("§e§l    SkittleEssentials v1.3.1");
            sender.sendMessage("§7        Created by: §eTwotoz");
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("");
            sender.sendMessage("§6Features:");
            sender.sendMessage("§e▪ §7Player Sizer - Adjust player size");
            sender.sendMessage("§e▪ §7Buildmode - Safe building mode for staff");
            sender.sendMessage("§e▪ §7Jailban - Prison system with bail mechanic");
            sender.sendMessage("§e▪ §7Jail Vote - Community voting system");
            sender.sendMessage("§e▪ §7Staff Chat - Private staff communication");
            sender.sendMessage("§e▪ §7Baltop Rewards - Auto LuckPerms groups for rich players");
            sender.sendMessage("§e▪ §7Fake Players - Server list spoofing");
            sender.sendMessage("§e▪ §7New Player Filter - Command blocking");
            sender.sendMessage("");
            sender.sendMessage("§6Quick Commands:");
            sender.sendMessage("§e/sizer §7- Change player sizes");
            sender.sendMessage("§e/buildmode §7- Toggle build mode");
            sender.sendMessage("§e/jailban §7- Manage jail system");
            sender.sendMessage("§e/startjailvote §7- Start community jail vote");
            sender.sendMessage("§e/skittle help §7- View all commands");
            sender.sendMessage("§e/skittle reload §7- Reload configuration");
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);

            case "baltop":
                return handleBaltop(sender);

            case "help":
                return handleHelp(sender, args);

            case "info":
            case "about":
                return handleInfo(sender);

            case "version":
            case "ver":
                return handleVersion(sender);

            default:
                sender.sendMessage("§cUnknown subcommand: §e" + subCommand);
                sender.sendMessage("§7Use §e/skittle help §7for available commands");
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("skittle.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        try {
            long startTime = System.currentTimeMillis();
            plugin.reloadSettings();
            long timeTaken = System.currentTimeMillis() - startTime;

            sender.sendMessage("§a§l✓ SkittleEssentials configuration reloaded!");
            sender.sendMessage("§7Took §e" + timeTaken + "ms");
        } catch (Exception e) {
            sender.sendMessage("§c§l✗ Error while reloading: " + e.getMessage());
            e.printStackTrace();
        }
        return true;
    }

    private boolean handleBaltop(CommandSender sender) {
        if (!sender.hasPermission("skittle.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        BaltopRewardManager baltopManager = plugin.getBaltopRewardManager();

        if (baltopManager == null) {
            sender.sendMessage("§c§l✗ Baltop rewards system is not enabled!");
            sender.sendMessage("§7Make sure Essentials and LuckPerms are installed.");
            return true;
        }

        sender.sendMessage("§e⟳ Forcing baltop rewards update...");
        baltopManager.forceUpdate();
        sender.sendMessage("§a§l✓ Baltop rewards update started!");
        sender.sendMessage("§7Check console for details.");

        return true;
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        if (args.length > 1) {
            String topic = args[1].toLowerCase();
            switch (topic) {
                case "sizer":
                    sender.sendMessage("§6§l=== Sizer Help ===");
                    sender.sendMessage("§e/sizer <scale> §7- Change your size");
                    sender.sendMessage("§e/sizer <player> <scale> §7- Change another player's size");
                    sender.sendMessage("");
                    sender.sendMessage("§7Regular players: §e0.7 - 1.2");
                    sender.sendMessage("§7OP players: §e0.01 - 25.0");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.sizer.use §7- Use on yourself");
                    sender.sendMessage("§e- skittle.sizer.other §7- Use on others");
                    sender.sendMessage("§e- skittle.sizer.admin §7- Full range access");
                    return true;

                case "buildmode":
                    sender.sendMessage("§6§l=== Buildmode Help ===");
                    sender.sendMessage("§e/buildmode §7- Check status");
                    sender.sendMessage("§e/buildmode on §7- Request activation");
                    sender.sendMessage("§e/buildmode on confirm §7- Confirm activation");
                    sender.sendMessage("§e/buildmode off §7- Deactivate");
                    sender.sendMessage("");
                    sender.sendMessage("§cWarning: §7Inventory will be cleared!");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.buildmode §7- Use buildmode");
                    return true;

                case "jailban":
                case "jail":
                    sender.sendMessage("§6§l=== Jailban Help ===");
                    sender.sendMessage("§e/jailban <player> <bail_amount> [reason]");
                    sender.sendMessage("§7  Send a player to jail with bail");
                    sender.sendMessage("§e/unjailban <player>");
                    sender.sendMessage("§7  Release a player from jail");
                    sender.sendMessage("§e/jaillist");
                    sender.sendMessage("§7  View all jailed players");
                    sender.sendMessage("§e/jailbal");
                    sender.sendMessage("§7  Check your jail balance (as prisoner)");
                    sender.sendMessage("§e/bail [confirm]");
                    sender.sendMessage("§7  Bail yourself out of jail");
                    sender.sendMessage("");
                    sender.sendMessage("§7Example: §e/jailban Steve 500 Griefing");
                    sender.sendMessage("§7Players earn $1 per mob kill to pay bail");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.jailban §7- Use jail commands");
                    sender.sendMessage("§e- skittle.jailban.bypass §7- Bypass restrictions");
                    sender.sendMessage("§e- skittle.jailban.notify §7- See jail chat");
                    return true;

                case "jailvote":
                case "vote":
                    sender.sendMessage("§6§l=== Jail Vote Help ===");
                    sender.sendMessage("§e/startjailvote §7- View info and cost");
                    sender.sendMessage("§e/startjailvote confirm §7- Start vote ($1M)");
                    sender.sendMessage("§e/jailvote <player> §7- Vote for a player");
                    sender.sendMessage("§e/jailvote §7- View current standings");
                    sender.sendMessage("");
                    sender.sendMessage("§7Duration: 2 minutes (120 seconds)");
                    sender.sendMessage("§7Cost: $1,000,000");
                    sender.sendMessage("§7Winner gets jailed with bail amount");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.jailvote.start §7- Start votes");
                    return true;

                case "staffchat":
                case "staff":
                    sender.sendMessage("§6§l=== Staff Chat Help ===");
                    sender.sendMessage("");
                    sender.sendMessage("§7Method 1 - Toggle Mode:");
                    sender.sendMessage("§e/sct §7or §e/staffchat §7- Toggle staff chat on/off");
                    sender.sendMessage("§7When enabled, all messages go to staff chat");
                    sender.sendMessage("");
                    sender.sendMessage("§7Method 2 - Prefix Mode:");
                    sender.sendMessage("§7Type §e! §7before your message to use staff chat");
                    sender.sendMessage("§7Example: §e!Hey team, check spawn");
                    sender.sendMessage("");
                    sender.sendMessage("§7Features:");
                    sender.sendMessage("§e▪ §7Only staff can see messages");
                    sender.sendMessage("§e▪ §7Discord bots can't see it");
                    sender.sendMessage("§e▪ §7Logged in console");
                    sender.sendMessage("§e▪ §7Both methods work together");
                    sender.sendMessage("");
                    sender.sendMessage("§7Aliases:");
                    sender.sendMessage("§e/staffchat §7| §e/sct §7| §e/sc");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.staffchat §7- Use and see staff chat");
                    return true;

                case "baltop":
                case "rewards":
                    sender.sendMessage("§6§l=== Baltop Rewards Help ===");
                    sender.sendMessage("§7Automatic LuckPerms groups for top 3 richest players");
                    sender.sendMessage("");
                    sender.sendMessage("§7How it works:");
                    sender.sendMessage("§e▪ §7Checks every 30 minutes");
                    sender.sendMessage("§e▪ §7#1 gets added to rank-1-group");
                    sender.sendMessage("§e▪ §7#2 gets added to rank-2-group");
                    sender.sendMessage("§e▪ §7#3 gets added to rank-3-group");
                    sender.sendMessage("§e▪ §7Groups are automatically created if missing");
                    sender.sendMessage("");
                    sender.sendMessage("§7Admin Commands:");
                    sender.sendMessage("§e/skittle baltop §7- Force manual update");
                    sender.sendMessage("");
                    sender.sendMessage("§7Requires: §eEssentials, LuckPerms");
                    return true;

                case "newplayerfilter":
                case "filter":
                case "npf":
                    sender.sendMessage("§6§l=== New Player Filter Help ===");
                    sender.sendMessage("§7Command blocking for new players");
                    sender.sendMessage("");
                    sender.sendMessage("§7How it works:");
                    sender.sendMessage("§e▪ §7Blocks certain commands until playtime threshold met");
                    sender.sendMessage("§e▪ §7Shows 'No permission.' to new players");
                    sender.sendMessage("§e▪ §7Supports both base commands and specific args");
                    sender.sendMessage("§e▪ §7Example: 'warp' blocks all, 'warp shop' blocks specific");
                    sender.sendMessage("");
                    sender.sendMessage("§7Default threshold: §e3.0 hours playtime");
                    sender.sendMessage("§7Message to new player: §cNo permission.");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.newplayerfilter.bypass §7- Bypass all filters");
                    sender.sendMessage("§7(OP players automatically bypass)");
                    sender.sendMessage("");
                    sender.sendMessage("§7Configuration: §econfig.yml > new-player-filter");
                    sender.sendMessage("§7Requires: §eEssentials (for playtime tracking)");
                    return true;

                default:
                    sender.sendMessage("§cUnknown help topic: §e" + topic);
                    sender.sendMessage("§7Available topics: §esizer, buildmode, jailban, jailvote, staffchat, baltop, newplayerfilter");
                    return true;
            }
        }

        // General help
        sender.sendMessage("§6§l=== SkittleEssentials Help ===");
        sender.sendMessage("");
        sender.sendMessage("§6Admin Commands:");
        sender.sendMessage("§e/skittle §7- Plugin information");
        sender.sendMessage("§e/skittle reload §7- Reload configuration");
        sender.sendMessage("§e/skittle baltop §7- Force baltop update");
        sender.sendMessage("§e/skittle info §7- Plugin details");
        sender.sendMessage("");
        sender.sendMessage("§6Available Help Topics:");
        sender.sendMessage("§e/skittle help sizer §7- Player size system");
        sender.sendMessage("§e/skittle help buildmode §7- Staff build mode");
        sender.sendMessage("§e/skittle help jailban §7- Prison bail system");
        sender.sendMessage("§e/skittle help jailvote §7- Community voting");
        sender.sendMessage("§e/skittle help staffchat §7- Private staff chat");
        sender.sendMessage("§e/skittle help baltop §7- Baltop rewards");
        sender.sendMessage("§e/skittle help newplayerfilter §7- Command blocking");
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l    SkittleEssentials Info");
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7Version: §e1.3.1");
        sender.sendMessage("§7Author: §eTwotoz");
        sender.sendMessage("§7API: §ePaper 1.20+");
        sender.sendMessage("");
        sender.sendMessage("§7Dependencies:");
        sender.sendMessage("§e▪ §7Vault (Required)");
        sender.sendMessage("§e▪ §7LuckPerms (Required)");
        sender.sendMessage("§e▪ §7Essentials (Required)");
        sender.sendMessage("");
        sender.sendMessage("§7Features Status:");

        FakePlayersManager fakePlayersManager = plugin.getFakePlayersManager();
        boolean fakePlayersEnabled = fakePlayersManager != null && fakePlayersManager.isEnabled();
        boolean baltopEnabled = plugin.getConfig().getBoolean("baltop-rewards.enabled", true);

        NewPlayerFilter newPlayerFilter = plugin.getNewPlayerFilter();
        boolean filterEnabled = newPlayerFilter != null && newPlayerFilter.isEnabled();

        sender.sendMessage((fakePlayersEnabled ? "§a✓" : "§c✗") + " §7Fake Players" +
                (fakePlayersEnabled ? " §7(+" + fakePlayersManager.getFakePlayerCount() + ")" : ""));
        sender.sendMessage("§a✓ §7Player Sizer");
        sender.sendMessage("§a✓ §7Buildmode");
        sender.sendMessage("§a✓ §7Jailban Bail System");
        sender.sendMessage("§a✓ §7Jail Vote System");
        sender.sendMessage("§a✓ §7Staff Chat");
        sender.sendMessage((baltopEnabled ? "§a✓" : "§c✗") + " §7Baltop Rewards");
        sender.sendMessage((filterEnabled ? "§a✓" : "§c✗") + " §7New Player Filter" +
                (filterEnabled ? " §7(" + newPlayerFilter.getPlaytimeThresholdHours() + "h threshold)" : ""));

        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        sender.sendMessage("§eSkittleEssentials §7v§61.3.1");
        sender.sendMessage("§7Running on §e" + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "baltop", "help", "info", "version");
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .filter(s -> {
                        // Filter admin commands
                        if ((s.equals("reload") || s.equals("baltop")) && !sender.hasPermission("skittle.admin")) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            List<String> topics = Arrays.asList("sizer", "buildmode", "jailban", "jailvote", "staffchat", "baltop", "newplayerfilter");
            return topics.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}