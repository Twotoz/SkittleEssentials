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
        executeSync(() -> {

            double currentScale = 1.0;
            AttributeInstance scaleAttr = player.getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttr != null) currentScale = scaleAttr.getBaseValue();

            boolean changed = currentScale != 1.0;

            resetToDefaults(player);
            updateOriginalJumpStrength(player);

            if (changed) player.sendMessage(message);

            BukkitTask task = activeShiftTasks.remove(player.getUniqueId());
            if (task != null) task.cancel();
        });
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled() || event.getFinalDamage() <= 0) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        long now = System.currentTimeMillis();

        resetAndCooldown(attacker, "§cYour size was reset because you dealt damage!");
        resetAndCooldown(victim,  "§cYour size was reset because you took damage!");

        combatCooldown.put(attacker.getUniqueId(), now);
        combatCooldown.put(victim.getUniqueId(), now);
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
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only!");
            return true;
        }
        if (args.length == 1) return resizeSelf(p, args[0]);
        if (args.length == 2 && p.hasPermission("skittle.sizer.other")) return resizeOther(p, args[0], args[1]);
        p.sendMessage("§cUsage: /sizer <scale> or /sizer <player> <scale>");
        return true;
    }

    private boolean resizeSelf(Player p, String s) {
        if (!p.hasPermission("skittle.sizer.use")) {
            p.sendMessage("§cNo permission!");
            return true;
        }
        if (isOnCooldown(p)) {
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
        if (isOnCooldown(t)) {
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
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        return new ArrayList<>();
    }
}
