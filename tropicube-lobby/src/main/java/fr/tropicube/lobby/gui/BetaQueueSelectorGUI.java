package fr.tropicube.lobby.gui;

import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.managers.LobbyServerManager;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/** Displays development queues explicitly published in the {@code BETA} category. */
public final class BetaQueueSelectorGUI {
    public static final String TYPE = "BETA";
    public static final int BACK_SLOT = 18;
    public static final int CANCEL_SLOT = 22;
    public static final int CLOSE_SLOT = 26;
    private static final Map<String, QueueDefinition> QUEUES = Map.of(
            "fallenkingdoms-beta-1v1", new QueueDefinition(11, 2, Material.IRON_SWORD,
                    "lobby.beta-fk-1v1-name", "lobby.beta-fk-1v1-lore"),
            "fallenkingdoms-beta-2v2", new QueueDefinition(15, 4, Material.DIAMOND_SWORD,
                    "lobby.beta-fk-2v2-name", "lobby.beta-fk-2v2-lore"));

    private BetaQueueSelectorGUI() { }

    public static Inventory build(TropicubeLobby plugin, Player player) {
        Holder holder = new Holder(new LinkedHashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, LangHelper.menuSize("beta-selector"),
                LangHelper.menuTitle(player, "beta-selector"));
        holder.inventory = inventory;
        draw(plugin, player, holder);
        return inventory;
    }

    public static void refresh(TropicubeLobby plugin, Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) {
            draw(plugin, player, holder);
        }
    }

    private static void draw(TropicubeLobby plugin, Player player, Holder holder) {
        NetworkMenuStyle.applyFrame(holder.inventory, player, LangHelper.menuFrame("beta-selector"));
        holder.templates.clear();
        for (LobbyServerManager.TemplateInfo template : plugin.getLobbyServerManager().getTemplatesForType(TYPE)) {
            QueueDefinition definition = QUEUES.get(template.id());
            if (definition == null) continue;
            holder.templates.put(definition.slot(), template.id());
            holder.inventory.setItem(definition.slot(), new ItemBuilder(definition.material())
                    .name(LangHelper.get(player, definition.nameKey()))
                    .lore(
                            LangHelper.get(player, definition.loreKey()),
                            LangHelper.get(player, "lobby.beta-capacity",
                                    definition.minimumPlayers(), template.maxPlayers()),
                            "",
                            LangHelper.get(player, "lobby.beta-join-click"))
                    .glow(player)
                    .build());
        }

        String active = plugin.getLobbyServerManager().getActiveMatchmaking(player.getUniqueId()).orElse(null);
        if (active != null) {
            holder.inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.RED_DYE)
                    .name(LangHelper.get(player, "lobby.beta-cancel-name"))
                    .lore(LangHelper.get(player, "lobby.beta-cancel-lore",
                            plainDisplayName(plugin, player, active)))
                    .build());
        }
        holder.inventory.setItem(BACK_SLOT, ItemBuilder.backButton(player));
        holder.inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
    }

    /** Resolves the trusted localized queue label while preserving its MiniMessage style. */
    public static Component displayNameComponent(TropicubeLobby plugin, Player player, String templateId) {
        QueueDefinition definition = QUEUES.get(templateId);
        return definition == null
                ? Component.text(plugin.getLobbyServerManager().getTemplateDisplayName(templateId))
                : LangHelper.component(player, definition.nameKey());
    }

    /** Resolves the queue label without formatting for insertion into an inventory lore string. */
    public static String plainDisplayName(TropicubeLobby plugin, Player player, String templateId) {
        return plainText(displayNameComponent(plugin, player, templateId));
    }

    /** Supplies a queue label as a rich placeholder instead of escaping it as plain text. */
    public static PlaceholderValues displayNamePlaceholder(Component displayName) {
        return PlaceholderValues.builder().putComponent("queue", displayName).build();
    }

    static String plainText(Component displayName) {
        return PlainTextComponentSerializer.plainText().serialize(displayName);
    }

    private record QueueDefinition(int slot, int minimumPlayers, Material material,
                                   String nameKey, String loreKey) { }

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, String> templates;
        private Inventory inventory;

        private Holder(Map<Integer, String> templates) {
            this.templates = templates;
        }

        public String templateAt(int slot) { return templates.get(slot); }
        @Override public @NonNull Inventory getInventory() { return inventory; }
    }
}
