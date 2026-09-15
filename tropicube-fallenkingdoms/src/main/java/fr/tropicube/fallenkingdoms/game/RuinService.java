package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.config.FallenKingdomsSettings;
import fr.tropicube.fallenkingdoms.map.BaseDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Selects a bounded ruin set once, then applies it in registered visual waves. */
public final class RuinService {
    private final TropicubeFallenKingdoms plugin;
    private final TaskRegistry tasks;
    private final FallenKingdomsSettings settings;
    public RuinService(TropicubeFallenKingdoms plugin, TaskRegistry tasks, FallenKingdomsSettings settings) {
        this.plugin = plugin; this.tasks = tasks; this.settings = settings;
    }
    public void ruin(World world, BaseDefinition base, long seed, Runnable completed) {
        var center = base.heart();
        int radius = (int) Math.ceil(settings.ruinRadius());
        List<Block> candidates = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++) {
            if (x * x + y * y + z * z > settings.ruinRadius() * settings.ruinRadius()) continue;
            Block block = world.getBlockAt((int) Math.floor(center.x()) + x, (int) Math.floor(center.y()) + y,
                    (int) Math.floor(center.z()) + z);
            if (base.region().contains(block.getLocation()) && destructible(block)) candidates.add(block);
        }
        Collections.shuffle(candidates, new Random(seed));
        int selectedCount = Math.min(candidates.size(), (int) Math.floor(candidates.size() * settings.ruinDestructionRatio()));
        List<Block> selected = List.copyOf(candidates.subList(0, selectedCount));
        for (int wave = 0; wave < settings.ruinWaves(); wave++) {
            int currentWave = wave;
            tasks.register(Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyWave(world, selected, currentWave);
                if (currentWave == settings.ruinWaves() - 1) completed.run();
            },
                    (long) wave * settings.ruinTicksBetweenWaves()));
        }
    }
    private void applyWave(World world, List<Block> selected, int wave) {
        for (int index = wave; index < selected.size(); index += settings.ruinWaves()) {
            Block block = selected.get(index);
            if (!destructible(block)) continue;
            world.spawnParticle(Particle.EXPLOSION, block.getLocation().add(.5, .5, .5), 1);
            block.setType(Material.AIR, false);
        }
        if (!selected.isEmpty()) world.playSound(selected.getFirst().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1F, .8F);
    }
    private boolean destructible(Block block) {
        if (block.getType().isAir() || block.getType() == Material.BEDROCK || block.getType() == Material.BARRIER
                || block.getType() == Material.END_PORTAL_FRAME) return false;
        return !settings.preserveContainers() || !(block.getState() instanceof Container);
    }
}
