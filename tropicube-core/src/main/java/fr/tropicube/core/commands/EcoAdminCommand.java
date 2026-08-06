package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// /eco <set|add|remove|top> <joueur> [montant]
/** Modifie administrativement le solde de la monnaie TropiCoin. */
public class EcoAdminCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public EcoAdminCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        var lm  = plugin.getLanguageManager();
        var eco = plugin.getEconomyManager();

        if (!sender.hasPermission("tropicube.eco.admin")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "economy.invalid-action"));
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            String language = lang(sender);
            CommandAsync.run(plugin, sender, language, () -> eco.getTopBalances(10), top -> {
                sender.sendMessage(lm.getComponentForLang(language, "economy.top-header"));
                for (int i = 0; i < top.size(); i++)
                    sender.sendMessage(lm.getComponentForLang(language, "economy.top-entry",
                            i + 1, top.get(i).getKey(), eco.format(top.get(i).getValue())));
            });
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "economy.invalid-action"));
            return true;
        }

        double amount;
        try { amount = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.invalid-amount"));
            return true;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.invalid-amount"));
            return true;
        }

        String targetName = args[1];
        String action = args[0].toLowerCase();
        if (!List.of("set", "add", "remove").contains(action) || amount == 0 && !action.equals("set")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender),
                    action.equals("set") ? "economy.invalid-action" : "general.invalid-amount"));
            return true;
        }
        Player online = plugin.getServer().getPlayer(targetName);
        UUID onlineUuid = online == null ? null : online.getUniqueId();
        String staffName = sender.getName();
        String language = lang(sender);
        CommandAsync.run(plugin, sender, language, () -> {
            UUID uuid = onlineUuid != null ? onlineUuid
                    : plugin.getPlayerDataManager().getUuidByName(targetName).orElse(null);
            if (uuid == null) return Result.NOT_FOUND;
            return switch (action) {
                case "set" -> { eco.setBalance(uuid, amount); yield Result.SUCCESS; }
                case "add" -> { eco.deposit(uuid, amount, "Admin add by " + staffName); yield Result.SUCCESS; }
                case "remove" -> eco.withdraw(uuid, amount, "Admin remove by " + staffName)
                        ? Result.SUCCESS : Result.INSUFFICIENT_FUNDS;
                default -> Result.INVALID;
            };
        }, result -> {
            switch (result) {
                case NOT_FOUND -> sender.sendMessage(lm.getComponentForLang(language,
                        "general.player-not-found", targetName));
                case INSUFFICIENT_FUNDS -> sender.sendMessage(lm.getComponentForLang(language,
                        "economy.insufficient-funds"));
                case SUCCESS -> sender.sendMessage(lm.getComponentForLang(language,
                        "economy.admin-" + action, action.equals("set") ? targetName : eco.format(amount),
                        action.equals("set") ? eco.format(amount) : targetName));
                case INVALID -> sender.sendMessage(lm.getComponentForLang(language, "economy.invalid-action"));
            }
        });
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }

    private enum Result { SUCCESS, NOT_FOUND, INSUFFICIENT_FUNDS, INVALID }
}
