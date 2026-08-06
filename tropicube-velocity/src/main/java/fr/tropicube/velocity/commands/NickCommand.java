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

/** Active ou retire l'identité anonymisée d'un joueur autorisé. */
public class NickCommand implements SimpleCommand {

    private final NickManager             nickManager;
    private final VelocityLanguageManager lm;

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

        if (!nickManager.canUseNick(uuid)) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-no-permission"));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length > 0 && args[0].equalsIgnoreCase("off")) {
            handleNickOff(player);
            return;
        }

        handleNickOn(player);
    }

    /** Génère et applique une identité sans déconnecter le joueur. */
    private void handleNickOn(Player player) {
        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-fetching"));
        String nickName = nickManager.generateRandomName();

        nickManager.fetchRandomSkin().thenAccept(skinOpt -> {
            if (!player.isActive()) return;

            if (skinOpt.isEmpty()) {
                player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-skin-error"));
                return;
            }

            NickManager.SkinData skin = skinOpt.get();
            nickManager.storeNick(player.getUniqueId(), nickName, skin);

            // Met aussi à jour la session Velocity afin de conserver le skin lors des transferts.
            // Le canal Redis côté Paper reste le mécanisme de repli si l'API interne est inaccessible.
            List<GameProfile.Property> props = new ArrayList<>();
            props.add(new GameProfile.Property("textures", skin.value(), skin.signature()));
            nickManager.tryUpdateSessionProfile(player,
                new GameProfile(player.getUniqueId(), nickName, props));

            // Demande à chaque backend d'actualiser le skin.
            nickManager.publishNickApply(player.getUniqueId());

            player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.nick-applied", nickName));
        });
    }

    /** Restaure l'identité originale sans déconnecter le joueur. */
    private void handleNickOff(Player player) {
        UUID uuid = player.getUniqueId();

        if (nickManager.getNick(uuid).isEmpty()) {
            player.sendMessage(lm.getComponent(uuid, "proxy.nick-not-nicked"));
            return;
        }

        nickManager.clearNick(uuid);

        // Restaure le profil de session Velocity capturé à la connexion.
        nickManager.getOriginalProfile(uuid).ifPresent(orig -> {
            List<GameProfile.Property> props = new ArrayList<>();
            props.add(new GameProfile.Property("textures", orig.skin().value(), orig.skin().signature()));
            nickManager.tryUpdateSessionProfile(player,
                new GameProfile(uuid, orig.name(), props));
        });

        // Demande aux backends de restaurer le skin et purge l'état Redis associé.
        nickManager.publishNickClear(uuid);

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
