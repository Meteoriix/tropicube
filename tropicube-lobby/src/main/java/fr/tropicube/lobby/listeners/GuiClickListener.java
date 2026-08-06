package fr.tropicube.lobby.listeners;

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

/**
 * Gère tous les clics dans les GUI Tropicube Lobby.
 *
 * <p>La détection des GUI repose sur le {@link InventoryHolder} custom de chaque inventaire
 * plutôt que sur la map {@code openGuis} du GuiManager. Cela garantit que l'événement est
 * toujours annulé dès qu'un de nos inventaires est visible, même si la map serait désynchronisée.
 */
public class GuiClickListener implements Listener {

    private final TropicubeLobby plugin;

    public GuiClickListener(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    // ── Événements d'inventaire ──────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // Identification par holder — fiable même si la map GuiManager est désynchronisée.
        InventoryHolder holder = e.getInventory().getHolder();
        if (!isOurGui(holder)) return;

        // Annuler AVANT tout filtrage : empêche le ramassage d'items, shift-click, etc.
        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;

        // Clic dans la partie basse (inventaire du joueur) → annulé mais sans action GUI.
        if (e.getClickedInventory() == null || e.getClickedInventory() == player.getInventory()) return;

        int slot = e.getRawSlot();

        switch (holder) {
            case ServerTypeSelectorGUI.Holder typeHolder -> handleTypeSelector(player, slot, typeHolder, e.getClick().isLeftClick());
            case ServerSelectorGUI.Holder serverHolder -> handleServerSelector(player, slot, serverHolder);
            case LanguageSelectorGUI.Holder _ -> handleLanguageSelector(player, slot);
            case VipShopGUI.Holder _ -> handleVipShop(player, slot);
            case CustomGameGUI.Holder customHolder -> handleCustomGame(player, slot, customHolder);
            case CustomGameTypeGUI.Holder customTypeHolder -> handleCustomGameType(player, slot, customTypeHolder);
            default -> {
            }
        }
    }

    /** Empêche le drag d'items dans nos inventaires. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (isOurGui(e.getInventory().getHolder())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!isOurGui(e.getView().getTopInventory().getHolder())) return;

        // Différé d'un tick : si un autre GUI s'ouvre dans la même transition (ex: TypeSelector → ServerSelector),
        // son holder sera visible au tick suivant → on ne nettoie pas à tort.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!isOurGui(player.getOpenInventory().getTopInventory().getHolder())) {
                plugin.getGuiManager().closeGui(player);
            }
        });
    }

    // ── Identification ───────────────────────────────────────────────────────

    /** @return true si ce holder appartient à l'un de nos GUI Tropicube. */
    public static boolean isOurGui(InventoryHolder holder) {
        return holder instanceof ServerTypeSelectorGUI.Holder
            || holder instanceof ServerSelectorGUI.Holder
            || holder instanceof LanguageSelectorGUI.Holder
            || holder instanceof VipShopGUI.Holder
            || holder instanceof CustomGameGUI.Holder
            || holder instanceof CustomGameTypeGUI.Holder;
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
            plugin.getLobbyServerManager().getBestServer(type).ifPresentOrElse(
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
                plugin.getLobbyServerManager().getBestServer(serverHolder.getType()).ifPresentOrElse(
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

        // Clic sur un serveur
        String serverId = serverHolder.getServerForSlot(slot);
        if (serverId == null) return;

        plugin.getLobbyServerManager().getServer(serverId).ifPresentOrElse(
                s -> {
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

    // Achat atomique : vérification, débit, attribution, puis compensation si nécessaire.

    /** @return true si la langue a bien été changée. */
    private boolean setPlayerLanguage(Player player, String lang) {
        TropicubeCore core = getCore();
        if (core == null) return false;
        try {
            core.getLanguageManager().setPlayerLanguage(player.getUniqueId(), lang, true);
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Erreur changement de langue : " + ex.getMessage());
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
            plugin.getLogger().warning("Erreur achat grade : " + ex.getMessage());
            if (withdrawn) {
                try {
                    economyManager.deposit(playerId, price,
                            "Remboursement achat grade " + gradeKey);
                } catch (RuntimeException rollbackError) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Échec du remboursement après l'achat du grade " + gradeKey, rollbackError);
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
