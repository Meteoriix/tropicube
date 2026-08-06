package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.managers.LobbyServerManager;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Menu principal : choix du type de serveur (Survival, PvP, Skyblock…).
 *
 * Les icônes de types sont chargées depuis la section {@code server-types} du
 * config.yml du lobby, avec mise en cache après le premier chargement réussi.
 */
public class ServerTypeSelectorGUI {

    private static final int SIZE = 27;
    private static final int CLOSE_SLOT = 26;

    /** Positions centrées pour un à neuf types ; les types suivants ne sont pas affichés. */
    private static final int[][] LAYOUTS = {
        {},
        {13},
        {11, 15},
        {10, 13, 16},
        {10, 12, 14, 16},
        {9, 11, 13, 15, 17},
        {2, 4, 6, 20, 22, 24},
        {1, 3, 5, 7, 20, 22, 24},
        {1, 3, 5, 7, 19, 21, 23, 25},
        {2, 4, 6, 11, 13, 15, 20, 22, 24},
    };

    // Icônes par type — chargées lazily depuis config + HDB, puis mises en cache.
    private static final Map<String, ItemStack> TYPE_ICON_CACHE = new ConcurrentHashMap<>();

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, String> slotToType;
        private Inventory inventory;

        private Holder(Map<Integer, String> slotToType) {
            this.slotToType = Collections.unmodifiableMap(slotToType);
        }

        public String getTypeForSlot(int slot) { return slotToType.get(slot); }
        public boolean isCloseSlot(int slot)   { return slot == CLOSE_SLOT; }

        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inventory)     { this.inventory = inventory; }
    }

    public static Inventory build(TropicubeLobby plugin, Player player) {
        Set<String> types = plugin.getLobbyServerManager().getAvailableTemplateTypes();
        List<String> sortedTypes = new ArrayList<>(types);
        Collections.sort(sortedTypes);
        int n = Math.min(sortedTypes.size(), LAYOUTS.length - 1);
        sortedTypes = sortedTypes.subList(0, n);

        int[] slots = LAYOUTS[n];
        Map<Integer, String> slotToType = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) slotToType.put(slots[i], sortedTypes.get(i));

        Holder holder = new Holder(slotToType);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LangHelper.component(player, "lobby.type-selector-title"));
        holder.setInventory(inv);

        ItemBuilder filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler.build());

        for (Map.Entry<Integer, String> e : slotToType.entrySet()) {
            inv.setItem(e.getKey(), buildTypeItem(plugin, player, e.getValue()));
        }

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    /** Refreshes all type-icon items in an already-open inventory (in-place, no flicker). */
    public static void refresh(TropicubeLobby plugin, Player player) {
        var topInv = player.getOpenInventory().getTopInventory();
        if (!(topInv.getHolder() instanceof Holder holder)) return;
        for (Map.Entry<Integer, String> e : holder.slotToType.entrySet()) {
            topInv.setItem(e.getKey(), buildTypeItem(plugin, player, e.getValue()));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static ItemStack buildTypeItem(TropicubeLobby plugin, Player player, String type) {
        List<LobbyServerManager.ServerInfo> servers = plugin.getLobbyServerManager().getServersByType(type);
        int totalPlayers  = servers.stream().mapToInt(LobbyServerManager.ServerInfo::playerCount).sum();
        int onlineServers = (int) servers.stream().filter(LobbyServerManager.ServerInfo::isOnline).count();

        ItemBuilder ib = new ItemBuilder(getTypeIcon(type))
                .name("<yellow>" + capitalize(type))
                .lore(
                        LangHelper.get(player, "lobby.type-lore-online", onlineServers, servers.size()),
                        LangHelper.get(player, "lobby.type-lore-players", totalPlayers),
                        "",
                        LangHelper.get(player, "lobby.type-lore-left-click"),
                        LangHelper.get(player, "lobby.type-lore-right-click")
                );
        if (onlineServers > 0) ib.glow();
        return ib.build();
    }

    private static ItemStack getTypeIcon(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        ItemStack cached = TYPE_ICON_CACHE.get(key);
        if (cached != null) return cached.clone();

        TropicubeLobby lobby = TropicubeLobby.getInstance();
        if (lobby == null) return new ItemStack(Material.PAPER);

        var section = lobby.getConfig().getConfigurationSection("server-types." + key);
        if (section == null) return new ItemStack(Material.PAPER);

        String headId = section.getString("head-id");
        if (headId != null) {
            try {
                if (Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) {
                    HeadDatabaseAPI hdb = core.getHeadDatabaseManager().getHeadDatabaseAPI();
                    ItemStack head = hdb == null ? null : hdb.getItemHead(headId);
                    if (head != null) {
                        TYPE_ICON_CACHE.put(key, head.clone());
                        return head;
                    }
                }
            } catch (Exception _) {}
        }

        String materialName = section.getString("material");
        if (materialName != null) {
            Material material = Material.matchMaterial(materialName);
            if (material != null) {
                ItemStack item = new ItemStack(material);
                TYPE_ICON_CACHE.put(key, item.clone());
                return item;
            }
        }

        return new ItemStack(Material.PAPER);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
