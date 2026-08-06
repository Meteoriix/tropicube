package fr.tropicube.sheepwars.listener;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Applique les règles de connexion, combat, mort et interaction de SheepWars. */
public class PlayerListener implements Listener {

    private final TropicubeSheepwars plugin;

    public PlayerListener(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);

        plugin.getPlayerDataManager().loadPlayer(player).thenRun(() ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!plugin.getGameManager().getGameMaps().isEmpty() && plugin.getGameManager().canJoin()) {
                    plugin.getGameManager().addPlayer(player);
                } else if (plugin.getGameManager().getGameMaps().isEmpty() && player.hasPermission("sheepwars.admin")) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (!player.isOnline()) return;
                        player.sendMessage(LangHelper.component(player, "sw.arena-not-configured-msg"));
                    }, 20L);
                }
            })
        );
    }

    /** Réduit de moitié la régénération naturelle de santé des joueurs en jeu uniquement. */
    @EventHandler
    public void onRegenerate(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.getGameManager().getPlayer(player) == null) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        event.setAmount(event.getAmount() / 2);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().getPlayer(player) != null) {
            if (plugin.getGameManager().getState() == GameState.PLAYING) {
                player.kill(DamageSource.builder(DamageType.GENERIC).build());
            }
            plugin.getGameManager().removePlayer(player);
        }
        plugin.getScoreboardManager().clear(player);
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
        event.quitMessage(null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Class selector item
        if (plugin.getClassKitMenu().isSelectorItem(item)) {
            event.setCancelled(true);
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING || state == GameState.ENDING) {
                player.sendMessage(LangHelper.component(player, "sw.no-class-change-ingame"));
                return;
            }
            plugin.getClassKitMenu().openClassMenu(player);
            return;
        }

        // Map vote/pick selector item
        if (plugin.getMapVoteMenu().isSelectorItem(item)) {
            event.setCancelled(true);
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING || state == GameState.ENDING) {
                player.sendMessage(LangHelper.component(player, "sw.no-class-change-ingame"));
                return;
            }
            plugin.getMapVoteMenu().open(player);
            return;
        }

        // Team selector item
        if (plugin.getTeamMenu().isSelectorItem(item)) {
            event.setCancelled(true);
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING || state == GameState.ENDING) {
                player.sendMessage(LangHelper.component(player, "sw.no-class-change-ingame"));
                return;
            }
            plugin.getTeamMenu().open(player);
            return;
        }

        // Réglages et lancement réservés à l'hôte ou à l'administration.
        if (plugin.getGameSettingsMenu().isSelectorItem(item)) {
            event.setCancelled(true);
            if (!plugin.getGameManager().getHostUuid().equals(player.getUniqueId())) {
                player.sendMessage(LangHelper.component(player, "sw.cmd-no-permission"));
                player.getInventory().remove(item);
                return;
            }

            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING || state == GameState.ENDING) {
                player.sendMessage(LangHelper.component(player, "sw.no-settings-change-ingame"));
                return;
            }
            plugin.getGameSettingsMenu().open(player);
            return;
        }

        // Le lit quitte toujours la partie vers le lobby principal.
        if (plugin.getGameManager().isLeaveItem(item)) {
            event.setCancelled(true);
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING) {
                // Transmet l'instance exacte afin que /sw join puisse y reconnecter le joueur.
                String instanceId = plugin.getGameManager().getInstanceId();
                if (instanceId != null && !instanceId.isBlank()) {
                    plugin.getRedisManager().set("sw:left-game:" + player.getUniqueId(), instanceId, 300);
                }
            }
            plugin.getGameManager().removePlayer(player);
            plugin.getGameManager().sendToLobby(player);
            return;
        }

        if (plugin.getGameManager().getState() != GameState.PLAYING) return;

        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null || !gp.isAlive()) return;

        SheepType type = plugin.getSheepManager().getSheepType(item);
        if (type == null) return;

        event.setCancelled(true);
        plugin.getSheepManager().launchSheep(player, type);
        gp.addSheepThrown();
        item.setAmount(item.getAmount() - 1);
    }

    /** SUPPORT_ARROWS: arrows heal teammates instead of damaging them.
     * Le mouton de force augmente de 15 % les dégâts de mêlée et à distance. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof Player target)) return;

        Player shooter;
        boolean isArrow;

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            shooter = p;
            isArrow = projectile instanceof AbstractArrow;
        } else if (event.getDamager() instanceof Player p) {
            shooter = p;
            isArrow = false;
        } else {
            return;
        }

        GamePlayer shooterGp = plugin.getGameManager().getPlayer(shooter);
        GamePlayer targetGp  = plugin.getGameManager().getPlayer(target);
        if (targetGp == null) return;
        if (shooterGp == null) {
            event.setCancelled(true);
            return;
        }

        // Une flèche alliée soigne avec SUPPORT_ARROWS ; sinon ses dégâts sont annulés.
        if (isArrow && shooterGp.getTeam() == targetGp.getTeam()) {
            event.setCancelled(true);
            if (shooterGp.getKit() == PlayerKit.SUPPORT_ARROWS) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 1));
            }
            return;
        }

        // STRENGTH SHEEP: +15% damage against enemies
        if (shooterGp.getTeam() != targetGp.getTeam()
                && plugin.getSheepManager().hasStrengthBuff(shooter.getUniqueId())) {
            event.setDamage(event.getDamage() * 1.15);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null) return;

        event.setCancelled(true);
        event.deathMessage(null);
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = player.getKiller();
        plugin.getGameManager().onPlayerDeath(player, killer);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (plugin.getGameManager().getState() != GameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null) {
            event.setCancelled(true);
            return;
        }
        if (!gp.isAlive()) {
            event.setCancelled(true);
            return;
        }

        // TANK_FALL: reduce fall damage by 80%
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (gp.getKit() == PlayerKit.TANK_FALL) {
                event.setDamage(event.getDamage() * 0.2);
            } else {
                event.setDamage(event.getDamage() * 0.5);
            }
        }


        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            Player killer = event.getDamageSource().getCausingEntity() instanceof Player causingPlayer
                    ? causingPlayer : player.getKiller();
            plugin.getGameManager().onPlayerDeath(player, killer);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Ne revérifie la hauteur qu'après un changement réel de bloc vertical.
        if (event.getFrom().getBlockY() <= event.getTo().getBlockY()) return;

        GameState state = plugin.getGameManager().getState();

        if (state == GameState.PLAYING) {
            GamePlayer gp = plugin.getGameManager().getPlayer(event.getPlayer());
            var map = plugin.getGameManager().getSelectedMap();
            if (gp != null && gp.isAlive() && map != null
                    && event.getTo().getY() < map.getVoidLimit()) {
                plugin.getGameManager().onPlayerDeath(event.getPlayer(), null);
            }
            return;
        }

        if (state != GameState.WAITING && state != GameState.STARTING) return;

        Location lobby = plugin.getGameManager().getLobby();
        if (lobby == null) return;

        if (event.getTo().getY() < plugin.getGameManager().getLobbyVoidLimit()) {
            event.getPlayer().teleport(lobby);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp != null && plugin.getGameManager().getLobby() != null) {
            event.setRespawnLocation(plugin.getGameManager().getLobby());
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null || gp.getTeam() == null) return;

        Component prefix = Component.text("[", NamedTextColor.GRAY)
                .append(Component.text(gp.getTeam().getDisplayName(), gp.getTeam().getColor()))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(player.getName() + " : ", NamedTextColor.WHITE));

        event.renderer((_, _, message, _) ->
                prefix.append(message.colorIfAbsent(NamedTextColor.GRAY)));
    }
}
