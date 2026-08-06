package fr.tropicube.sheepwars.game;

import fr.skytasul.glowingentities.GlowingEntities;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.MapsUtil;
import fr.tropicube.sheepwars.util.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static net.kyori.adventure.text.format.NamedTextColor.*;

/**
 * Machine d'état principale d'une partie SheepWars. Elle pilote l'attente,
 * les votes, le compte à rebours, le jeu, la victoire et le retour au lobby.
 */
public class GameManager {

    private final TropicubeSheepwars plugin;
    public final NamespacedKey gameStartKey;
    public final NamespacedKey gameStartCancelKey;
    public final NamespacedKey leaveItemKey;
    private final List<GameMap> gameMaps = new ArrayList<>();
    private final Map<UUID, GamePlayer> players = new ConcurrentHashMap<>();
    private static final UUID NO_HOST = new UUID(0, 0);
    private final UUID hostUuid;
    private final String instanceId = System.getenv("INSTANCE_ID");
    private GameMap selectedMap;

    private GameState state = GameState.WAITING;
    private Location lobby;
    private int lobbyVoidLimit;
    private BukkitTask currentTask;
    private BukkitTask sheepTask;
    private int countdown;
    private int gameTime;
    private final GlowingEntities glowingEntities;

    public GameManager(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.gameStartKey = new NamespacedKey(plugin, "game_start_item");
        this.gameStartCancelKey = new NamespacedKey(plugin, "game_start_cancel_item");
        this.leaveItemKey = new NamespacedKey(plugin, "leave_game_item");
        this.hostUuid = parseHostUuid(System.getenv("HOST_UUID"));
        this.glowingEntities = new GlowingEntities(plugin);
    }

    public List<GameMap> getGameMaps() {
        return List.copyOf(gameMaps);
    }

    public GameState getState() {
        return state;
    }

    public Location getLobby() { return lobby; }

    public int getLobbyVoidLimit() { return lobbyVoidLimit; }

    public Collection<GamePlayer> getPlayers() {
        return List.copyOf(players.values());
    }

    public GamePlayer getPlayer(UUID uuid) {
        return players.get(uuid);
    }

    public GamePlayer getPlayer(Player player) {
        return players.get(player.getUniqueId());
    }

    public List<GamePlayer> getTeamPlayers(GameTeam team) {
        return players.values().stream()
                .filter(p -> p.getTeam() == team)
                .toList();
    }

    public List<GamePlayer> getAlivePlayers() {
        return players.values().stream()
                .filter(GamePlayer::isAlive)
                .toList();
    }

    public List<GamePlayer> getAliveTeamPlayers(GameTeam team) {
        return players.values().stream()
                .filter(p -> p.getTeam() == team && p.isAlive())
                .toList();
    }

    public UUID getHostUuid() {
        return hostUuid;
    }

    public GameMap getSelectedMap() { return selectedMap; }

    public void setSelectedMap(GameMap map) { this.selectedMap = map; }

    // ============================================================
    //                     GESTION DES JOUEURS
    // ============================================================

    public boolean canJoin() {
        return state == GameState.WAITING || state == GameState.STARTING;
    }

    public void addPlayer(Player player) {
        if (!canJoin()) {
            player.sendMessage(LangHelper.component(player, "sw.game-already-started"));
            return;
        }

        if (players.containsKey(player.getUniqueId())) return;

        int maxPlayers = getMaxPlayers();
        if (players.size() >= maxPlayers) {
            player.sendMessage(LangHelper.component(player, "sw.game-already-started"));
            return;
        }

        GamePlayer gp = new GamePlayer(player.getUniqueId());
        gp.setTeam(assignTeam());

        PlayerKit kit = plugin.getPlayerDataManager().getKit(player.getUniqueId());
        PlayerClass playerClass = kit.getPlayerClass();

        if (plugin.getGameSettingsMenu().isClassEnabled(playerClass) && plugin.getGameSettingsMenu().isKitEnabled(kit)) {
            gp.setKit(kit);
            gp.setPlayerClass(playerClass);
        } else {
            gp.setKit(PlayerKit.NONE);
            gp.setPlayerClass(PlayerClass.NONE);
        }

        players.put(player.getUniqueId(), gp);

        // Réinitialise le joueur et envoie au lobby
        resetPlayer(player);
        if (lobby != null) player.teleport(lobby);
        player.setGameMode(GameMode.ADVENTURE);
        setupWaitingHotbar(player);
        plugin.getScoreboardManager().updateAll();

        broadcastLang("sw.player-joined", player.getName(), players.size(), maxPlayers);

        // Lance le countdown si on a assez de joueurs et auto-start activé
        int minPlayers = getMinPlayers();
        boolean autoStart = plugin.getConfig().getBoolean("default-settings.auto-start", false);
        if (autoStart && state == GameState.WAITING && players.size() >= minPlayers) {
            startCountdown();
        }
    }

    public void removePlayer(Player player) {
        GamePlayer gp = players.remove(player.getUniqueId());
        if (gp == null) return;
        disableGlowingFor(gp);
        plugin.getMapVoteMenu().removeVote(player.getUniqueId());
        plugin.getScoreboardManager().clear(player);

        if (state == GameState.WAITING || state == GameState.STARTING) {
            broadcastLang("sw.player-left", player.getName());
        } else if (state == GameState.PLAYING) {
            if (gp.isAlive()) {
                gp.setAlive(false);
                player.setGameMode(GameMode.SPECTATOR);
                checkWinCondition();
            }
        }

        // Annule le countdown si plus assez de joueurs
        int min = getMinPlayers();
        if (state == GameState.STARTING && players.size() < min) {
            cancelCountdown();
        }
        plugin.getScoreboardManager().updateAll();
    }

    public GameTeam assignTeam() {
        long redCount = 0, blueCount = 0;
        for (GamePlayer gp : players.values()) {
            if (gp.getTeam() == GameTeam.RED) redCount++;
            else if (gp.getTeam() == GameTeam.BLUE) blueCount++;
        }
        if (redCount == blueCount) return GameTeam.random();
        return redCount < blueCount ? GameTeam.RED : GameTeam.BLUE;
    }

    // ============================================================
    //                     HOTBAR
    // ============================================================

    public void setupWaitingHotbar(Player player) {
        player.getInventory().clear();
        // Slot 0: Team selector
        player.getInventory().setItem(0, plugin.getTeamMenu().createSelectorItem(player));
        // Slot 1: Class selector
        player.getInventory().setItem(1, plugin.getClassKitMenu().createSelectorItem(player));
        // Slot 2: Map Vote
        player.getInventory().setItem(2, plugin.getMapVoteMenu().createSelectorItem(player));
        // Slot 4: Settings+Launch (host)
        if (player.getUniqueId().equals(hostUuid)) {
            player.getInventory().setItem(4, plugin.getGameSettingsMenu().createSelectorItem(player));
        }
        // Slot 8: Leave game (bed)
        player.getInventory().setItem(8, createLeaveItem(player.getUniqueId()));
    }

    public ItemStack createLeaveItem(UUID uuid) {
        return new ItemBuilder(Material.RED_BED)
                .name(LangHelper.component(uuid, "sw.leave-item-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(uuid, "sw.leave-item-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .persistentData(leaveItemKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isLeaveItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(leaveItemKey, PersistentDataType.BYTE);
    }

    public void sendToLobby(Player player) {
        plugin.getRedisManager().publishCommand("PROXY", "CONNECT:" + player.getUniqueId() + ":lobby");
    }

    private void resetPlayer(Player player) {
        // Restaure les attributs modifiés par les kits avant de vider l'état de santé.
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
        var knockbackAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) knockbackAttr.setBaseValue(0.0);

        player.getInventory().clear();
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(0);
        player.setFireTicks(0);
        player.setExp(0);
        player.setLevel(0);
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
    }

    // ============================================================
    //                    COMPTE À REBOURS
    // ============================================================

    public void startCountdown() {
        if (state != GameState.WAITING || currentTask != null) return;
        int minPlayers = getMinPlayers();
        if (players.size() < minPlayers) return;
        state = GameState.STARTING;
        updateInstanceStatus(ServerInstance.Status.GAME_STARTING);
        countdown = Math.max(1, plugin.getConfig().getInt("default-settings.countdown", 10));

        currentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (countdown <= 0) {
                startGame();
                return;
            }

            if (countdown <= 5 || countdown == 10 || countdown == 15) {
                broadcastLang("sw.countdown", countdown);
                playSoundAll(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.0F);
            }
            countdown--;
            plugin.getScoreboardManager().updateAll();
        }, 0L, 20L);
    }

    public void cancelCountdown() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        if (state != GameState.STARTING) return;
        state = GameState.WAITING;
        updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
        broadcastLang("sw.countdown-cancelled");
        plugin.getScoreboardManager().updateAll();
    }

    // ============================================================
    //                    DÉBUT DE PARTIE
    // ============================================================

    public void startGame() {
        if (state != GameState.STARTING) return;
        int minPlayers = getMinPlayers();
        if (players.size() < minPlayers) {
            cancelCountdown();
            return;
        }
        if (plugin.getGameSettingsMenu().isMapVoteEnabled()) {
            selectedMap = plugin.getMapVoteMenu().resolveWinnerAndReset();
        }

        if (selectedMap == null || selectedMap.isNotReady()) {
            broadcastLang("sw.arena-not-ready-start");
            if (currentTask != null) { currentTask.cancel(); currentTask = null; }
            state = GameState.WAITING;
            updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            plugin.getScoreboardManager().updateAll();
            return;
        }

        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        if (sheepTask != null) {
            sheepTask.cancel();
            sheepTask = null;
        }
        state = GameState.PLAYING;
        updateInstanceStatus(ServerInstance.Status.GAME_PLAYING);
        if (instanceId != null) {
            plugin.getRedisManager().set("sw:game-started:" + instanceId, "1", 7200);
            var selfInstance = plugin.getRedisManager().getInstance(instanceId);
            if (selfInstance != null && selfInstance.getTemplateId() != null) {
                plugin.getRedisManager().publishCommand("PROXY", "CREATE_GAME:" + selfInstance.getTemplateId() + ":" + instanceId);
            }
        }
        gameTime = Math.max(1, plugin.getConfig().getInt("default-settings.game-duration", 600));

        plugin.getSheepManager().reset();

        int sheepDelaySeconds = Math.max(1,
                plugin.getConfig().getInt("default-settings.sheep-give-delay", 10));
        if (sheepDelaySeconds < 5) {
            Player host = Bukkit.getPlayer(hostUuid);
            if (host != null)
                host.sendMessage(LangHelper.component(host, "sw.settings-apocalypse-chat", sheepDelaySeconds));
        }

        boolean randomKits = plugin.getConfig().getBoolean("default-settings.random-kits", false);

        // Pré-mélange des spawns par équipe pour éviter les doublons
        Map<GameTeam, List<Location>> shuffledSpawns = new EnumMap<>(GameTeam.class);
        Map<GameTeam, Integer> spawnIndex = new EnumMap<>(GameTeam.class);
        for (GameTeam team : GameTeam.values()) {
            List<Location> list = new ArrayList<>(selectedMap.getSpawns(team));
            Collections.shuffle(list);
            shuffledSpawns.put(team, list);
            spawnIndex.put(team, 0);
        }

        // Téléporte chaque joueur au spawn de son équipe
        for (GamePlayer gp : players.values()) {
            Player p = gp.getBukkitPlayer();
            if (p == null) continue;

            // Résolution du kit : aléatoire ou vérification que le kit sélectionné est encore actif
            if (randomKits) {
                gp.setKit(randomEnabledKit());
            } else if (!plugin.getGameSettingsMenu().isKitEnabled(gp.getKit())) {
                gp.setKit(PlayerKit.NONE);
            }

            GameTeam team = gp.getTeam();
            List<Location> spawns = shuffledSpawns.get(team);
            Location spawn = null;
            if (spawns != null && !spawns.isEmpty()) {
                int idx = spawnIndex.merge(team, 1, Integer::sum) - 1;
                spawn = spawns.get(idx % spawns.size());
            }
            if (spawn != null) p.teleport(spawn);

            p.setGameMode(GameMode.SURVIVAL);
            resetPlayer(p);

            // Kit de départ (modifié selon la classe choisie)
            giveBaseKit(p, gp);
            applyKitEffects(p, gp);

            // Titre de bienvenue
            p.showTitle(Title.title(
                    Component.text(LangHelper.get(p.getUniqueId(), "sw.title-start"), gp.getTeam().getColor()),
                    Component.text(LangHelper.get(p.getUniqueId(), "sw.subtitle-start", gp.getTeam().getDisplayName()), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);

        }

        int blueCount = getTeamPlayers(GameTeam.BLUE).size();
        int redCount = getTeamPlayers(GameTeam.RED).size();
        if (blueCount != redCount) {
            GameTeam disadvantaged = blueCount > redCount ? GameTeam.RED : GameTeam.BLUE;
            for (GamePlayer player : getTeamPlayers(disadvantaged)) {
                Player bukkitPlayer = player.getBukkitPlayer();
                if (bukkitPlayer == null) continue;
                for (int i = 0; i < 3; i++) {
                    bukkitPlayer.getInventory().addItem(plugin.getSheepManager()
                            .createSheepItem(plugin.getSheepManager().randomSheepType()));
                }
            }
        }

        enableTeamGlowing();

        broadcastLang("sw.game-started");

        // Tâche périodique pendant la partie
        currentTask = Bukkit.getScheduler().runTaskTimer(plugin, this::gameTick, 20L, 20L);

        // Tâche distribution de moutons spéciaux (stockée pour pouvoir être annulée)
        int delay = sheepDelaySeconds * 20;
        sheepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::distributeSpecialSheep, delay, delay);

        plugin.getScoreboardManager().updateAll();
    }

    // ============================================================
    //                      GESTION DES KITS
    // ============================================================

    private void giveBaseKit(Player p, GamePlayer gp) {
        PlayerKit kit = gp.getKit();

        // Épée
        ItemStack sword;
        if (kit == PlayerKit.DPS_SWORD) {
            sword = new ItemStack(Material.STONE_SWORD);
            ItemMeta m = sword.getItemMeta();
            m.setUnbreakable(true);
            m.addEnchant(Enchantment.SHARPNESS, 1, true);
            sword.setItemMeta(m);
        } else {
            sword = new ItemStack(Material.WOODEN_SWORD);
            ItemMeta m = sword.getItemMeta();
            m.setUnbreakable(true);
            sword.setItemMeta(m);
        }
        p.give(sword);

        // Arc
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta bowMeta = bow.getItemMeta();
        bowMeta.setUnbreakable(true);
        bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
        if (kit == PlayerKit.DPS_BOW) bowMeta.addEnchant(Enchantment.POWER, 1, true);
        bowMeta.setHideTooltip(true);
        bowMeta.setEnchantmentGlintOverride(false);
        bow.setItemMeta(bowMeta);
        p.give(new ItemStack(Material.ARROW));
        p.give(bow);

        //Armure
        LeatherArmorMeta leatherArmorMeta = (LeatherArmorMeta) new ItemStack(Material.LEATHER_HELMET).getItemMeta();
        leatherArmorMeta.setUnbreakable(true);
        leatherArmorMeta.setHideTooltip(true);
        leatherArmorMeta.setColor(gp.getTeam().getDyeColor().getColor());


        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        helmet.setItemMeta(leatherArmorMeta);

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        chestplate.setItemMeta(leatherArmorMeta);

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        leggings.setItemMeta(leatherArmorMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        boots.setItemMeta(leatherArmorMeta);

        p.getInventory().setItem(EquipmentSlot.HEAD, helmet);
        p.getInventory().setItem(EquipmentSlot.CHEST, chestplate);
        p.getInventory().setItem(EquipmentSlot.LEGS, leggings);
        p.getInventory().setItem(EquipmentSlot.FEET, boots);

        // Mouton de départ
        p.getInventory().addItem(plugin.getSheepManager().createSheepItem(plugin.getSheepManager().randomSheepType()));
    }

    private PlayerKit randomEnabledKit() {
        List<PlayerKit> enabled = Arrays.stream(PlayerKit.values())
                .filter(k -> k != PlayerKit.NONE && plugin.getGameSettingsMenu().isKitEnabled(k))
                .toList();
        if (enabled.isEmpty()) return PlayerKit.NONE;
        return enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    private void applyKitEffects(Player p, GamePlayer gp) {
        PlayerKit kit = gp.getKit();
        switch (kit) {
            case TANK_HEARTS -> {
                var maxHp = p.getAttribute(Attribute.MAX_HEALTH);
                if (maxHp != null) {
                    maxHp.setBaseValue(28.0);
                    p.setHealth(28.0);
                }
            }
            case TANK_KNOCKBACK -> {
                var kb = p.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.setBaseValue(0.8);
            }
            case SUPPORT_JUMP ->
                p.addPotionEffect(new PotionEffect(
                        PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false));
            default -> { /* other kits act through listeners */ }
        }
    }

    private void gameTick() {
        gameTime--;

        if (gameTime <= 0) {
            // Égalité par timeout : l'équipe avec le plus de joueurs en vie gagne
            int red = getAliveTeamPlayers(GameTeam.RED).size();
            int blue = getAliveTeamPlayers(GameTeam.BLUE).size();
            GameTeam winner = (red > blue) ? GameTeam.RED : (blue > red) ? GameTeam.BLUE : null;
            endGame(winner);
            return;
        }

        plugin.getScoreboardManager().updateAll();
    }

    private void distributeSpecialSheep() {
        if (state != GameState.PLAYING) return;
        for (GamePlayer gp : getAlivePlayers()) {
            Player p = gp.getBukkitPlayer();
            if (p == null) continue;
            SheepType type = plugin.getSheepManager().randomSheepType();
            p.getInventory().addItem(plugin.getSheepManager().createSheepItem(type));
            p.playSound(p.getLocation(), Sound.ENTITY_SHEEP_AMBIENT, 1.0F, 1.0F);
        }
    }

    // ============================================================
    //                       MORT / VICTOIRE
    // ============================================================

    public void onPlayerDeath(Player player, Player killer) {
        GamePlayer gp = getPlayer(player);
        if (gp == null || !gp.isAlive()) return;

        gp.setAlive(false);
        disableGlowingFor(gp);
        player.setGameMode(GameMode.SPECTATOR);
        player.removePotionEffect(PotionEffectType.GLOWING);

        Component swPrefix = Component.text("[SW] ", AQUA);
        if (killer != null && !killer.equals(player)) {
            GamePlayer killerGp = getPlayer(killer);
            if (killerGp != null && killerGp.getTeam() != gp.getTeam()) {
                killerGp.addKill();
                for (GamePlayer gp2 : players.values()) {
                    Player p2 = gp2.getBukkitPlayer();
                    if (p2 == null) continue;
                    p2.sendMessage(swPrefix.append(
                        Component.text(player.getName(), gp.getTeam().getColor())
                            .append(LangHelper.component(p2.getUniqueId(), "sw.killed-by-connector"))
                            .append(Component.text(killer.getName(), killerGp.getTeam().getColor()))));
                }
            } else {
                broadcastDeath(player, gp);
            }
        } else {
            broadcastDeath(player, gp);
        }

        checkWinCondition();
    }

    private void checkWinCondition() {
        if (state != GameState.PLAYING) return;
        int redAlive = getAliveTeamPlayers(GameTeam.RED).size();
        int blueAlive = getAliveTeamPlayers(GameTeam.BLUE).size();

        if (redAlive == 0 && blueAlive == 0) {
            endGame(null);
        } else if (redAlive == 0) {
            endGame(GameTeam.BLUE);
        } else if (blueAlive == 0) {
            endGame(GameTeam.RED);
        }
    }

    private void endGame(GameTeam winner) {
        if (state != GameState.PLAYING) return;
        state = GameState.ENDING;
        updateInstanceStatus(ServerInstance.Status.GAME_ENDING);

        if (currentTask != null) currentTask.cancel();
        if (sheepTask != null) sheepTask.cancel();

        Component endPrefix = Component.text("[SW] ", AQUA);
        for (GamePlayer gp : players.values()) {
            Player p = gp.getBukkitPlayer();
            if (p == null) continue;
            Component locTitle = winner == null
                    ? Component.text(LangHelper.get(p.getUniqueId(), "sw.title-draw"), NamedTextColor.YELLOW)
                    : Component.text(LangHelper.get(p.getUniqueId(), "sw.title-winner", winner.getDisplayName()), winner.getColor());
            p.showTitle(Title.title(
                    locTitle,
                    Component.text(LangHelper.get(p.getUniqueId(), "sw.subtitle-end"), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            p.sendMessage(endPrefix.append(locTitle));
        }

        removeGameArrows();
        removeGameItems();

        // Update scoreboard to end-of-game state immediately
        plugin.getScoreboardManager().updateAll();

        // Renvoie tous les joueurs au lobby après huit secondes et propose une revanche.
        Bukkit.getScheduler().runTaskLater(plugin, this::sendAllToLobbyAndReset, 160L);
    }

    private void sendAllToLobbyAndReset() {
        if (instanceId != null) plugin.getRedisManager().delete("sw:game-started:" + instanceId);
        if (!hostUuid.equals(NO_HOST)) plugin.getRedisManager().delete("host:" + hostUuid);

        String nextServer = instanceId != null ? plugin.getRedisManager().get("sw:next-game:" + instanceId) : null;
        if (nextServer != null) plugin.getRedisManager().delete("sw:next-game:" + instanceId);

        // Transmet le type afin que le lobby trouve ou crée une partie équivalente.
        var selfInstance = instanceId != null ? plugin.getRedisManager().getInstance(instanceId) : null;
        String serverType = selfInstance != null && selfInstance.getServerType() != null
                ? selfInstance.getServerType() : "sheepwars";

        // La commande /playnext consomme ce marqueur au format serveur|type.
        // Le serveur reste vide lorsqu'aucune instance n'a été précréée.
        String postGameValue = (nextServer != null ? nextServer : "") + "|" + serverType;
        for (UUID uuid : players.keySet()) {
            plugin.getRedisManager().set("post-game:" + uuid, postGameValue, 120);
        }

        // Notify players they are being sent back
        Component prefix = Component.text("[SW] ", AQUA);
        for (GamePlayer gp : players.values()) {
            Player p = gp.getBukkitPlayer();
            if (p == null) continue;
            p.sendMessage(prefix.append(LangHelper.component(p.getUniqueId(), "sw.replay-message")));
        }

        // Send everyone to lobby (staggered to avoid Velocity congestion)
        int delay = 0;
        for (UUID uuid : new HashSet<>(players.keySet())) {
            final int d = delay;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) sendToLobby(p);
            }, d);
            delay += 5;
        }

        players.clear();
        plugin.getSheepManager().reset();
        plugin.getScoreboardManager().clearAll();
        state = GameState.ENDED;
        plugin.getScoreboardManager().updateAll();
    }

    private void updateInstanceStatus(ServerInstance.Status status) {
        if (instanceId == null || instanceId.isBlank()) return;
        ServerInstance instance = plugin.getRedisManager().getInstance(instanceId);
        if (instance == null) return;
        instance.setStatus(status);
        plugin.getRedisManager().saveInstance(instance);
    }

    private void removeGameArrows() {
        World world = getGameWorld();
        if (world == null) return;
        for (var e : world.getEntities()) {
            if (e instanceof AbstractArrow) e.remove();
        }
    }

    private void removeGameItems() {
        World world = getGameWorld();
        if (world == null) return;
        for (var e : world.getEntities()) {
            if (e instanceof Item) e.remove();
        }
    }

    private World getGameWorld() {
        if (selectedMap != null) {
            for (GameTeam team : GameTeam.values()) {
                List<Location> spawns = selectedMap.getSpawns(team);
                if (!spawns.isEmpty()) return spawns.getFirst().getWorld();
            }
        }
        return lobby != null ? lobby.getWorld() : null;
    }

    private int getMaxPlayers() {
        // MapsUtil charge au maximum huit spawns par équipe.
        return Math.min(16, Math.max(1,
                plugin.getConfig().getInt("default-settings.max-players", 16)));
    }

    private int getMinPlayers() {
        return Math.min(getMaxPlayers(), Math.max(1,
                plugin.getConfig().getInt("default-settings.min-players", 2)));
    }

    // ============================================================
    //                     UTILITAIRES
    // ============================================================

    public void broadcastLang(String key, Object... args) {
        Component prefix = Component.text("[SW] ", AQUA);
        for (GamePlayer gp : players.values()) {
            Player p = gp.getBukkitPlayer();
            if (p != null) p.sendMessage(prefix.append(LangHelper.component(p.getUniqueId(), key, args)));
        }
    }

    private void broadcastDeath(Player player, GamePlayer gp) {
        Component prefix = Component.text("[SW] ", AQUA);
        for (GamePlayer gp2 : players.values()) {
            Player p2 = gp2.getBukkitPlayer();
            if (p2 == null) continue;
            p2.sendMessage(prefix.append(
                Component.text(player.getName(), gp.getTeam().getColor())
                    .append(LangHelper.component(p2.getUniqueId(), "sw.died-connector"))));
        }
    }

    public void playSoundAll(Sound sound, float volume, float pitch) {
        for (GamePlayer gp : players.values()) {
            Player p = gp.getBukkitPlayer();
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    public int getCountdown() {
        return countdown;
    }

    public int getGameTime() {
        return gameTime;
    }

    /** Retourne l'identifiant Redis de l'instance courante, ou {@code null} hors orchestration. */
    public String getInstanceId() {
        return instanceId;
    }

    public void enableTeamGlowing() {
        for(GameTeam team : GameTeam.values()) {
            for(GamePlayer teamPlayer : getTeamPlayers(team)) {
                Player bukkitTeamPlayer = teamPlayer.getBukkitPlayer();
                if (bukkitTeamPlayer == null) continue;
                for(GamePlayer teammate : getTeamPlayers(team)) {
                    Entity teammateEntity = teammate.getBukkitPlayer();
                    if (teammateEntity == null) continue;
                    try {
                        // Le scoreboard personnel reste l'unique propriétaire de l'équipe et de sa couleur.
                        // Fournir une couleur ici créerait une seconde équipe glow-* côté client.
                        glowingEntities.setGlowing(teammateEntity, bukkitTeamPlayer);
                    } catch (ReflectiveOperationException e) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Impossible d'activer le surlignage d'équipe", e);
                    }
                }

            }
        }

    }

    private void disableGlowingFor(GamePlayer gamePlayer) {
        Player entity = gamePlayer.getBukkitPlayer();
        if (entity == null || gamePlayer.getTeam() == null) return;
        for (GamePlayer teammate : getTeamPlayers(gamePlayer.getTeam())) {
            Player viewer = teammate.getBukkitPlayer();
            if (viewer == null) continue;
            try {
                glowingEntities.unsetGlowing(entity, viewer);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(java.util.logging.Level.FINE,
                        "Impossible de retirer le surlignage d'équipe", e);
            }
        }
    }

    // ============================================================
    //                  CHARGEMENT DE L'ARENE
    // ============================================================

    public void loadGame() {
        gameMaps.clear();
        var section = plugin.getConfig().getConfigurationSection("locations");
        if (section == null) return;

        String worldName = section.getString("world");
        if (worldName == null) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().severe("Monde SheepWars introuvable : " + worldName);
            return;
        }

        lobby = MapsUtil.loadLocation(section, world, "lobby");
        ConfigurationSection lobbySection = section.getConfigurationSection("lobby");
        if (lobbySection != null) lobbyVoidLimit = lobbySection.getInt("void_limit");
        gameMaps.addAll(MapsUtil.loadMaps(section, world));
    }

    public void shutdown() {
        if (currentTask != null) currentTask.cancel();
        if (sheepTask != null) sheepTask.cancel();
        currentTask = null;
        sheepTask = null;
        players.clear();
        glowingEntities.disable();
        if (plugin.getSheepManager() != null) plugin.getSheepManager().reset();
    }

    private UUID parseHostUuid(String value) {
        if (value == null || value.isBlank()) return NO_HOST;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("HOST_UUID invalide : '" + value + "'. Mode sans hôte activé.");
            return NO_HOST;
        }
    }
}
