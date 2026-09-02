package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

/** Internal console-only bridge used by the local language editor after synchronizing files. */
public final class LanguageEditorReloadCommand implements SimpleCommand {
    private final TropicubeVelocity plugin;
    private final ProxyServer server;
    private final VelocityLanguageManager languages;

    public LanguageEditorReloadCommand(TropicubeVelocity plugin, ProxyServer server,
                                       VelocityLanguageManager languages) {
        this.plugin = plugin;
        this.server = server;
        this.languages = languages;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (source instanceof Player) return;
        server.getScheduler().buildTask(plugin, languages::reload).schedule();
        source.sendMessage(net.kyori.adventure.text.Component.text("Rechargement des langues programmé."));
    }
}
