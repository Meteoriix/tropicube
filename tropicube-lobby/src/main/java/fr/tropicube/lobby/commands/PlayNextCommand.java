package fr.tropicube.lobby.commands;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Reconnecte un joueur à une partie après la fin de la précédente.
 *
 * Un mini-jeu déclenche ce flux en stockant la clé Redis générique
 * {@code post-game:<uuid> = <nextServer>|<serverType>} avant de renvoyer le
 * joueur au lobby. Le lobby consomme cette valeur, affiche un lien cliquable,
 * puis cette commande résout la connexion.
 *
 * Ordre de résolution :
 * <ol>
 *   <li>instance précréée si elle est joignable ;</li>
 *   <li>meilleure instance disponible du même type ;</li>
 *   <li>demande de création depuis le template correspondant.</li>
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

        // Priorité à l'instance précréée annoncée par le mini-jeu.
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

        // Sinon, choisit la meilleure instance du type ou en demande une nouvelle.
        if (serverType.isEmpty()) {
            player.sendMessage(LangHelper.component(player, "lobby.no-server"));
            return true;
        }
        final String type = serverType;
        plugin.getLobbyServerManager().getBestServer(type).ifPresentOrElse(
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
