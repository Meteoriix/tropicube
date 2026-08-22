package fr.tropicube.lobby.gui;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import fr.tropicube.lobby.utils.PlayerHeadProfileCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralizes the opening and monitoring of GUI menus opened by player.
 */
public class GuiManager {

    private final TropicubeLobby plugin;
    private final PlayerHeadProfileCache playerHeadProfiles;

    /** Associates the player with the open GUI type, to route clicks. */
    private final Map<UUID, GuiType> openGuis = new ConcurrentHashMap<>();

    public GuiManager(TropicubeLobby plugin) {
        this.plugin = plugin;
        this.playerHeadProfiles = new PlayerHeadProfileCache(plugin.getLogger());
    }

    public enum GuiType {
        SERVER_SELECTOR,
        LANGUAGE_SELECTOR,
        VIP_SHOP,
        SERVER_TYPE_SELECTOR,
        CUSTOM_GAME,
        CUSTOM_GAME_TYPE_SELECTOR,
        SETTINGS,
        SOCIAL
    }

    // ── Opening menus ───────────────────────── ─────────────────────────

    public void openServerSelector(Player player, String type, int page) {
        Inventory inv = ServerSelectorGUI.build(plugin, player, type, page);
        openGuis.put(player.getUniqueId(), GuiType.SERVER_SELECTOR);
        player.openInventory(inv);
    }

    public void openServerTypeSelector(Player player) {
        Inventory inv = ServerTypeSelectorGUI.build(plugin, player);
        openGuis.put(player.getUniqueId(), GuiType.SERVER_TYPE_SELECTOR);
        player.openInventory(inv);
    }

    public void openLanguageSelector(Player player) {
        Inventory inv = LanguageSelectorGUI.build(player);
        openGuis.put(player.getUniqueId(), GuiType.LANGUAGE_SELECTOR);
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        UUID playerId = player.getUniqueId();
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        var replay = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> plugin.getRedisManager().getAutoReplayRemaining(playerId));
        replay.thenCombine(core.getPlayerPreferenceService().load(playerId), SettingsSnapshot::new)
                .whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Impossible de charger les paramètres de " + playerId, error);
                        online.sendMessage(LangHelper.component(online, "general.operation-failed"));
                        return;
                    }
                    openGuis.put(playerId, GuiType.SETTINGS);
                    online.openInventory(SettingsGUI.build(online, snapshot.autoReplay(), snapshot.preferences()));
                }));
    }

    private record SettingsSnapshot(int autoReplay,
                                    fr.tropicube.core.network.PlayerPreferenceService.Preferences preferences) {}

    public void openVipShop(Player player) {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        if (!(corePlugin instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }

        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                double balance = core.getEconomyManager().getBalance(playerId);
                String grade = core.getPermissionManager().getGrade(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer == null) return;
                    Inventory inventory = VipShopGUI.build(onlinePlayer, balance, grade);
                    openGuis.put(playerId, GuiType.VIP_SHOP);
                    onlinePlayer.openInventory(inventory);
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        MessageStyle.log("tc", "GUI", "<yellow>Impossible de charger la boutique VIP pour " + playerId), exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null) {
                        onlinePlayer.sendMessage(LangHelper.component(onlinePlayer, "general.operation-failed"));
                    }
                });
            }
        });
    }

    /** Loads SQL and Redis social data asynchronously, then opens one coherent snapshot. */
    public void openSocial(Player player) {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        if (!(corePlugin instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }
        UUID playerId = player.getUniqueId();
        var social = core.getSocialService();
        social.friends(playerId).thenCombine(social.requests(playerId), (friends, requests) -> {
            List<CompletableFuture<SocialGUI.FriendEntry>> entryFutures = friends.stream()
                    .map(friend -> playerHeadProfiles.resolve(friend.playerId())
                            .thenApply(profile -> new SocialGUI.FriendEntry(friend.playerId(), friend.username(),
                                    social.isOnline(friend.playerId()), profile)))
                    .toList();
            var party = social.party(playerId);
            var invites = social.partyInvites(playerId);
            Map<UUID, String> names = new HashMap<>();
            if (party != null) party.members().forEach(member -> names.put(member.playerId(), social.displayName(member.playerId())));
            invites.keySet().forEach(id -> names.put(id, social.displayName(id)));
            return CompletableFuture.allOf(entryFutures.toArray(CompletableFuture[]::new))
                    .thenApply(ignored -> new SocialSnapshot(
                            entryFutures.stream().map(CompletableFuture::join).toList(),
                            requests, party, invites, names));
        }).thenCompose(snapshot -> snapshot)
                .whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Impossible de charger le menu Social", error);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
                return;
            }
            try {
                Inventory inventory = SocialGUI.build(online, snapshot.friends(), snapshot.requests(), snapshot.party(),
                        snapshot.invites(), snapshot.names());
                openGuis.put(playerId, GuiType.SOCIAL);
                online.openInventory(inventory);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Impossible de construire le menu Social pour " + playerId, exception);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
            }
        }));
    }

    public void openCustomGameMenu(Player player, boolean whitelisted) {
        Inventory inv = CustomGameGUI.build(plugin, player, whitelisted);
        openGuis.put(player.getUniqueId(), GuiType.CUSTOM_GAME);
        player.openInventory(inv);
    }

    public void openCustomGameTypeMenu(Player player) {
        if (hasCustomGameOrCreation(player.getUniqueId())) {
            openCustomGameMenu(player, false);
            return;
        }
        Inventory inv = CustomGameTypeGUI.build(player);
        openGuis.put(player.getUniqueId(), GuiType.CUSTOM_GAME_TYPE_SELECTOR);
        player.openInventory(inv);
    }

    /** Indicates if the player already has a custom server or if its creation is reserved. */
    public boolean hasCustomGameOrCreation(UUID playerId) {
        return plugin.getRedisManager().exists("host:" + playerId)
                || plugin.getRedisManager().exists("host-creation:" + playerId);
    }

    // ── Suivi ────────────────────────────────────────────────────────────────

    public GuiType getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public boolean hasGuiOpen(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }
    public void clearAll() {
        openGuis.clear();
        playerHeadProfiles.clear();
    }

    public void onPlayerQuit(UUID playerId) {
        openGuis.remove(playerId);
    }

    private record SocialSnapshot(List<SocialGUI.FriendEntry> friends,
                                  List<fr.tropicube.core.social.FriendshipRepository.PendingRequest> requests,
                                  fr.tropicube.docker.model.PartySnapshot party,
                                  Map<UUID, String> invites, Map<UUID, String> names) { }

    /**
     * Refreshes all open server menus in place, without reopening them.
     * inventories. This method must be called on the main thread.
     */
    public void refreshOpenServerGuis() {
        for (Map.Entry<UUID, GuiType> entry : openGuis.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            switch (entry.getValue()) {
                case SERVER_TYPE_SELECTOR -> ServerTypeSelectorGUI.refresh(plugin, p);
                case SERVER_SELECTOR      -> ServerSelectorGUI.refresh(plugin, p);
                default -> {}
            }
        }
    }
}
