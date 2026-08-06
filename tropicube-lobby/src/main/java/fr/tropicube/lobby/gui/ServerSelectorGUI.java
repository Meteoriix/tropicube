package fr.tropicube.lobby.gui;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.managers.LobbyServerManager;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
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
 * Affiche la liste paginée des serveurs disponibles pour un type donné.
 *
 * Le {@link Holder} porte le contexte (type, page) et le mapping slot → serverId.
 * Les champs de pagination et de mapping sont mutables afin que {@link #refresh}
 * puisse les mettre à jour en place sans recréer l'inventaire.
 */
public class ServerSelectorGUI {

    private static final int SIZE      = 54;
    private static final int PAGE_SIZE = 28; // 4 lignes × 7 colonnes intérieures

    public static final int SLOT_PREV  = 45;
    public static final int SLOT_BEST  = 48;
    public static final int SLOT_BACK  = 49;
    public static final int SLOT_CLOSE = 50;
    public static final int SLOT_NEXT  = 53;

    private static Component buildTitle(Player player, String type, int page) {
        return LangHelper.component(player, "lobby.server-selector-title", capitalize(type), page + 1);
    }

    public static final class Holder implements InventoryHolder {
        private final String type;
        private final int    page;
        // État mutable actualisé sur place afin d'éviter de rouvrir l'inventaire.
        boolean hasPrevPage;
        boolean hasNextPage;
        Map<Integer, String> slotToServerId;
        private Inventory inventory;

        private Holder(String type, int page, boolean hasPrev, boolean hasNext,
                       Map<Integer, String> slotToServerId) {
            this.type            = type;
            this.page            = page;
            this.hasPrevPage     = hasPrev;
            this.hasNextPage     = hasNext;
            this.slotToServerId  = Collections.unmodifiableMap(slotToServerId);
        }

        public String  getType()        { return type; }
        public int     getPage()        { return page; }
        public boolean hasPrevPage()    { return hasPrevPage; }
        public boolean hasNextPage()    { return hasNextPage; }
        public String  getServerForSlot(int slot) { return slotToServerId.get(slot); }

        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inventory)     { this.inventory = inventory; }
    }

    public static Inventory build(TropicubeLobby plugin, Player player, String type, int page) {
        if (page < 0) page = 0;

        List<LobbyServerManager.ServerInfo> servers = filteredServers(plugin, type);
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

        Holder holder = new Holder(type, page, hasPrev, hasNext, slotToServerId);
        Inventory inv = Bukkit.createInventory(holder, SIZE, buildTitle(player, type, page));
        holder.setInventory(inv);

        drawBorder(inv);

        for (int i = fromIndex; i < toIndex; i++) {
            inv.setItem(innerSlots.get(i - fromIndex), buildServerItem(player, servers.get(i)).build());
        }

        drawControls(inv, player, hasPrev, hasNext);
        return inv;
    }

    /**
     * Actualise les serveurs et la pagination d'un inventaire déjà ouvert.
     * Le holder est modifié sur place afin d'éviter tout scintillement visuel.
     */
    public static void refresh(TropicubeLobby plugin, Player player) {
        var topInv = player.getOpenInventory().getTopInventory();
        if (!(topInv.getHolder() instanceof Holder holder)) return;

        String type = holder.type;
        int page    = holder.page; // page is immutable — don't jump pages on refresh

        List<LobbyServerManager.ServerInfo> servers = filteredServers(plugin, type);
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

        ItemStack border = new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build();
        topInv.setItem(SLOT_PREV, hasPrev
                ? new ItemBuilder(Material.ARROW).name(LangHelper.get(player, "lobby.server-prev-page")).build()
                : border);
        topInv.setItem(SLOT_NEXT, hasNext
                ? new ItemBuilder(Material.ARROW).name(LangHelper.get(player, "lobby.server-next-page")).build()
                : border);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static List<LobbyServerManager.ServerInfo> filteredServers(TropicubeLobby plugin, String type) {
        return plugin.getLobbyServerManager().getServersByType(type).stream()
                .filter(s -> s.isOnline() || s.isStarting())
                .toList();
    }

    private static void drawBorder(Inventory inv) {
        ItemStack border = new ItemBuilder(Material.BLUE_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++)  inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) {
            inv.setItem(i,     border);
            inv.setItem(i + 8, border);
        }
    }

    private static void drawControls(Inventory inv, Player player, boolean hasPrev, boolean hasNext) {
        if (hasPrev) inv.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-prev-page")).build());
        if (hasNext) inv.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-next-page")).build());

        inv.setItem(SLOT_BACK, ItemBuilder.backButton(player));
        inv.setItem(SLOT_BEST, new ItemBuilder(Material.NETHER_STAR)
                .name(LangHelper.get(player, "lobby.server-best"))
                .lore(LangHelper.get(player, "lobby.server-best-lore1"),
                      LangHelper.get(player, "lobby.server-best-lore2"))
                .build());
        inv.setItem(SLOT_CLOSE, ItemBuilder.closeButton(player));
    }

    private static ItemBuilder buildServerItem(Player player, LobbyServerManager.ServerInfo s) {
        Material icon;
        String statusLabel;
        String namePrefix;

        if (s.isOnline() && !s.isFull()) {
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
        if (s.isOnline() && !s.isFull()) {
            lore.add(LangHelper.get(player, "lobby.server-click-join"));
        } else if (s.isOnline()) {
            lore.add(LangHelper.get(player, "lobby.server-click-full"));
            lore.add(LangHelper.get(player, "lobby.server-click-full2", s.id()));
        } else {
            lore.add(LangHelper.get(player, "lobby.server-click-starting"));
        }

        ItemBuilder ib = new ItemBuilder(icon)
                .name(namePrefix + "⬛ " + s.id().toUpperCase(Locale.ROOT))
                .lore(lore.toArray(new String[0]));
        if (s.isOnline() && !s.isFull()) ib.glow();
        return ib;
    }

    private static List<Integer> computeInnerSlots() {
        List<Integer> slots = new ArrayList<>(PAGE_SIZE);
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                slots.add(row * 9 + col);
        return slots;
    }

    public static String resolveServerClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Holder)) return null;
        return ((Holder) holder).getServerForSlot(event.getRawSlot());
    }

    public static Holder getHolder(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        return (holder instanceof Holder h) ? h : null;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
