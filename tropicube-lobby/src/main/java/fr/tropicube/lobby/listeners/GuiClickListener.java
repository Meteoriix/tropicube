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
            case ServerTypeSelectorGUI.Holder typeHolder -> handleTypeSelector(player, slot, typeHolder, e.getClick().isLeftClick());
            case ServerSelectorGUI.Holder serverHolder -> handleServerSelector(player, slot, serverHolder);
            case LanguageSelectorGUI.Holder _ -> handleLanguageSelector(player, slot);
            case SettingsGUI.Holder _ -> handleSettings(player, slot);
            case VipShopGUI.Holder _ -> handleVipShop(player, slot);
            case CustomGameGUI.Holder customHolder -> handleCustomGame(player, slot, customHolder);
            case CustomGameTypeGUI.Holder customTypeHolder -> handleCustomGameType(player, slot, customTypeHolder);
            case SocialGUI.Holder socialHolder -> handleSocial(player, slot, socialHolder);
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
            || holder instanceof SocialGUI.Holder;
    }

    // ── Handlers ────────────────────────────────────────────────────────────

    private void handleTypeSelector(Player player, int slot, ServerTypeSelectorGUI.Holder typeHolder, boolean leftClick) {
        if (typeHolder.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        String type = typeHolder.getTypeForSlot(slot);
        if (type == null) return;

        if (leftClick) {
            plugin.getLobbyServerManager().getBestServer(type, player.getUniqueId()).ifPresentOrElse(
                    s -> {
                        player.closeInventory();
                        player.sendMessage(LangHelper.component(player, "lobby.connect", s.id()));
                        plugin.getLobbyServerManager().connectToServer(player, s.id());
                    },
                    () -> {
                        player.closeInventory();
                        player.sendMessage(LangHelper.component(player, "lobby.game-queued"));
                        plugin.getLobbyServerManager().requestStartGame(player, type);
                    }
            );
        } else {
            plugin.getGuiManager().openServerSelector(player, type, 0);
        }
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
                plugin.getLobbyServerManager().getBestServer(
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
            case ServerSelectorGUI.SLOT_PREV -> {
                if (serverHolder.hasPrevPage()) {
                    plugin.getGuiManager().openServerSelector(
                            player, serverHolder.getType(), serverHolder.getPage() - 1);
                }
                return;
            }
            case ServerSelectorGUI.SLOT_NEXT -> {
                if (serverHolder.hasNextPage()) {
                    plugin.getGuiManager().openServerSelector(
                            player, serverHolder.getType(), serverHolder.getPage() + 1);
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

    private void handleVipShop(Player player, int slot) {
        if (slot == VipShopGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        String gradeKey = VipShopGUI.getEntryForSlot(slot);
        if (gradeKey == null) return;

        int price = VipShopGUI.getPriceForGrade(gradeKey);
        if (price < 0) return;

        var playerId = player.getUniqueId();
        player.closeInventory();
        player.sendMessage(LangHelper.component(player, "lobby.vip-processing"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PurchaseResult result = purchaseGrade(playerId, gradeKey, price);
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(playerId);
                if (onlinePlayer == null) return;
                switch (result) {
                    case PURCHASED -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer, "lobby.vip-bought",
                            VipShopGUI.getDisplayNameForGrade(gradeKey)));
                    case INSUFFICIENT_FUNDS -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer,
                            "lobby.vip-no-funds", VipShopGUI.formatCoins(price)));
                    case ALREADY_OWNED -> { }
                    case FAILED -> onlinePlayer.sendMessage(LangHelper.component(onlinePlayer,
                            "general.operation-failed"));
                }
                plugin.getGuiManager().openVipShop(onlinePlayer);
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

    private void handleSocial(Player player, int slot, SocialGUI.Holder holder) {
        if (slot == SocialGUI.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        SocialGUI.Action action = holder.action(slot);
        if (action == null) return;
        switch (action.type()) {
            case FRIEND_JOIN -> player.performCommand("friend join " + action.argument());
            case FRIEND_ACCEPT -> player.performCommand("friend accept " + action.argument());
            case PARTY_ACCEPT -> player.performCommand("party accept " + action.argument());
            case FOLLOW_TOGGLE -> player.performCommand("party follow " + action.argument());
            case PARTY_WARP -> player.performCommand("party warp");
        }
        if (action.type() == SocialGUI.ActionType.FRIEND_JOIN || action.type() == SocialGUI.ActionType.PARTY_WARP) {
            player.closeInventory();
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.getGuiManager().openSocial(player), 5L);
        }
    }

    // Atomic purchase: verification, debit, allocation, then compensation if necessary.

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

    private PurchaseResult purchaseGrade(java.util.UUID playerId, String gradeKey, int price) {
        TropicubeCore core = getCore();
        if (core == null) return PurchaseResult.FAILED;
        var economyManager = core.getEconomyManager();
        boolean withdrawn = false;
        try {
            String currentGrade = core.getPermissionManager().getGrade(playerId);
            if (VipShopGUI.isGradeOwned(currentGrade, gradeKey)) return PurchaseResult.ALREADY_OWNED;
            double balance = economyManager.getBalance(playerId);
            if (balance < price) return PurchaseResult.INSUFFICIENT_FUNDS;
            withdrawn = economyManager.withdraw(playerId, price, "Achat grade " + gradeKey);
            if (!withdrawn) return PurchaseResult.INSUFFICIENT_FUNDS;
            core.getPermissionManager().setGrade(playerId, gradeKey, 0L);
            return PurchaseResult.PURCHASED;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning(MessageStyle.log("tc", "GUI_CLICK", "<yellow>Erreur achat grade : " + ex.getMessage()));
            if (withdrawn) {
                try {
                    economyManager.deposit(playerId, price,
                            "Remboursement achat grade " + gradeKey);
                } catch (RuntimeException rollbackError) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            MessageStyle.log("tc", "GUI_CLICK", "<red>Échec du remboursement après l'achat du grade " + gradeKey), rollbackError);
                }
            }
            return PurchaseResult.FAILED;
        }
    }

    private enum PurchaseResult {
        PURCHASED,
        ALREADY_OWNED,
        INSUFFICIENT_FUNDS,
        FAILED
    }

    private TropicubeCore getCore() {
        var corePlugin = Bukkit.getPluginManager().getPlugin("TropicubeCore");
        return corePlugin instanceof TropicubeCore core ? core : null;
    }
}
