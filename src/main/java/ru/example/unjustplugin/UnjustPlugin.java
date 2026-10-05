package ru.example.unjustplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class UnjustPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, String> originalNames = new HashMap<>();
    private final Map<UUID, String> fakeNames = new HashMap<>();
    private final Set<UUID> immortals = new HashSet<>();
    private final Random random = new Random();

    private final String[] famousNames = {
        "Notch", "Dream", "Technoblade", "Herobrine", "Steve", "Alex",
        "Ph1LzA", "TommyInnit", "Wilbur", "Sapnap", "GeorgeNotFound",
        "CaptainSparklez", "DanTDM", "SkyDoesMinecraft", "Stampy",
        "PopularMMOs", "PrestonPlayz", "SSundee", "JeromeASF",
        "BajanCanadian", "Vikkstar", "MrBeast", "PewDiePie"
    };

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("UnjustPlugin включен!");
    }

    // ==================== СМЕРТЬ ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        event.setDeathMessage(null);

        // Какой ник показывать в чате
        String displayName = fakeNames.containsKey(uuid)
                ? fakeNames.get(uuid)
                : player.getName();

        // Причина смерти
        String deathReason = getDeathReason(player);

        // Сообщения
        Bukkit.broadcastMessage("§f" + displayName + " " + deathReason);
        Bukkit.broadcastMessage("§e" + displayName + " покинул игру");
        player.sendMessage("§fТы " + deathReason);

        // Звук визера
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(
                onlinePlayer.getLocation(),
                Sound.ENTITY_WITHER_SPAWN,
                1.0F,
                1.0F
            );
        }

        // Сохраняем оригинальный ник
        if (!originalNames.containsKey(uuid)) {
            originalNames.put(uuid, player.getName());
        }

        // Новый фейковый ник
        String newFakeName = generateFakeName();
        fakeNames.put(uuid, newFakeName);

        // Применяем через 1 секунду
        Bukkit.getScheduler().runTaskLater(
            UnjustPlugin.this,
            () -> applyFakeName(player, newFakeName),
            20L
        );
    }

    // ==================== БЕССМЕРТИЕ ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (!immortals.contains(player.getUniqueId())) return;

        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0) {
            event.setCancelled(true);
            player.setHealth(1.0);
            player.sendMessage(ChatColor.GOLD + "[Бессмертие] " + ChatColor.YELLOW
                    + "Ты был спасён от смерти!");
        }
    }

    // ==================== ВХОД ====================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (fakeNames.containsKey(uuid)) {
            applyFakeName(player, fakeNames.get(uuid));
        }
    }

    // ==================== ПРИЧИНА СМЕРТИ ====================

    private String getDeathReason(Player player) {
        String deathReason = "умер";
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage == null) return deathReason;

        EntityDamageEvent.DamageCause cause = lastDamage.getCause();
        switch (cause) {
            case ENTITY_ATTACK:
            case ENTITY_SWEEP_ATTACK:
                if (player.getKiller() != null) {
                    deathReason = "был убит игроком " + player.getKiller().getName();
                } else {
                    deathReason = "был убит мобом";
                }
                break;
            case PROJECTILE: deathReason = "был застрелен"; break;
            case FALL: deathReason = "разбился насмерть"; break;
            case BLOCK_EXPLOSION:
            case ENTITY_EXPLOSION: deathReason = "взорвался"; break;
            case FIRE:
            case FIRE_TICK: deathReason = "сгорел"; break;
            case LAVA: deathReason = "сгорел в лаве"; break;
            case DROWNING: deathReason = "утонул"; break;
            case VOID: deathReason = "упал в пустоту"; break;
            case POISON: deathReason = "отравился"; break;
            case WITHER: deathReason = "умер от иссушения"; break;
            case STARVATION: deathReason = "умер от голода"; break;
            case MAGIC: deathReason = "умер от магии"; break;
            case LIGHTNING: deathReason = "был убит молнией"; break;
            case SUFFOCATION: deathReason = "задохнулся"; break;
            case CONTACT: deathReason = "умер от кактуса"; break;
            case CRAMMING: deathReason = "был раздавлен"; break;
            case FLY_INTO_WALL: deathReason = "влетел в стену"; break;
            case HOT_FLOOR: deathReason = "сгорел на магме"; break;
            case DRAGON_BREATH: deathReason = "умер от дыхания дракона"; break;
            case FALLING_BLOCK: deathReason = "был раздавлен блоком"; break;
            case THORNS: deathReason = "умер от шипов"; break;
            default: deathReason = "умер";
        }
        return deathReason;
    }

    // ==================== ГЕНЕРАЦИЯ ФЕЙКОВОГО НИКА ====================

    private String generateFakeName() {
        String base = famousNames[random.nextInt(famousNames.length)];
        return slightlyModify(base);
    }

    private String slightlyModify(String name) {
        StringBuilder sb = new StringBuilder();

        for (char c : name.toCharArray()) {
            if (random.nextInt(100) < 40) {
                switch (Character.toLowerCase(c)) {
                    case 'o': sb.append('0'); continue;
                    case 'i': sb.append(random.nextBoolean() ? '1' : 'l'); continue;
                    case 'e': sb.append('3'); continue;
                    case 'a': sb.append('4'); continue;
                    case 's': sb.append('5'); continue;
                    case 't': sb.append('7'); continue;
                    case 'b': sb.append('8'); continue;
                    case 'g': sb.append('9'); continue;
                    default: break;
                }
            }
            sb.append(c);
        }

        if (random.nextInt(100) < 30) {
            sb.append('_');
        }

        return sb.toString();
    }

    private void applyFakeName(Player player, String fakeName) {
        player.setDisplayName(ChatColor.WHITE + fakeName);
        player.setPlayerListName(ChatColor.WHITE + fakeName);
    }

    // ==================== КОМАНДЫ ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("unjustsmpplugin")) {
            return false;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // ----- reset -----
        if (sub.equals("reset")) {
            Player target = findPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден!");
                return true;
            }

            UUID uuid = target.getUniqueId();
            if (originalNames.containsKey(uuid)) {
                String original = originalNames.get(uuid);
                target.setDisplayName(original);
                target.setPlayerListName(original);
                originalNames.remove(uuid);
                fakeNames.remove(uuid);
                sender.sendMessage(ChatColor.GREEN + "Ник игрока " + original + " восстановлен!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "У этого игрока нет фейкового ника.");
            }
            return true;
        }

        // ----- immortal -----
        if (sub.equals("immortal")) {
            Player target = findPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок не найден!");
                return true;
            }

            UUID uuid = target.getUniqueId();
            boolean enable;

            if (args.length >= 3) {
                enable = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true");
            } else {
                enable = !immortals.contains(uuid);
            }

            if (enable) {
                immortals.add(uuid);
                target.sendMessage(ChatColor.GOLD + "[Бессмертие] " + ChatColor.GREEN
                        + "Ты теперь бессмертен!");
                sender.sendMessage(ChatColor.GREEN + "Бессмертие включено для " + target.getName());
            } else {
                immortals.remove(uuid);
                target.sendMessage(ChatColor.GOLD + "[Бессмертие] " + ChatColor.RED
                        + "Ты больше не бессмертен.");
                sender.sendMessage(ChatColor.GREEN + "Бессмертие выключено для " + target.getName());
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private Player findPlayer(String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) return target;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (fakeNames.containsKey(online.getUniqueId())
                    && fakeNames.get(online.getUniqueId()).equalsIgnoreCase(name)) {
                return online;
            }
        }
        return null;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "===== UnjustPlugin =====");
        sender.sendMessage(ChatColor.WHITE + "/unjustsmpplugin reset <ник>");
        sender.sendMessage(ChatColor.WHITE + "/unjustsmpplugin immortal <ник> [on/off]");
        sender.sendMessage(ChatColor.GRAY + "Сокращённо: /usp");
    }
}
