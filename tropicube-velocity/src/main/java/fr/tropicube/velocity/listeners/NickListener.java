package fr.tropicube.velocity.listeners;

import fr.tropicube.velocity.util.MessageStyle;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.util.GameProfile;
import fr.tropicube.velocity.managers.NickManager;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Applies nickname and skin information during proxy connections. */
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

        // Retains the actual Mojang profile to enable seamless recovery.
        for (GameProfile.Property p : original.getProperties()) {
            if ("textures".equals(p.getName())) {
                nickManager.storeOriginalProfile(uuid, original.getName(), p);
                break;
            }
        }

        // Applies the active identity during initial login or quick return.
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
            // Restores full life after quick reconnection.
            nickManager.refreshNickTtl(uuid);
            logger.debug(MessageStyle.log("NICK", "<dark_gray>" + "Applied nick '{}' at login for '{}'"), nickData.nickName(), original.getName());
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (nickManager.getNick(uuid).isPresent()) {
            // Retains the complete identity for its normal lifetime.
            // The backends clean their local display cache and reapply it on return.
            nickManager.parkNick(uuid);
            nickManager.publishNickReset(uuid);
        } else {
            nickManager.clearNick(uuid);
            nickManager.clearOriginalProfile(uuid);
        }
    }
}
