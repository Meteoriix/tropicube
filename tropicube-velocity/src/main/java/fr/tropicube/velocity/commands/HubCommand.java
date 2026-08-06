package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

/** Transfère un joueur vers le lobby disponible le moins chargé. */
public class HubCommand implements SimpleCommand {
    private final TropiServerManager manager;
    private final VelocityLanguageManager lm;

    public HubCommand(TropiServerManager manager, VelocityLanguageManager lm) {
        this.manager = manager;
        this.lm = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(lm.getComponent(invocation.source(), "general.player-only"));
            return;
        }
        manager.getBestLobby().ifPresentOrElse(
                lobby -> {
                    player.createConnectionRequest(lobby).connect().whenComplete((result, error) -> {
                        if (error != null || result == null || !result.isSuccessful()) {
                            player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.transfer-failed"));
                            return;
                        }
                        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.hub-transfer",
                                lobby.getServerInfo().getName()));
                    });
                },
                () -> player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.hub-none"))
        );
    }
}
