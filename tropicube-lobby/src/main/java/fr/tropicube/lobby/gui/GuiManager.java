package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralise l'ouverture et le suivi des menus GUI ouverts par joueur.
 */
public class GuiManager {

    private final TropicubeLobby plugin;

    /** Associe le joueur au type de GUI ouvert, pour router les clics. */
    private final Map<UUID, GuiType> openGuis = new ConcurrentHashMap<>();

    public GuiManager(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    public enum GuiType {
        SERVER_SELECTOR,
        LANGUAGE_SELECTOR,
        VIP_SHOP,
        SERVER_TYPE_SELECTOR,
        CUSTOM_GAME,
        CUSTOM_GAME_TYPE_SELECTOR
    }

    // ── Ouverture des menus ──────────────────────────────────────────────────

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
                        "Impossible de charger la boutique VIP pour " + playerId, exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player onlinePlayer = Bukkit.getPlayer(playerId);
                    if (onlinePlayer != null) {
                        onlinePlayer.sendMessage(LangHelper.component(onlinePlayer, "general.operation-failed"));
                    }
                });
            }
        });
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

    /** Indique si le joueur possède déjà un serveur personnalisé ou si sa création est réservée. */
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
    }

    public void onPlayerQuit(UUID playerId) {
        openGuis.remove(playerId);
    }

    /**
     * Actualise sur place tous les menus de serveurs ouverts, sans rouvrir les
     * inventaires. Cette méthode doit être appelée sur le thread principal.
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
