package twotoz.skittleEssentials.commands;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SizerCommand implements CommandExecutor, TabCompleter, Listener {

    private final JavaPlugin plugin;

    // Thread-safe collections for concurrent access
    private final Map<UUID, Double> originalJumpStrength = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeShiftTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatCooldown = new ConcurrentHashMap<>();

    private static final long COOLDOWN_DURATION = 10_000L;
    private static final int SHIFT_RESET_TICKS = 5;

    public SizerCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void updateOriginalJumpStrength(Player player) {
        AttributeInstance jumpAttr = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
        if (jumpAttr != null) {
            originalJumpStrength.put(player.getUniqueId(), jumpAttr.getBaseValue());
        }
    }

    private void applySizeEffect(Player player, double scale) {
        if (scale == 1.0) {
            resetToDefaults(player);
            updateOriginalJumpStrength(player);
            return;
        }

        AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scale);
        }
        applyMovementAttributes(player, scale);
        spawnParticles(player, Particle.WITCH);

        updateOriginalJumpStrength(player);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) updateOriginalJumpStrength(player);
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        originalJumpStrength.remove(uuid);
        BukkitTask task = activeShiftTasks.remove(uuid);
        if (task != null) task.cancel();
        combatCooldown.remove(uuid);
    }

    // =====================================================================
    // SHIFT BOOST — TIMER STARTS ON UNSHIFT
    // =====================================================================
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        boolean isSneaking = event.isSneaking();

        AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
        AttributeInstance jumpAttr = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
        if (scaleAttr == null || jumpAttr == null) return;

        Double original = originalJumpStrength.get(player.getUniqueId());
        if (original == null) return;

        double scale = scaleAttr.getBaseValue();

        // -------- SHIFT PRESS → APPLY BOOST --------
        if (isSneaking) {

            // Cancel previous reset task
            BukkitTask old = activeShiftTasks.remove(player.getUniqueId());
            if (old != null) old.cancel();

            double newJump;
            if (scale < 0.25) {
                newJump = original + 0.1;
            } else if (scale < 0.7) {
                newJump = 0.4;
            } else {
                return;
            }

            jumpAttr.setBaseValue(newJump);
            return;
        }

        // -------- SHIFT RELEASE → START TIMER --------
        BukkitTask resetTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                AttributeInstance current = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
                if (current != null) current.setBaseValue(original);
                activeShiftTasks.remove(player.getUniqueId());
            }
        }.runTaskLater(plugin, SHIFT_RESET_TICKS);

        activeShiftTasks.put(player.getUniqueId(), resetTask);
    }

    // =====================================================================
    // COMBAT RESET — NO SPAM WHEN ALREADY SIZE 1.0
    // =====================================================================
    private void resetAndCooldown(Player player, String message) {
        // Check voor permanent size permission
        if (player.hasPermission("skittle.sizer.permanent")) {
            return; // Skip reset EN cooldown voor spelers met permanent size permission
        }

        executeSync(() -> {

            double currentScale = 1.0;
            AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttr != null) currentScale = scaleAttr.getBaseValue();

            boolean changed = currentScale != 1.0;

            // Alleen resetten en particles spawnen als er daadwerkelijk iets verandert
            if (changed) {
                resetToDefaults(player);
                player.sendMessage(message);
            }

            updateOriginalJumpStrength(player);

            BukkitTask task = activeShiftTasks.remove(player.getUniqueId());
            if (task != null) task.cancel();

            // Cooldown voor normale spelers
            combatCooldown.put(player.getUniqueId(), System.currentTimeMillis());
        });
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getFinalDamage() <= 0) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        // Reset attacker (tenzij permanent permission)
        resetAndCooldown(attacker, "§cYour size was reset because you dealt damage!");

        // Reset victim (tenzij permanent permission)
        resetAndCooldown(victim, "§cYour size was reset because you took damage!");
    }

    // =====================================================================
    // RESIZE COMMAND LOGIC
    // =====================================================================
    private boolean applyResize(Player executor, Player target, String scaleStr) {
        try {
            double scale = Double.parseDouble(scaleStr);

            double min = executor.isOp() || executor.hasPermission("skittle.sizer.admin")
                    ? plugin.getConfig().getDouble("sizer.op-min-scale", 0.01)
                    : plugin.getConfig().getDouble("sizer.default-min-scale", 0.7);

            double max = executor.isOp() || executor.hasPermission("skittle.sizer.admin")
                    ? plugin.getConfig().getDouble("sizer.op-max-scale", 25.0)
                    : plugin.getConfig().getDouble("sizer.default-max-scale", 1.2);

            if (scale < min || scale > max) {
                executor.sendMessage(String.format("§cScale must be between %.2f and %.2f!", min, max));
                return true;
            }

            executeSync(() -> applySizeEffect(target, scale));

            String msg = target == executor
                    ? "§aYour size is now §e" + scale + "x"
                    : "§a" + target.getName() + "'s size was set to §e" + scale + "x";

            executor.sendMessage(msg);
            if (target != executor) {
                target.sendMessage("§aYour size was changed to §e" + scale + "x §aby §e" + executor.getName());
            }

            return true;

        } catch (NumberFormatException e) {
            executor.sendMessage("§cInvalid number!");
            return true;
        }
    }

    private void executeSync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    private void resetToDefaults(Player player) {
        setAttribute(player, Attribute.GENERIC_SCALE, 1.0);
        setAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, 0.1);
        setAttribute(player, Attribute.GENERIC_JUMP_STRENGTH, 0.42);
        setAttribute(player, Attribute.GENERIC_STEP_HEIGHT, 0.6);
        setAttribute(player, Attribute.GENERIC_SAFE_FALL_DISTANCE, 3.0);
        setAttribute(player, Attribute.GENERIC_FLYING_SPEED, 0.1);
        spawnParticles(player, Particle.HAPPY_VILLAGER);
    }

    private void applyMovementAttributes(Player player, double scale) {
        double jumpFactor = switch ((int) (scale * 10)) {
            case 0, 1, 2 -> scale / 0.42;
            case 3, 4 -> lerp(0.714, 0.81, (scale - 0.3) / 0.2);
            case 5, 6 -> lerp(0.81, 0.881, (scale - 0.5) / 0.2);
            default -> scale < 1.0 ? lerp(0.881, 1.0, (scale - 0.7) / 0.3) : Math.sqrt(scale);
        };

        double movementFactor = Math.sqrt(scale) * 1.1;

        setAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, clamp(0.1 * movementFactor, 0.05, 1.0));
        setAttribute(player, Attribute.GENERIC_JUMP_STRENGTH, clamp(0.42 * jumpFactor, 0.1, 2.0));
        setAttribute(player, Attribute.GENERIC_STEP_HEIGHT, clamp(0.6 * scale, 0.1, 1.5));
        setAttribute(player, Attribute.GENERIC_SAFE_FALL_DISTANCE, clamp(3.0 * scale, 2.0, 8.0));
        setAttribute(player, Attribute.GENERIC_FLYING_SPEED, clamp(0.1 * movementFactor, 0.05, 1.0));
    }

    private void setAttribute(Player p, Attribute a, double v) {
        AttributeInstance attr = p.getAttribute(a);
        if (attr != null) attr.setBaseValue(v);
    }

    private double lerp(double a, double b, double f) { return a + f * (b - a); }

    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private void spawnParticles(Player p, Particle particle) {
        try {
            int count = particle == Particle.WITCH ? 20 : 10;
            double spread = particle == Particle.WITCH ? 0.5 : 0.3;
            p.getWorld().spawnParticle(
                    particle, p.getLocation().add(0, 1, 0),
                    count, spread, spread, spread, 0.1
            );
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Console command: /sizer <player> <scale>
        if (!(sender instanceof Player)) {
            if (args.length != 2) {
                sender.sendMessage("§cUsage: /sizer <player> <scale>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found!");
                return true;
            }
            return applyResizeConsole(sender, target, args[1]);
        }

        // Player commands
        Player p = (Player) sender;
        if (args.length == 1) return resizeSelf(p, args[0]);
        if (args.length == 2 && p.hasPermission("skittle.sizer.other")) return resizeOther(p, args[0], args[1]);
        p.sendMessage("§cUsage: /sizer <scale> or /sizer <player> <scale>");
        return true;
    }

    private boolean applyResizeConsole(CommandSender console, Player target, String scaleStr) {
        try {
            double scale = Double.parseDouble(scaleStr);
            double min = plugin.getConfig().getDouble("sizer.op-min-scale", 0.01);
            double max = plugin.getConfig().getDouble("sizer.op-max-scale", 25.0);

            if (scale < min || scale > max) {
                console.sendMessage(String.format("§cScale must be between %.2f and %.2f!", min, max));
                return true;
            }

            executeSync(() -> applySizeEffect(target, scale));
            console.sendMessage("§a" + target.getName() + "'s size was set to §e" + scale + "x");
            target.sendMessage("§aYour size was changed to §e" + scale + "x §aby Console");
            return true;
        } catch (NumberFormatException e) {
            console.sendMessage("§cInvalid number!");
            return true;
        }
    }

    private boolean resizeSelf(Player p, String s) {
        if (!p.hasPermission("skittle.sizer.use")) {
            p.sendMessage("§cNo permission!");
            return true;
        }
        // Permanent permission bypass cooldown check
        if (!p.hasPermission("skittle.sizer.permanent") && isOnCooldown(p)) {
            p.sendMessage("§cWait " + getRemainingSeconds(p) + "s after combat!");
            return true;
        }
        return applyResize(p, p, s);
    }

    private boolean resizeOther(Player e, String n, String s) {
        Player t = Bukkit.getPlayerExact(n);
        if (t == null) {
            e.sendMessage("§cPlayer not found!");
            return true;
        }
        // Permanent permission bypass cooldown check
        if (!t.hasPermission("skittle.sizer.permanent") && isOnCooldown(t)) {
            e.sendMessage("§c" + t.getName() + " is in combat cooldown!");
            return true;
        }
        return applyResize(e, t, s);
    }

    private boolean isOnCooldown(Player p) {
        Long time = combatCooldown.get(p.getUniqueId());
        return time != null && System.currentTimeMillis() - time < COOLDOWN_DURATION;
    }

    private long getRemainingSeconds(Player p) {
        Long time = combatCooldown.get(p.getUniqueId());
        if (time == null) return 0;
        return (COOLDOWN_DURATION - (System.currentTimeMillis() - time) + 999) / 1000;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        // Voor console: alleen spelers suggereren in arg 1
        if (!(sender instanceof Player)) {
            if (args.length == 1) {
                String input = args[0].toLowerCase();
                completions = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            } else if (args.length == 2) {
                completions.addAll(Arrays.asList("0.1", "0.5", "1.0", "1.5", "2.0"));
            }
            return completions;
        }

        Player player = (Player) sender;

        // Voor spelers zonder other permission: alleen scale values
        if (!player.hasPermission("skittle.sizer.other")) {
            if (args.length == 1) {
                completions.addAll(Arrays.asList("0.7", "0.8", "0.9", "1.0", "1.1", "1.2"));
            }
            return completions;
        }

        // Voor spelers met other permission
        if (args.length == 1) {
            String input = args[0].toLowerCase();

            // Als input lijkt op een getal, suggereer scale values
            if (input.isEmpty() || input.matches("^[0-9.]+$")) {
                completions.addAll(Arrays.asList("0.1", "0.5", "1.0", "1.5", "2.0"));
            }

            // Voeg spelersnamen toe
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList()));

        } else if (args.length == 2) {
            // Als arg1 een speler is, suggereer scale values voor arg2
            if (Bukkit.getPlayerExact(args[0]) != null) {
                completions.addAll(Arrays.asList("0.1", "0.5", "1.0", "1.5", "2.0"));
            }
        }

        return completions;
    }
}