package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

// /balance
/** Displays only the command sender's TropiCoin balance. */
public class BalanceCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public BalanceCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String[] args) {
        var eco = plugin.getEconomyManager();
        var lm  = plugin.getLanguageManager();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(lm.getComponentForLang("fr", "general.player-only"));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        String language = lang(sender);
        CommandAsync.run(plugin, sender, language,
                () -> eco.getBalance(player.getUniqueId()),
                balance -> sender.sendMessage(lm.getComponent(player.getUniqueId(), "economy.balance",
                        eco.format(balance))));
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
