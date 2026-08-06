package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;
import net.kyori.adventure.text.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Commande /tropi - Panneau d'administration des serveurs Docker.
 */
public class TropiAdminCommand implements SimpleCommand {

    private final TropiServerManager manager;
    private final VelocityLanguageManager lm;

    private static final String PERMISSION = "tropicube.admin";

    public TropiAdminCommand(TropiServerManager manager, VelocityLanguageManager lm) {
        this.manager = manager;
        this.lm = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(lm.getComponent(source, "general.no-permission"));
            return;
        }

        if (args.length == 0) {
            sendHelp(source);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> handleList(source);
            case "start" -> handleStart(source, args);
            case "stop" -> handleStop(source, args);
            case "kill" -> handleKill(source, args);
            case "info" -> handleInfo(source, args);
            case "templates" -> handleTemplates(source);
            case "maintenance" -> handleMaintenance(source, args);
            case "reload" -> handleReload(source);
            default -> sendHelp(source);
        }
    }

    private void handleList(CommandSource source) {
        Map<String, ServerInstance> instances = manager.getActiveInstances();
        source.sendMessage(lm.getComponent(source, "proxy.admin-header"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-list-count", instances.size()));
        source.sendMessage(Component.text(""));

        if (instances.isEmpty()) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-list-empty"));
        } else {
            instances.values().forEach(inst -> {
                String statusColor = switch (inst.getStatus()) {
                    case GAME_WAITING, GAME_STARTING, GAME_PLAYING -> "<color:green>";
                    case STARTING -> "<color:yellow>";
                    case STOPPING -> "<color:gold>";
                    case ERROR -> "<color:red>";
                    default -> "<color:gray>";
                };
                source.sendMessage(lm.getComponent(source, "proxy.admin-list-entry",
                        inst.getServerType(),
                        statusColor + inst.getServerName() + "</color>",
                        inst.getOnlinePlayers(), inst.getMaxPlayers(),
                        inst.getPort(),
                        inst.getInstanceId().substring(0, 8)));
            });
        }
        source.sendMessage(lm.getComponent(source, "proxy.admin-footer"));
    }

    private void handleStart(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-start-usage"));
            return;
        }
        String templateId = args[1];
        String customName = args.length >= 3 ? args[2] : null;

        if (!manager.getTemplates().containsKey(templateId)) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-start-not-found", templateId));
            source.sendMessage(lm.getComponent(source, "proxy.admin-start-available",
                    String.join(", ", manager.getTemplates().keySet())));
            return;
        }

        source.sendMessage(lm.getComponent(source, "proxy.admin-starting", templateId));
        manager.createServer(templateId, customName, false)
                .thenAccept(instance -> source.sendMessage(lm.getComponent(source, "proxy.admin-started",
                        instance.getServerName(), instance.getPort(),
                        instance.getInstanceId().substring(0, 8))))
                .exceptionally(e -> {
                    source.sendMessage(lm.getComponent(source, "proxy.admin-start-error",
                            errorMessage(e)));
                    return null;
                });
    }

    private void handleStop(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-stop-usage"));
            return;
        }
        resolveInstance(args[1]).ifPresentOrElse(instance -> {
            source.sendMessage(lm.getComponent(source, "proxy.admin-stopping", instance.getServerName()));
            manager.stopServer(instance.getInstanceId())
                    .thenAccept(ok -> source.sendMessage(lm.getComponent(
                            source, ok ? "proxy.admin-stopped" : "proxy.admin-stop-failed")))
                    .exceptionally(_ -> {
                        source.sendMessage(lm.getComponent(source, "proxy.admin-stop-failed"));
                        return null;
                    });
        }, () -> source.sendMessage(lm.getComponent(source, "proxy.admin-instance-not-found", args[1])));
    }

    private void handleKill(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-kill-usage"));
            return;
        }
        resolveInstance(args[1]).ifPresentOrElse(instance -> {
            source.sendMessage(lm.getComponent(source, "proxy.admin-killing", instance.getServerName()));
            String serverName = instance.getServerName();
            manager.killServer(instance.getInstanceId())
                    .thenAccept(ok -> source.sendMessage(lm.getComponent(
                            source, ok ? "proxy.admin-killed" : "proxy.admin-kill-failed", serverName)))
                    .exceptionally(_ -> {
                        source.sendMessage(lm.getComponent(source, "proxy.admin-kill-failed", serverName));
                        return null;
                    });
        }, () -> source.sendMessage(lm.getComponent(source, "proxy.admin-instance-not-found", args[1])));
    }

    private void handleInfo(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-usage"));
            return;
        }
        resolveInstance(args[1]).ifPresentOrElse(i -> {
            source.sendMessage(lm.getComponent(source, "proxy.admin-header"));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-name", i.getServerName()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-id", i.getInstanceId()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-container", i.getContainerName()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-type", i.getServerType()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-template", i.getTemplateId()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-status", i.getStatus()));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-address",
                    i.getHost() != null ? i.getHost() : "127.0.0.1", i.getPort()));
            String rconVal = i.getRconPort() != 0
                    ? (i.getHost() != null ? i.getHost() : "127.0.0.1") + ":" + i.getRconPort()
                    : lm.get(source, "proxy.admin-info-rcon-disabled");
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-rcon", rconVal));
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-players",
                    i.getOnlinePlayers(), i.getMaxPlayers()));
            String wlVal = i.isWhitelisted()
                    ? lm.get(source, "proxy.admin-info-whitelist-yes")
                    : lm.get(source, "proxy.admin-info-whitelist-no");
            source.sendMessage(lm.getComponent(source, "proxy.admin-info-whitelist", wlVal));
            source.sendMessage(lm.getComponent(source, "proxy.admin-footer"));
        }, () -> source.sendMessage(lm.getComponent(source, "proxy.admin-instance-not-found", args[1])));
    }

    private void handleTemplates(CommandSource source) {
        source.sendMessage(lm.getComponent(source, "proxy.admin-header"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-templates-count",
                manager.getTemplates().size()));
        manager.getTemplates().values().forEach(t -> {
            String maintenance = t.isMaintenanceMode() ? lm.get(source, "proxy.admin-template-maintenance") : "";
            source.sendMessage(lm.getComponent(source, "proxy.admin-template-entry",
                    t.getId(), t.getName(), t.getServerType(), t.getDockerImage(),
                    t.getMinRam(), t.getMaxRam(), maintenance));
        });
        source.sendMessage(lm.getComponent(source, "proxy.admin-footer"));
    }

    private void handleMaintenance(CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-maintenance-usage"));
            return;
        }
        var template = manager.getTemplates().get(args[1]);
        if (template == null) {
            source.sendMessage(lm.getComponent(source, "proxy.admin-start-not-found", args[1]));
            return;
        }
        boolean state = args[2].equalsIgnoreCase("on");
        template.setMaintenanceMode(state);
        source.sendMessage(lm.getComponent(source,
                state ? "proxy.admin-maintenance-on" : "proxy.admin-maintenance-off", args[1]));
    }

    private void handleReload(CommandSource source) {
        source.sendMessage(lm.getComponent(source, "proxy.admin-reload-start"));
        lm.reload();
        source.sendMessage(lm.getComponent(source, "proxy.admin-reload-done"));
    }

    private Optional<ServerInstance> resolveInstance(String nameOrId) {
        Optional<ServerInstance> byName = manager.getInstanceByName(nameOrId);
        if (byName.isPresent()) return byName;
        return manager.getActiveInstances().values().stream()
                .filter(i -> i.getInstanceId().startsWith(nameOrId))
                .findFirst();
    }

    private static String errorMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(lm.getComponent(source, "proxy.admin-header"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-title"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-list"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-start"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-stop"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-kill"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-info"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-templates"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-maintenance"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-help-reload"));
        source.sendMessage(lm.getComponent(source, "proxy.admin-footer"));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            return Arrays.asList("list", "start", "stop", "kill", "info", "templates", "maintenance", "reload");
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "start", "maintenance" -> new ArrayList<>(manager.getTemplates().keySet());
                case "stop", "kill", "info" -> manager.getActiveInstances().values().stream()
                        .map(ServerInstance::getServerName).collect(Collectors.toList());
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("maintenance")) {
            return Arrays.asList("on", "off");
        }
        return Collections.emptyList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
