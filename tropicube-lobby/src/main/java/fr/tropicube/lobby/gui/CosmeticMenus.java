package fr.tropicube.lobby.gui;

import fr.tropicube.core.cosmetic.CosmeticCatalog;
import fr.tropicube.core.cosmetic.CosmeticService;
import fr.tropicube.core.cosmetic.CosmeticService.Filter;
import fr.tropicube.core.ui.MenuSession;
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
public final class CosmeticMenus implements Listener, AutoCloseable {
    private volatile boolean closed;
    private final TropicubeLobby plugin;
    private final CosmeticService cosmetics;
    public CosmeticMenus(TropicubeLobby plugin) {
        this.plugin = plugin;
        this.cosmetics = plugin.getCore().getCosmeticService();
        for (String id : List.of("wardrobe", "cosmetic-detail", "cosmetic-confirm")) {
            if (plugin.getMenuTemplates().menu(id).rows() != 6)
                throw new IllegalArgumentException("menus." + id + ".rows: expected 6 for wardrobe navigation");
        }
    }
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    /** Holder binds actions to this exact rendered snapshot, never to an item supplied by a player. */
    private static final class Screen implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Runnable refresh;
        private final MenuSession session = new MenuSession();
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
    private void load(Player player, Screen screen, java.util.function.Consumer<CosmeticService.Snapshot> success) {
        screen.inventory.setItem(22, NetworkMenuStyle.item(Material.CLOCK, LangHelper.component(player, "cosmetics.loading")));
        cosmetics.snapshot(player.getUniqueId()).whenComplete((snapshot, error) -> onServer(() -> {
            if (!current(player, screen)) return;
            screen.inventory.setItem(22, null);
            if (error != null) { failure(player, screen, error); return; }
            success.accept(snapshot);
        }));
    }
    /** Opens the shared catalogue with an explicit return to Profil. */
    public void openWardrobe(Player player) {
        openWardrobe(player, CosmeticCatalog.Category.TRAIL, Filter.ALL, 0, () -> plugin.getCore().getPlayerCenterMenu().openHome(player));
    }
    /** Boutique uses the exact same catalogue and retains its own return path. */
    public void openShop(Player player) {
        openWardrobe(player, CosmeticCatalog.Category.TRAIL, Filter.ALL, 0, () -> plugin.getGuiManager().openVipShop(player));
    }
    private void openWardrobe(Player player, CosmeticCatalog.Category category, Filter filter, int page, Runnable back) {
        Runnable refresh = () -> openWardrobe(player, category, filter, page, back);
        Screen screen = screen(player, "wardrobe", refresh, back);
        load(player, screen, snapshot -> {
            button(player, screen, 2, Material.FEATHER, "cosmetics.trails", () -> openWardrobe(player, CosmeticCatalog.Category.TRAIL, filter, 0, back));
            button(player, screen, 6, Material.NOTE_BLOCK, "cosmetics.sounds", () -> openWardrobe(player, CosmeticCatalog.Category.SOUND, filter, 0, back));
            button(player, screen, 47, Material.HOPPER, "cosmetics.filter-" + filter.name().toLowerCase(Locale.ROOT),
                    () -> openWardrobe(player, category, Filter.values()[(filter.ordinal()+1)%Filter.values().length], 0, back));
            button(player, screen, 49, Material.MILK_BUCKET, "cosmetics.clear", () -> mutate(player, screen,
                    () -> cosmetics.equip(player.getUniqueId(), category, null)));
            var entries = snapshot.entries(cosmetics.catalog(), category, filter);
            int lastPage = Math.max(0, (entries.size()-1)/CONTENT.length);
            int safePage = Math.min(page, lastPage);
            if (entries.isEmpty()) screen.inventory.setItem(22, NetworkMenuStyle.item(Material.GRAY_DYE, LangHelper.component(player, "cosmetics.empty")));
            for (int index = safePage*CONTENT.length; index < Math.min(entries.size(), (safePage+1)*CONTENT.length); index++) {
                var entry = entries.get(index);
                int slot = CONTENT[index % CONTENT.length];
                boolean equipped = entry.id().equals(snapshot.equipped().get(category));
                screen.inventory.setItem(slot, NetworkMenuStyle.item(snapshot.available(entry) ? icon(category) : Material.GRAY_DYE,
                        LangHelper.component(player, entry.nameKey()),
                        LangHelper.component(player, accessKey(entry), fr.tropicube.language.PlaceholderValues.of("requirement", entry.requirement())),
                        LangHelper.component(player, equipped ? (snapshot.available(entry) ? "cosmetics.equipped" : "cosmetics.suspended") : snapshot.available(entry) ? "cosmetics.available" : "cosmetics.locked"),
                        LangHelper.component(player, "cosmetics.details-action")));
                screen.actions.put(slot, () -> detail(player, entry, refresh));
            }
            if (safePage > 0) button(player, screen, 48, Material.ARROW, "cosmetics.previous", () -> openWardrobe(player, category, filter, safePage-1, back));
            if (safePage < lastPage) button(player, screen, 50, Material.ARROW, "cosmetics.next", () -> openWardrobe(player, category, filter, safePage+1, back));
        });
    }
    private void detail(Player player, CosmeticCatalog.Entry entry, Runnable back) {
        Screen screen = screen(player, "cosmetic-detail", () -> detail(player, entry, back), back);
        load(player, screen, snapshot -> {
            screen.inventory.setItem(13, NetworkMenuStyle.item(icon(entry.category()), LangHelper.component(player, entry.nameKey()),
                    LangHelper.component(player, accessKey(entry), fr.tropicube.language.PlaceholderValues.of("requirement", entry.requirement())),
                    LangHelper.component(player, entry.id().equals(snapshot.equipped().get(entry.category()))
                            ? snapshot.available(entry) ? "cosmetics.equipped" : "cosmetics.suspended"
                            : snapshot.available(entry) ? "cosmetics.available" : "cosmetics.locked")));
            button(player, screen, 20, Material.SPYGLASS, "cosmetics.preview", () -> plugin.getCosmeticEffects().preview(player, entry));
            var preview = screen.inventory.getItem(20);
            preview.editMeta(meta -> {
                var lore = new java.util.ArrayList<>(meta.lore());
                lore.add(entry.category() == CosmeticCatalog.Category.TRAIL
                        ? LangHelper.component(player, "cosmetics.preview-trail-info", plugin.getCosmeticEffects().previewSeconds())
                        : LangHelper.component(player, "cosmetics.preview-sound-info"));
                meta.lore(lore);
            });
            if (snapshot.available(entry) || entry.id().equals(snapshot.equipped().get(entry.category()))) {
                boolean selected = entry.id().equals(snapshot.equipped().get(entry.category()));
                button(player, screen, 24, Material.LIME_DYE, selected ? "cosmetics.clear" : "cosmetics.equip",
                        () -> mutate(player, screen, () -> cosmetics.equip(player.getUniqueId(), entry.category(), selected ? null : entry.id())));
            } else if (entry.access() == CosmeticCatalog.Access.CURRENCY) {
                button(player, screen, 24, Material.GOLD_INGOT, "cosmetics.buy", () -> confirm(player, entry, back), entry.requirement());
            } else screen.inventory.setItem(24, NetworkMenuStyle.item(Material.GRAY_DYE, LangHelper.component(player, "cosmetics.locked"),
                    LangHelper.component(player, accessKey(entry), fr.tropicube.language.PlaceholderValues.of("requirement", entry.requirement()))));
        });
    }
    private void confirm(Player player, CosmeticCatalog.Entry entry, Runnable back) {
        Screen screen = screen(player, "cosmetic-confirm", () -> confirm(player, entry, back), () -> detail(player, entry, back));
        load(player, screen, snapshot -> {
            screen.inventory.setItem(13, NetworkMenuStyle.item(icon(entry.category()), LangHelper.component(player, entry.nameKey()),
                    LangHelper.component(player, "cosmetics.price", entry.requirement(), snapshot.balance(),
                            snapshot.balance().subtract(java.math.BigDecimal.valueOf(entry.requirement()))),
                    LangHelper.component(player, "cosmetics.purchase-terms")));
            if (snapshot.purchased().contains(entry.id())) {
                screen.inventory.setItem(22, NetworkMenuStyle.item(Material.LIME_DYE, LangHelper.component(player, "cosmetics.result-owned")));
            } else if (snapshot.balance().compareTo(java.math.BigDecimal.valueOf(entry.requirement())) < 0) {
                screen.inventory.setItem(22, NetworkMenuStyle.item(Material.GRAY_DYE, LangHelper.component(player, "cosmetics.result-insufficient-funds")));
            } else button(player, screen, 22, Material.EMERALD, "cosmetics.confirm",
                    () -> mutate(player, screen, () -> cosmetics.buy(player.getUniqueId(), entry.id(), entry.requirement()), () -> detail(player, entry, back)));
        });
    }
    private void mutate(Player player, Screen screen, java.util.function.Supplier<CompletableFuture<CosmeticService.Result>> operation) {
        mutate(player, screen, operation, screen.refresh);
    }
    private void mutate(Player player, Screen screen, java.util.function.Supplier<CompletableFuture<CosmeticService.Result>> operation,
                        Runnable afterSuccess) {
        if (!screen.session.beginAction()) return;
        CompletableFuture<CosmeticService.Result> future;
        try { future = operation.get(); }
        catch (RuntimeException error) {
            screen.session.completeAction();
            failure(player, screen, error);
            return;
        }
        future.whenComplete((result, error) -> onServer(() -> {
            if (error == null && player.isOnline()) plugin.getCosmeticEffects().refresh(player);
            if (!current(player, screen)) return;
            if (!screen.session.completeAction()) return;
            if (error != null) { failure(player, screen, error); return; }
            player.sendMessage(LangHelper.component(player, "cosmetics.result-" + result.name().toLowerCase(Locale.ROOT).replace('_','-')));
            if (result == CosmeticService.Result.SUCCESS || result == CosmeticService.Result.OWNED) afterSuccess.run();
            else screen.refresh.run();
        }));
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
    private void onServer(Runnable action) { if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); }); }
    @EventHandler public void inventoryClosed(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Screen screen) screen.session.close();
    }
    /** Disables callbacks before Paper cancels the plugin tasks. */
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
    private static Material icon(CosmeticCatalog.Category category) { return category == CosmeticCatalog.Category.TRAIL ? Material.FEATHER : Material.NOTE_BLOCK; }
    private static String accessKey(CosmeticCatalog.Entry entry) { return "cosmetics.access-" + entry.access().name().toLowerCase(Locale.ROOT); }
}
