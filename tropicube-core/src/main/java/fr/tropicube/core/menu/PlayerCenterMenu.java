package fr.tropicube.core.menu;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.NotificationService;
import fr.tropicube.core.progression.MissionService;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Localized network center and paginated seven-day notification inbox. */
public final class PlayerCenterMenu implements Listener {
    private static final int PAGE_SIZE = 45;
    private static final List<String> FILTERS = List.of("ALL", "GUILD", "SOCIAL", "SYSTEM", "MISSION", "MODERATION");
    private final TropicubeCore plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey idKey;
    private final NamespacedKey hotbarKey;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public PlayerCenterMenu(TropicubeCore plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "center_action");
        this.idKey = new NamespacedKey(plugin, "notification_id");
        this.hotbarKey = new NamespacedKey(plugin, "center_hotbar");
    }

    /** Builds the same localized player-center entry point for every game waiting area. */
    public ItemStack createHotbarItem(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        item.editMeta(meta -> {
            meta.itemName(message(player, "center.hotbar-name"));
            meta.lore(List.of(message(player, "center.hotbar-lore")));
            meta.getPersistentDataContainer().set(hotbarKey, PersistentDataType.BYTE, (byte) 1);
        });
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
        Inventory inventory = Bukkit.createInventory(null, 27, message(player, "center.title"));
        inventory.setItem(10, item(player, Material.PLAYER_HEAD, "center.profile", "PROFILE"));
        inventory.setItem(12, item(player, Material.WRITABLE_BOOK, "center.missions", "MISSIONS"));
        inventory.setItem(14, item(player, Material.BELL, "center.notifications", "NOTIFICATIONS"));
        inventory.setItem(16, item(player, Material.SHIELD, "center.guilds", "GUILDS"));
        inventory.setItem(22, item(player, Material.COMPARATOR, "center.privacy", "SETTINGS"));
        player.openInventory(inventory);
        states.put(player.getUniqueId(), new State(View.HOME, 0, "ALL"));
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
                    Inventory inventory = Bukkit.createInventory(null, 54,
                            message(online, "center.notifications-title", result.page() + 1, result.unread()));
                    for (int slot = 0; slot < result.notifications().size(); slot++) {
                        NotificationService.Notification notification = result.notifications().get(slot);
                        Material material = notification.read() ? Material.PAPER : Material.ENCHANTED_BOOK;
                        ItemStack entry = new ItemStack(material);
                        var meta = entry.getItemMeta();
                        meta.itemName(message(online, notification.messageKey(), notification.arguments().toArray()));
                        meta.lore(List.of(message(online, "center.notification-category", notification.category()),
                                message(online, notification.action().type() == NotificationService.ActionType.NONE
                                        ? "center.notification-read" : "center.notification-action"),
                                message(online, "center.notification-delete")));
                        meta.getPersistentDataContainer().set(idKey, PersistentDataType.LONG, notification.id());
                        entry.setItemMeta(meta);
                        inventory.setItem(slot, entry);
                    }
                    inventory.setItem(45, item(online, Material.ARROW, "center.previous", "PREVIOUS"));
                    inventory.setItem(47, item(online, Material.HOPPER, "center.filter", "FILTER", result.page()));
                    inventory.setItem(49, item(online, Material.BARRIER, "center.back", "BACK"));
                    inventory.setItem(51, item(online, Material.LIME_DYE, "center.mark-all-read", "READ_ALL"));
                    inventory.setItem(52, item(online, Material.LAVA_BUCKET, "center.delete-read", "DELETE_READ"));
                    inventory.setItem(53, item(online, result.hasNext() ? Material.ARROW : Material.GRAY_DYE,
                            "center.next", result.hasNext() ? "NEXT" : "NONE"));
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
                    Inventory inventory = Bukkit.createInventory(null, 54,
                            message(online, "center.profile-header", profile.username()));
                    inventory.setItem(10, display(Material.EXPERIENCE_BOTTLE,
                            message(online, "center.profile-network", profile.networkLevel(),
                                    profile.networkExperience(), profile.grade())));
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
                                plugin.getLanguageManager().get(playerId, title.displayKey())));
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
                                    archive.tier(), Math.round(archive.rating()), archive.rankedMatches())));
                    inventory.setItem(49, item(online, Material.BARRIER, "center.back", "BACK"));
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
                    Inventory inventory = Bukkit.createInventory(null, 27, message(online, "center.missions-header"));
                    for (int index = 0; index < missions.size(); index++) {
                        MissionService.Assignment assignment = missions.get(index);
                        ItemStack entry = display(assignment.completed() ? Material.LIME_DYE : Material.WRITABLE_BOOK,
                                message(online, "center.missions-entry", assignment.rotation(), assignment.slot() + 1,
                                        assignment.mission().id(), assignment.progress(), assignment.mission().target(),
                                        assignment.rewarded() ? "✓" : ""),
                                message(online, "center.mission-actions"));
                        entry.editMeta(meta -> meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                                "MISSION:" + assignment.rotation().name() + ":" + assignment.slot()));
                        inventory.setItem(10 + index, entry);
                    }
                    inventory.setItem(22, item(online, Material.BARRIER, "center.back", "BACK"));
                    online.openInventory(inventory);
                    states.put(playerId, new State(View.MISSIONS, 0, "ALL"));
                }));
    }

    public void openGuilds(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getGuildService().currentRanking(20).thenCombine(
                plugin.getGuildService().guild(playerId), GuildPage::new).whenComplete((page, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online == null) return;
                    if (error != null) { online.sendMessage(message(online, "general.operation-failed")); return; }
                    Inventory inventory = Bukkit.createInventory(null, 27, message(online, "guild.ranking-header"));
                    for (int index = 0; index < Math.min(20, page.ranking().size()); index++) {
                        var value = page.ranking().get(index);
                        inventory.setItem(index, display(Material.SHIELD, message(online, "guild.ranking-entry",
                                index + 1, value.tag(), value.name(), Math.round(value.score()), value.rankedMatches())));
                    }
                    if (page.guild() != null) inventory.setItem(24, display(Material.GOLDEN_HELMET,
                            message(online, "guild.info", page.guild().tag(), page.guild().name(),
                                    page.guild().level(), page.guild().experience())));
                    inventory.setItem(22, item(online, Material.BARRIER, "center.back", "BACK"));
                    online.openInventory(inventory);
                    states.put(playerId, new State(View.GUILDS, 0, "ALL"));
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
            if (event.getClick() == ClickType.RIGHT) {
                plugin.getNotificationService().delete(player.getUniqueId(), notificationId)
                        .thenRun(() -> openNotifications(player, state.page(), state.filter()));
            } else {
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
        switch (action) {
            case "PROFILE" -> openProfile(player);
            case "MISSIONS" -> openMissions(player);
            case "NOTIFICATIONS" -> openNotifications(player, 0, "ALL");
            case "GUILDS" -> openGuilds(player);
            case "SETTINGS" -> dispatch(player, "settings");
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
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.itemName(message(player, key, args));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack display(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.itemName(name); if (lore.length > 0) meta.lore(List.of(lore)); });
        return item;
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
            } else {
                plugin.getMissionService().claim(player.getUniqueId(), rotation, slot)
                        .thenRun(() -> openMissions(player));
            }
        } catch (IllegalArgumentException ignored) { }
    }

    private Component message(Player player, String key, Object... args) {
        return plugin.getLanguageManager().getComponent(player.getUniqueId(), key, args);
    }
    private void dispatch(Player player, String command) { player.closeInventory(); Bukkit.dispatchCommand(player, command); }
    private String nextFilter(String current) {
        int index = FILTERS.indexOf(current == null ? "ALL" : current);
        return FILTERS.get((Math.max(0, index) + 1) % FILTERS.size());
    }
    private enum View { HOME, PROFILE, MISSIONS, NOTIFICATIONS, GUILDS }
    private record GuildPage(List<fr.tropicube.core.guild.GuildService.Ranking> ranking,
                             fr.tropicube.core.guild.GuildService.Guild guild) { }
    private record State(View view, int page, String filter) {}
}
