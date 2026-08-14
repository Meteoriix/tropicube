package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.List;

/** Lets a custom-game host add or remove known players from their private instance. */
public final class WhitelistCommand implements SimpleCommand {

    private final TropicubeVelocity plugin;
    private final TropiServerManager serverManager;
    private final VelocityLanguageManager languageManager;

    public WhitelistCommand(TropicubeVelocity plugin, TropiServerManager serverManager,
                            VelocityLanguageManager languageManager) {
        this.plugin = plugin;
        this.serverManager = serverManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(languageManager.getComponent(invocation.source(), "general.player-only"));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length != 2 || !("add".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]))) {
            player.sendMessage(languageManager.getComponent(player.getUniqueId(), "proxy.whitelist-usage"));
            return;
        }

        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            TropiServerManager.WhitelistUpdate update = serverManager.updateHostedWhitelist(
                    player.getUniqueId(), args[1], "add".equalsIgnoreCase(args[0]));
            player.sendMessage(languageManager.getComponent(
                    player.getUniqueId(), update.messageKey(), update.targetName()));
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) return List.of("add", "remove");
        if (args.length == 2) {
            return plugin.getServer().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }
}
