package fr.tropicube.core.menu;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.NotificationService;
import fr.tropicube.core.progression.MissionService;
import fr.tropicube.core.util.ComponentLines;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Localized network center and paginated seven-day notification inbox. */
public final class PlayerCenterMenu implements Listener {
    private static final int PAGE_SIZE = 36;
    private static final int[] MISSION_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19};
    private static final List<String> FILTERS = List.of("ALL", "GUILD", "SOCIAL", "SYSTEM", "MISSION", "MODERATION");
    private final TropicubeCore plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey idKey;
    private final NamespacedKey hotbarKey;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private volatile Consumer<Player> settingsOpener;

    public PlayerCenterMenu(TropicubeCore plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "center_action");
        this.idKey = new NamespacedKey(plugin, "notification_id");
        this.hotbarKey = new NamespacedKey(plugin, "center_hotbar");
    }

    /** Builds the same localized player-center entry point for every game waiting area. */
    public ItemStack createHotbarItem(Player player) {
        ItemStack item = NetworkMenuStyle.playerHead(player,
                message(player, "center.profile-hotbar-name"), message(player, "center.profile-hotbar-lore"));
        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        skullMeta.getPersistentDataContainer().set(hotbarKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(skullMeta);
        return item;
    }

    /** Opens the global center when a lobby or mini-game places its shared hotbar item. */
    @EventHandler
    public void interact(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer()
                .has(hotbarKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        openHome(event.getPlayer());
    }

    public void openHome(Player player) {
        Inventory inventory = Bukkit.createInventory(null, menuSize("profile-home"), menuTitle(player, "profile-home"));
        NetworkMenuStyle.frame(inventory, player);
        inventory.setItem(13, playerHead(player, "center.profile", "center.profile-lore",
                "center.profile-action", "PROFILE"));
        inventory.setItem(21, navigationItem(player, Material.WRITABLE_BOOK,
                "center.missions", "center.missions-lore", "center.missions-action", "MISSIONS"));
        inventory.setItem(23, navigationItem(player, Material.BELL,
                "center.notifications", "center.notifications-lore", "center.notifications-action", "NOTIFICATIONS"));
        inventory.setItem(31, navigationItem(player, Material.COMPARATOR,
                "center.privacy", "center.privacy-lore", "center.privacy-action", "SETTINGS"));
        inventory.setItem(53, navigationItem(player, Material.BARRIER,
                "lobby.close-button", "lobby.close-button-lore", "CLOSE"));
        player.openInventory(inventory);
        states.put(player.getUniqueId(), new State(View.HOME, 0, "ALL"));
    }

    /** Registers the Lobby-owned graphical settings page while that plugin is active. */
    public void setSettingsOpener(Consumer<Player> settingsOpener) {
        this.settingsOpener = settingsOpener;
    }

    public void clearSettingsOpener() {
        this.settingsOpener = null;
    }

    public void openNotifications(Player player, int page, String filter) {
        UUID playerId = player.getUniqueId();
        plugin.getNotificationService().page(playerId, filter, page, PAGE_SIZE).whenComplete((result, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null) {
                        online.sendMessage(message(online, "general.operation-failed"));
                        return;
                    }
                    Inventory inventory = Bukkit.createInventory(null, menuSize("profile-notifications"),
                            menuTitle(online, "profile-notifications", result.page() + 1, result.unread()));
                    NetworkMenuStyle.frame(inventory, online);
                    for (int slot = 0; slot < result.notifications().size(); slot++) {
                        NotificationService.Notification notification = result.notifications().get(slot);
                        Material material = notification.read() ? Material.PAPER : Material.ENCHANTED_BOOK;
                        ItemStack entry = new ItemStack(material);
                        var meta = entry.getItemMeta();
                        meta.itemName(message(online, notification.messageKey(), notification.arguments().toArray()));
                        meta.lore(ComponentLines.splitAll(List.of(message(online, "center.notification-category",
                                        localizedValue(online, "center.notification-category-value-",
                                                notification.category())),
                                message(online, notification.action().type() == NotificationService.ActionType.NONE
                                        ? "center.notification-read" : "center.notification-action"),
                                message(online, "center.notification-delete"))));
                        meta.getPersistentDataContainer().set(idKey, PersistentDataType.LONG, notification.id());
                        entry.setItemMeta(meta);
                        inventory.setItem(9 + slot, entry);
                    }
                    inventory.setItem(45, actionItem(online, Material.ARROW,
                            "center.previous", "center.previous-action", "PREVIOUS"));
                    inventory.setItem(47, actionItem(online, Material.HOPPER,
                            "center.filter", "center.filter-action", "FILTER",
                            messageText(online, "center.notification-category-value-"
                                    + filter.toLowerCase(Locale.ROOT).replace('_', '-'))));
                    inventory.setItem(49, navigationItem(online, Material.ARROW,
                            "center.back", "lobby.back-button-lore", "BACK"));
                    inventory.setItem(50, actionItem(online, Material.LIME_DYE,
                            "center.mark-all-read", "center.mark-all-read-action", "READ_ALL"));
                    inventory.setItem(51, actionItem(online, Material.LAVA_BUCKET,
                            "center.delete-read", "center.delete-read-action", "DELETE_READ"));
                    inventory.setItem(52, result.hasNext()
                            ? actionItem(online, Material.ARROW, "center.next", "center.next-action", "NEXT")
                            : item(online, Material.GRAY_DYE, "center.next", "NONE"));
                    inventory.setItem(53, navigationItem(online, Material.BARRIER,
                            "lobby.close-button", "lobby.close-button-lore", "CLOSE"));
                    online.openInventory(inventory);
                    states.put(playerId, new State(View.NOTIFICATIONS, result.page(), filter));
                }));
    }

    public void openProfile(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getProfileService().view(playerId, playerId).whenComplete((profile, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null || profile == null) { online.sendMessage(message(online, "general.operation-failed")); return; }
                    Inventory inventory = Bukkit.createInventory(null, menuSize("profile-details"),
                            menuTitle(online, "profile-details", profile.username()));
                    NetworkMenuStyle.frame(inventory, online);
                    inventory.setItem(4, playerHead(online, "center.profile-identity",
                            "center.profile-identity-lore", "NONE", profile.username()));
                    var grade = plugin.getPermissionManager().getAllGrades().get(profile.grade());
                    String gradeName = grade == null ? humanize(profile.grade()) : grade.displayName();
                    inventory.setItem(10, display(Material.EXPERIENCE_BOTTLE,
                            message(online, "center.profile-network", profile.networkLevel(),
                                    profile.networkExperience(), gradeName)));
                    inventory.setItem(12, display(Material.WHITE_WOOL,
                            message(online, "center.profile-sheepwars", profile.matches(), profile.wins(), profile.kills())));
                    inventory.setItem(14, display(Material.GOLD_INGOT,
                            message(online, "center.profile-details", Math.round(profile.rating()), profile.friends(),
                                    profile.guildName() == null ? "—" : profile.guildName(),
                                    plugin.getEconomyManager().format(profile.balance()))));
                    if (profile.selectedTitleKey() != null) inventory.setItem(16, display(Material.NAME_TAG,
                            message(online, "center.profile-title", plugin.getLanguageManager().get(
                                    playerId, profile.selectedTitleKey()))));
                    int titleSlot = 18;
                    for (var title : profile.titles()) if (titleSlot < 27) {
                        ItemStack titleItem = display(Material.NAME_TAG, message(online, "center.profile-title",
                                        plugin.getLanguageManager().get(playerId, title.displayKey())),
                                message(online, "center.profile-title-action"));
                        titleItem.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey,
                                PersistentDataType.STRING, "TITLE:" + title.id()));
                        inventory.setItem(titleSlot++, titleItem);
                    }
                    int slot = 27;
                    for (var badge : profile.badges()) if (slot < 36) inventory.setItem(slot++, display(
                            Material.NETHER_STAR, message(online, "center.profile-badge",
                                    plugin.getLanguageManager().get(playerId, badge.displayKey()))));
                    slot = 36;
                    for (var archive : profile.seasonArchives()) if (slot < 45) inventory.setItem(slot++, display(
                            Material.CLOCK, message(online, "center.profile-season", archive.seasonKey(),
                                    messageText(online, "center.rank-" + archive.tier().toLowerCase(Locale.ROOT)),
                                    Math.round(archive.rating()), archive.rankedMatches())));
                    inventory.setItem(45, navigationItem(online, Material.ARROW,
                            "center.back", "lobby.back-button-lore", "BACK"));
                    inventory.setItem(53, navigationItem(online, Material.BARRIER,
                            "lobby.close-button", "lobby.close-button-lore", "CLOSE"));
                    online.openInventory(inventory);
                    states.put(playerId, new State(View.PROFILE, 0, "ALL"));
                }));
    }

    public void openMissions(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getMissionService().current(playerId).whenComplete((missions, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null) { online.sendMessage(message(online, "general.operation-failed")); return; }
                    Inventory inventory = Bukkit.createInventory(null, menuSize("profile-missions"),
                            menuTitle(online, "profile-missions"));
                    NetworkMenuStyle.frame(inventory, online);
                    for (int index = 0; index < missions.size(); index++) {
                        MissionService.Assignment assignment = missions.get(index);
                        String rotation = messageText(online, "center.mission-rotation-"
                                + assignment.rotation().name().toLowerCase(Locale.ROOT));
                        String eventKey = "center.mission-event-" + assignment.mission().event()
                                .toLowerCase(Locale.ROOT).replace('_', '-');
                        Component rewards = assignment.mission().rerollTokens() > 0
                                ? message(online, "center.mission-rewards-with-token",
                                        assignment.mission().experience(),
                                        plugin.getEconomyManager().format(assignment.mission().currency()),
                                        assignment.mission().rerollTokens())
                                : message(online, "center.mission-rewards",
                                        assignment.mission().experience(),
                                        plugin.getEconomyManager().format(assignment.mission().currency()));
                        String statusKey = assignment.rewarded() ? "center.mission-status-claimed"
                                : assignment.completed() ? "center.mission-status-ready"
                                : "center.mission-status-active";
                        String actionLoreKey = assignment.rewarded() ? "center.mission-actions-none"
                                : assignment.rotation() == MissionService.Rotation.DAILY
                                ? "center.mission-actions-daily" : "center.mission-actions-weekly";
                        ItemStack entry = display(assignment.completed() ? Material.LIME_DYE : Material.WRITABLE_BOOK,
                                message(online, "center.mission-title", rotation, assignment.slot() + 1),
                                message(online, eventKey, assignment.mission().target()),
                                message(online, "center.mission-progress", assignment.progress(),
                                        assignment.mission().target()),
                                rewards,
                                message(online, statusKey),
                                message(online, actionLoreKey));
                        entry.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                                "MISSION:" + assignment.rotation().name() + ":" + assignment.slot()));
                        if (index < MISSION_SLOTS.length) inventory.setItem(MISSION_SLOTS[index], entry);
                    }
                    inventory.setItem(45, navigationItem(online, Material.ARROW,
                            "center.back", "lobby.back-button-lore", "BACK"));
                    inventory.setItem(53, navigationItem(online, Material.BARRIER,
                            "lobby.close-button", "lobby.close-button-lore", "CLOSE"));
                    online.openInventory(inventory);
                    states.put(playerId, new State(View.MISSIONS, 0, "ALL"));
                }));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        Long notificationId = clicked.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.LONG);
        if (notificationId != null && state.view() == View.NOTIFICATIONS) {
            if (event.getClick().isRightClick()) {
                plugin.getNotificationService().delete(player.getUniqueId(), notificationId)
                        .thenRun(() -> openNotifications(player, state.page(), state.filter()));
            } else if (event.getClick().isLeftClick()) {
                plugin.getNotificationService().inbox(player.getUniqueId(), 100).thenAccept(values -> values.stream()
                        .filter(value -> value.id() == notificationId).findFirst().ifPresent(value -> {
                            if (value.action().type() == NotificationService.ActionType.SUGGEST_COMMAND) {
                                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                                        message(player, "center.notification-command", value.action().value())
                                                .clickEvent(ClickEvent.suggestCommand(value.action().value()))));
                            }
                        })).thenCompose(ignored -> plugin.getNotificationService().markRead(
                                player.getUniqueId(), notificationId))
                        .thenRun(() -> openNotifications(player, state.page(), state.filter()));
            }
            return;
        }
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("NONE")) return;
        boolean missionAction = action.startsWith("MISSION:");
        if (!event.getClick().isLeftClick() && !(missionAction && event.getClick().isRightClick())) return;
        switch (action) {
            case "PROFILE" -> openProfile(player);
            case "MISSIONS" -> openMissions(player);
            case "NOTIFICATIONS" -> openNotifications(player, 0, "ALL");
            case "SETTINGS" -> {
                Consumer<Player> opener = settingsOpener;
                if (opener == null) dispatch(player, "settings"); else opener.accept(player);
            }
            case "CLOSE" -> player.closeInventory();
            case "BACK" -> openHome(player);
            case "PREVIOUS" -> openNotifications(player, Math.max(0, state.page() - 1), state.filter());
            case "NEXT" -> openNotifications(player, state.page() + 1, state.filter());
            case "FILTER" -> openNotifications(player, 0, nextFilter(state.filter()));
            case "READ_ALL" -> plugin.getNotificationService().markAllRead(player.getUniqueId())
                    .thenRun(() -> openNotifications(player, state.page(), state.filter()));
            case "DELETE_READ" -> plugin.getNotificationService().deleteRead(player.getUniqueId())
                    .thenRun(() -> openNotifications(player, state.page(), state.filter()));
            default -> {
                if (action.startsWith("MISSION:")) handleMission(player, action, event.getClick());
                else if (action.startsWith("TITLE:")) plugin.getProfileService().selectTitle(
                        player.getUniqueId(), action.substring("TITLE:".length())).thenRun(() -> openProfile(player));
            }
        }
    }

    @EventHandler public void close(InventoryCloseEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private ItemStack item(Player player, Material material, String key, String action, Object... args) {
        ItemStack item = NetworkMenuStyle.item(material, message(player, key, args));
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Player player, Material material, String nameKey,
                                 String loreKey, String action, Object... nameArgs) {
        ItemStack item = NetworkMenuStyle.item(material,
                message(player, nameKey, nameArgs), message(player, loreKey));
        item.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action));
        return item;
    }

    private ItemStack navigationItem(Player player, Material material, String nameKey,
                                     String descriptionKey, String actionKey, String action) {
        ItemStack item = NetworkMenuStyle.item(material, message(player, nameKey),
                message(player, descriptionKey), Component.empty(), message(player, actionKey));
        item.editMeta(meta -> meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action));
        return item;
    }

    private ItemStack navigationItem(Player player, Material material, String nameKey, String loreKey, String action) {
        ItemStack item = NetworkMenuStyle.item(material, message(player, nameKey), message(player, loreKey));
        item.editMeta(meta -> meta.getPersistentDataContainer().set(this.actionKey, PersistentDataType.STRING, action));
        return item;
    }

    private ItemStack playerHead(Player player, String nameKey, String descriptionKey,
                                 String actionLoreKey, String action, Object... nameArgs) {
        ItemStack item = NetworkMenuStyle.playerHead(player, message(player, nameKey, nameArgs),
                message(player, descriptionKey), Component.empty(), message(player, actionLoreKey));
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player player, String nameKey, String loreKey, String action, Object... nameArgs) {
        ItemStack item = NetworkMenuStyle.playerHead(player,
                message(player, nameKey, nameArgs), message(player, loreKey));
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack display(Material material, Component name, Component... lore) {
        return NetworkMenuStyle.item(material, name, lore);
    }

    private void handleMission(Player player, String action, ClickType click) {
        String[] fields = action.split(":");
        if (fields.length != 3) return;
        try {
            MissionService.Rotation rotation = MissionService.Rotation.valueOf(fields[1]);
            int slot = Integer.parseInt(fields[2]);
            if (click == ClickType.RIGHT && rotation == MissionService.Rotation.DAILY) {
                int allowance = player.hasPermission("tropicube.missions.reroll.bonus") ? 4 : 2;
                plugin.getMissionService().rerollDaily(player.getUniqueId(), slot, allowance)
                        .thenRun(() -> openMissions(player));
            } else if (click.isLeftClick()) {
                plugin.getMissionService().claim(player.getUniqueId(), rotation, slot)
                        .thenRun(() -> openMissions(player));
            }
        } catch (IllegalArgumentException ignored) { }
    }

    private Component message(Player player, String key, Object... args) {
        return plugin.getLanguageManager().getComponent(player.getUniqueId(), key, args);
    }

    private int menuSize(String id) {
        return plugin.getMenuTemplates().menu(id).rows() * 9;
    }

    private Component menuTitle(Player player, String id, Object... args) {
        return message(player, plugin.getMenuTemplates().menu(id).titleKey(), args);
    }
    private String messageText(Player player, String key, Object... args) {
        return plugin.getLanguageManager().get(player.getUniqueId(), key, args);
    }
    private Component localizedValue(Player player, String keyPrefix, String raw) {
        return message(player, keyPrefix + raw.toLowerCase(Locale.ROOT).replace('_', '-'));
    }
    private static String humanize(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        String spaced = raw.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
    private void dispatch(Player player, String command) { player.closeInventory(); Bukkit.dispatchCommand(player, command); }
    private String nextFilter(String current) {
        int index = FILTERS.indexOf(current == null ? "ALL" : current);
        return FILTERS.get((Math.max(0, index) + 1) % FILTERS.size());
    }
    private enum View { HOME, PROFILE, MISSIONS, NOTIFICATIONS }
    private record State(View view, int page, String filter) {}
}
