package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Reconnects a player to a game after the previous one has ended.
 *
 * A mini-game triggers this flow by storing the generic Redis key
 * {@code post-game:<uuid> = <nextServer>|<serverType>} before returning the
 * player in the lobby. The lobby consumes this value, displays a clickable link,
 * then this command resolves the connection.
 *
 * Order of resolution:
 * <ol>
 * <li>pre-created instance if it can be reached;</li>
 * <li>best available instance of the same type;</li>
 * <li>creation request from the corresponding template.</li>
 * </ol>
 */
public class PlayNextCommand implements CommandExecutor {

    private final TropicubeLobby plugin;

    public PlayNextCommand(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        String stored = plugin.getPlayerLobbyListener().removePostGameTarget(player.getUniqueId());

        String nextServer = "";
        String serverType = "";

        if (stored != null) {
            int sep = stored.lastIndexOf('|');
            if (sep >= 0) {
                nextServer = stored.substring(0, sep);
                serverType = stored.substring(sep + 1);
            } else {
                nextServer = stored;
            }
        }

        // Priority to the precreated instance announced by the mini-game.
        if (!nextServer.isEmpty()) {
            boolean isJoinable = plugin.getLobbyServerManager().getServer(nextServer)
                    .filter(server -> server.isMatchmakingJoinable())
                    .isPresent();
            if (isJoinable) {
                player.sendMessage(LangHelper.component(player, "lobby.connect", nextServer));
                plugin.getLobbyServerManager().connectToServer(player, nextServer);
                return true;
            }
        }

        // Otherwise, choose the best instance of the type or request a new one.
        if (serverType.isEmpty()) {
            player.sendMessage(LangHelper.component(player, "lobby.no-server"));
            return true;
        }
        final String type = serverType;
        plugin.getLobbyServerManager().getBestServer(type, player.getUniqueId()).ifPresentOrElse(
                s -> {
                    player.sendMessage(LangHelper.component(player, "lobby.connect", s.id()));
                    plugin.getLobbyServerManager().connectToServer(player, s.id());
                },
                () -> {
                    player.sendMessage(LangHelper.component(player, "lobby.game-queued"));
                    plugin.getLobbyServerManager().requestStartGame(player, type);
                }
        );
        return true;
    }
}
