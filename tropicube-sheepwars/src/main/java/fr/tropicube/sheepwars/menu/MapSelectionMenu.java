package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.TropicubeSheepwars;
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

/** Organise le vote des joueurs pour la carte de la prochaine partie. */
public class MapSelectionMenu implements Listener {

    private final TropicubeSheepwars plugin;
    public final NamespacedKey menuItemKey;

    /** playerUuid → the map they voted for (or host-selected map when vote disabled) */
    private final Map<UUID, GameMap> votes = new HashMap<>();
    private final Set<UUID> openMenus = new HashSet<>();

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
        // Sans vote, seul l'hôte voit l'objet et peut ouvrir ce menu.
    }

    // ── Vote menu (all players, vote mode enabled) ─────────────────────────

    private void openVoteMenu(Player player, List<GameMap> maps) {
        Inventory inv = Bukkit.createInventory(null, 9,
                LangHelper.component(player, "sw.map-vote-title"));

        GameMap myVote = votes.get(player.getUniqueId());
        Map<GameMap, Integer> counts = countVotes(maps);

        for (int i = 0; i < maps.size() && i < 9; i++) {
            GameMap map = maps.get(i);
            boolean voted = map == myVote;
            inv.setItem(i, voteItem(player, map, counts.getOrDefault(map, 0), voted));
        }

        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
    }

    // ── Host pick menu (vote mode disabled) ────────────────────────────────

    private void openPickMenu(Player player, List<GameMap> maps) {
        Inventory inv = Bukkit.createInventory(null, 9,
                LangHelper.component(player, "sw.map-pick-title"));

        GameMap selected = plugin.getGameManager().getSelectedMap();
        for (int i = 0; i < maps.size() && i < 9; i++) {
            GameMap map = maps.get(i);
            inv.setItem(i, pickItem(player, map, map == selected));
        }

        player.openInventory(inv);
        openMenus.add(player.getUniqueId());
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
        if (!openMenus.contains(uuid)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() == player.getInventory()) return;

        int slot = event.getRawSlot();
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        if (slot < 0 || slot >= maps.size()) return;

        GameMap clicked = maps.get(slot);

        if (plugin.getGameSettingsMenu().isMapVoteEnabled()) {
            votes.put(uuid, clicked);
            player.sendMessage(LangHelper.component(player, "sw.map-vote-cast", clicked.getName()));
            player.closeInventory();
        } else if (uuid.equals(plugin.getGameManager().getHostUuid())) {
            plugin.getGameManager().setSelectedMap(clicked);
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
     * Compte les votes et choisit la carte majoritaire ; les égalités et l'absence
     * de vote sont départagées aléatoirement. Les votes sont ensuite effacés.
     */
    public GameMap resolveWinnerAndReset() {
        List<GameMap> maps = plugin.getGameManager().getGameMaps();
        if (maps.isEmpty()) return null;

        Map<GameMap, Integer> counts = countVotes(maps);
        int max = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);

        List<GameMap> winners = counts.entrySet().stream()
                .filter(e -> e.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();

        votes.clear();
        return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
    }

    public void reset() {
        votes.clear();
    }

    public void removeVote(UUID uuid) {
        votes.remove(uuid);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private Map<GameMap, Integer> countVotes(List<GameMap> maps) {
        Map<GameMap, Integer> counts = new LinkedHashMap<>();
        for (GameMap map : maps) counts.put(map, 0);
        for (GameMap voted : votes.values()) {
            if (counts.containsKey(voted)) counts.computeIfPresent(voted, (_, count) -> count + 1);
        }
        return counts;
    }
}
