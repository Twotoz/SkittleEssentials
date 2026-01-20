package twotoz.skittleEssentials.commands;

import twotoz.skittleEssentials.SkittleEssentials;
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
            sender.sendMessage("§e§l    SkittleEssentials v1.5.2");
            sender.sendMessage("§7        Created by: §eTwotoz");
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            sender.sendMessage("");
            sender.sendMessage("§6Features:");
            sender.sendMessage("§e▪ §7Player Sizer - Adjust player size");
            sender.sendMessage("§e▪ §7Buildmode - Safe building mode for staff");
            sender.sendMessage("§e▪ §7Jailban - Prison system with bail mechanic");
            sender.sendMessage("§e▪ §7Jail Vote - Community voting system");
            sender.sendMessage("§e▪ §7Staff Chat - Private staff communication");
            sender.sendMessage("§e▪ §7Local Chat - Proximity chat with spy mode");
            sender.sendMessage("§e▪ §7Fake Players - Server list spoofing");
            sender.sendMessage("");
            sender.sendMessage("§6Quick Commands:");
            sender.sendMessage("§e/sizer §7- Change player sizes");
            sender.sendMessage("§e/buildmode §7- Toggle build mode");
            sender.sendMessage("§e/jailban §7- Manage jail system");
            sender.sendMessage("§e/startjailvote §7- Start community jail vote");
            sender.sendMessage("§e/localchat §7- Toggle local chat");
            sender.sendMessage("§e/skittle help §7- View all commands");
            sender.sendMessage("§e/skittle reload §7- Reload configuration");
            sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);

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
                    sender.sendMessage("§e/jaillist §7- View all jailed players");
                    sender.sendMessage("§e/jailbal §7- Check your bail balance");
                    sender.sendMessage("§e/bail confirm §7- Bail yourself out");
                    sender.sendMessage("§e/jailchatspy §7- See jail chat messages");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.jailban §7- Use jail commands");
                    sender.sendMessage("§e- skittle.jailban.bypass §7- Bypass restrictions");
                    sender.sendMessage("§e- skittle.jailban.spy §7- Use spy mode");
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
                    sender.sendMessage("§7Method 1 - Toggle Mode:");
                    sender.sendMessage("§e/sct §7or §e/staffchat §7- Toggle staff chat on/off");
                    sender.sendMessage("");
                    sender.sendMessage("§7Method 2 - Prefix Mode:");
                    sender.sendMessage("§7Type §e! §7before your message to use staff chat");
                    sender.sendMessage("");
                    sender.sendMessage("§7Aliases: §e/sct, /sc");
                    return true;

                case "localchat":
                case "local":
                    sender.sendMessage("§6§l=== Local Chat Help ===");
                    sender.sendMessage("§7Chat with nearby players.");
                    sender.sendMessage("");
                    sender.sendMessage("§7Commands:");
                    sender.sendMessage("§e/localchat §7- Toggle local chat mode");
                    sender.sendMessage("§e/localchatspy §7- Toggle spy mode (see all)");
                    sender.sendMessage("§7Or type §e? §7before message");
                    sender.sendMessage("");
                    sender.sendMessage("§7Permissions:");
                    sender.sendMessage("§e- skittle.localchat.use");
                    sender.sendMessage("§e- skittle.localchat.spy");
                    return true;

                default:
                    sender.sendMessage("§cUnknown help topic: §e" + topic);
                    sender.sendMessage("§7Available topics: §esizer, buildmode, jailban, jailvote, staffchat, localchat");
                    return true;
            }
        }

        // General help
        sender.sendMessage("§6§l=== SkittleEssentials Help ===");
        sender.sendMessage("");
        sender.sendMessage("§6Admin Commands:");
        sender.sendMessage("§e/skittle §7- Plugin information");
        sender.sendMessage("§e/skittle reload §7- Reload configuration");
        sender.sendMessage("");
        sender.sendMessage("§6Available Help Topics:");
        sender.sendMessage("§e/skittle help sizer §7- Player size system");
        sender.sendMessage("§e/skittle help buildmode §7- Staff build mode");
        sender.sendMessage("§e/skittle help jailban §7- Prison bail system");
        sender.sendMessage("§e/skittle help jailvote §7- Community voting");
        sender.sendMessage("§e/skittle help staffchat §7- Private staff chat");
        sender.sendMessage("§e/skittle help localchat §7- Proximity chat");
        return true;
    }

    private boolean handleInfo(CommandSender sender) {
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e§l    SkittleEssentials Info");
        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7Version: §e1.5.2");
        sender.sendMessage("§7Author: §eTwotoz");
        sender.sendMessage("§7API: §ePaper 1.21+ / Folia 1.20.4+");
        sender.sendMessage("");
        sender.sendMessage("§7Features Status:");

        FakePlayersManager fakePlayersManager = plugin.getFakePlayersManager();
        boolean fakePlayersEnabled = fakePlayersManager != null && fakePlayersManager.isEnabled();

        sender.sendMessage((fakePlayersEnabled ? "§a✓" : "§c✗") + " §7Fake Players" +
                (fakePlayersEnabled ? " §7(+" + fakePlayersManager.getFakePlayerCount() + ")" : ""));
        sender.sendMessage("§a✓ §7Player Sizer");
        sender.sendMessage("§a✓ §7Buildmode");
        sender.sendMessage("§a✓ §7Jailban Bail System");
        sender.sendMessage("§a✓ §7Jail Vote System");
        sender.sendMessage("§a✓ §7Staff Chat");
        sender.sendMessage("§a✓ §7Local Chat");

        sender.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        sender.sendMessage("§eSkittleEssentials §7v§61.5.2");
        sender.sendMessage("§7Running on §e" + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("reload", "help", "info", "version");
            return subCommands.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .filter(s -> {
                        // Filter admin commands
                        if (s.equals("reload") && !sender.hasPermission("skittle.admin")) {
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            List<String> topics = Arrays.asList("sizer", "buildmode", "jailban", "jailvote", "staffchat", "localchat");
            return topics.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}