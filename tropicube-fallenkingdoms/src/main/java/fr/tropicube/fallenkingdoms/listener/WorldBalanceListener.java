package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.fallenkingdoms.config.DropSettings;
import fr.tropicube.fallenkingdoms.config.SpawnSettings;
import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.game.WorldBalanceRules;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

/** Applies Fallen Kingdoms-only spawn and resource balancing at Paper event boundaries. */
public final class WorldBalanceListener implements Listener {
    private final GameSession session;
    private final SpawnSettings spawns;
    private final DropSettings drops;
    private final Random random = new Random();
    private final Enchantment silkTouch = enchantment("silk_touch");
    private final Enchantment fortune = enchantment("fortune");

    public WorldBalanceListener(GameSession session, SpawnSettings spawns, DropSettings drops) {
        this.session = session;
        this.spawns = spawns;
        this.drops = drops;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void creatureSpawn(CreatureSpawnEvent event) {
        boolean keep = WorldBalanceRules.keepSpawn(session.state().active(), session.isNight(),
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL,
                event.getEntity() instanceof Monster, spawns.naturalHostileNightRetention(), random.nextDouble());
        if (!keep) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void gravelDrop(BlockDropItemEvent event) {
        if (!session.state().active() || event.getBlockState().getType() != Material.GRAVEL) return;
        var tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getEnchantmentLevel(silkTouch) > 0) return;
        int fortuneLevel = tool.getEnchantmentLevel(fortune);
        Material result = random.nextDouble() < WorldBalanceRules.flintChance(drops.flintBaseChance(), fortuneLevel)
                ? Material.FLINT : Material.GRAVEL;
        event.getItems().stream().filter(item -> item.getItemStack().getType() == Material.FLINT
                        || item.getItemStack().getType() == Material.GRAVEL)
                .findFirst().ifPresent(item -> item.setItemStack(item.getItemStack().withType(result)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void creeperDeath(EntityDeathEvent event) {
        if (!session.state().active() || !(event.getEntity() instanceof Creeper)) return;
        event.getDrops().stream().filter(item -> item.getType() == Material.GUNPOWDER).forEach(item ->
                item.setAmount(WorldBalanceRules.multiplyDrop(item.getAmount(), drops.creeperGunpowderMultiplier())));
    }

    private static Enchantment enchantment(String key) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .getOrThrow(NamespacedKey.minecraft(key));
    }
}
