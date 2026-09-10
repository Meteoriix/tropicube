package fr.tropicube.lobby.gui;

import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Lobby-owned discovery and cosmetic screens. Inventory identity guards all asynchronous completions. */
public final class GameDiscoveryMenus implements Listener, AutoCloseable {
    private volatile boolean closed;
    private final TropicubeLobby plugin;
    public GameDiscoveryMenus(TropicubeLobby plugin) {
        this.plugin = plugin;
        for (String id : List.of("game-modes", "player-guide", "player-progression", "player-loading")) {
            if (plugin.getMenuTemplates().menu(id).rows() != 6)
                throw new IllegalArgumentException("menus." + id + ".rows: expected 6 for discovery navigation");
        }
    }
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    /** Holder binds actions to this exact rendered snapshot, never to an item supplied by a player. */
    private static final class Screen implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Runnable refresh;
        private final fr.tropicube.core.ui.MenuSession session = new fr.tropicube.core.ui.MenuSession();
        public Inventory getInventory() { return inventory; }
    }
    private Screen screen(Player player, String template, Runnable refresh, Runnable back) {
        var definition = plugin.getMenuTemplates().menu(template);
        Screen screen = new Screen(); screen.refresh = refresh;
        screen.inventory = Bukkit.createInventory(screen, definition.rows() * 9, LangHelper.component(player, definition.titleKey()));
        NetworkMenuStyle.frame(screen.inventory, player);
        button(player, screen, 45, Material.ARROW, "cosmetics.back", back);
        button(player, screen, 53, Material.BARRIER, "cosmetics.close", player::closeInventory);
        player.openInventory(screen.inventory);
        return screen;
    }
    private void button(Player player, Screen screen, int slot, Material material, String key, Runnable action, Object... args) {
        screen.inventory.setItem(slot, NetworkMenuStyle.item(material, LangHelper.component(player, key, args),
                LangHelper.component(player, "cosmetics.action", NetworkMenuStyle.actionPlaceholders(LangHelper.component(player, key, args)))));
        screen.actions.put(slot, action);
    }
    /** Reads progression anew on every opening; closing invalidates the pending inventory. */
    public void openProgression(Player player) {
        Screen screen = screen(player, "player-progression", () -> openProgression(player),
                () -> plugin.getCore().getPlayerCenterMenu().openHome(player));
        screen.inventory.setItem(22, NetworkMenuStyle.item(Material.CLOCK, LangHelper.component(player, "cosmetics.loading")));
        plugin.getCore().getNetworkProgressionService().get(player.getUniqueId()).whenComplete((progress, error) -> onServer(() -> {
            if (!current(player, screen)) return;
            if (error != null) { failure(player, screen, error); return; }
            screen.inventory.setItem(22, null);
            screen.inventory.setItem(4, NetworkMenuStyle.item(Material.EXPERIENCE_BOTTLE,
                    LangHelper.component(player, "cosmetics.progress", progress.level(),
                            fr.tropicube.core.progression.NetworkProgressionService.experienceToNextLevel(progress.experience()))));
            button(player, screen, 49, Material.WRITABLE_BOOK, "center.missions", () -> plugin.getCore().getPlayerCenterMenu().openMissions(player));
            var upcoming = plugin.getCore().getCosmeticCatalog().upcoming(progress.level());
            if (upcoming.isEmpty()) screen.inventory.setItem(22, NetworkMenuStyle.item(Material.PAPER,
                    LangHelper.component(player, "cosmetics.progress-complete")));
            for (int index = 0; index < Math.min(CONTENT.length, upcoming.size()); index++) {
                var entry = upcoming.get(index);
                screen.inventory.setItem(CONTENT[index], NetworkMenuStyle.item(
                        entry.category() == fr.tropicube.core.cosmetic.CosmeticCatalog.Category.TRAIL ? Material.FEATHER : Material.NOTE_BLOCK,
                        LangHelper.component(player, entry.nameKey()), LangHelper.component(player, "cosmetics.access-level", entry.requirement())));
            }
        }));
    }
    public void openGuide(Player player) { openGuide(player, null); }
    private void openGuide(Player player, String topic) {
        Screen screen = screen(player, "player-guide", () -> openGuide(player, topic),
                topic == null ? () -> plugin.getCore().getPlayerCenterMenu().openHome(player) : () -> openGuide(player));
        String[] topics = {"play", "progress", "social", "customize"};
        Runnable[] actions = {() -> plugin.getGuiManager().openServerTypeSelector(player),
                () -> openProgression(player),
                () -> plugin.getGuiManager().openSocial(player), () -> plugin.getCosmeticMenus().openWardrobe(player)};
        for (int index = 0; index < topics.length; index++) {
            String selected = topics[index];
            if (topic == null) button(player, screen, 19 + index*2, Material.BOOK, "cosmetics.guide-" + selected, () -> openGuide(player, selected));
            else if (topic.equals(selected)) {
                screen.inventory.setItem(13, NetworkMenuStyle.item(Material.BOOK, LangHelper.component(player, "cosmetics.guide-"+selected),
                        LangHelper.component(player, "cosmetics.help-"+selected)));
                button(player, screen, 22, Material.COMPASS, "cosmetics.open-"+selected, actions[index]);
            }
        }
    }
    public void openModes(Player player, String type) {
        Screen screen = screen(player, "game-modes", () -> openModes(player, type), () -> plugin.getGuiManager().openServerTypeSelector(player));
        if (plugin.getLobbyServerManager().getTemplateIdForType(type).isPresent()) button(player, screen, 20, Material.COMPASS, "cosmetics.quick", () -> {
            player.closeInventory();
            if ("sheepwars".equalsIgnoreCase(type)) player.performCommand("quickplay");
            else plugin.getLobbyServerManager().requestStartGame(player, type);
        });
        else screen.inventory.setItem(20, NetworkMenuStyle.locked(LangHelper.component(player, "cosmetics.quick"), LangHelper.component(player, "cosmetics.mode-unavailable")));
        if (!plugin.getLobbyServerManager().getRankedTemplatesForType(type).isEmpty()) button(player, screen, 22, Material.IRON_SWORD, "cosmetics.ranked", () -> plugin.getGuiManager().openRankedSelector(player, type));
        else screen.inventory.setItem(22, NetworkMenuStyle.locked(LangHelper.component(player, "cosmetics.ranked"), LangHelper.component(player, "cosmetics.mode-unavailable")));
        button(player, screen, 24, Material.SPYGLASS, "cosmetics.browse", () -> plugin.getGuiManager().openServerSelector(player, type, 0));
    }
    public void refreshLanguage(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Screen screen) screen.refresh.run();
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Screen screen)) return;
        event.setCancelled(true);
        if (!screen.session.canInteract() || event.getClick() != ClickType.LEFT || event.getRawSlot() < 0 || event.getRawSlot() >= screen.inventory.getSize()) return;
        Runnable action = screen.actions.get(event.getRawSlot());
        if (action != null) action.run();
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Screen) event.setCancelled(true);
    }
    private boolean current(Player player, Screen screen) { return !closed && screen.session.isOpen() && player.isOnline() && player.getOpenInventory().getTopInventory() == screen.inventory; }
    private void onServer(Runnable action) { if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (!closed && plugin.isEnabled()) action.run(); }); }
    /** A visible, cancellable request token for shared Settings, Grades and Ranked adapters. */
    public Inventory loading(Player player, Runnable retry, Runnable back) {
        Screen screen = screen(player, "player-loading", retry, back);
        screen.inventory.setItem(22, NetworkMenuStyle.loading(LangHelper.component(player, "cosmetics.loading"),
                LangHelper.component(player, "cosmetics.back")));
        return screen.inventory;
    }
    public void loadFailed(Player player, Inventory expected, Throwable error) {
        if (expected.getHolder() instanceof Screen screen && current(player, screen)) failure(player, screen, error);
    }
    @EventHandler public void inventoryClosed(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Screen screen) screen.session.close();
    }
    @Override public void close() {
        closed = true;
        org.bukkit.event.HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Screen screen) {
                screen.session.close();
                player.closeInventory();
            }
        }
    }
    private void failure(Player player, Screen screen, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Player menu failed for " + player.getUniqueId(), error);
        button(player, screen, 22, Material.RED_DYE, "cosmetics.retry", screen.refresh);
    }
}
