package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.managers.LobbyServerManager;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import fr.tropicube.docker.model.InstanceMode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * Displays the paginated list of servers available for a given type.
 *
 * The {@link Holder} carries the context (type and page) and the slot-to-serverId mapping.
 * Paging and mapping fields are mutable so that {@link #refresh}
 * can update them in place without recreating the inventory.
 */
public class ServerSelectorGUI {

    private static final int SIZE      = 54;
    private static final int PAGE_SIZE = 28; // 4 lignes × 7 colonnes intérieures

    public static final int SLOT_PREV  = 45;
    public static final int SLOT_BEST  = 48;
    public static final int SLOT_FILTER = 47;
    public static final int SLOT_BACK  = 49;
    public static final int SLOT_CLOSE = 50;
    public static final int SLOT_NEXT  = 53;

    private static Component buildTitle(Player player, String type, int page) {
        return LangHelper.component(player, "lobby.server-selector-title",
                ServerTypeSelectorGUI.displayType(player, type), page + 1);
    }

    public static final class Holder implements InventoryHolder {
        private final String type;
        private final int    page;
        private Filter filter;
        // Mutable state refreshed in place to avoid reopening inventory.
        boolean hasPrevPage;
        boolean hasNextPage;
        Map<Integer, String> slotToServerId;
        private Inventory inventory;

        private Holder(String type, int page, boolean hasPrev, boolean hasNext,
                       Map<Integer, String> slotToServerId, Filter filter) {
            this.type            = type;
            this.page            = page;
            this.filter          = filter;
            this.hasPrevPage     = hasPrev;
            this.hasNextPage     = hasNext;
            this.slotToServerId  = Collections.unmodifiableMap(slotToServerId);
        }

        public String  getType()        { return type; }
        public Filter getFilter() { return filter; }
        public void nextFilter() { filter = filter.next(); }
        public int     getPage()        { return page; }
        public boolean hasPrevPage()    { return hasPrevPage; }
        public boolean hasNextPage()    { return hasNextPage; }
        public String  getServerForSlot(int slot) { return slotToServerId.get(slot); }

        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inventory)     { this.inventory = inventory; }
    }

    public static Inventory build(TropicubeLobby plugin, Player player, String type, int page) {
        return build(plugin, player, type, page, Filter.ALL);
    }

    public static Inventory build(TropicubeLobby plugin, Player player, String type, int page, Filter filter) {
        if (page < 0) page = 0;

        List<LobbyServerManager.ServerInfo> servers = filteredServers(plugin, player, type, filter);
        int totalPages = Math.max(1, (int) Math.ceil(servers.size() / (double) PAGE_SIZE));
        if (page >= totalPages) page = totalPages - 1;

        List<Integer> innerSlots = computeInnerSlots();
        int fromIndex = page * PAGE_SIZE;
        int toIndex   = Math.min(fromIndex + PAGE_SIZE, servers.size());

        Map<Integer, String> slotToServerId = new LinkedHashMap<>();
        for (int i = fromIndex; i < toIndex; i++)
            slotToServerId.put(innerSlots.get(i - fromIndex), servers.get(i).id());

        boolean hasPrev = page > 0;
        boolean hasNext = toIndex < servers.size();

        Holder holder = new Holder(type, page, hasPrev, hasNext, slotToServerId, filter);
        Inventory inv = Bukkit.createInventory(holder, SIZE, buildTitle(player, type, page));
        holder.setInventory(inv);

        drawBorder(inv);

        for (int i = fromIndex; i < toIndex; i++) {
            inv.setItem(innerSlots.get(i - fromIndex), buildServerItem(player, servers.get(i)).build());
        }

        drawControls(inv, player, hasPrev, hasNext, holder.filter);
        return inv;
    }

    /**
     * Refreshes the servers and pagination of an already open inventory.
     * The holder is modified on site to avoid any visual flicker.
     */
    public static void refresh(TropicubeLobby plugin, Player player) {
        var topInv = player.getOpenInventory().getTopInventory();
        if (!(topInv.getHolder() instanceof Holder holder)) return;

        String type = holder.type;
        int page    = holder.page; // page is immutable — don't jump pages on refresh

        List<LobbyServerManager.ServerInfo> servers = filteredServers(plugin, player, type, holder.filter);
        List<Integer> innerSlots = computeInnerSlots();
        int fromIndex = page * PAGE_SIZE;
        int toIndex   = Math.min(fromIndex + PAGE_SIZE, servers.size());

        // Clear all inner slots then repopulate
        for (int s : innerSlots) topInv.setItem(s, null);

        Map<Integer, String> newSlotMap = new LinkedHashMap<>();
        for (int i = fromIndex; i < toIndex; i++) {
            int slot = innerSlots.get(i - fromIndex);
            newSlotMap.put(slot, servers.get(i).id());
            topInv.setItem(slot, buildServerItem(player, servers.get(i)).build());
        }

        boolean hasPrev = page > 0;
        boolean hasNext = toIndex < servers.size();

        holder.hasPrevPage    = hasPrev;
        holder.hasNextPage    = hasNext;
        holder.slotToServerId = Collections.unmodifiableMap(newSlotMap);

        ItemStack border = NetworkMenuStyle.item(NetworkMenuStyle.BACKGROUND, Component.text(" "));
        topInv.setItem(SLOT_PREV, hasPrev
                ? new ItemBuilder(Material.ARROW).name(LangHelper.get(player, "lobby.server-prev-page")).build()
                : border);
        topInv.setItem(SLOT_NEXT, hasNext
                ? new ItemBuilder(Material.ARROW).name(LangHelper.get(player, "lobby.server-next-page")).build()
                : border);
        topInv.setItem(SLOT_FILTER, filterItem(player, holder.filter));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<LobbyServerManager.ServerInfo> filteredServers(
            TropicubeLobby plugin, Player player, String type, Filter filter) {
        return plugin.getLobbyServerManager().getServersByType(type, player.getUniqueId()).stream()
                .filter(LobbyServerManager.ServerInfo::isListed)
                .filter(server -> server.mode() == InstanceMode.QUICK_PLAY
                        || (server.mode() == InstanceMode.CUSTOM && !server.privateGame()))
                .filter(server -> filter == Filter.ALL
                        || (filter == Filter.QUICK_PLAY && server.mode() == InstanceMode.QUICK_PLAY)
                        || (filter == Filter.CUSTOM && server.mode() == InstanceMode.CUSTOM))
                .sorted(Comparator.comparing(LobbyServerManager.ServerInfo::isJoinable).reversed()
                        .thenComparing(LobbyServerManager.ServerInfo::playerCount, Comparator.reverseOrder())
                        .thenComparing(LobbyServerManager.ServerInfo::id))
                .toList();
    }

    private static void drawBorder(Inventory inv) {
        NetworkMenuStyle.frame(inv);
    }

    private static void drawControls(Inventory inv, Player player, boolean hasPrev, boolean hasNext, Filter filter) {
        if (hasPrev) inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-prev-page")).build());
        if (hasNext) inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-next-page")).build());

        inv.setItem(SLOT_BACK, ItemBuilder.backButton(player));
        inv.setItem(SLOT_FILTER, filterItem(player, filter));
        if (filter != Filter.CUSTOM) {
            inv.setItem(SLOT_BEST, new ItemBuilder(Material.NETHER_STAR)
                    .name(LangHelper.get(player, "lobby.server-best"))
                    .lore(LangHelper.get(player, "lobby.server-best-lore"))
                    .build());
        }
        inv.setItem(SLOT_CLOSE, ItemBuilder.closeButton(player));
    }

    private static ItemStack filterItem(Player player, Filter filter) {
        String label = LangHelper.get(player, "lobby.server-filter-" + filter.name().toLowerCase(Locale.ROOT));
        return new ItemBuilder(Material.HOPPER)
                .name(LangHelper.get(player, "lobby.server-filter-name", label))
                .lore("",
                        LangHelper.get(player, "lobby.server-filter-click")).build();
    }

    public enum Filter {
        ALL, QUICK_PLAY, CUSTOM;
        public Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static ItemBuilder buildServerItem(Player player, LobbyServerManager.ServerInfo s) {
        Material icon;
        String statusLabel;
        String namePrefix;

        if (s.isPlaying()) {
            icon = Material.BLUE_CONCRETE;
            statusLabel = LangHelper.get(player, "lobby.server-status-playing");
            namePrefix = "<blue>";
        } else if (s.isOnline() && !s.isFull()) {
            icon = Material.LIME_CONCRETE;
            statusLabel = LangHelper.get(player, "lobby.server-status-online");
            namePrefix  = "<green>";
        } else if (s.isOnline()) {
            icon = Material.RED_CONCRETE;
            statusLabel = LangHelper.get(player, "lobby.server-status-full");
            namePrefix  = "<red>";
        } else {
            icon = Material.ORANGE_CONCRETE;
            statusLabel = LangHelper.get(player, "lobby.server-status-starting");
            namePrefix  = "<gold>";
        }

        int bars = s.maxPlayers() > 0
                ? Math.max(0, Math.min(10, (int) ((double) s.playerCount() / s.maxPlayers() * 10)))
                : 0;
        String bar = "<green>" + "█".repeat(bars) + "<gray>" + "█".repeat(10 - bars);

        List<String> lore = new ArrayList<>();
        lore.add(LangHelper.get(player, "lobby.server-status",  statusLabel));
        lore.add(LangHelper.get(player, "lobby.server-players", s.playerCount(), s.maxPlayers()));
        lore.add(bar);
        lore.add("");
        lore.add(LangHelper.get(player, "lobby.server-template", s.templateName()));
        lore.add("");
        if (s.isPlaying() && !s.isFull()) {
            lore.add(LangHelper.get(player, "lobby.server-click-spectate"));
        } else if (s.isOnline() && !s.isFull()) {
            lore.add(LangHelper.get(player, "lobby.server-click-join"));
        } else if (s.isOnline()) {
            lore.add(LangHelper.get(player, "lobby.server-click-full"));
            lore.add(LangHelper.get(player, "lobby.server-click-full2", s.id()));
        } else {
            lore.add(LangHelper.get(player, "lobby.server-click-starting"));
        }

        ItemBuilder ib = new ItemBuilder(icon)
                .name(namePrefix + LangHelper.get(player, "lobby.server-instance-name",
                        s.templateName(), Math.floorMod(s.id().hashCode(), 10_000)))
                .lore(lore.toArray(new String[0]));
        if (s.isJoinable()) ib.glow();
        return ib;
    }

    private static List<Integer> computeInnerSlots() {
        List<Integer> slots = new ArrayList<>(PAGE_SIZE);
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots;
    }

    public static Holder getHolder(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        return (holder instanceof Holder h) ? h : null;
    }

}
