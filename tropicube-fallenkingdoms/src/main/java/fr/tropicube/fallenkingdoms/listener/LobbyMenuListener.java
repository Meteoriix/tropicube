package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.core.ui.MenuTemplateRegistry;
import fr.tropicube.core.ui.UiReloadParticipant;
import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.game.GameState;
import fr.tropicube.fallenkingdoms.game.KingdomId;
import fr.tropicube.fallenkingdoms.game.KitDefinition;
import fr.tropicube.fallenkingdoms.map.MapDefinition;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Presents the Fallen Kingdoms waiting room using the same navigation and visual rules as
 * SheepWars while keeping kingdom, kit, and map semantics local to this game.
 */
public final class LobbyMenuListener implements Listener, UiReloadParticipant {
    static final int TEAM_SLOT = 0;
    static final int KIT_SLOT = 1;
    static final int MAP_SLOT = 2;
    static final int HOST_SLOT = 4;
    static final int PROFILE_SLOT = 7;
    static final int LEAVE_SLOT = 8;

    private final TropicubeFallenKingdoms plugin;
    private final TropicubeCore core;
    private final GameSession session;
    private final NamespacedKey actionKey;
    private final Map<UUID, MenuType> openMenus = new HashMap<>();

    public LobbyMenuListener(TropicubeFallenKingdoms plugin, GameSession session) {
        this.plugin = plugin;
        this.session = session;
        this.core = (TropicubeCore) Bukkit.getPluginManager().getPlugin("TropicubeCore");
        this.actionKey = new NamespacedKey(plugin, "waiting_action");
        Bukkit.getServicesManager().register(UiReloadParticipant.class, this, plugin, ServicePriority.Normal);
    }

    public void unregister() {
        Bukkit.getServicesManager().unregister(UiReloadParticipant.class, this);
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) prepareHotbar(event.getPlayer());
        });
    }

    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String action = action(event.getItem());
        if (action == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!waiting()) {
            player.sendMessage(text(player, "fk.selection-locked"));
            return;
        }
        switch (action) {
            case "open:team" -> openTeams(player);
            case "open:kit" -> openKits(player);
            case "open:map" -> openMaps(player);
            case "host:start" -> updateCountdown(player, true);
            case "host:cancel" -> updateCountdown(player, false);
            case "leave" -> core.getRedisManager().publishCommand("PROXY",
                    "CONNECT:" + player.getUniqueId() + ":lobby");
            default -> { }
        }
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MenuType type = openMenus.get(player.getUniqueId());
        if (type == null) {
            if (waiting() && (action(event.getCurrentItem()) != null || numberKeyTargetsAction(player, event))) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() == player.getInventory()) return;
        String action = action(event.getCurrentItem());
        if (action == null) return;
        if (action.equals("back") || action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (!waiting()) {
            player.closeInventory();
            player.sendMessage(text(player, "fk.selection-locked"));
            return;
        }
        applySelection(player, type, action);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void drop(PlayerDropItemEvent event) {
        if (waiting() && action(event.getItemDrop().getItemStack()) != null) event.setCancelled(true);
    }

    @EventHandler
    public void swapHands(PlayerSwapHandItemsEvent event) {
        if (waiting() && (action(event.getMainHandItem()) != null || action(event.getOffHandItem()) != null)) {
            event.setCancelled(true);
        }
    }

    private void applySelection(Player player, MenuType type, String action) {
        boolean selected = false;
        Component selection = Component.empty();
        try {
            if (type == MenuType.TEAM && action.startsWith("team:")) {
                KingdomId kingdom = KingdomId.valueOf(action.substring(5));
                selected = session.chooseKingdom(player.getUniqueId(), kingdom);
                selection = text(player, kingdomKey(kingdom));
            } else if (type == MenuType.KIT && action.startsWith("kit:")) {
                String kit = action.substring(4);
                selected = session.chooseKit(player.getUniqueId(), kit);
                selection = text(player, "fk.kit-" + kit);
            } else if (type == MenuType.MAP && action.startsWith("map:")) {
                String map = action.substring(4);
                selected = session.chooseMap(player.getUniqueId(), map);
                selection = text(player, session.availableMaps().stream().filter(candidate -> candidate.id().equals(map))
                        .map(MapDefinition::displayNameKey).findFirst().orElse(session.mapDisplayNameKey()));
            }
        } catch (IllegalArgumentException ignored) {
            selected = false;
        }
        if (!selected) {
            player.sendMessage(text(player, "fk.selection-refused"));
            return;
        }
        String messageKey = switch (type) {
            case TEAM -> "fk.team-selected-message";
            case KIT -> "fk.kit-selected-message";
            case MAP -> "fk.map-vote-message";
        };
        player.sendMessage(text(player, messageKey,
                PlaceholderValues.builder().putComponent("selection", selection).build()));
        prepareHotbar(player);
        session.refreshHud(player);
        player.closeInventory();
    }

    private void updateCountdown(Player player, boolean start) {
        if (!plugin.isHost(player)) return;
        boolean changed = start ? session.startCountdown() : session.cancelCountdown();
        player.sendMessage(text(player, changed
                ? start ? "fk.countdown-started" : "fk.countdown-cancelled"
                : "fk.transition-refused"));
    }

    private void prepareHotbar(Player player) {
        if (!waiting()) return;
        player.getInventory().clear();
        player.getInventory().setItem(TEAM_SLOT, selectorTeam(player));
        player.getInventory().setItem(KIT_SLOT, selectorKit(player));
        player.getInventory().setItem(MAP_SLOT, selectorMap(player));
        if (plugin.isHost(player)) player.getInventory().setItem(HOST_SLOT, hostControl(player));
        player.getInventory().setItem(PROFILE_SLOT, core.getPlayerCenterMenu().createHotbarItem(player));
        player.getInventory().setItem(LEAVE_SLOT, item(Material.RED_BED, text(player, "fk.leave-item-name"), "leave",
                text(player, "fk.leave-item-lore")));
        session.refreshHud(player);
    }

    private ItemStack selectorTeam(Player player) {
        KingdomId selected = session.preferredKingdom(player.getUniqueId());
        Material material = selected == null ? Material.WHITE_BANNER : Material.valueOf(selected.name() + "_BANNER");
        Component value = text(player, selected == null ? "fk.choice-none" : kingdomKey(selected));
        return item(material, text(player, "fk.selector-team-name"), "open:team",
                text(player, "fk.selector-team-lore", selection(value)));
    }

    private ItemStack selectorKit(Player player) {
        String selected = session.preferredKit(player.getUniqueId());
        KitDefinition kit = session.availableKits().get(selected);
        Material material = kit == null ? Material.COMPASS : kit.icon();
        Component value = kit == null ? text(player, "fk.choice-none") : text(player, "fk.kit-" + kit.id());
        return item(material, text(player, "fk.selector-kit-name"), "open:kit",
                text(player, "fk.selector-kit-lore", selection(value)));
    }

    private ItemStack selectorMap(Player player) {
        String selected = session.mapVoteOf(player.getUniqueId());
        MapDefinition map = session.availableMaps().stream().filter(candidate -> candidate.id().equals(selected))
                .findFirst().orElse(null);
        Component value = text(player, map == null ? session.mapDisplayNameKey() : map.displayNameKey());
        return item(Material.FILLED_MAP, text(player, "fk.selector-map-name"), "open:map",
                text(player, "fk.selector-map-lore", selection(value)));
    }

    private ItemStack hostControl(Player player) {
        boolean countdown = session.state() == GameState.COUNTDOWN;
        return item(countdown ? Material.REDSTONE_BLOCK : Material.NETHER_STAR,
                text(player, countdown ? "fk.host-cancel-name" : "fk.host-start-name"),
                countdown ? "host:cancel" : "host:start",
                text(player, countdown ? "fk.host-cancel-lore" : "fk.host-start-lore"));
    }

    private void openTeams(Player player) {
        Inventory inventory = menu(player, "waiting-team");
        List<Integer> slots = template("waiting-team").dynamicRegion("kingdoms").slots();
        int index = 0;
        KingdomId current = session.preferredKingdom(player.getUniqueId());
        for (KingdomId kingdom : session.availableKingdoms()) {
            if (index >= slots.size()) break;
            PlaceholderValues values = PlaceholderValues.builder()
                    .put("count", session.kingdomPreferences(kingdom))
                    .put("max_players", session.maxPlayersPerKingdom()).build();
            inventory.setItem(slots.get(index++), item(Material.valueOf(kingdom.name() + "_WOOL"),
                    text(player, kingdomKey(kingdom)), "team:" + kingdom.name(),
                    text(player, "fk.team-preferences", values), Component.empty(),
                    text(player, current == kingdom ? "fk.menu-selected" : "fk.menu-select")));
        }
        open(player, inventory, MenuType.TEAM);
    }

    private void openKits(Player player) {
        Inventory inventory = menu(player, "waiting-kit");
        List<Integer> slots = template("waiting-kit").dynamicRegion("kits").slots();
        int index = 0;
        String current = session.preferredKit(player.getUniqueId());
        for (KitDefinition kit : session.availableKits().values()) {
            if (index >= slots.size()) break;
            inventory.setItem(slots.get(index++), item(kit.icon(), text(player, "fk.kit-" + kit.id()),
                    "kit:" + kit.id(), text(player, "fk.kit-" + kit.id() + "-description"), Component.empty(),
                    text(player, current.equals(kit.id()) ? "fk.menu-selected" : "fk.menu-select")));
        }
        open(player, inventory, MenuType.KIT);
    }

    private void openMaps(Player player) {
        Inventory inventory = menu(player, "waiting-map");
        List<Integer> slots = template("waiting-map").dynamicRegion("maps").slots();
        int index = 0;
        String current = session.mapVoteOf(player.getUniqueId());
        for (MapDefinition map : session.availableMaps()) {
            if (index >= slots.size()) break;
            inventory.setItem(slots.get(index++), item(Material.FILLED_MAP, text(player, map.displayNameKey()),
                    "map:" + map.id(), text(player, "fk.map-votes",
                            PlaceholderValues.of("votes", session.mapVotes(map.id()))), Component.empty(),
                    text(player, map.id().equals(current) ? "fk.menu-selected" : "fk.map-selected")));
        }
        open(player, inventory, MenuType.MAP);
    }

    private Inventory menu(Player player, String id) {
        MenuTemplateRegistry.Menu template = template(id);
        Inventory inventory = Bukkit.createInventory(null, template.rows() * 9,
                text(player, template.titleKey()));
        NetworkMenuStyle.applyFrame(inventory, player, template.frame());
        addNavigation(inventory, player, template.button("back"));
        addNavigation(inventory, player, template.button("close"));
        return inventory;
    }

    private void addNavigation(Inventory inventory, Player player, MenuTemplateRegistry.Button button) {
        inventory.setItem(button.slot(), item(button.material(), text(player, button.nameKey()), button.action(),
                text(player, button.loreKey())));
    }

    private void open(Player player, Inventory inventory, MenuType type) {
        player.openInventory(inventory);
        openMenus.put(player.getUniqueId(), type);
    }

    private MenuTemplateRegistry.Menu template(String id) {
        return plugin.menuTemplates().menu(id);
    }

    private ItemStack item(Material material, Component name, String action, Component... lore) {
        ItemStack item = NetworkMenuStyle.item(material, name, lore);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action));
        return item;
    }

    private String action(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
                : item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private boolean numberKeyTargetsAction(Player player, InventoryClickEvent event) {
        return event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
                && action(player.getInventory().getItem(event.getHotbarButton())) != null;
    }

    private boolean waiting() {
        return session.state() == GameState.WAITING || session.state() == GameState.COUNTDOWN;
    }

    private PlaceholderValues selection(Component value) {
        return PlaceholderValues.builder().putComponent("selection", value).build();
    }

    private String kingdomKey(KingdomId kingdom) {
        return "fk.team-" + kingdom.name().toLowerCase(Locale.ROOT);
    }

    private Component text(Player player, String key, Object... arguments) {
        return core.getLanguageManager().getComponent(player.getUniqueId(), key, arguments);
    }

    private Component text(Player player, String key, PlaceholderValues values) {
        return core.getLanguageManager().getComponent(player.getUniqueId(), key, values);
    }

    public void refresh(Player player) {
        prepareHotbar(player);
        MenuType type = openMenus.get(player.getUniqueId());
        if (type == MenuType.TEAM) openTeams(player);
        else if (type == MenuType.KIT) openKits(player);
        else if (type == MenuType.MAP) openMaps(player);
    }

    @Override
    public Runnable prepareReload() {
        return () -> { };
    }

    @Override
    public void refreshViewers() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    private enum MenuType { TEAM, KIT, MAP }
}
