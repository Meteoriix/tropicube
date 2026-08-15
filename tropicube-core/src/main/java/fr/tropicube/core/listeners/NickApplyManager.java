package fr.tropicube.core.listeners;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.docker.model.NickIdentity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Apply or remove a nick skin on the backend side (Paper) without disconnecting the player.
 * <p>
 * Redis events listened to:
 * NICK_APPLY:{uuid} — applies the nick stored in nick:{uuid}
 * NICK_RESET:{uuid} — restores the original skin stored in nick:original:{uuid}
 * NICK_CLEAR:{uuid} — restores the profile then purges both nick keys
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
            if (apply) {
                String raw = plugin.getRedisManager().get(NickIdentity.key(uuid));
                NickIdentity.fromJson(raw).ifPresentOrElse(
                        identity -> plugin.getServer().getScheduler().runTask(plugin,
                                () -> applyNick(uuid, identity)),
                        () -> {
                            if (raw != null) plugin.getLogger().warning("[Nick] Invalid identity payload for " + uuid);
                        });
                return;
            }
            String originalProfile = plugin.getRedisManager().get("nick:original:" + uuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> resetNick(uuid, originalProfile));
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Apply nick skin ──────────────────────────────────────────

    private void applyNick(UUID uuid, NickIdentity identity) {
        Player target = plugin.getServer().getPlayer(uuid);
        if (target == null) return;
        plugin.getPermissionManager().setDisplayGradeOverride(uuid, identity.displayGrade());
        swapSkin(target, identity.name(), identity.skinValue(), identity.skinSignature());
    }

    // /nick off: restores the skin then purges the Redis state of the identity.

    private void handleClear(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // All backends receive the event. Only the one who
                // owns the player can restore his profile and purge Redis.
                if (plugin.getServer().getPlayer(uuid) == null) return;
                String originalProfile = plugin.getRedisManager().get("nick:original:" + uuid);
                resetNick(uuid, originalProfile);
                plugin.getRedisManager().delete("nick:" + uuid);
                plugin.getRedisManager().delete("nick:original:" + uuid);
            });
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Restore original skin ────────────────────────────────────

    private void resetNick(UUID uuid, String raw) {
        Player target = plugin.getServer().getPlayer(uuid);
        if (target == null) return;
        plugin.getPermissionManager().clearDisplayGradeOverride(uuid);
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

        // Updates the Adventure name of the chat and player list.
        Component nameComponent = Component.text(displayName);
        target.displayName(nameComponent);
        target.playerListName(plugin.getPermissionManager()
                .getCachedDisplayFormattedName(target.getUniqueId(), displayName)
                .map(MiniMessage.miniMessage()::deserialize)
                .orElse(nameComponent));

        // Forces observers to reload the entity in order to display the new skin.
        for (Player observer : plugin.getServer().getOnlinePlayers()) {
            if (!observer.equals(target)) {
                observer.hidePlayer(plugin, target);
                observer.showPlayer(plugin, target);
            }
        }
    }
}
