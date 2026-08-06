package fr.tropicube.sheepwars.listener;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.sheep.SheepManager;
import fr.tropicube.sheepwars.sheep.types.DistortSheep;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;

/** Traduit l'utilisation des objets-moutons en lancement de capacités. */
public class SheepListener implements Listener {

    private final TropicubeSheepwars plugin;

    /** Clé PDC des FallingBlocks du DistortSheep — mise en cache pour éviter des allocations répétées. */
    private final NamespacedKey distortFbKey;

    public SheepListener(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.distortFbKey = new NamespacedKey(plugin, DistortSheep.FB_KEY);
    }

    // ── Comportement des moutons ───────────────────────────────────────────

    @EventHandler
    public void onSheepDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Sheep sheep)) return;
        if (plugin.getSheepManager().isNotGameSheep(sheep)) return;

        event.setDroppedExp(0);
        event.getDrops().clear();

        // Rend la laine du type logique, indépendamment de la couleur de clignotement.
        String typeName = sheep.getPersistentDataContainer()
                .get(plugin.getSheepManager().sheepTypeKey, PersistentDataType.STRING);
        if (typeName != null) {
            try {
                SheepType type = SheepType.valueOf(typeName);
                if(event.getDamageSource().getCausingEntity() instanceof Player player) player.getInventory().addItem(plugin.getSheepManager().createSheepItem(type));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /** Empêche les moutons du jeu de cibler automatiquement (seul le ciblage CUSTOM via le code est autorisé). */
    @EventHandler
    public void onSheepTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Sheep sheep)) return;
        if (plugin.getSheepManager().isNotGameSheep(sheep)) return;
        if (!event.getReason().equals(EntityTargetEvent.TargetReason.CUSTOM)) {
            event.setCancelled(true);
        }
    }

    // ── Golem Mécha ───────────────────────────────────────────────────────

    /** Empêche le golem mécha d'attaquer les coéquipiers ou les non-joueurs. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGolemAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof IronGolem golem)) return;
        SheepManager.MechaData data = plugin.getSheepManager().getGolem(golem.getUniqueId());
        if (data == null) return;

        if (!(event.getEntity() instanceof Player target)) {
            event.setCancelled(true);
            return;
        }

        Player thrower = Bukkit.getPlayer(data.throwerUUID());
        if (thrower == null) {
            event.setCancelled(true);
            return;
        }

        GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
        GamePlayer targetGp  = plugin.getGameManager().getPlayer(target);
        if (throwerGp == null || targetGp == null || !targetGp.isAlive()) {
            event.setCancelled(true);
            return;
        }

        if (throwerGp.getTeam() == targetGp.getTeam()) {
            event.setCancelled(true);
        }
    }

    /** Nettoie le mouton passager quand le golem mécha meurt. */
    @EventHandler
    public void onGolemDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        SheepManager.MechaData data = plugin.getSheepManager().removeGolem(golem.getUniqueId());
        if (data == null) return;

        var passenger = Bukkit.getEntity(data.passengerUUID());
        if (passenger != null) passenger.remove();
        event.getDrops().clear();
    }

    // ── Suivi des blocs pour la régénération ──────────────────────────────

    /** Prevents item drops from explosions during the game (blocks still get destroyed). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isNotActiveGame()) return;
        event.setYield(0F); // no item drops from exploded blocks
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isNotActiveGame()) return;
        event.setYield(0F);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isNotActiveGame()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpreadFire(BlockSpreadEvent event) {
        if (isNotActiveGame()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (isNotActiveGame()) {
            event.setCancelled(true);
        }
    }

    /** Empêche les FallingBlocks du DistortSheep de se poser naturellement. */
    @EventHandler(ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fb)) return;
        if (fb.getPersistentDataContainer().has(distortFbKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            fb.remove();
        }
    }

    // ── Utilitaire ────────────────────────────────────────────────────────

    private boolean isNotActiveGame() {
        GameState state = plugin.getGameManager().getState();
        return state != GameState.PLAYING;
    }
}
