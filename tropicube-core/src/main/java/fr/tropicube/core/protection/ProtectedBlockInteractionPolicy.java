package fr.tropicube.core.protection;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Allows a trusted Paper game plugin to opt a player into an interaction that
 * is blocked by the network-wide backend protection.
 *
 * <p>Implementations must make their decision synchronously without database,
 * Redis, disk or network access because the policy runs on the Paper thread.</p>
 */
@FunctionalInterface
public interface ProtectedBlockInteractionPolicy {
    /** Returns whether the player may interact with the protected block now. */
    boolean allows(Player player, Block block);
}
