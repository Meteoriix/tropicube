package fr.tropicube.sheepwars.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

/** Resolves the identity last applied by Core for player-facing SheepWars text. */
public final class PlayerDisplayName {

    private PlayerDisplayName() {}

    /**
     * Uses Adventure's display name because Core updates it immediately for
     * both {@code /nick} and {@code /nick off}, unlike the connection name.
     */
    public static String resolve(Player player) {
        return resolve(player.displayName(), player.getName());
    }

    static String resolve(Component displayName, String fallbackName) {
        String visibleName = PlainTextComponentSerializer.plainText().serialize(displayName);
        return visibleName.isBlank() ? fallbackName : visibleName;
    }
}
