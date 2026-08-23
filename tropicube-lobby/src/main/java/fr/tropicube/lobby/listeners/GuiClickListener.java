package fr.tropicube.lobby.listeners;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.gui.*;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Handles all clicks in Tropicube Lobby GUIs.
 *
 * <p> GUI detection is based on the custom {@link InventoryHolder} of each inventory
 * rather than on the GuiManager map {@code openGuis}. This ensures that the event is
 * always canceled as soon as one of our inventories is visible, even if the map would be out of sync.
 */
public class GuiClickListener implements Listener {

    enum TypeSelectorAction {
        QUICK_PLAY,
        RANKED,
        PUBLIC_INSTANCES,
        NONE
    }

    private final TropicubeLobby plugin;

    public GuiClickListener(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    // ── Inventory Events ─────────────────────── ───────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // Identification by holder — reliable even if the GuiManager map is out of sync.
        InventoryHolder holder = e.getInventory().getHolder();
        if (!isOurGui(holder)) return;

        // Cancel BEFORE any filtering: prevents picking up of items, shift-click, etc.
        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;

        // Click in the lower part (player inventory) → canceled but without GUI action.
        if (e.getClickedInventory() == null || e.getClickedInventory() == player.getInventory()) return;

        int slot = e.getRawSlot();

        switch (holder) {
            case ServerTypeSelectorGUI.Holder typeHolder -> handleTypeSelector(player, slot, typeHolder, e.getClick());
            case ServerSelectorGUI.Holder serverHolder -> handleServerSelector(player, slot, serverHolder);
            case RankedSelectorGUI.Holder rankedHolder -> handleRankedSelector(player, slot, rankedHolder);
            case LanguageSelectorGUI.Holder _ -> handleLanguageSelector(player, slot);
            case SettingsGUI.Holder _ -> handleSettings(player, slot);
            case VipShopGUI.Holder shopHolder -> handleVipShop(player, slot, shopHolder);
            case CustomGameGUI.Holder customHolder -> handleCustomGame(player, slot, customHolder);
            case CustomGameTypeGUI.Holder customTypeHolder -> handleCustomGameType(player, slot, customTypeHolder);
            case SocialGUI.Holder socialHolder -> handleSocial(player, slot, socialHolder, e.getClick());
            case FriendRequestsGUI.Holder requestsHolder ->
                    handleFriendRequests(player, slot, requestsHolder, e.getClick());
            default -> {
            }
        }
    }

    /** Prevents items from being dragged into our inventories. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (isOurGui(e.getInventory().getHolder())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!isOurGui(e.getView().getTopInventory().getHolder())) return;

        // Delayed by one tick: if another GUI opens in the same transition (ex: TypeSelector → ServerSelector),
        // its holder will be visible on the next tick → we do not clean it wrongly.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isOurGui(player.getOpenInventory().getTopInventory().getHolder())) {
                plugin.getGuiManager().closeGui(player);
            }
        });
    }

    // ── Identification ───────────────────────────────────────────────────────

    /** @return true if this holder belongs to one of our Tropicube GUIs. */
    public static boolean isOurGui(InventoryHolder holder) {
        return holder instanceof ServerTypeSelectorGUI.Holder
            || holder instanceof ServerSelectorGUI.Holder
            || holder instanceof LanguageSelectorGUI.Holder
            || holder instanceof SettingsGUI.Holder
            || holder instanceof VipShopGUI.Holder
            || holder instanceof CustomGameGUI.Holder
            || holder instanceof CustomGameTypeGUI.Holder
            || holder instanceof SocialGUI.Holder
            || holder instanceof FriendRequestsGUI.Holder
            || holder instanceof RankedSelectorGUI.Holder;
    }

    // ── Handlers ────────────────────────────────────────────────────────────

    private void handleTypeSelector(Player player, int slot, ServerTypeSelectorGUI.Holder typeHolder,
                                    ClickType click) {
        if (typeHolder.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        if (typeHolder.isCustomGameSlot(slot)) {
            if (typeHolder.isCustomGameAllowed()) plugin.getGuiManager().openCustomGameTypeMenu(player);
            else player.sendMessage(LangHelper.component(player, "lobby.selector-custom-locked-message"));
            return;
        }

        String type = typeHolder.getTypeForSlot(slot);
        if (type == null) return;

        switch (typeSelectorAction(click)) {
            case QUICK_PLAY -> {
                player.closeInventory();
                if ("sheepwars".equalsIgnoreCase(type)) player.performCommand("quickplay");
                else plugin.getLobbyServerManager().requestStartGame(player, type);
            }
            case RANKED -> plugin.getGuiManager().openRankedSelector(player, type);
            case PUBLIC_INSTANCES -> plugin.getGuiManager().openServerSelector(player, type, 0);
            case NONE -> {
            }
        }
    }

    /**
     * Resolves the game-menu action without depending on a live Paper inventory.
     * Shift-left must be checked before {@link ClickType#isLeftClick()} because Paper
     * includes it in left clicks. Middle click remains as a defensive compatibility path,
     * although the vanilla client only sends that action in creative mode.
     */
    static TypeSelectorAction typeSelectorAction(ClickType click) {
        if (click == ClickType.SHIFT_LEFT || click == ClickType.MIDDLE) {
            return TypeSelectorAction.PUBLIC_INSTANCES;
        }
        if (click.isLeftClick()) return TypeSelectorAction.QUICK_PLAY;
        if (click.isRightClick()) return TypeSelectorAction.RANKED;
        return TypeSelectorAction.NONE;
    }

    private void handleRankedSelector(Player player, int slot, RankedSelectorGUI.Holder holder) {
        if (slot == RankedSelectorGUI.CLOSE_SLOT) { player.closeInventory(); return; }
        if (slot == RankedSelectorGUI.BACK_SLOT) { plugin.getGuiManager().openServerTypeSelector(player); return; }
        if (slot == RankedSelectorGUI.CANCEL_SLOT) {
            plugin.getLobbyServerManager().cancelMatchmaking(player);
            player.sendMessage(LangHelper.component(player, "lobby.ranked-cancelled"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getGuiManager().openRankedSelector(player, "SHEEPWARS"), 2L);
            return;
        }
        String template = holder.templateAt(slot);
        if (template == null) return;
        player.closeInventory();
        player.performCommand("competitive " + (template.contains("4v4") ? "4v4" : "8v8"));
    }

    private void handleServerSelector(Player player, int slot, ServerSelectorGUI.Holder serverHolder) {
        switch (slot) {
            case ServerSelectorGUI.SLOT_CLOSE -> {
                player.closeInventory();
                return;
            }
            case ServerSelectorGUI.SLOT_BACK -> {
                plugin.getGuiManager().openServerTypeSelector(player);
                return;
            }
            case ServerSelectorGUI.SLOT_BEST -> {
                if (serverHolder.getFilter() == ServerSelectorGUI.Filter.CUSTOM) return;
                plugin.getLobbyServerManager().getBestQuickPlayServer(
                        serverHolder.getType(), player.getUniqueId()).ifPresentOrElse(
                        s -> {
                            player.closeInventory();
                            player.sendMessage(LangHelper.component(player, "lobby.connect", s.id()));
                            plugin.getLobbyServerManager().connectToServer(player, s.id());
                        },
                        () -> player.sendMessage(LangHelper.component(player, "lobby.no-server"))
                );
                return;
            }
            case ServerSelectorGUI.SLOT_FILTER -> {
                serverHolder.nextFilter();
                plugin.getGuiManager().openServerSelector(
                        player, serverHolder.getType(), 0, serverHolder.getFilter());
                return;
            }
            case ServerSelectorGUI.SLOT_PREV -> {
                if (serverHolder.hasPrevPage()) {
                    plugin.getGuiManager().openServerSelector(
                            player, serverHolder.getType(), serverHolder.getPage() - 1, serverHolder.getFilter());
                }
                return;
            }
            case ServerSelectorGUI.SLOT_NEXT -> {
                if (serverHolder.hasNextPage()) {
                    plugin.getGuiManager().openServerSelector(
                            player, serverHolder.getType(), serverHolder.getPage() + 1, serverHolder.getFilter());
                }
                return;
            }
        }

        // Click on a server
        String serverId = serverHolder.getServerForSlot(slot);
        if (serverId == null) return;

        plugin.getLobbyServerManager().getServer(serverId).ifPresentOrElse(
                s -> {
                    if (!s.isVisibleTo(player.getUniqueId())) {
                        player.sendMessage(LangHelper.component(player, "lobby.server-not-found"));
                        return;
                    }
                    if (!s.isOnline()) {
                        player.sendMessage(LangHelper.component(player, "lobby.server-offline"));
                        return;
                    }
                    if (s.isFull()) {
                        player.sendMessage(LangHelper.component(player, "lobby.server-full", serverId));
                        return;
                    }
                    if (!s.isJoinable()) {
                        player.sendMessage(LangHelper.component(player, "lobby.server-unavailable"));
                        return;
                    }
                    player.closeInventory();
                    player.sendMessage(LangHelper.component(player, "lobby.connect", serverId));
                    plugin.getLobbyServerManager().connectToServer(player, serverId);
                },
                () -> player.sendMessage(LangHelper.component(player, "lobby.server-not-found"))
        );
    }

    private void handleLanguageSelector(Player player, int slot) {
        if (slot == LanguageSelectorGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String langCode = LanguageSelectorGUI.getLangForSlot(slot);
        if (langCode == null) return;

        boolean changed = setPlayerLanguage(player, langCode);
        player.closeInventory();
        player.sendMessage(LangHelper.component(player, changed ? "lobby.lang-changed" : "lobby.lang-unavailable"));
        if (changed) {
            plugin.getPlayerLobbyListener().setupHotbar(player);
            plugin.getScoreboardManager().setup(player);
        }
    }

    private void handleSettings(Player player, int slot) {
        if (slot == SettingsGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == SettingsGUI.LANGUAGE_SLOT) {
            plugin.getGuiManager().openLanguageSelector(player);
            return;
        }
        if (slot == SettingsGUI.VISIBILITY_SLOT || slot == SettingsGUI.HINTS_SLOT
                || slot == SettingsGUI.PROFILE_VISIBILITY_SLOT || slot == SettingsGUI.MESSAGE_PRIVACY_SLOT
                || slot == SettingsGUI.GLOBAL_CHAT_SLOT || slot == SettingsGUI.EFFECTS_SLOT) {
            if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
            UUID playerId = player.getUniqueId();
            core.getPlayerPreferenceService().load(playerId).thenCompose(current -> {
                var visibility = current.lobbyVisibility();
                if (slot == SettingsGUI.VISIBILITY_SLOT) {
                    var values = fr.tropicube.core.network.PlayerPreferenceService.LobbyVisibility.values();
                    visibility = values[(visibility.ordinal() + 1) % values.length];
                }
                var profileVisibility = current.profileVisibility();
                if (slot == SettingsGUI.PROFILE_VISIBILITY_SLOT) {
                    var values = fr.tropicube.core.network.PlayerPreferenceService.ProfileVisibility.values();
                    profileVisibility = values[(profileVisibility.ordinal() + 1) % values.length];
                }
                var messagePrivacy = current.messagePrivacy();
                if (slot == SettingsGUI.MESSAGE_PRIVACY_SLOT) {
                    var values = fr.tropicube.core.network.PlayerPreferenceService.MessagePrivacy.values();
                    messagePrivacy = values[(messagePrivacy.ordinal() + 1) % values.length];
                }
                var updated = new fr.tropicube.core.network.PlayerPreferenceService.Preferences(
                        profileVisibility, messagePrivacy,
                        slot == SettingsGUI.GLOBAL_CHAT_SLOT ? !current.globalChatEnabled() : current.globalChatEnabled(),
                        visibility, slot == SettingsGUI.HINTS_SLOT ? !current.contextualHelp() : current.contextualHelp(),
                        slot == SettingsGUI.EFFECTS_SLOT ? !current.lobbyEffectsEnabled() : current.lobbyEffectsEnabled());
                return core.getPlayerPreferenceService().save(playerId, updated);
            }).thenRun(() -> {
                core.getRedisManager().publishPlayerEvent("PREFERENCES_CHANGED", playerId.toString());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) plugin.getGuiManager().openSettings(online);
                });
            });
            return;
        }
        if (slot != SettingsGUI.AUTO_REPLAY_SLOT) return;
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int current = plugin.getRedisManager().getAutoReplayRemaining(playerId);
            plugin.getRedisManager().setAutoReplay(playerId, current < 0, plugin.getAutoReplayBatchSize());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) plugin.getGuiManager().openSettings(online);
            });
        });
    }

    private void handleVipShop(Player player, int slot, VipShopGUI.Holder holder) {
        if (holder.view() == VipShopGUI.View.HOME) {
            if (slot == VipShopGUI.HOME_CLOSE_SLOT) player.closeInventory();
            else if (slot == VipShopGUI.HOME_GRADES_SLOT) plugin.getGuiManager().openVipGrades(player);
            return;
        }
        if (slot == VipShopGUI.GRADES_CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == VipShopGUI.GRADES_BACK_SLOT) {
            plugin.getGuiManager().openVipShop(player);
            return;
        }

        String gradeKey = VipShopGUI.getEntryForSlot(slot);
        if (gradeKey == null) return;

        var playerId = player.getUniqueId();
        player.closeInventory();
        player.sendMessage(LangHelper.component(player, "lobby.vip-processing"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String currentGrade = coreGrade(playerId);
            int price = VipShopGUI.getUpgradePrice(currentGrade, gradeKey);
            var result = new fr.tropicube.core.network.GradePurchaseService(getCore())
                    .purchase(playerId, currentGrade, gradeKey, price).join();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer == null) return;
                switch (result) {
                    case PURCHASED -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer, "lobby.vip-bought",
                            VipShopGUI.getDisplayNameForGrade(gradeKey)));
                    case INSUFFICIENT_FUNDS -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer,
                            "lobby.vip-no-funds", VipShopGUI.formatCoins(price)));
                    case ALREADY_OWNED -> { }
                    case STALE_GRADE, INVALID -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer,
                            "general.operation-failed"));
                }
                plugin.getGuiManager().openVipGrades(onlinePlayer);
            });
        });
    }

    private void handleCustomGame(Player player, int slot, CustomGameGUI.Holder holder) {
        if (holder.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        if (holder.isStopSlot(slot)) {
            player.closeInventory();
            player.sendMessage(LangHelper.component(player, "lobby.host-stop-requested"));
            plugin.getRedisManager().publishCommand("PROXY", "STOP_HOST:" + player.getUniqueId());
            return;
        }

        String templateId = holder.getTemplateForSlot(slot);
        if (templateId == null) return;

        if (plugin.getGuiManager().hasCustomGameOrCreation(player.getUniqueId())) {
            player.closeInventory();
            player.sendMessage(LangHelper.component(player, "lobby.host-already-exists"));
            return;
        }

        player.closeInventory();
        player.sendMessage(LangHelper.component(player, "lobby.custom-game-creating"));
        plugin.getRedisManager().publishCommand("PROXY", "CREATE_HOST:" + player.getUniqueId() + ":" + templateId + ":" + holder.isWhitelisted());
    }

    private void handleCustomGameType(Player player, int slot, CustomGameTypeGUI.Holder holder) {
        if(holder.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        if (holder.isPublicGameSlot(slot)) {
            plugin.getGuiManager().openCustomGameMenu(player, false);
            return;
        }

        if (holder.isPrivateGameSlot(slot)) {
            plugin.getGuiManager().openCustomGameMenu(player, true);
        }

    }

    private void handleSocial(Player player, int slot, SocialGUI.Holder holder, ClickType click) {
        if (slot == SocialGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        SocialGUI.Action action = holder.action(slot, click);
        if (action == null) return;
        switch (action.type()) {
            case FRIEND_JOIN -> player.performCommand("friend join " + action.argument());
            case PARTY_INVITE -> player.performCommand("party invite " + action.argument());
            case OPEN_FRIEND_REQUESTS -> plugin.getGuiManager().openFriendRequests(player, 0);
            case PARTY_ACCEPT -> player.performCommand("party accept " + action.argument());
            case FOLLOW_TOGGLE -> player.performCommand("party follow " + action.argument());
            case PARTY_WARP -> player.performCommand("party warp");
        }
        if (action.type() == SocialGUI.ActionType.OPEN_FRIEND_REQUESTS) {
            return;
        }
        if (action.type() == SocialGUI.ActionType.FRIEND_JOIN || action.type() == SocialGUI.ActionType.PARTY_WARP) {
            player.closeInventory();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getGuiManager().openSocial(player), 5L);
        }
    }

    private void handleFriendRequests(Player player, int slot, FriendRequestsGUI.Holder holder, ClickType click) {
        if (slot == FriendRequestsGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        if (slot == FriendRequestsGUI.BACK_SLOT) {
            plugin.getGuiManager().openSocial(player);
            return;
        }
        if (slot == FriendRequestsGUI.PREVIOUS_SLOT && holder.hasPrevious()) {
            plugin.getGuiManager().openFriendRequests(player, holder.page() - 1);
            return;
        }
        if (slot == FriendRequestsGUI.NEXT_SLOT && holder.hasNext()) {
            plugin.getGuiManager().openFriendRequests(player, holder.page() + 1);
            return;
        }
        FriendRequestsGUI.Action action = holder.action(slot, click);
        if (action == null) return;
        switch (action.type()) {
            case ACCEPT -> player.performCommand("friend accept " + action.username());
            case DENY -> player.performCommand("friend deny " + action.username());
            case CANCEL -> player.performCommand("friend cancel " + action.username());
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> plugin.getGuiManager().openFriendRequests(player, holder.page()), 5L);
    }

    /** @return true if the language has been changed. */
    private boolean setPlayerLanguage(Player player, String lang) {
        TropicubeCore core = getCore();
        if (core == null) return false;
        try {
            core.getLanguageManager().setPlayerLanguage(player.getUniqueId(), lang, true);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning(MessageStyle.log("tc", "GUI_CLICK", "<yellow>Erreur changement de langue : " + ex.getMessage()));
            return false;
        }
    }

    private String coreGrade(UUID playerId) {
        TropicubeCore core = getCore();
        return core == null ? "JOUEUR" : core.getPermissionManager().getGrade(playerId);
    }

    private TropicubeCore getCore() {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return corePlugin instanceof TropicubeCore core ? core : null;
    }
}
