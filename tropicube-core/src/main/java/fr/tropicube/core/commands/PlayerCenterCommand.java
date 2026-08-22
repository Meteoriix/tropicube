package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.PlayerPreferenceService;
import fr.tropicube.core.network.ProfileService;
import fr.tropicube.core.progression.MissionService;
import fr.tropicube.core.util.CommandAsync;
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
            case "center", "centre" -> { plugin.getPlayerCenterMenu().openHome(player); yield true; }
            case "profile", "profil" -> profile(player, args);
            case "settings", "preferences", "parametres" -> settings(player, args);
            case "missions" -> missions(player, args);
            case "notifications", "inbox" -> notifications(player, args);
            default -> false;
        };
    }

    private boolean profile(Player player, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            plugin.getProfileService().selectTitle(player.getUniqueId(), args[1])
                    .thenAccept(selected -> syncSend(player, selected
                            ? "center.profile-title-selected" : "center.profile-title-unavailable"));
            return true;
        }
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
                        send(viewer, "center.profile-private");
                        return;
                    }
                    send(viewer, "center.profile-header", profile.username());
                    if (profile.selectedTitleKey() != null) send(viewer, "center.profile-title",
                            plugin.getLanguageManager().get(viewer.getUniqueId(), profile.selectedTitleKey()));
                    send(viewer, "center.profile-network", profile.networkLevel(), profile.networkExperience(), profile.grade());
                    send(viewer, "center.profile-sheepwars", profile.matches(), profile.wins(), profile.kills());
                    if (profile.access() == ProfileService.Access.FULL) {
                        send(viewer, "center.profile-details", Math.round(profile.rating()), profile.friends(),
                                profile.guildName() == null ? "—" : profile.guildName(),
                                plugin.getEconomyManager().format(profile.balance()));
                        profile.badges().forEach(badge -> send(viewer, "center.profile-badge",
                                plugin.getLanguageManager().get(viewer.getUniqueId(), badge.displayKey())));
                        profile.seasonArchives().forEach(archive -> send(viewer, "center.profile-season",
                                archive.seasonKey(), archive.tier(), Math.round(archive.rating()), archive.rankedMatches()));
                        profile.kitMasteries().forEach(mastery -> send(viewer, "center.profile-mastery",
                                mastery.kitId(), mastery.level(), mastery.experience(),
                                mastery.branch() == null ? "—" : mastery.branch()));
                    }
                }));
    }

    private boolean settings(Player player, String[] args) {
        if (args.length == 0) {
            plugin.getPlayerPreferenceService().load(player.getUniqueId()).thenAccept(value ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> send(player, "center.settings-status",
                            value.profileVisibility(), value.messagePrivacy(), value.globalChatEnabled(),
                            value.lobbyVisibility(), value.contextualHelp())));
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
                    send(player, "center.settings-saved"));
        })
                .exceptionally(error -> { plugin.getServer().getScheduler().runTask(plugin, () ->
                        send(player, "center.settings-usage"));
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
            catch (NumberFormatException error) { send(player, "center.missions-reroll-usage"); return true; }
            int allowance = player.hasPermission("tropicube.missions.reroll.bonus") ? 4 : 2;
            plugin.getMissionService().rerollDaily(player.getUniqueId(), slot, allowance)
                    .thenAccept(result -> syncSend(player, "center.missions-reroll-result", result));
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("claim")) {
            try {
                MissionService.Rotation rotation = MissionService.Rotation.valueOf(args[1].toUpperCase(Locale.ROOT));
                plugin.getMissionService().claim(player.getUniqueId(), rotation, parseSlot(args[2]))
                        .thenAccept(result -> syncSend(player, "center.missions-claim-result", result));
            } catch (IllegalArgumentException error) { send(player, "center.missions-claim-usage"); }
            return true;
        }
        plugin.getMissionService().current(player.getUniqueId()).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    send(player, "center.missions-header");
                    values.forEach(value -> send(player, "center.missions-entry", value.rotation(),
                            value.slot() + 1, value.mission().id(), value.progress(), value.mission().target(),
                            value.rewarded() ? "✓" : ""));
                }));
        return true;
    }

    private boolean notifications(Player player, String[] args) {
        if (args.length == 0 && plugin.getServer().getPluginManager().isPluginEnabled("TropicubeLobby")) {
            plugin.getPlayerCenterMenu().openNotifications(player, 0, "ALL");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("read")) {
            try { plugin.getNotificationService().markRead(player.getUniqueId(), Long.parseLong(args[1])); }
            catch (NumberFormatException ignored) { send(player, "center.notification-invalid-id"); }
            return true;
        }
        plugin.getNotificationService().inbox(player.getUniqueId(), 20).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    send(player, "center.notifications");
                    values.forEach(value -> player.sendMessage(
                            plugin.getLanguageManager().getComponent(player.getUniqueId(), "center.notification-line",
                                    value.id(), value.category(),
                                    plugin.getLanguageManager().get(player.getUniqueId(), value.messageKey(), value.arguments().toArray()),
                                    value.read() ? "" : plugin.getLanguageManager().get(player.getUniqueId(), "center.unread"))));
                }));
        return true;
    }

    private void send(Player player, String key, Object... values) {
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), key, values));
    }
    private void syncSend(Player player, String key, Object... values) {
        plugin.getServer().getScheduler().runTask(plugin, () -> send(player, key, values));
    }
    private static int parseSlot(String value) { return Integer.parseInt(value) - 1; }
    private static boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("off") || value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("booléen invalide");
    }
}
