package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.PlayerPreferenceService;
import fr.tropicube.core.network.ProfileService;
import fr.tropicube.core.progression.MissionService;
import fr.tropicube.core.util.CommandAsync;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.UUID;

/** Text entry points for profiles, preferences, missions and the notification center. */
public final class PlayerCenterCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public PlayerCenterCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) return false;
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "profile", "profil" -> profile(player, args);
            case "settings", "preferences", "parametres" -> settings(player, args);
            case "missions" -> missions(player, args);
            case "notifications", "inbox" -> notifications(player, args);
            default -> false;
        };
    }

    private boolean profile(Player player, String[] args) {
        if (args.length == 0) { showProfile(player, player.getUniqueId()); return true; }
        String language = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        CommandAsync.run(plugin, player, language,
                () -> plugin.getPlayerDataManager().getUuidByName(args[0]).orElse(null), target -> {
                    if (target == null) player.sendMessage(plugin.getLanguageManager().getComponent(
                            player.getUniqueId(), "general.player-not-found", args[0]));
                    else showProfile(player, target);
                });
        return true;
    }

    private void showProfile(Player viewer, UUID target) {
        plugin.getProfileService().view(viewer.getUniqueId(), target).thenAccept(profile ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (profile == null) return;
                    if (profile.access() == ProfileService.Access.HIDDEN) {
                        viewer.sendMessage(Component.text("Ce profil est privé.", NamedTextColor.RED));
                        return;
                    }
                    viewer.sendMessage(Component.text("Profil de " + profile.username(), NamedTextColor.GOLD));
                    viewer.sendMessage(Component.text("Niveau réseau " + profile.networkLevel() + " • "
                            + profile.networkExperience() + " XP • grade " + profile.grade(), NamedTextColor.AQUA));
                    viewer.sendMessage(Component.text("SheepWars : " + profile.matches() + " parties • "
                            + profile.wins() + " victoires • " + profile.kills() + " éliminations", NamedTextColor.GRAY));
                    if (profile.access() == ProfileService.Access.FULL) viewer.sendMessage(Component.text(
                            "Cote " + Math.round(profile.rating()) + " • " + profile.friends() + " amis • guilde "
                                    + (profile.guildName() == null ? "aucune" : profile.guildName()) + " • solde "
                                    + plugin.getEconomyManager().format(profile.balance()), NamedTextColor.GRAY));
                }));
    }

    private boolean settings(Player player, String[] args) {
        if (args.length == 0) {
            plugin.getPlayerPreferenceService().load(player.getUniqueId()).thenAccept(value ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(Component.text(
                            "Profil=" + value.profileVisibility() + " • MP=" + value.messagePrivacy()
                                    + " • chat global=" + value.globalChatEnabled() + " • entités="
                                    + value.lobbyVisibility() + " • aide=" + value.contextualHelp(), NamedTextColor.AQUA))));
            return true;
        }
        plugin.getPlayerPreferenceService().load(player.getUniqueId()).thenCompose(current -> {
            PlayerPreferenceService.Preferences updated = parseSetting(current, args);
            return updated == null ? java.util.concurrent.CompletableFuture.failedFuture(
                    new IllegalArgumentException("Réglage invalide"))
                    : plugin.getPlayerPreferenceService().save(player.getUniqueId(), updated);
        }).thenRun(() -> {
            plugin.getCommunicationService().preferenceChanged(player.getUniqueId());
            plugin.getRedisManager().publishPlayerEvent("PREFERENCES_CHANGED", player.getUniqueId().toString());
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("Préférence enregistrée.", NamedTextColor.GREEN)));
        })
                .exceptionally(error -> { plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("Usage : /settings profile|messages|global|entities|hints <valeur>", NamedTextColor.RED)));
                    return null; });
        return true;
    }

    private PlayerPreferenceService.Preferences parseSetting(PlayerPreferenceService.Preferences value, String[] args) {
        if (args.length != 2) return null;
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "profile" -> new PlayerPreferenceService.Preferences(
                        PlayerPreferenceService.ProfileVisibility.valueOf(args[1].toUpperCase(Locale.ROOT)),
                        value.messagePrivacy(), value.globalChatEnabled(), value.lobbyVisibility(), value.contextualHelp());
                case "messages" -> new PlayerPreferenceService.Preferences(value.profileVisibility(),
                        PlayerPreferenceService.MessagePrivacy.valueOf(args[1].toUpperCase(Locale.ROOT)),
                        value.globalChatEnabled(), value.lobbyVisibility(), value.contextualHelp());
                case "global" -> new PlayerPreferenceService.Preferences(value.profileVisibility(), value.messagePrivacy(),
                        parseBoolean(args[1]), value.lobbyVisibility(), value.contextualHelp());
                case "entities" -> new PlayerPreferenceService.Preferences(value.profileVisibility(), value.messagePrivacy(),
                        value.globalChatEnabled(), PlayerPreferenceService.LobbyVisibility.valueOf(
                                args[1].toUpperCase(Locale.ROOT)), value.contextualHelp());
                case "hints" -> new PlayerPreferenceService.Preferences(value.profileVisibility(), value.messagePrivacy(),
                        value.globalChatEnabled(), value.lobbyVisibility(), parseBoolean(args[1]));
                default -> null;
            };
        } catch (IllegalArgumentException error) { return null; }
    }

    private boolean missions(Player player, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("reroll")) {
            int slot;
            try { slot = parseSlot(args[1]); }
            catch (NumberFormatException error) { reply(player, "Usage : /missions reroll <1..5>"); return true; }
            int allowance = player.hasPermission("tropicube.missions.reroll.bonus") ? 4 : 2;
            plugin.getMissionService().rerollDaily(player.getUniqueId(), slot, allowance)
                    .thenAccept(result -> reply(player, "Reroll : " + result));
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("claim")) {
            try {
                MissionService.Rotation rotation = MissionService.Rotation.valueOf(args[1].toUpperCase(Locale.ROOT));
                plugin.getMissionService().claim(player.getUniqueId(), rotation, parseSlot(args[2]))
                        .thenAccept(result -> reply(player, "Récompense : " + result));
            } catch (IllegalArgumentException error) { reply(player, "Usage : /missions claim <daily|weekly> <1..5>"); }
            return true;
        }
        plugin.getMissionService().current(player.getUniqueId()).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Missions personnelles", NamedTextColor.GOLD));
                    values.forEach(value -> player.sendMessage(Component.text(value.rotation() + " "
                            + (value.slot() + 1) + " • " + value.mission().id() + " • " + value.progress()
                            + "/" + value.mission().target() + (value.rewarded() ? " ✓" : ""), NamedTextColor.GRAY)));
                }));
        return true;
    }

    private boolean notifications(Player player, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("read")) {
            try { plugin.getNotificationService().markRead(player.getUniqueId(), Long.parseLong(args[1])); }
            catch (NumberFormatException ignored) { reply(player, "Identifiant invalide."); }
            return true;
        }
        plugin.getNotificationService().inbox(player.getUniqueId(), 20).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(Component.text("Centre de notifications", NamedTextColor.GOLD));
                    values.forEach(value -> player.sendMessage(Component.text("#" + value.id() + " ["
                            + value.category() + "] " + value.messageKey() + (value.read() ? "" : " • nouveau"),
                            value.read() ? NamedTextColor.GRAY : NamedTextColor.AQUA)));
                }));
        return true;
    }

    private void reply(Player player, String text) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(Component.text(text, NamedTextColor.AQUA)));
    }
    private static int parseSlot(String value) { return Integer.parseInt(value) - 1; }
    private static boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("booléen invalide");
    }
}
