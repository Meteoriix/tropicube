package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.sheepwars.game.GameMap;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Organizes the players' vote for the map of the next game. */
public class MapSelectionMenu implements Listener {

    private final TropicubeSheepwars plugin;
    public final NamespacedKey menuItemKey;

    /** playerUuid → the map they voted for (or host-selected map when vote disabled) */
    private final Map<UUID, GameMap> votes = new HashMap<>();
    private final Map<UUID, Integer> voteWeights = new HashMap<>();
    private final Map<UUID, OpenMenu> openMenus = new HashMap<>();

    public MapSelectionMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "map_vote_item");
    }

    // ── Hotbar item ────────────────────────────────────────────────────────

    public ItemStack createSelectorItem(Player player) {
        boolean isVote = plugin.getGameSettingsMenu().isMapVoteEnabled();
        String nameKey = isVote ? "sw.map-vote-item-name" : "sw.map-pick-item-name";
        String loreKey = isVote ? "sw.map-vote-item-lore" : "sw.map-pick-item-lore";
        return new ItemBuilder(Material.FILLED_MAP)
                .name(LangHelper.component(player, nameKey).decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, loreKey).decoration(TextDecoration.ITALIC, false))
                .persistentData(menuItemKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isSelectorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(menuItemKey, PersistentDataType.BYTE);
    }

    // ── Open routing ───────────────────────────────────────────────────────

    public void open(Player player) {
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        if (maps.isEmpty()) {
            player.sendMessage(LangHelper.component(player, "sw.cmd-arena-not-ready"));
            return;
        }
        if (plugin.getGameSettingsMenu().isMapVoteEnabled()) {
            openVoteMenu(player, maps);
        } else if (player.getUniqueId().equals(plugin.getGameManager().getHostUuid())) {
            openPickMenu(player, maps);
        }
        // Without voting, only the host sees the object and can open this menu.
    }

    // ── Vote menu (all players, vote mode enabled) ─────────────────────────

    private void openVoteMenu(Player player, List<GameMap> maps) {
        openMapMenu(player, maps, true, 0);
    }

    private void openMapMenu(Player player, List<GameMap> maps, boolean voteMode, int requestedPage) {
        String menuId = voteMode ? "map-vote" : "map-pick";
        var template = plugin.getMenuTemplates().menu(menuId);
        List<Integer> slots = template.dynamicRegion("maps").slots();
        int pageCount = Math.max(1, (maps.size() + slots.size() - 1) / slots.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        Inventory inv = Bukkit.createInventory(null, LangHelper.menuSize(menuId),
                LangHelper.menuTitle(player, menuId));
        NetworkMenuStyle.applyFrame(inv, player, LangHelper.menuFrame(menuId));

        GameMap myVote = votes.get(player.getUniqueId());
        Map<GameMap, Integer> counts = MapVoteTally.counts(maps, votes, voteWeights);

        int offset = page * slots.size();
        for (int index = 0; index < slots.size() && offset + index < maps.size(); index++) {
            GameMap map = maps.get(offset + index);
            inv.setItem(slots.get(index), voteMode
                    ? voteItem(player, map, counts.getOrDefault(map, 0), map == myVote)
                    : pickItem(player, map, map == plugin.getGameManager().getSelectedMap()));
        }
        if (page > 0) setTemplateButton(inv, player, template.button("previous"));
        setTemplateButton(inv, player, template.button("close"));
        if (page + 1 < pageCount) setTemplateButton(inv, player, template.button("next"));

        player.openInventory(inv);
        openMenus.put(player.getUniqueId(), new OpenMenu(voteMode, page));
    }

    // ── Host pick menu (vote mode disabled) ────────────────────────────────

    private void openPickMenu(Player player, List<GameMap> maps) {
        openMapMenu(player, maps, false, 0);
    }

    private void setTemplateButton(Inventory inventory, Player player,
                                   fr.tropicube.core.ui.MenuTemplateRegistry.Button button) {
        List<Component> lore = button.loreKey() == null ? List.of()
                : List.of(LangHelper.component(player, button.loreKey()));
        inventory.setItem(button.slot(), new ItemBuilder(button.material())
                .name(LangHelper.component(player, button.nameKey()).decoration(TextDecoration.ITALIC, false))
                .lore(lore)
                .noTooltip()
                .build());
    }

    // ── Item builders ──────────────────────────────────────────────────────

    private ItemStack voteItem(Player player, GameMap map, int voteCount, boolean isMyVote) {
        Component name = Component.text(map.getName(),
                isMyVote ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(LangHelper.component(player, "sw.map-label-votes", voteCount)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (isMyVote) {
            lore.add(LangHelper.component(player, "sw.map-label-voted")
                    .decoration(TextDecoration.ITALIC, false));
        }

        return new ItemBuilder(Material.FILLED_MAP)
                .name(name)
                .lore(lore)
                .noTooltip()
                .build();
    }

    private ItemStack pickItem(Player player, GameMap map, boolean isSelected) {
        Component name = Component.text(map.getName(),
                isSelected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (isSelected) {
            lore.add(LangHelper.component(player, "sw.map-label-selected")
                    .decoration(TextDecoration.ITALIC, false));
        }

        return new ItemBuilder(Material.FILLED_MAP)
                .name(name)
                .lore(lore)
                .noTooltip()
                .build();
    }

    // ── Event handlers ─────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        OpenMenu openMenu = openMenus.get(uuid);
        if (openMenu == null) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()) return;

        String menuId = openMenu.voteMode ? "map-vote" : "map-pick";
        var template = plugin.getMenuTemplates().menu(menuId);
        int slot = event.getRawSlot();
        if (slot == template.button("close").slot()) {
            player.closeInventory();
            return;
        }
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        if (slot == template.button("previous").slot()) {
            openMapMenu(player, maps, openMenu.voteMode, openMenu.page - 1);
            return;
        }
        if (slot == template.button("next").slot()) {
            openMapMenu(player, maps, openMenu.voteMode, openMenu.page + 1);
            return;
        }
        int localIndex = template.dynamicRegion("maps").slots().indexOf(slot);
        int mapIndex = openMenu.page * template.dynamicRegion("maps").slots().size() + localIndex;
        if (localIndex < 0 || mapIndex >= maps.size()) return;

        GameMap clicked = maps.get(mapIndex);

        if (openMenu.voteMode && plugin.getGameSettingsMenu().isMapVoteEnabled()) {
            votes.put(uuid, clicked);
            voteWeights.put(uuid, player.hasPermission("sheepwars.mapvote.weight.2") ? 2 : 1);
            plugin.getScoreboardManager().updateAll();
            player.sendMessage(LangHelper.component(player, "sw.map-vote-cast", clicked.getName()));
            player.closeInventory();
            refreshOpenMenus();
        } else if (!openMenu.voteMode && uuid.equals(plugin.getGameManager().getHostUuid())) {
            plugin.getGameManager().setSelectedMap(clicked);
            // Waiting sidebars are event-driven, so publish the host choice immediately.
            plugin.getScoreboardManager().updateAll();
            player.sendMessage(LangHelper.component(player, "sw.map-pick-selected-msg", clicked.getName()));
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    // ── Vote resolution ────────────────────────────────────────────────────

    /**
     * Counts the votes and chooses the majority card; equalities and the absence
     * votes are decided randomly. The votes are then erased.
     */
    public GameMap resolveWinnerAndReset() {
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        if (maps.isEmpty()) return null;

        Map<GameMap, Integer> counts = MapVoteTally.counts(maps, votes, voteWeights);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<GameMap> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        votes.clear();
        voteWeights.clear();
        return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
    }

    public void reset() {
        votes.clear();
        voteWeights.clear();
    }

    public void removeVote(UUID uuid) {
        GameMap removed = votes.remove(uuid);
        voteWeights.remove(uuid);
        if (removed != null) refreshOpenMenus();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    public MapVoteTally.Standing currentStanding() {
        return MapVoteTally.standing(plugin.getGameManager().getGameMaps(), votes, voteWeights);
    }

    private void refreshOpenMenus() {
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        for (Map.Entry<UUID, OpenMenu> entry : List.copyOf(openMenus.entrySet())) {
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null && viewer.isOnline()) {
                openMapMenu(viewer, maps, entry.getValue().voteMode, entry.getValue().page);
            }
        }
    }

    private record OpenMenu(boolean voteMode, int page) { }
}
