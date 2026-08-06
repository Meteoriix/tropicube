package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import fr.tropicube.velocity.managers.NickManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Applique les informations de pseudonyme et de skin lors des connexions au proxy. */
public class NickListener {

    private final NickManager nickManager;
    private final Logger      logger;

    public NickListener(NickManager nickManager, Logger logger) {
        this.nickManager = nickManager;
        this.logger      = logger;
    }

    @Subscribe
    public void onGameProfile(GameProfileRequestEvent event) {
        GameProfile original = event.getOriginalProfile();
        UUID        uuid     = original.getId();

        // Conserve le profil Mojang réel pour permettre une restauration transparente.
        for (GameProfile.Property p : original.getProperties()) {
            if ("textures".equals(p.getName())) {
                nickManager.storeOriginalProfile(uuid, original.getName(), p);
                break;
            }
        }

        // Applique l'identité active lors de la connexion initiale ou d'un retour rapide.
        nickManager.getNick(uuid).ifPresent(nickData -> {
            List<GameProfile.Property> props = new ArrayList<>();
            for (GameProfile.Property p : original.getProperties()) {
                if (!"textures".equals(p.getName())) props.add(p);
            }
            props.add(new GameProfile.Property(
                "textures",
                nickData.skin().value(),
                nickData.skin().signature()
            ));
            event.setGameProfile(new GameProfile(uuid, nickData.nickName(), props));
            // Restaure la durée de vie complète après une reconnexion rapide.
            nickManager.refreshNickTtl(uuid);
            logger.debug("[Nick] Applied nick '{}' at login for '{}'", nickData.nickName(), original.getName());
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (nickManager.getNick(uuid).isPresent()) {
            // Conserve l'identité 30 secondes pour absorber une reconnexion rapide.
            // Les backends nettoient l'affichage à la déconnexion et le réappliquent au retour.
            nickManager.parkNick(uuid);
            nickManager.publishNickReset(uuid);
        } else {
            nickManager.clearNick(uuid);
            nickManager.clearOriginalProfile(uuid);
        }
    }
}
