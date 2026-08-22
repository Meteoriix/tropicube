package fr.tropicube.sheepwars.command;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.competitive.KitMasteryBranch;
import fr.tropicube.sheepwars.competitive.SheepWarsProgressionService;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.util.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Player-facing competitive profile and reversible kit-progression preferences. */
public final class SheepWarsCommand implements CommandExecutor {
    private final TropicubeSheepwars plugin;

    public SheepWarsCommand(TropicubeSheepwars plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("rank")) {
            plugin.getProgressionService().rating(player.getUniqueId()).thenAccept(view ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        String tier = LangHelper.get(player, "sw.rank-" + view.rating().tier().name().toLowerCase(Locale.ROOT));
                        player.sendMessage(LangHelper.component(player, "sw.competitive-rank", tier,
                                Math.round(view.rating().value()), Math.round(view.rating().uncertainty()),
                                view.rating().placementsRemaining()));
                    }));
            return true;
        }
        if (args[0].equalsIgnoreCase("mastery") && args.length == 2) {
            PlayerKit kit = plugin.getPlayerDataManager().getKit(player.getUniqueId());
            if (kit == PlayerKit.NONE) {
                player.sendMessage(LangHelper.component(player, "sw.mastery-kit-required"));
                return true;
            }
            KitMasteryBranch branch = switch (args[1].toLowerCase(Locale.ROOT)) {
                case "a", "1" -> KitMasteryBranch.BRANCH_A;
                case "b", "2" -> KitMasteryBranch.BRANCH_B;
                default -> null;
            };
            if (branch == null) return false;
            plugin.getProgressionService().selectBranch(player.getUniqueId(), kit, branch).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                            LangHelper.component(player, "sw.mastery-branch-selected", branch.name()))));
            return true;
        }
        if (args[0].equalsIgnoreCase("mastery") && args.length == 1) {
            plugin.getKitMasteryMenu().open(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("summary") && args.length == 2) {
            try {
                SheepWarsProgressionService.SummaryVisibility visibility =
                        SheepWarsProgressionService.SummaryVisibility.valueOf(args[1].toUpperCase(Locale.ROOT));
                plugin.getProgressionService().setSummaryVisibility(player.getUniqueId(), visibility).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                                LangHelper.component(player, "sw.summary-visibility-set", visibility.name()))));
                return true;
            } catch (IllegalArgumentException ignored) { return false; }
        }
        return false;
    }
}
