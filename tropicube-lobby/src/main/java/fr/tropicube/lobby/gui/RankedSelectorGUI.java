package fr.tropicube.lobby.gui;

import fr.tropicube.docker.model.InstanceMode;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.managers.LobbyServerManager;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Live selector for the ranked modes published by Velocity. */
public final class RankedSelectorGUI {
    public static final int BACK_SLOT = 18;
    public static final int CANCEL_SLOT = 22;
    public static final int CLOSE_SLOT = 26;
    private static final Map<InstanceMode, Integer> MODE_SLOTS = Map.of(
            InstanceMode.RANKED_4V4, 11, InstanceMode.RANKED_8V8, 15);

    private RankedSelectorGUI() { }

    public static Inventory build(TropicubeLobby plugin, Player player, String type) {
        Holder holder = new Holder(type, new LinkedHashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, LangHelper.menuSize("ranked-selector"),
                LangHelper.menuTitle(player, "ranked-selector"));
        holder.inventory = inventory;
        draw(plugin, player, holder);
        return inventory;
    }

    public static void refresh(TropicubeLobby plugin, Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder)) return;
        draw(plugin, player, holder);
    }

    private static void draw(TropicubeLobby plugin, Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        NetworkMenuStyle.frame(inventory, player);
        holder.templates.clear();
        for (LobbyServerManager.TemplateInfo template : plugin.getLobbyServerManager()
                .getRankedTemplatesForType(holder.type)) {
            Integer slot = MODE_SLOTS.get(template.mode());
            if (slot == null) continue;
            holder.templates.put(slot, template.id());
            var stats = plugin.getLobbyServerManager().getRankedStats(template.id()).orElse(null);
            int maximumParty = template.mode() == InstanceMode.RANKED_4V4 ? 2 : 4;
            String label = template.mode() == InstanceMode.RANKED_4V4 ? "4v4" : "8v8";
            ItemBuilder item = new ItemBuilder(template.mode() == InstanceMode.RANKED_4V4
                    ? Material.IRON_SWORD : Material.DIAMOND_SWORD)
                    .name(LangHelper.get(player, "lobby.ranked-mode-name", label))
                    .lore(
                            LangHelper.get(player, "lobby.ranked-capacity", template.maxPlayers()),
                            LangHelper.get(player, "lobby.ranked-party-limit", maximumParty),
                            LangHelper.get(player, "lobby.ranked-shared-rating"),
                            stats == null ? LangHelper.get(player, "lobby.ranked-stats-unavailable")
                                    : LangHelper.get(player, "lobby.ranked-live-stats", stats.groups(),
                                            stats.reservedPlayers(), stats.capacity(), stats.oldestWaitSeconds()),
                            "",
                            LangHelper.get(player, "lobby.ranked-click"));
            inventory.setItem(slot, item.glow().build());
        }
        String active = plugin.getLobbyServerManager().getActiveMatchmaking(player.getUniqueId()).orElse(null);
        if (active != null) {
            inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_DYE)
                    .name(LangHelper.get(player, "lobby.ranked-cancel-name"))
                    .lore(LangHelper.get(player, "lobby.ranked-cancel-lore",
                            plugin.getLobbyServerManager().getTemplateDisplayName(active))).build());
        }
        inventory.setItem(BACK_SLOT, ItemBuilder.backButton(player));
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
    }

    public static final class Holder implements InventoryHolder {
        private final String type;
        private final Map<Integer, String> templates;
        private Inventory inventory;

        private Holder(String type, Map<Integer, String> templates) {
            this.type = type;
            this.templates = templates;
        }

        public String templateAt(int slot) { return templates.get(slot); }
        @Override public @NonNull Inventory getInventory() { return inventory; }
    }
}
