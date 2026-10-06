package ru.example.unjustplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class UnjustPlugin extends JavaPlugin implements Listener, TabCompleter {

    private final Map<UUID, String> originalNames = new HashMap<>();
    private final Map<UUID, String> fakeNames = new HashMap<>();
    private final Set<UUID> immortals = new HashSet<>();
    private final Random random = new Random();

    // Значения из конфига
    private List<String> famousNames = new ArrayList<>();
    private int replaceChance = 40;
    private int underscoreChance = 30;

    @Override
    public void onEnable() {
        // Создаём config.yml при первом запуске (если его нет)
        try {
            saveDefaultConfig();
        } catch (Exception e) {
            getLogger().warning("Не удалось создать config.yml: " + e.getMessage());
        }

        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("unjustsmpplugin") != null) {
            getCommand("unjustsmpplugin").setTabCompleter(this);
        }

        getLogger().info("UnjustPlugin включен!");
    }

    /**
     * Читает значения из config.yml в поля класса.
     * Все ошибки ловятся, чтобы плагин не падал.
     */
    private void loadConfigValues() {
        try {
            FileConfiguration cfg = getConfig();
            famousNames = cfg.getStringList("famous-names");
            replaceChance = cfg.getInt("replace-chance", 40);
            underscoreChance = cfg.getInt("underscore-chance", 30);

            if (famousNames == null || famousNames.isEmpty()) {
                famousNames = Arrays.asList(
                    "Notch", "Dream", "Technoblade", "Herobrine", "Steve", "Alex"
                );
                getLogger().warning("Список famous-names пуст в config.yml! Использую дефолтный.");
            }
        } catch (Exception e) {
            getLogger().warning("Ошибка загрузки config.yml: " + e.getMessage());
            famousNames = Arrays.asList(
                "Notch", "Dream", "Technoblade", "Herobrine", "Steve", "Alex"
            );
            replaceChance = 40;
            underscoreChance = 30;
        }
    }

    // ==================== СМЕРТЬ ====================

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        event.setDeathMessage(null);

        String displayName = fakeNames.containsKey(uuid)
                ? fakeNames.get(uuid)
                : player.getName();

        String deathReason = getDeathReason(player);

        Bukkit.broadcastMessage("§f" + displayName + " " + deathReason);
        Bukkit.broadcastMessage("§e" + displayName + " покинул игру");
        player.sendMessage("§fТы " + deathReason);

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(
                onlinePlayer.getLocation(),
                Sound.ENTITY_WITHER_SPAWN,
                1.0F,
                1.0F
            );
        }

        if (!originalNames.containsKey(uuid)) {
            originalNames.put(uuid, player.getName());
        }

        String newFakeName = generateFakeName();
        fakeNames.put(uuid, newFakeName);

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
        if (famousNames == null || famousNames.isEmpty()) {
            return "Player_" + (1000 + random.nextInt(9000));
        }
        String base = famousNames.get(random.nextInt(famousNames.size()));
        return slightlyModify(base);
    }

    private String slightlyModify(String name) {
        StringBuilder sb = new StringBuilder();

        for (char c : name.toCharArray()) {
            if (random.nextInt(100) < replaceChance) {
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

        if (random.nextInt(100) < underscoreChance) {
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

        try {
            // /usp reload
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен!");
                return true;
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

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Произошла ошибка при выполнении команды.");
            getLogger().warning("Ошибка команды: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    // ==================== АВТОДОПОЛНЕНИЕ (TAB) ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            for (String sub : Arrays.asList("reset", "immortal", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            String prefix = args[1].toLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                String name = online.getName();
                String fake = fakeNames.get(online.getUniqueId());

                if (name.toLowerCase().startsWith(prefix)) {
                    result.add(name);
                }
                if (fake != null && fake.toLowerCase().startsWith(prefix)) {
                    result.add(fake);
                }
            }
            return result;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("immortal")) {
            String prefix = args[2].toLowerCase();
            for (String opt : Arrays.asList("on", "off")) {
                if (opt.startsWith(prefix)) {
                    result.add(opt);
                }
            }
            return result;
        }

        return result;
    }

    // ==================== ПОИСК ИГРОКА ====================

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
        sender.sendMessage(ChatColor.WHITE + "/unjustsmpplugin reload");
        sender.sendMessage(ChatColor.GRAY + "Сокращённо: /usp");
    }
}
