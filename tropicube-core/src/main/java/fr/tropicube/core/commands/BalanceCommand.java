package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

// /balance [joueur]
/** Affiche le solde TropiCoin du joueur ou celui d'une cible autorisée. */
public class BalanceCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public BalanceCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String[] args) {
        var eco = plugin.getEconomyManager();
        var lm  = plugin.getLanguageManager();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(lm.getComponentForLang("fr", "general.specify-player"));
                return true;
            }
            String language = lang(sender);
            CommandAsync.run(plugin, sender, language,
                    () -> eco.getBalance(player.getUniqueId()),
                    balance -> sender.sendMessage(lm.getComponent(player.getUniqueId(), "economy.balance",
                            eco.format(balance))));
            return true;
        }

        if (!sender.hasPermission("tropicube.eco.others")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }

        String name = args[0];
        Player online = plugin.getServer().getPlayer(name);
        var onlineUuid = online == null ? null : online.getUniqueId();
        String language = lang(sender);
        CommandAsync.run(plugin, sender, language, () -> {
            var uuid = onlineUuid != null ? onlineUuid
                    : plugin.getPlayerDataManager().getUuidByName(name).orElse(null);
            return uuid == null ? null : eco.getBalance(uuid);
        }, balance -> {
            if (balance == null) sender.sendMessage(lm.getComponentForLang(language,
                    "general.player-not-found", name));
            else sender.sendMessage(lm.getComponentForLang(language,
                    "economy.balance-other", name, eco.format(balance)));
        });
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
