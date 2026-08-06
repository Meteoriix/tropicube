package fr.tropicube.lobby.listeners;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gère l'arrivée des joueurs dans le lobby, leur hotbar, et le double-saut.
 */
public class PlayerLobbyListener implements Listener {

    private final TropicubeLobby plugin;

    /** Sauts aériens restants ; {@link Integer#MAX_VALUE} représente un accès illimité. */
    private final Map<UUID, Integer> remainingJumps = new HashMap<>();

    /** Joueurs utilisant le vol permanent réservé au personnel. */
    private final Set<UUID> staffFlyMode = new HashSet<>();

    /** Cibles de revanche transmises par les mini-jeux au format {@code serveur|type}. */
    private final Map<UUID, String> postGameTargets = new HashMap<>();

    /** Instance SheepWars active que le joueur peut rejoindre après une sortie volontaire. */
    private final Map<UUID, String> rejoinTargets = new HashMap<>();

    // Items de la hotbar lobby
    private static final int SLOT_SERVERS     = 0;
    private static final int SLOT_CUSTOM_GAME = 2;
    private static final int SLOT_LANG        = 4;
    private static final int SLOT_VIP         = 8;

    /** Priorité minimale du grade autorisé à héberger une partie personnalisée. */
    private static final int VIP_PLUS_MIN_PRIORITY = 20;

    public PlayerLobbyListener(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    private int maxJumps(Player player) {
        if (player.hasPermission("tropicube.lobby.infinitejump")) return Integer.MAX_VALUE;
        if (player.hasPermission("tropicube.lobby.jump.double"))  return 2;
        if (player.hasPermission("tropicube.lobby.jump"))         return 1;
        return 0;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        // Capture le marqueur avant que le chargement asynchrone de Core ne le supprime.
        boolean isTransfer = plugin.getRedisManager().exists("transfer:" + uuid);

        // Recherche une partie SheepWars quittée volontairement mais toujours active.
        String rejoinInstanceId = plugin.getRedisManager().get("sw:left-game:" + uuid);
        boolean hasRejoinFlag = rejoinInstanceId != null;
        if (rejoinInstanceId != null) {
            plugin.getRedisManager().delete("sw:left-game:" + uuid);
            rejoinTargets.put(uuid, rejoinInstanceId);
        }

        // Récupère la proposition de revanche publiée par le mini-jeu précédent.
        String postGameValue = plugin.getRedisManager().get("post-game:" + uuid);
        if (postGameValue != null) {
            plugin.getRedisManager().delete("post-game:" + uuid);
            postGameTargets.put(uuid, postGameValue);
        }

        // Téléportation au spawn lobby
        teleportToSpawn(player);

        // Donner la hotbar et configurer le scoreboard après un tick
        new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) return;
                setupHotbar(player);
                player.setGameMode(GameMode.ADVENTURE);
                int max = maxJumps(player);
                if (max > 0) remainingJumps.put(uuid, max);
                player.setAllowFlight(player.hasPermission("tropicube.lobby.fly") || max > 0);
                plugin.getScoreboardManager().setup(player);
                plugin.getScoreboardManager().updateAll();

                // Propose de rejoindre à nouveau la partie encore active.
                if (hasRejoinFlag) {
                    player.sendMessage(LangHelper.component(player, "lobby.sw-rejoin-message"));
                }

                // Propose un lien de revanche après une partie terminée.
                if (postGameTargets.containsKey(uuid)) {
                    player.sendMessage(LangHelper.component(player, "lobby.post-game-message"));
                }
            }
        }.runTaskLater(plugin, 5L);

        // Le délai laisse le chargement asynchrone de Core appliquer le grade.
        // Un transfert interne n'est pas annoncé comme une nouvelle connexion.
        if (!isTransfer) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (!player.isOnline()) return;
                    if (!player.hasPermission("tropicube.announce.join")) return;
                    String formattedName = LangHelper.getFormattedName(player.getUniqueId(), player.getName());
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        online.sendMessage(LangHelper.component(online, "join.message", formattedName));
                    }
                }
            }.runTaskLater(plugin, 20L);
        }

        e.joinMessage(null);
    }

    /**
     * Bascule entre le vol permanent du personnel et les sauts aériens illimités.
     * @return {@code true} si le vol vient d'être activé, sinon {@code false}
     */
    public boolean toggleFlyMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (staffFlyMode.contains(uuid)) {
            staffFlyMode.remove(uuid);
            player.setAllowFlight(false);
            remainingJumps.put(uuid, Integer.MAX_VALUE);
            player.setAllowFlight(true);
            return false;
        } else {
            staffFlyMode.add(uuid);
            remainingJumps.remove(uuid);
            player.setAllowFlight(true);
            return true;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        remainingJumps.remove(uuid);
        staffFlyMode.remove(uuid);
        postGameTargets.remove(uuid);
        rejoinTargets.remove(uuid);
        plugin.getGuiManager().onPlayerQuit(uuid);
        plugin.getScoreboardManager().clear(e.getPlayer());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getScoreboardManager().updateAll());
    }

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player player = e.getPlayer();
        if (e.getAction() != Action.RIGHT_CLICK_AIR
                && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) return;

        if (!item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) return;
        CustomModelDataComponent customModelDataComponent = meta.getCustomModelDataComponent();
        if (customModelDataComponent.getFloats().isEmpty()) return;
        switch (customModelDataComponent.getFloats().getFirst().intValue()) {
            case 1001 -> { e.setCancelled(true); plugin.getGuiManager().openServerTypeSelector(player); }
            case 1002 -> { e.setCancelled(true); plugin.getGuiManager().openLanguageSelector(player); }
            case 1003 -> { e.setCancelled(true); plugin.getGuiManager().openVipShop(player); }
            case 1004 -> { e.setCancelled(true); plugin.getGuiManager().openCustomGameTypeMenu(player); }
        }
    }

    // ── Double saut ──────────────────────────────────────────────────────────

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player player = e.getPlayer();
        if (!e.isFlying()) return;
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;
        if (staffFlyMode.contains(player.getUniqueId())) return;  // staff fly mode: fly freely
        // Fly-perm players bypass double-jump UNLESS they switched to jump mode via /flymode
        if (player.hasPermission("tropicube.lobby.fly") && !remainingJumps.containsKey(player.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("lobby.double-jump", true)) return;

        Integer remaining = remainingJumps.get(player.getUniqueId());
        if (remaining == null || remaining == 0) return;

        e.setCancelled(true);
        player.setAllowFlight(false);

        player.setVelocity(player.getEyeLocation().getDirection().multiply(2));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.5f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4f, 1.8f);

        var loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 18, 0.35, 0.1, 0.35, 0.06);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 6, 0.4, 0.1, 0.4, 0.0);
        player.getWorld().spawnParticle(Particle.CRIT, loc, 14, 0.3, 0.2, 0.3, 0.3);
        player.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.4, 0.15, 0.4, 0.04);

        // Decrement counter (infinite stays at MAX_VALUE)
        if (remaining != Integer.MAX_VALUE) {
            remaining--;
            remainingJumps.put(player.getUniqueId(), remaining);
        }

        // Réactive le vol au tick suivant afin que le client traite d'abord sa désactivation.
        // Sans saut restant, attend que l'atterrissage réinitialise le compteur.
        if (remaining > 0) {
            new BukkitRunnable() {
                @Override public void run() {
                    if (player.isOnline()) player.setAllowFlight(true);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onLand(PlayerMoveEvent e) {
        Player player = e.getPlayer();
        if (player.getAllowFlight()) return;       // nothing to restore
        if (e.getTo().clone().subtract(0, 0.1, 0).getBlock().isPassable()) return;
        // En mode saut, même un membre du personnel réarme ses sauts au sol.
        if (player.hasPermission("tropicube.lobby.fly") && !remainingJumps.containsKey(player.getUniqueId())) return;

        UUID uuid = player.getUniqueId();
        if (!remainingJumps.containsKey(uuid)) return;

        int max = maxJumps(player);
        if (max == 0) return;

        remainingJumps.put(uuid, max);
        player.setAllowFlight(true);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Consomme la cible de revanche mémorisée pour ce joueur. */
    public String removePostGameTarget(UUID uuid) {
        return postGameTargets.remove(uuid);
    }

    /** Consomme l'instance SheepWars proposée pour une reconnexion volontaire. */
    public String removeRejoinTarget(UUID uuid) {
        return rejoinTargets.remove(uuid);
    }

    public void setupHotbar(Player player) {
        player.getInventory().clear();

        player.getInventory().setItem(SLOT_SERVERS,
                new ItemBuilder(Material.COMPASS)
                        .name(LangHelper.get(player, "lobby.hotbar-servers-name"))
                        .lore(LangHelper.get(player, "lobby.hotbar-servers-lore1"),
                                "",
                                LangHelper.get(player, "lobby.hotbar-servers-lore2"))
                        .customModelData(1001)
                        .glow().build());

        if (hasMinGradePriority(player)) {
            player.getInventory().setItem(SLOT_CUSTOM_GAME,
                    new ItemBuilder(Material.COMMAND_BLOCK)
                            .name(LangHelper.get(player, "lobby.hotbar-custom-game-name"))
                            .lore(LangHelper.get(player, "lobby.hotbar-custom-game-lore1"),
                                    "",
                                    LangHelper.get(player, "lobby.hotbar-custom-game-lore2"))
                            .customModelData(1004)
                            .glow().build());
        }

        ItemStack languageIcon = new ItemStack(Material.PLAYER_HEAD);
        ItemStack vipIcon = new ItemStack(Material.GOLD_INGOT);
        try {
            if (Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) {
                HeadDatabaseAPI hdbapi = core.getHeadDatabaseManager().getHeadDatabaseAPI();
                if (hdbapi != null) {
                    ItemStack loadedLanguageIcon = hdbapi.getItemHead("71786");
                    ItemStack loadedVipIcon = hdbapi.getItemHead("66671");
                    if (loadedLanguageIcon != null) languageIcon = loadedLanguageIcon;
                    if (loadedVipIcon != null) vipIcon = loadedVipIcon;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("HeadDatabase indisponible, utilisation des icônes de secours.");
        }

        player.getInventory().setItem(SLOT_LANG,
                new ItemBuilder(languageIcon)
                        .name(LangHelper.get(player, "lobby.hotbar-lang-name"))
                        .lore(LangHelper.get(player, "lobby.hotbar-lang-lore1"),
                                "",
                                LangHelper.get(player, "lobby.hotbar-lang-lore2"))
                        .customModelData(1002)
                        .build());

        player.getInventory().setItem(SLOT_VIP,
                new ItemBuilder(vipIcon)
                        .name(LangHelper.get(player, "lobby.hotbar-vip-name"))
                        .lore(LangHelper.get(player, "lobby.hotbar-vip-lore1"),
                                "",
                                LangHelper.get(player, "lobby.hotbar-vip-lore2"))
                        .customModelData(1003)
                        .glow().build());
    }

    private boolean hasMinGradePriority(Player player) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return false;
        return core.getPermissionManager().getPriority(player.getUniqueId()) >= VIP_PLUS_MIN_PRIORITY;
    }

    public boolean teleportToSpawn(Player player) {
        var spawn = plugin.getConfig().getConfigurationSection("lobby.spawn");
        if (spawn == null) return false;
        double x = spawn.getDouble("x", 0);
        double y = spawn.getDouble("y", 64);
        double z = spawn.getDouble("z", 0);
        float yaw = (float) spawn.getDouble("yaw", 0);
        float pitch = (float) spawn.getDouble("pitch", 0);
        String worldName = spawn.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().getFirst();
        if (world == null) return false;
        return player.teleport(new Location(world, x, y, z, yaw, pitch));
    }
}
