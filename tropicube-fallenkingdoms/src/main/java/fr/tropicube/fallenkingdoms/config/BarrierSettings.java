package fr.tropicube.fallenkingdoms.config;

/** Bounded particle rendering settings for enemy base walls. */
public record BarrierSettings(int renderIntervalTicks, int viewDistanceBlocks,
                              double particleSpacingBlocks, int verticalRadiusBlocks) {
    public BarrierSettings {
        if (renderIntervalTicks < 1 || renderIntervalTicks > 20
                || viewDistanceBlocks < 8 || viewDistanceBlocks > 64
                || !Double.isFinite(particleSpacingBlocks) || particleSpacingBlocks < 1 || particleSpacingBlocks > 4
                || verticalRadiusBlocks < 2 || verticalRadiusBlocks > 32)
            throw new IllegalArgumentException("protections.enemy-base-barrier: paramètres de rendu hors limites");
    }
}
