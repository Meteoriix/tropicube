package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

// ============================================================
//  /find <joueur> — Trouver sur quel serveur est un joueur
// ============================================================
/** Indique sur quelle instance se trouve un joueur connecté au proxy. */
public class FindCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final RedisManager redisManager;
    private final VelocityLanguageManager lm;

    public FindCommand(ProxyServer proxy, RedisManager redisManager, VelocityLanguageManager lm) {
        this.proxy = proxy;
        this.redisManager = redisManager;
        this.lm = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("tropicube.admin.find")) {
            source.sendMessage(lm.getComponent(source, "general.no-permission"));
            return;
        }
        if (args.length < 1) {
            source.sendMessage(lm.getComponent(source, "proxy.find-usage"));
            return;
        }

        proxy.getPlayer(args[0]).ifPresentOrElse(
                player -> {
                    String serverId = redisManager.getPlayerServer(player.getUniqueId().toString());
                    String serverName = player.getCurrentServer()
                            .map(s -> s.getServer().getServerInfo().getName())
                            .orElse("?");
                    String idSuffix = serverId != null
                            ? lm.get(source, "proxy.find-id", serverId.substring(0, Math.min(8, serverId.length())))
                            : "";
                    source.sendMessage(lm.getComponent(source, "proxy.find-result",
                            player.getUsername(), serverName + idSuffix));
                },
                () -> source.sendMessage(lm.getComponent(source, "proxy.find-not-online", args[0]))
        );
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tropicube.admin.find");
    }
}
