package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.managers.LobbyServerManager.TemplateInfo;
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

/**
 * Menu de création de partie personnalisée (Custom Game).
 * Chaque template disponible est affiché comme une option cliquable.
 * Accessible aux joueurs ayant la permission {@code tropicube.lobby.customgame}.
 */
public class CustomGameGUI {

    private static final int SIZE = 27;
    static final int CLOSE_SLOT = 26;
    static final int STOP_SLOT  = 22;

    public static final class Holder implements InventoryHolder {
        /** Mapping slot → templateId pour le routage des clics. */
        private final Map<Integer, String> slotToTemplate;
        private final boolean hasStopButton;
        private final boolean whitelisted;
        private Inventory inventory;

        private Holder(Map<Integer, String> slotToTemplate, boolean hasStopButton, boolean whitelisted) {
            this.slotToTemplate = Collections.unmodifiableMap(slotToTemplate);
            this.hasStopButton  = hasStopButton;
            this.whitelisted = whitelisted;
        }

        public String getTemplateForSlot(int slot)  { return slotToTemplate.get(slot); }
        public boolean isCloseSlot(int slot)        { return slot == CLOSE_SLOT; }
        public boolean isStopSlot(int slot)         { return hasStopButton && slot == STOP_SLOT; }
        public boolean isWhitelisted()              { return whitelisted; }

        @Override public @NonNull Inventory getInventory() { return inventory; }
        private void setInventory(Inventory inv)            { this.inventory = inv; }
    }

    public static Inventory build(TropicubeLobby plugin, Player player, boolean whitelisted) {
        UUID playerId = player.getUniqueId();
        String hostInstanceId = plugin.getRedisManager().get("host:" + playerId);
        boolean creationPending = plugin.getRedisManager().exists("host-creation:" + playerId);
        boolean hasCustomGame = hostInstanceId != null || creationPending;
        List<TemplateInfo> templates = hasCustomGame
                ? List.of()
                : plugin.getLobbyServerManager().getCustomGameTemplates();

        // Centre les templates dans la ligne du milieu (row 1)
        int n = Math.min(templates.size(), 7);
        int startSlot = n == 0 ? 13 : 13 - (n - 1);

        Map<Integer, String> slotToTemplate = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            slotToTemplate.put(startSlot + (i * 2), templates.get(i).id());
        }

        // L'hôte peut arrêter sa partie tant qu'elle n'a pas commencé.
        boolean showStop = hostInstanceId != null
                && !plugin.getRedisManager().exists("sw:game-started:" + hostInstanceId);

        Holder holder = new Holder(slotToTemplate, showStop, whitelisted);
        Inventory inv = Bukkit.createInventory(holder, SIZE, LangHelper.component(player, "lobby.custom-game-title"));
        holder.setInventory(inv);

        ItemStack filler = ItemBuilder.filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        if (creationPending) {
            inv.setItem(13, new ItemBuilder(Material.CLOCK)
                    .name(LangHelper.get(player, "lobby.custom-game-pending-name"))
                    .lore(LangHelper.get(player, "lobby.custom-game-pending-lore"))
                    .build());
        } else if (hostInstanceId != null) {
            inv.setItem(13, new ItemBuilder(Material.BARRIER)
                    .name(LangHelper.get(player, "lobby.custom-game-active-name"))
                    .lore(LangHelper.get(player, "lobby.custom-game-active-lore"))
                    .build());
        } else if (templates.isEmpty()) {
            inv.setItem(13, new ItemBuilder(Material.BARRIER)
                    .name(LangHelper.get(player, "lobby.custom-game-no-templates"))
                    .build());
        } else {
            for (int i = 0; i < n; i++) {
                TemplateInfo tpl = templates.get(i);
                inv.setItem(startSlot + (i * 2), buildTemplateItem(plugin, player, tpl));
            }
        }

        if (showStop) {
            inv.setItem(STOP_SLOT, new ItemBuilder(Material.TNT)
                    .name(LangHelper.get(player, "lobby.host-stop-name"))
                    .lore(LangHelper.get(player, "lobby.host-stop-lore1"),
                          "",
                          LangHelper.get(player, "lobby.host-stop-lore2"))
                    .build());
        }

        inv.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inv;
    }

    private static ItemStack buildTemplateItem(TropicubeLobby plugin, Player player, TemplateInfo tpl) {
        String typeKey = tpl.type().toLowerCase(Locale.ROOT);
        ItemStack icon = getTypeIcon(plugin, typeKey);

        return new ItemBuilder(icon)
                .name("<gold>" + tpl.name())
                .lore(
                        LangHelper.get(player, "lobby.custom-game-type", tpl.type()),
                        LangHelper.get(player, "lobby.custom-game-max-players", tpl.maxPlayers()),
                        "",
                        LangHelper.get(player, "lobby.custom-game-click")
                )
                .glow()
                .build();
    }

    private static ItemStack getTypeIcon(TropicubeLobby plugin, String typeKey) {
        var section = plugin.getConfig().getConfigurationSection("server-types." + typeKey);
        if (section != null) {
            String headId = section.getString("head-id");
            if (headId != null) {
                try {
                    if (Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) {
                        HeadDatabaseAPI hdb = core.getHeadDatabaseManager().getHeadDatabaseAPI();
                        ItemStack head = hdb == null ? null : hdb.getItemHead(headId);
                        if (head != null) return head;
                    }
                } catch (Exception ignored) {}
            }
            String materialName = section.getString("material");
            if (materialName != null) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) return new ItemStack(material);
            }
        }
        return new ItemStack(Material.COMMAND_BLOCK);
    }
}
