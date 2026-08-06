package fr.tropicube.core.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tropicube.core.TropicubeCore;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Applique ou retire un nick skin côté backend (Paper) sans déconnecter le joueur.
 * <p>
 * Événements Redis écoutés :
 *   NICK_APPLY:{uuid}  — applique le nick stocké dans nick:{uuid}
 *   NICK_RESET:{uuid}  — restaure le skin original stocké dans nick:original:{uuid}
 */
public class NickApplyManager {

    private final TropicubeCore plugin;

    public NickApplyManager(TropicubeCore plugin) {
        this.plugin = plugin;
        plugin.getRedisManager().subscribeToPlayerEvents(msg -> {
            if (msg.startsWith("NICK_APPLY:")) {
                handleEvent(msg.substring("NICK_APPLY:".length()), true);
            } else if (msg.startsWith("NICK_RESET:")) {
                handleEvent(msg.substring("NICK_RESET:".length()), false);
            } else if (msg.startsWith("NICK_CLEAR:")) {
                handleClear(msg.substring("NICK_CLEAR:".length()));
            }
        });
    }

    private void handleEvent(String uuidStr, boolean apply) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                if (apply) {
                    applyNick(uuid);
                    return;
                }
                resetNick(uuid);
                });
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Apply nick skin ──────────────────────────────────────────

    private void applyNick(UUID uuid) {
        Player target = plugin.getServer().getPlayer(uuid);
        if (target == null) return;

        String raw = plugin.getRedisManager().get("nick:" + uuid);
        if (raw == null) return;

        try {
            JsonObject obj      = JsonParser.parseString(raw).getAsJsonObject();
            String     nickName = obj.get("n").getAsString();
            String     skinVal  = obj.get("v").getAsString();
            String     skinSig  = obj.has("s") ? obj.get("s").getAsString() : "";

            swapSkin(target, nickName, skinVal, skinSig);
        } catch (Exception e) {
            plugin.getLogger().warning("[Nick] Failed to apply nick for " + uuid + ": " + e.getMessage());
        }
    }

    // /nick off : restaure le skin puis purge l'état Redis de l'identité.

    private void handleClear(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                resetNick(uuid);
                plugin.getRedisManager().delete("nick:" + uuid);
                plugin.getRedisManager().delete("nick:original:" + uuid);
            });
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Restore original skin ────────────────────────────────────

    private void resetNick(UUID uuid) {
        Player target = plugin.getServer().getPlayer(uuid);
        if (target == null) return;

        String raw = plugin.getRedisManager().get("nick:original:" + uuid);
        if (raw == null) return;

        try {
            JsonObject obj      = JsonParser.parseString(raw).getAsJsonObject();
            String     origName = obj.get("n").getAsString();
            String     skinVal  = obj.get("v").getAsString();
            String     skinSig  = obj.has("s") ? obj.get("s").getAsString() : "";

            swapSkin(target, origName, skinVal, skinSig);
        } catch (Exception e) {
            plugin.getLogger().warning("[Nick] Failed to reset nick for " + uuid + ": " + e.getMessage());
        }
    }

    // ── Shared skin-swap helper ──────────────────────────────────

    private void swapSkin(Player target, String displayName, String skinValue, String skinSig) {
        PlayerProfile profile = target.getServer().createProfile(target.getUniqueId(), displayName);
        profile.setProperty(new ProfileProperty("textures", skinValue, skinSig));
        target.setPlayerProfile(profile);

        // Actualise le nom Adventure du chat et de la liste des joueurs.
        Component nameComponent = Component.text(displayName);
        target.displayName(nameComponent);
        target.playerListName(nameComponent);

        // Force les observateurs à recharger l'entité afin d'afficher le nouveau skin.
        for (Player observer : plugin.getServer().getOnlinePlayers()) {
            if (!observer.equals(target)) {
                observer.hidePlayer(plugin, target);
                observer.showPlayer(plugin, target);
            }
        }
    }
}
