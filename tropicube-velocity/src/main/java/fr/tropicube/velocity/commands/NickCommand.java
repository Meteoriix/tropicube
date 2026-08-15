package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import fr.tropicube.velocity.managers.NickManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Enables or removes the anonymized identity of an authorized player. */
public class NickCommand implements SimpleCommand {

    private final NickManager             nickManager;
    private final VelocityLanguageManager lm;
    private final NickRequestRegistry requests = new NickRequestRegistry();

    enum NickAction { ENABLE, DISABLE, INVALID }

    public NickCommand(NickManager nickManager, VelocityLanguageManager lm) {
        this.nickManager = nickManager;
        this.lm          = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(lm.getComponent(invocation.source(), "general.player-only"));
            return;
        }

        UUID uuid = player.getUniqueId();
        NickAction action = parseAction(invocation.arguments());

        if (action == NickAction.INVALID) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-usage"));
            return;
        }

        // Deactivation is always possible so as to never lock
        // a player in an active identity after a rank change.
        if (action == NickAction.DISABLE) {
            handleNickOff(player);
            return;
        }

        if (!nickManager.canUseNick(uuid)) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-no-permission"));
            return;
        }

        handleNickOn(player);
    }

    static NickAction parseAction(String[] arguments) {
        if (arguments.length == 0) return NickAction.ENABLE;
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("off")) return NickAction.DISABLE;
        return NickAction.INVALID;
    }

    /** Generates and applies an identity without logging out the player. */
    private void handleNickOn(Player player) {
        UUID uuid = player.getUniqueId();
        Object requestToken = requests.begin(uuid);
        if (requestToken == null) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-already-fetching"));
            return;
        }
        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-fetching"));
        String nickName = nickManager.generateRandomName();

        nickManager.fetchRandomSkin().whenComplete((skinOpt, error) -> {
            // /nick off or a more recent request invalidates this callback.
            if (!requests.complete(uuid, requestToken)) return;
            if (!player.isActive()) return;

            if (error != null || skinOpt == null || skinOpt.isEmpty()) {
                player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-skin-error"));
                return;
            }

            NickManager.SkinData skin = skinOpt.get();
            nickManager.storeNick(player.getUniqueId(), nickName, skin);

            // Also updates the Velocity session to preserve the skin during transfers.
            // The Redis channel on the Paper side remains the fallback mechanism if the internal API is inaccessible.
            List<GameProfile.Property> props = new ArrayList<>();
            props.add(new GameProfile.Property("textures", skin.value(), skin.signature()));
            nickManager.tryUpdateSessionProfile(player,
                new GameProfile(player.getUniqueId(), nickName, props));

            // Ask each backend to refresh the skin.
            nickManager.publishNickApply(player.getUniqueId());

            player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-applied", nickName));
        });
    }

    /** Restores original identity without disconnecting the player. */
    private void handleNickOff(Player player) {
        UUID uuid = player.getUniqueId();
        requests.cancel(uuid);

        if (!nickManager.hasRecoverableNickState(uuid)) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-not-nicked"));
            return;
        }

        // Restores the captured Velocity session profile at login.
        nickManager.getOriginalProfile(uuid).ifPresent(orig -> {
            List<GameProfile.Property> props = new ArrayList<>();
            props.add(new GameProfile.Property("textures", orig.skin().value(), orig.skin().signature()));
            nickManager.tryUpdateSessionProfile(player,
                new GameProfile(uuid, orig.name(), props));
        });

        // The owning backend acknowledges restoration by purging the Redis state.
        // Keeping it until then makes this request safe to retry if Pub/Sub delivery is lost.
        nickManager.requestNickClear(uuid);

        player.sendMessage(lm.getComponent(uuid, "proxy.nick-removed"));
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String partial = args.length == 1 ? args[0] : "";
            if ("off".startsWith(partial.toLowerCase()))
                return CompletableFuture.completedFuture(List.of("off"));
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
