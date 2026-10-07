package ru.example.unjustplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

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
    private final Set<UUID> orbitalPotions = new HashSet<>();
    private final Random random = new Random();

    private List<String> famousNames = new ArrayList<>();
    private int replaceChance = 40;
    private int underscoreChance = 30;
    private boolean blockChat = true;

    private static final String TEAM_NAME = "usp_nametag";
    private static final String MACE_NAME = "§fWindcharge Shot";
    private static final String POTION_ROD_NAME = "§fOrbital Potion";

    private static final double ORBITAL_HEIGHT = 100.0;
    private static final int RINGS = 10;
    private static final int CHARGE_PER_RING = 30;
    private static final double MIN_RADIUS = 2.0;
    private static final double MAX_RADIUS = 25.0;

    @Override
    public void onEnable() {
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

    private void loadConfigValues() {
        try {
            FileConfiguration cfg = getConfig();
            famousNames = cfg.getStringList("famous-names");
            replaceChance = cfg.getInt("replace-chance", 40);
            underscoreChance = cfg.getInt("underscore-chance", 30);
            blockChat = cfg.getBoolean("block-chat", true);

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
            blockChat = true;
        }
    }

    // ==================== ЗАПРЕТ ЧАТА ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!blockChat) return;
        if (event.getPlayer().isOp()) return;
        if (event.getPlayer().hasPermission("unjust.chat")) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Чат отключён. Писать могут только операторы.");
    }

    // ==================== СМЕНА НИКА ====================

    private void applyFakeName(Player player, String fakeName) {
        player.setDisplayName(ChatColor.WHITE + fakeName);
        player.setPlayerListName(ChatColor.WHITE + fakeName);

        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            if (team == null) {
                team = board.registerNewTeam(TEAM_NAME);
            }

            for (String entry : new ArrayList<>(team.getEntries())) {
                if (entry.equals(player.getName())) {
                    team.removeEntry(player.getName());
                }
            }

            team.addEntry(player.getName());
            team.setPrefix(fakeName + " ");
        } catch (Exception e) {
            getLogger().warning("Ошибка установки ника над головой: " + e.getMessage());
        }
    }

    private void resetFakeName(Player player) {
        player.setDisplayName(player.getName());
        player.setPlayerListName(player.getName());

        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam(TEAM_NAME);
            if (team != null && team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        } catch (Exception e) {
            getLogger().warning("Ошибка сброса ника над головой: " + e.getMessage());
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

            player.playEffect(EntityEffect.HURT);
            player.playSound(player.getLocation(),
                    Sound.ENTITY_PLAYER_HURT, 1.0F, 1.0F);

            applyKnockback(player, event);
        }
    }

    private void applyKnockback(Player player, EntityDamageEvent event) {
        double power = 1.2;

        if (event instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent byEntity = (EntityDamageByEntityEvent) event;
            Entity damager = byEntity.getDamager();

            Vector direction = player.getLocation().toVector()
                    .subtract(damager.getLocation().toVector());

            if (direction.lengthSquared() > 0.01) {
                direction = direction.normalize().multiply(power);
                direction.setY(0.4);
                player.setVelocity(direction);
                return;
            }
        }

        Vector backward = player.getLocation().getDirection()
                .multiply(-1)
                .normalize()
                .multiply(power);
        backward.setY(0.4);

        player.setVelocity(backward);
    }

    // ==================== ОРБИТАЛЬНЫЙ УДАР (БУЛАВА) ====================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player attacker = (Player) event.getDamager();
        LivingEntity target = (LivingEntity) event.getEntity();

        if (attacker.equals(target)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (item.getType() != Material.MACE) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.hasDisplayName()) return;
        if (!meta.getDisplayName().equals(MACE_NAME)) return;

        event.setCancelled(true);
        fireOrbitalWindStrike(target.getLocation());
        breakMace(attacker, item);
    }

    private void breakMace(Player player, ItemStack item) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        player.getInventory().setItemInMainHand(null);
        player.updateInventory();
    }

    /**
     * Спавнит WindCharge и через задержку взрывает его.
     */
    private void spawnExplosiveWindCharge(World world, Location loc) {
        org.bukkit.entity.WindCharge charge = world.spawn(
                loc, org.bukkit.entity.WindCharge.class);
        charge.setVelocity(new Vector(0, -1.5, 0));

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (charge.isValid()) {
                world.createExplosion(charge.getLocation(), 2.0F, false, false);
                charge.remove();
            }
        }, 80L);
    }

    private void fireOrbitalWindStrike(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        Location spawnBase = center.clone().add(0, ORBITAL_HEIGHT, 0);

        // ===== ЦЕНТР =====
        for (int i = 0; i < 5; i++) {
            Location centerLoc = spawnBase.clone().add(0, -i * 0.5, 0);
            spawnExplosiveWindCharge(world, centerLoc);
        }

        // ===== КОЛЬЦА =====
        for (int ring = 0; ring < RINGS; ring++) {
            double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS)
                    * ((double) ring / (RINGS - 1));
            double angleStep = 360.0 / CHARGE_PER_RING;

            for (int i = 0; i < CHARGE_PER_RING; i++) {
                double angle = Math.toRadians(i * angleStep);
                double x = spawnBase.getX() + Math.cos(angle) * radius;
                double z = spawnBase.getZ() + Math.sin(angle) * radius;

                Location spawnLoc = new Location(world, x, spawnBase.getY(), z);
                spawnExplosiveWindCharge(world, spawnLoc);
            }
        }

        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 2.0F, 0.5F);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.7F);
    }

    // ==================== ОРБИТАЛЬНЫЙ УДАР (ЗЕЛЬЯ) ====================

    @EventHandler
    public void onPotionThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion)) return;
        if (!(event.getEntity().getShooter() instanceof Player)) return;

        ThrownPotion potion = (ThrownPotion) event.getEntity();

        ItemStack item = potion.getItem();
        if (item == null) return;
        if (item.getType() != Material.SPLASH_POTION) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.hasDisplayName()) return;
        if (!meta.getDisplayName().equals(POTION_ROD_NAME)) return;

        orbitalPotions.add(potion.getUniqueId());
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion)) return;

        ThrownPotion potion = (ThrownPotion) event.getEntity();

        if (!orbitalPotions.contains(potion.getUniqueId())) return;
        orbitalPotions.remove(potion.getUniqueId());

        event.setCancelled(true);

        Location landing = potion.getLocation();
        fireOrbitalPotionStrike(landing);
    }

    private ItemStack createRandomHarmfulPotion() {
        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();
        if (potionMeta == null) return potionItem;

        int amplifier = random.nextInt(3);

        Object[][] effects = {
            { PotionEffectType.INSTANT_DAMAGE, 1, Color.fromRGB(120, 0, 0) },
            { PotionEffectType.POISON, 1200, Color.fromRGB(50, 150, 50) },
            { PotionEffectType.WEAKNESS, 1800, Color.fromRGB(120, 120, 120) },
            { PotionEffectType.SLOWNESS, 1200, Color.fromRGB(100, 100, 150) },
            { PotionEffectType.SLOW_FALLING, 1200, Color.fromRGB(200, 200, 255) },
            { PotionEffectType.HUNGER, 1800, Color.fromRGB(150, 100, 0) },
            { PotionEffectType.BLINDNESS, 600, Color.fromRGB(30, 30, 30) },
            { PotionEffectType.NAUSEA, 1200, Color.fromRGB(100, 50, 150) },
            { PotionEffectType.MINING_FATIGUE, 1200, Color.fromRGB(80, 80, 80) },
            { PotionEffectType.LEVITATION, 600, Color.fromRGB(220, 220, 255) }
        };

        Object[] chosen = effects[random.nextInt(effects.length)];
        PotionEffectType type = (PotionEffectType) chosen[0];
        int duration = (int) chosen[1];
        Color color = (Color) chosen[2];

        potionMeta.setColor(color);
        potionMeta.addCustomEffect(
            new PotionEffect(type, duration, amplifier),
            true
        );
        potionItem.setItemMeta(potionMeta);

        return potionItem;
    }

    private void fireOrbitalPotionStrike(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        Location spawnBase = center.clone().add(0, ORBITAL_HEIGHT, 0);

        // ===== ЦЕНТР =====
        for (int i = 0; i < 5; i++) {
            Location centerLoc = spawnBase.clone().add(0, -i * 0.5, 0);
            ThrownPotion potion = (ThrownPotion) world.spawnEntity(
                    centerLoc, org.bukkit.entity.EntityType.POTION);
            potion.setItem(createRandomHarmfulPotion());
            potion.setVelocity(new Vector(0, -1.5, 0));
        }

        // ===== КОЛЬЦА =====
        for (int ring = 0; ring < RINGS; ring++) {
            double radius = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS)
                    * ((double) ring / (RINGS - 1));
            double angleStep = 360.0 / CHARGE_PER_RING;

            for (int i = 0; i < CHARGE_PER_RING; i++) {
                double angle = Math.toRadians(i * angleStep);
                double x = spawnBase.getX() + Math.cos(angle) * radius;
                double z = spawnBase.getZ() + Math.sin(angle) * radius;

                Location spawnLoc = new Location(world, x, spawnBase.getY(), z);
                ThrownPotion potion = (ThrownPotion) world.spawnEntity(
                        spawnLoc, org.bukkit.entity.EntityType.POTION);

                potion.setItem(createRandomHarmfulPotion());
                potion.setVelocity(new Vector(0, -1.5, 0));
            }
        }

        world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 2.0F, 0.5F);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 0.7F);
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

    // ==================== ВЫДАЧА ПРЕДМЕТОВ ====================

    private void giveOrbitalMace(Player player) {
        ItemStack mace = new ItemStack(Material.MACE, 1);
        ItemMeta meta = mace.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MACE_NAME);
            meta.setLore(Arrays.asList(
                "§7Ударь по врагу —",
                "§7в небе раскроются кольца",
                "§7из §fзарядов ветра§7!",
                "§c⚠ Ломается после одного удара"
            ));
            mace.setItemMeta(meta);
        }

        player.getInventory().addItem(mace);
        player.sendMessage(ChatColor.WHITE + "Ты получил " + MACE_NAME + "§f!");
    }

    private void givePotionRod(Player player) {
        ItemStack potionRod = new ItemStack(Material.SPLASH_POTION, 1);
        PotionMeta potionMeta = (PotionMeta) potionRod.getItemMeta();
        if (potionMeta != null) {
            potionMeta.setDisplayName(POTION_ROD_NAME);
            potionMeta.setColor(Color.fromRGB(120, 0, 0));
            potionMeta.addCustomEffect(
                new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1),
                true
            );
            potionMeta.setLore(Arrays.asList(
                "§7Брось зелье —",
                "§7в точке приземления",
                "§7с неба полетят кольца",
                "§7из §cслучайных вредных зелий§7!",
                "§c⚠ Одноразовое"
            ));
            potionRod.setItemMeta(potionMeta);
        }

        player.getInventory().addItem(potionRod);
        player.sendMessage(ChatColor.WHITE + "Ты получил " + POTION_ROD_NAME + "§f!");
    }

    // ==================== КОМАНДЫ ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("unjustsmpplugin")) {
            return false;
        }

        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfigValues();
                sender.sendMessage(ChatColor.GREEN + "Конфиг перезагружен!");
                return true;
            }

            if (args.length >= 1 && args[0].equalsIgnoreCase("chat")) {
                if (args.length < 2) {
                    String status = blockChat ? "§cзаблокирован" : "§aразблокирован";
                    sender.sendMessage(ChatColor.YELLOW + "Чат сейчас " + status);
                    return true;
                }

                String mode = args[1].toLowerCase();
                if (mode.equals("block")) {
                    blockChat = true;
                    getConfig().set("block-chat", true);
                    saveConfig();
                    Bukkit.broadcastMessage(ChatColor.RED + "Чат заблокирован.");
                    return true;
                } else if (mode.equals("unblock")) {
                    blockChat = false;
                    getConfig().set("block-chat", false);
                    saveConfig();
                    Bukkit.broadcastMessage(ChatColor.GREEN + "Чат разблокирован.");
                    return true;
                }
                return true;
            }

            if (args.length >= 3 && args[0].equalsIgnoreCase("orbital")
                    && args[1].equalsIgnoreCase("mace")
                    && args[2].equalsIgnoreCase("rod")) {

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игроков!");
                    return true;
                }

                giveOrbitalMace((Player) sender);
                return true;
            }

            if (args.length >= 3 && args[0].equalsIgnoreCase("orbital")
                    && args[1].equalsIgnoreCase("potion")
                    && args[2].equalsIgnoreCase("rod")) {

                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "Только для игроков!");
                    return true;
                }

                givePotionRod((Player) sender);
                return true;
            }

            if (args.length < 2) {
                sendHelp(sender);
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("reset")) {
                Player target = findPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Игрок не найден!");
                    return true;
                }

                UUID uuid = target.getUniqueId();
                if (originalNames.containsKey(uuid)) {
                    resetFakeName(target);
                    originalNames.remove(uuid);
                    fakeNames.remove(uuid);
                    sender.sendMessage(ChatColor.GREEN + "Ник игрока восстановлен!");
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "У этого игрока нет фейкового ника.");
                }
                return true
