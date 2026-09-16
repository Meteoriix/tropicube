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
        FALLENKINGDOMS_CUSTOM_GAME,
        CUSTOM_GAME_TYPE_SELECTOR,
        SETTINGS,
        SOCIAL,
        FRIEND_REQUESTS,
        PARTY_INVITES,
        RANKED_SELECTOR,
        BETA_SELECTOR
    }

    // ── Opening menus ───────────────────────── ─────────────────────────

    public void openServerSelector(Player player, String type, int page) {
        openServerSelector(player, type, page, ServerSelectorGUI.Filter.ALL);
    }

    public void openServerSelector(Player player, String type, int page, ServerSelectorGUI.Filter filter) {
        Inventory inv = ServerSelectorGUI.build(plugin, player, type, page, filter);
        openGuis.put(player.getUniqueId(), GuiType.SERVER_SELECTOR);
        player.openInventory(inv);
    }

    public void openServerTypeSelector(Player player) {
        Inventory inv = ServerTypeSelectorGUI.build(plugin, player);
        openGuis.put(player.getUniqueId(), GuiType.SERVER_TYPE_SELECTOR);
        player.openInventory(inv);
        showHint(player, "GAME_SELECTOR", "lobby.hint-game-selector");
    }

    public void openRankedSelector(Player player, String type) {
        UUID playerId = player.getUniqueId();
        Inventory loading = plugin.getDiscoveryMenus().loading(player, () -> openRankedSelector(player, type),
                () -> openServerTypeSelector(player));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLobbyServerManager().refreshPlayerMatchmaking(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != loading) return;
                    openGuis.put(playerId, GuiType.RANKED_SELECTOR);
                    player.openInventory(RankedSelectorGUI.build(plugin, player, type));
                });
            } catch (RuntimeException error) {
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getDiscoveryMenus().loadFailed(player, loading, error));
            }
        });
    }

    public void openBetaSelector(Player player) {
        UUID playerId = player.getUniqueId();
        Inventory loading = plugin.getDiscoveryMenus().loading(player, () -> openBetaSelector(player),
                () -> openServerTypeSelector(player));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLobbyServerManager().refreshPlayerMatchmaking(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != loading) return;
                    openGuis.put(playerId, GuiType.BETA_SELECTOR);
                    player.openInventory(BetaQueueSelectorGUI.build(plugin, player));
                });
            } catch (RuntimeException error) {
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin,
                        () -> plugin.getDiscoveryMenus().loadFailed(player, loading, error));
            }
        });
    }

    public void openLanguageSelector(Player player) {
        Inventory inv = LanguageSelectorGUI.build(player);
        openGuis.put(player.getUniqueId(), GuiType.LANGUAGE_SELECTOR);
        player.openInventory(inv);
    }

    public void openSettings(Player player) {
        UUID playerId = player.getUniqueId();
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        Inventory loading = plugin.getDiscoveryMenus().loading(player, () -> openSettings(player),
                () -> plugin.getCore().getPlayerCenterMenu().openHome(player));
        var replay = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> plugin.getRedisManager().getAutoReplayRemaining(playerId));
        replay.thenCombine(core.getPlayerPreferenceService().load(playerId), SettingsSnapshot::new)
                .whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != player || online.getOpenInventory().getTopInventory() != loading) return;
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Impossible de charger les paramètres de " + playerId, error);
                        plugin.getDiscoveryMenus().loadFailed(online, loading, error);
                        return;
                    }
                    openGuis.put(playerId, GuiType.SETTINGS);
                    online.openInventory(SettingsGUI.build(online, snapshot.autoReplay(), snapshot.preferences()));
                    showHint(online, "SETTINGS", "lobby.hint-settings");
                }));
    }

    private record SettingsSnapshot(int autoReplay,
                                    fr.tropicube.core.network.PlayerPreferenceService.Preferences preferences) {}

    public void openVipShop(Player player) {
        openVipShop(player, false);
    }

    public void openVipGrades(Player player) {
        openVipShop(player, true);
    }

    /** Opens the local confirmation screen using a price computed from the displayed grade. */
    public void openVipGradeConfirmation(Player player, String gradeKey, int price) {
        openGuis.put(player.getUniqueId(), GuiType.VIP_SHOP);
        player.openInventory(VipShopGUI.buildConfirmation(player, gradeKey, price));
    }

    private void openVipShop(Player player, boolean grades) {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        if (!(corePlugin instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }

        UUID playerId = player.getUniqueId();
        Inventory loading = plugin.getDiscoveryMenus().loading(player, () -> openVipShop(player, grades),
                grades ? () -> openVipShop(player) : player::closeInventory);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                double balance = core.getEconomyManager().getBalance(playerId);
                String grade = core.getPermissionManager().getGrade(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != player || onlinePlayer.getOpenInventory().getTopInventory() != loading) return;
                    Inventory inventory = grades
                            ? VipShopGUI.buildGrades(onlinePlayer, balance, grade)
                            : VipShopGUI.build(onlinePlayer, balance, grade);
                    openGuis.put(playerId, GuiType.VIP_SHOP);
                    onlinePlayer.openInventory(inventory);
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        MessageStyle.log("tc", "GUI", "<yellow>Impossible de charger la boutique VIP pour " + playerId), exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null) {
                        plugin.getDiscoveryMenus().loadFailed(onlinePlayer, loading, exception);
                    }
                });
            }
        });
    }

    /** Loads SQL and Redis social data asynchronously, then opens one coherent snapshot. */
    public void openSocial(Player player) {
        openSocial(player, SocialGUI.View.FRIENDS);
    }

    /** Opens one of the two Social views from the same asynchronously loaded snapshot. */
    public void openSocial(Player player, SocialGUI.View view) {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        if (!(corePlugin instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }
        UUID playerId = player.getUniqueId();
        Inventory loading = SocialGUI.loading(player, view);
        player.openInventory(loading);
        var social = core.getSocialService();
        social.friends(playerId).thenCombine(social.requests(playerId), (friends, requests) -> {
            List<CompletableFuture<SocialGUI.FriendEntry>> entryFutures = friends.stream()
                    .map(friend -> playerHeadProfiles.resolve(friend.playerId())
                            .thenApply(profile -> new SocialGUI.FriendEntry(friend.playerId(), friend.username(),
                                    social.isOnline(friend.playerId()), profile)))
                    .toList();
            var party = social.party(playerId);
            var invites = social.partyInvites(playerId);
            var sentInvites = social.sentPartyInvites(playerId);
            List<CompletableFuture<SocialGUI.PartyEntry>> partyEntryFutures = party == null ? List.of()
                    : party.members().stream().map(member -> playerHeadProfiles.resolve(member.playerId())
                            .thenApply(profile -> new SocialGUI.PartyEntry(member.playerId(),
                                    social.displayName(member.playerId()), social.isOnline(member.playerId()),
                                    member.playerId().equals(party.leaderId()), profile)))
                    .toList();
            CompletableFuture<?>[] profiles = java.util.stream.Stream.concat(
                    entryFutures.stream(), partyEntryFutures.stream()).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(profiles)
                    .thenApply(ignored -> new SocialSnapshot(
                            entryFutures.stream().map(CompletableFuture::join).toList(),
                            partyEntryFutures.stream().map(CompletableFuture::join).toList(),
                            requests.size(), invites.size(), sentInvites.size(), party));
        }).thenCompose(snapshot -> snapshot)
                .whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || online.getOpenInventory().getTopInventory() != loading) return;
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Impossible de charger le menu Social", error);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
                loading.setItem(22, new fr.tropicube.lobby.utils.ItemBuilder(org.bukkit.Material.RED_DYE)
                        .name(LangHelper.get(online, "general.operation-failed")).build());
                return;
            }
            try {
                Inventory inventory = SocialGUI.build(online, view, snapshot.friends(), snapshot.partyMembers(),
                        snapshot.friendRequestCount(), snapshot.receivedPartyInviteCount(),
                        snapshot.sentPartyInviteCount(), snapshot.party());
                openGuis.put(playerId, GuiType.SOCIAL);
                online.openInventory(inventory);
                showHint(online, "SOCIAL", "lobby.hint-social");
            } catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Impossible de construire le menu Social pour " + playerId, exception);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
            }
        }));
    }

    /** Loads both directions of pending friend requests without blocking the Paper thread. */
    public void openFriendRequests(Player player, int page) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }
        UUID playerId = player.getUniqueId();
        var social = core.getSocialService();
        social.requests(playerId).thenCombine(social.sentRequests(playerId), (incoming, sent) -> {
            List<CompletableFuture<FriendRequestsGUI.RequestEntry>> incomingEntries = incoming.stream()
                    .map(request -> playerHeadProfiles.resolve(request.requesterId())
                            .thenApply(profile -> new FriendRequestsGUI.RequestEntry(
                                    request.requesterId(), request.username(), profile)))
                    .toList();
            List<CompletableFuture<FriendRequestsGUI.RequestEntry>> sentEntries = sent.stream()
                    .map(request -> playerHeadProfiles.resolve(request.targetId())
                            .thenApply(profile -> new FriendRequestsGUI.RequestEntry(
                                    request.targetId(), request.username(), profile)))
                    .toList();
            CompletableFuture<?>[] profiles = java.util.stream.Stream.concat(
                    incomingEntries.stream(), sentEntries.stream()).toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(profiles).thenApply(ignored -> new FriendRequestSnapshot(
                    incomingEntries.stream().map(CompletableFuture::join).toList(),
                    sentEntries.stream().map(CompletableFuture::join).toList()));
        }).thenCompose(snapshot -> snapshot)
                .whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null) {
                        plugin.getLogger().log(java.util.logging.Level.WARNING,
                                "Impossible de charger les demandes d'amis", error);
                        online.sendMessage(LangHelper.component(online, "general.operation-failed"));
                        return;
                    }
                    openGuis.put(playerId, GuiType.FRIEND_REQUESTS);
                    online.openInventory(FriendRequestsGUI.build(
                            online, snapshot.incoming(), snapshot.sent(), page));
                }));
    }

    /** Loads received and sent party invitations and resolves their player heads asynchronously. */
    public void openPartyInvites(Player player) {
        openPartyInvites(player, 0);
    }

    /** Loads one page of received and sent party invitations and resolves their player heads asynchronously. */
    public void openPartyInvites(Player player, int page) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) {
            player.sendMessage(LangHelper.component(player, "general.operation-failed"));
            return;
        }
        UUID playerId = player.getUniqueId();
        var social = core.getSocialService();
        CompletableFuture.supplyAsync(() -> new PartyInviteIds(
                social.partyInvites(playerId).keySet(), social.sentPartyInvites(playerId).keySet()))
                .thenCompose(invites -> {
                    List<CompletableFuture<PartyInvitesGUI.InviteEntry>> incomingEntries = invites.incoming().stream()
                            .map(leaderId -> playerHeadProfiles.resolve(leaderId)
                                    .thenApply(profile -> new PartyInvitesGUI.InviteEntry(
                                            leaderId, social.displayName(leaderId), profile)))
                            .toList();
                    List<CompletableFuture<PartyInvitesGUI.InviteEntry>> sentEntries = invites.sent().stream()
                            .map(targetId -> playerHeadProfiles.resolve(targetId)
                                    .thenApply(profile -> new PartyInvitesGUI.InviteEntry(
                                            targetId, social.displayName(targetId), profile)))
                            .toList();
                    CompletableFuture<?>[] profiles = java.util.stream.Stream.concat(
                            incomingEntries.stream(), sentEntries.stream()).toArray(CompletableFuture[]::new);
                    return CompletableFuture.allOf(profiles).thenApply(ignored -> new PartyInviteSnapshot(
                            incomingEntries.stream().map(CompletableFuture::join).toList(),
                            sentEntries.stream().map(CompletableFuture::join).toList()));
                }).whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            if (error != null) {
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Impossible de charger les invitations de party", error);
                online.sendMessage(LangHelper.component(online, "general.operation-failed"));
                return;
            }
            openGuis.put(playerId, GuiType.PARTY_INVITES);
            online.openInventory(PartyInvitesGUI.build(online, snapshot.incoming(), snapshot.sent(), page));
        }));
    }

    public void showHint(Player player, String hintId, String messageKey) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        core.getContextualHelpService().claim(player.getUniqueId(), hintId).thenAccept(show -> {
            if (!show) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) online.sendMessage(LangHelper.component(online, messageKey));
            });
        });
    }

    public void openCustomGameMenu(Player player, boolean whitelisted) {
        Inventory inv = CustomGameGUI.build(plugin, player, whitelisted);
        openGuis.put(player.getUniqueId(), GuiType.CUSTOM_GAME);
        player.openInventory(inv);
    }
    public void openFallenKingdomsCustomGame(Player player,FallenKingdomsCustomGameGUI.Holder holder){
        openGuis.put(player.getUniqueId(),GuiType.FALLENKINGDOMS_CUSTOM_GAME);player.openInventory(FallenKingdomsCustomGameGUI.build(player,holder));
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

    /** Rebuilds active settings/shop/routing screens after a language change. */
    public void refreshLanguage(Player player) {
        var holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof VipShopGUI.Holder shop) openVipShop(player, shop.view() != VipShopGUI.View.HOME);
        else if (holder instanceof SettingsGUI.Holder) openSettings(player);
        else if (holder instanceof RankedSelectorGUI.Holder ranked) openRankedSelector(player, ranked.type());
        else if (holder instanceof BetaQueueSelectorGUI.Holder) openBetaSelector(player);
        else if (holder instanceof ServerTypeSelectorGUI.Holder) openServerTypeSelector(player);
        else if (holder instanceof ServerSelectorGUI.Holder servers) openServerSelector(player, servers.getType(), servers.getPage(), servers.getFilter());
        else if(holder instanceof FallenKingdomsCustomGameGUI.Holder fk)openFallenKingdomsCustomGame(player,fk);
    }

    // ── Suivi ────────────────────────────────────────────────────────────────

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }
    public void clearAll() {
        openGuis.clear();
        playerHeadProfiles.clear();
    }

    public void onPlayerQuit(UUID playerId) {
        if (plugin.getGuildMenus() != null) plugin.getGuildMenus().quit(playerId);
        openGuis.remove(playerId);
    }

    private record SocialSnapshot(List<SocialGUI.FriendEntry> friends,
                                  List<SocialGUI.PartyEntry> partyMembers,
                                  int friendRequestCount, int receivedPartyInviteCount,
                                  int sentPartyInviteCount,
                                  fr.tropicube.docker.model.PartySnapshot party) { }

    private record FriendRequestSnapshot(List<FriendRequestsGUI.RequestEntry> incoming,
                                         List<FriendRequestsGUI.RequestEntry> sent) { }

    private record PartyInviteIds(java.util.Set<UUID> incoming, java.util.Set<UUID> sent) { }

    private record PartyInviteSnapshot(List<PartyInvitesGUI.InviteEntry> incoming,
                                       List<PartyInvitesGUI.InviteEntry> sent) { }

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
                case RANKED_SELECTOR      -> RankedSelectorGUI.refresh(plugin, p);
                default -> {}
            }
        }
    }
}
