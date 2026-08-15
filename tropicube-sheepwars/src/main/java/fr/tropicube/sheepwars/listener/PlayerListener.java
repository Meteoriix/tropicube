package fr.tropicube.sheepwars.listener;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.util.PlayerDisplayName;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Applies SheepWars connection, combat, death and interaction rules. */
public class PlayerListener implements Listener {

    private final TropicubeSheepwars plugin;
    private final Map<UUID, Integer> nextMedicHealTick = new HashMap<>();

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

    /** Halves the natural health regeneration of players in-game only. */
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
        GamePlayer gamePlayer = plugin.getGameManager().getPlayer(player);
        if (gamePlayer != null) {
            if (plugin.getGameManager().getState() == GameState.PLAYING && gamePlayer.isAlive()) {
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

        // Settings and launch reserved for the host or administration.
        if (plugin.getWhitelistMenu().isSelectorItem(item)) {
            event.setCancelled(true);
            if (!plugin.getGameManager().getHostUuid().equals(player.getUniqueId())
                    || !plugin.getGameManager().isPrivateCustomGame()) {
                player.sendMessage(LangHelper.component(player, "sw.cmd-no-permission"));
                player.getInventory().remove(item);
                return;
            }
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.ENDING) return;
            plugin.getWhitelistMenu().open(player);
            return;
        }

        // Settings and launch reserved for the host or administration.
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

        // The bed always leaves the match for the main lobby.
        if (plugin.getGameManager().isLeaveItem(item)) {
            event.setCancelled(true);
            GameState state = plugin.getGameManager().getState();
            if (state == GameState.PLAYING) {
                // Pass the exact instance so that /sw join can reconnect the player to it.
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
     * Force Sheep increases melee and ranged damage by 15%. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (plugin.getGameManager().getState() != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof Player target)) return;

        Player shooter;
        boolean isArrow;

        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            if (plugin.getSheepManager().isMeteorFireball(projectile.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
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

        // An allied arrow heals with SUPPORT_ARROWS; otherwise its damage is canceled.
        if (isArrow && shooterGp.getTeam() == targetGp.getTeam()) {
            event.setCancelled(true);
            if (shooterGp.getKit() == PlayerKit.SUPPORT_ARROWS) {
                int currentTick = org.bukkit.Bukkit.getCurrentTick();
                int availableAt = nextMedicHealTick.getOrDefault(target.getUniqueId(), 0);
                if (currentTick >= availableAt) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                            plugin.getGameplayBalance().ticks("kits.medic-regeneration-seconds"),
                            plugin.getGameplayBalance().integer("kits.medic-regeneration-amplifier")));
                    nextMedicHealTick.put(target.getUniqueId(), currentTick
                            + plugin.getGameplayBalance().ticks("kits.medic-cooldown-seconds"));
                }
            }
            return;
        }

        // STRENGTH SHEEP: +20% damage against enemies
        if (shooterGp.getTeam() != targetGp.getTeam()
                && !plugin.getSheepManager().isApplyingSheepDamage()
                && plugin.getSheepManager().hasStrengthBuff(shooter.getUniqueId())) {
            event.setDamage(event.getDamage()
                    * plugin.getGameplayBalance().decimal("kits.strength-damage-multiplier"));
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

        if (plugin.getSheepManager().isCreatingSheepExplosion()
                && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)) {
            event.setCancelled(true);
            return;
        }

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

        // Everyone takes half fall damage; the tank specialist takes 35% of vanilla.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (gp.getKit() == PlayerKit.TANK_FALL) {
                event.setDamage(event.getDamage()
                        * plugin.getGameplayBalance().decimal("kits.tank-fall-damage-multiplier"));
            } else {
                event.setDamage(event.getDamage()
                        * plugin.getGameplayBalance().decimal("kits.normal-fall-damage-multiplier"));
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
        // Only recheck the height after an actual vertical block change.
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

        Component prefix = Component.text(gp.getTeam().getDisplayName(), gp.getTeam().getColor(),
                        TextDecoration.BOLD)
                .append(Component.text(" " + PlayerDisplayName.resolve(player) + " > ", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.BOLD, false));

        event.renderer((_, _, message, _) ->
                prefix.append(message.colorIfAbsent(NamedTextColor.GRAY)));
    }
}
