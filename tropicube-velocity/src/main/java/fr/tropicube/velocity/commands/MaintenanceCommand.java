package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import fr.tropicube.velocity.managers.MaintenanceManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.util.MessageStyle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Controls network or game-type maintenance drains. */
public final class MaintenanceCommand implements SimpleCommand {
    private final MaintenanceManager maintenance;
    private final TropiServerManager servers;
    private final int defaultDeadlineMinutes;

    public MaintenanceCommand(MaintenanceManager maintenance, TropiServerManager servers, int defaultDeadlineMinutes) {
        this.maintenance = maintenance;
        this.servers = servers;
        if (defaultDeadlineMinutes < 1 || defaultDeadlineMinutes > 1_440) {
            throw new IllegalArgumentException("defaultDeadlineMinutes doit être compris entre 1 et 1440");
        }
        this.defaultDeadlineMinutes = defaultDeadlineMinutes;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length < 2) {
            invocation.source().sendMessage(MessageStyle.component(
                    "<tc><yellow>Usage: /maintenance <network|type> <on|off|status> [minutes] [motif]"));
            return;
        }
        String scope = args[0];
        switch (args[1].toLowerCase()) {
            case "on" -> enable(invocation, scope, args);
            case "off" -> {
                maintenance.disable(scope);
                invocation.source().sendMessage(MessageStyle.component("<tc><green>Maintenance désactivée pour <white>" + scope));
            }
            case "status" -> invocation.source().sendMessage(maintenance.state(scope)
                    .map(state -> MessageStyle.component("<tc><yellow>Maintenance active jusqu'à <white>" + state.deadlineAt()
                            + "</white> : " + state.reason()))
                    .orElseGet(() -> MessageStyle.component("<tc><green>Aucune maintenance pour <white>" + scope)));
            default -> invocation.source().sendMessage(MessageStyle.component("<tc><red>Action attendue: on, off ou status."));
        }
    }

    private void enable(Invocation invocation, String scope, String[] args) {
        int minutes = defaultDeadlineMinutes;
        if (args.length >= 3) {
            try {
                minutes = Integer.parseInt(args[2]);
            } catch (NumberFormatException error) {
                invocation.source().sendMessage(MessageStyle.component("<tc><red>La durée doit être un nombre de minutes."));
                return;
            }
        }
        if (minutes < 1 || minutes > 1_440) {
            invocation.source().sendMessage(MessageStyle.component("<tc><red>La durée doit être comprise entre 1 et 1440 minutes."));
            return;
        }
        String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Maintenance Tropicube";
        var state = maintenance.enable(scope, Duration.ofMinutes(minutes).toMillis(), reason);
        invocation.source().sendMessage(MessageStyle.component("<tc><green>Drain activé jusqu'à <white>" + state.deadlineAt()));
    }

    @Override public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tropicube.admin.maintenance");
    }

    @Override public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            List<String> scopes = new ArrayList<>(servers.getTemplates().keySet());
            scopes.addFirst("network");
            return scopes;
        }
        if (args.length == 2) return List.of("on", "off", "status");
        return List.of();
    }
}
