package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.ArrayList;
import java.util.List;

/** Implements the administrative command {@code /send <player|*> <server>}. */
public class SendCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final TropiServerManager manager;
    private final VelocityLanguageManager lm;

    public SendCommand(ProxyServer proxy, TropiServerManager manager, VelocityLanguageManager lm) {
        this.proxy = proxy;
        this.manager = manager;
        this.lm = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission("tropicube.admin.send")) {
            source.sendMessage(lm.getComponent(source, "general.no-permission"));
            return;
        }
        if (args.length < 2) {
            source.sendMessage(lm.getComponent(source, "proxy.send-usage"));
            return;
        }

        String targetName = args[0];
        String serverName = args[1];

        manager.getInstanceByName(serverName).ifPresentOrElse(_ -> proxy.getServer(serverName).ifPresentOrElse(srv -> {
            if (targetName.equals("*")) {
                proxy.getAllPlayers().forEach(p -> p.createConnectionRequest(srv).connect()
                        .whenComplete((result, error) -> {
                            if (error != null || result == null || !result.isSuccessful()) {
                                p.sendMessage(lm.getComponent(p.getUniqueId(), "proxy.transfer-failed"));
                            }
                        }));
                source.sendMessage(lm.getComponent(source, "proxy.send-all", serverName));
            } else {
                proxy.getPlayer(targetName).ifPresentOrElse(
                        p -> {
                            p.createConnectionRequest(srv).connect().whenComplete((result, error) -> {
                                if (error != null || result == null || !result.isSuccessful()) {
                                    source.sendMessage(lm.getComponent(source, "proxy.transfer-failed"));
                                    return;
                                }
                                source.sendMessage(lm.getComponent(source, "proxy.send-player", targetName, serverName));
                                p.sendMessage(lm.getComponent(p.getUniqueId(), "proxy.send-received", serverName));
                            });
                        },
                        () -> source.sendMessage(lm.getComponent(source, "proxy.send-player-not-found", targetName))
                );
            }
        }, () -> source.sendMessage(lm.getComponent(source, "proxy.send-server-not-found", serverName))),
                () -> source.sendMessage(lm.getComponent(source, "proxy.send-server-not-found", serverName)));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tropicube.admin.send");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!hasPermission(invocation)) return List.of();
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0];
            List<String> players = new ArrayList<>();
            players.add("*");
            proxy.getAllPlayers().forEach(player -> players.add(player.getUsername()));
            return matching(players, prefix);
        }
        if (args.length == 2) {
            return matching(manager.getActiveInstances().values().stream()
                    .filter(instance -> instance.isOnline() && proxy.getServer(instance.getServerName()).isPresent())
                    .map(instance -> instance.getServerName()).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(List<String> values, String prefix) {
        return values.stream().filter(value -> value.regionMatches(true, 0, prefix, 0, prefix.length()))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
