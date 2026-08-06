package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// /lang [code]
/** Consulte ou change la langue persistée du joueur. */
public class LanguageCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public LanguageCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getComponentForLang("fr", "general.player-only"));
            return true;
        }
        var lm = plugin.getLanguageManager();

        if (args.length == 0) {
            player.sendMessage(lm.getComponent(player.getUniqueId(), "language.header"));
            LanguageManager.SUPPORTED_LANGUAGES.forEach(lang ->
                    player.sendMessage(MiniMessage.miniMessage().deserialize(
                            "  <yellow>/lang " + lang + " <dark_gray>— " + lm.getLanguageDisplayName(lang))));
            player.sendMessage(lm.getComponent(player.getUniqueId(), "language.current",
                    lm.getLanguageDisplayName(lm.getPlayerLanguage(player.getUniqueId()))));
            return true;
        }

        String langCode = args[0].toLowerCase();
        if (!LanguageManager.SUPPORTED_LANGUAGES.contains(langCode)) {
            player.sendMessage(lm.getComponent(player.getUniqueId(), "language.invalid",
                    String.join(", ", LanguageManager.SUPPORTED_LANGUAGES)));
            return true;
        }

        lm.setPlayerLanguage(player.getUniqueId(), langCode, true);
        player.sendMessage(lm.getComponent(player.getUniqueId(), "language.selected",
                lm.getLanguageDisplayName(langCode)));
        return true;
    }
}
