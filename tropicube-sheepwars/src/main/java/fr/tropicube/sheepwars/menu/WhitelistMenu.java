package fr.tropicube.sheepwars.menu;

import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.docker.model.WhitelistUpdateProtocol;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.sheepwars.util.ItemBuilder;
import fr.tropicube.sheepwars.util.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Host-only GUI for adding and removing members of a private custom game. */
public final class WhitelistMenu implements Listener {

    private static final int SIZE = 54;
    private static final int ADD_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final long UPDATE_TIMEOUT_TICKS = 100L;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final List<Integer> MEMBER_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final TropicubeSheepwars plugin;
    private final NamespacedKey selectorKey;
    private final Map<UUID, PendingUpdate> pendingUpdates = new HashMap<>();

    public WhitelistMenu(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.selectorKey = new NamespacedKey(plugin, "private_whitelist_item");
    }

    public ItemStack createSelectorItem(Player player) {
        return new ItemBuilder(Material.IRON_DOOR)
                .name(LangHelper.component(player, "sw.whitelist-item-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, "sw.whitelist-item-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .persistentData(selectorKey, PersistentDataType.BYTE, (byte) 1)
                .build();
    }

    public boolean isSelectorItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(selectorKey, PersistentDataType.BYTE);
    }

    /** Loads Redis away from the Paper thread, then opens a consistent member snapshot. */
    public void open(Player player) {
        UUID playerId = player.getUniqueId();
        player.sendMessage(LangHelper.component(player, "sw.whitelist-loading"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ServerInstance instance = loadOwnedPrivateInstance(playerId);
            Map<UUID, String> members = new LinkedHashMap<>();
            if (instance != null) {
                for (UUID memberId : instance.getWhitelistedPlayers()) {
                    if (memberId.equals(playerId)) continue;
                    String name = plugin.getRedisManager().get("player:name:" + memberId);
                    members.put(memberId, name == null || name.isBlank() ? memberId.toString() : name);
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null) return;
                if (instance == null) {
                    online.sendMessage(LangHelper.component(online, "sw.whitelist-unavailable"));
                    return;
                }
                online.openInventory(buildMemberInventory(online, members));
            });
        });
    }

    private ServerInstance loadOwnedPrivateInstance(UUID hostId) {
        String instanceId = plugin.getRedisManager().get("host:" + hostId);
        if (instanceId == null || !instanceId.equals(plugin.getGameManager().getInstanceId())) return null;
        ServerInstance instance = plugin.getRedisManager().getInstance(instanceId);
        return instance != null && instance.isWhitelisted() ? instance : null;
    }

    private Inventory buildMemberInventory(Player player, Map<UUID, String> members) {
        MemberHolder holder = new MemberHolder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                LangHelper.component(player, "sw.whitelist-menu-title"));
        holder.inventory = inventory;
        NetworkMenuStyle.frame(inventory);

        int index = 0;
        for (Map.Entry<UUID, String> member : members.entrySet()) {
            if (index >= MEMBER_SLOTS.size()) break;
            int slot = MEMBER_SLOTS.get(index++);
            holder.members.put(slot, member.getKey());
            inventory.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .name(Component.text(member.getValue()).decoration(TextDecoration.ITALIC, false))
                    .lore(LangHelper.component(player, "sw.whitelist-remove-lore")
                            .decoration(TextDecoration.ITALIC, false))
                    .build());
        }
        inventory.setItem(ADD_SLOT, new ItemBuilder(Material.ANVIL)
                .name(LangHelper.component(player, "sw.whitelist-add-name")
                        .decoration(TextDecoration.ITALIC, false))
                .lore(LangHelper.component(player, "sw.whitelist-add-lore")
                        .decoration(TextDecoration.ITALIC, false))
                .build());
        inventory.setItem(CLOSE_SLOT, new ItemBuilder(Material.BARRIER)
                .name(LangHelper.component(player, "sw.whitelist-close")
                        .decoration(TextDecoration.ITALIC, false)).build());
        return inventory;
    }

    private void openAnvil(Player player) {
        String placeholder = LangHelper.get(player, "sw.whitelist-anvil-placeholder");
        AnvilHolder holder = new AnvilHolder(placeholder);
        Inventory inventory = Bukkit.createInventory(holder, InventoryType.ANVIL,
                LangHelper.component(player, "sw.whitelist-anvil-title"));
        holder.inventory = inventory;
        inventory.setItem(0, new ItemBuilder(Material.NAME_TAG)
                .name(LangHelper.component(player, "sw.whitelist-anvil-placeholder")
                        .decoration(TextDecoration.ITALIC, false)).build());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getInventory().getHolder() instanceof AnvilHolder holder)) return;
        event.getView().setRepairCost(0);
        event.getView().setRepairItemCountCost(0);
        event.getView().setMaximumRepairCost(1);
        String name = event.getView().getRenameText();
        if (!isValidIdentifier(name) || name.trim().equalsIgnoreCase(holder.placeholder)) {
            event.setResult(null);
            return;
        }
        event.setResult(new ItemBuilder(Material.LIME_DYE)
                .name(Component.text(name.trim()).decoration(TextDecoration.ITALIC, false)).build());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof MemberHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() == CLOSE_SLOT) {
                player.closeInventory();
            } else if (event.getRawSlot() == ADD_SLOT) {
                openAnvil(player);
            } else {
                UUID memberId = holder.members.get(event.getRawSlot());
                if (memberId != null) publishUpdate(player, false, memberId.toString());
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof AnvilHolder)
                || !(event.getView() instanceof AnvilView anvilView)) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return;
        String name = anvilView.getRenameText();
        if (!isValidIdentifier(name)) return;
        publishUpdate(player, true, name.trim());
    }

    private void publishUpdate(Player player, boolean add, String target) {
        UUID playerId = player.getUniqueId();
        UUID requestId = UUID.randomUUID();
        player.closeInventory();
        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingUpdate pending = pendingUpdates.remove(requestId);
            if (pending == null) return;
            Player online = Bukkit.getPlayer(pending.hostId());
            if (online != null) {
                online.sendMessage(LangHelper.component(online, "sw.whitelist-update-timeout"));
                open(online);
            }
        }, UPDATE_TIMEOUT_TICKS);
        pendingUpdates.put(requestId, new PendingUpdate(playerId, timeoutTask));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getRedisManager().publishCommand("PROXY", WhitelistUpdateProtocol.requestCommand(
                        playerId, add, requestId, target));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning(MessageStyle.log("sw", "WHITELIST",
                        "<yellow>Échec de publication : " + exception.getMessage()));
                Bukkit.getScheduler().runTask(plugin, () -> failRequest(requestId));
            }
        });
    }

    /** Handles the Velocity acknowledgement on the Paper scheduler before touching inventories. */
    public void handleProxyCommand(String message) {
        WhitelistUpdateProtocol.parseResultMessage(message).ifPresent(result ->
                Bukkit.getScheduler().runTask(plugin, () -> completeRequest(result.hostId(), result.requestId())));
    }

    private void completeRequest(UUID hostId, UUID requestId) {
        PendingUpdate pending = pendingUpdates.get(requestId);
        if (pending == null || !pending.hostId().equals(hostId)) return;
        pendingUpdates.remove(requestId);
        pending.timeoutTask().cancel();
        Player online = Bukkit.getPlayer(hostId);
        if (online != null) open(online);
    }

    private void failRequest(UUID requestId) {
        PendingUpdate pending = pendingUpdates.remove(requestId);
        if (pending == null) return;
        pending.timeoutTask().cancel();
        Player online = Bukkit.getPlayer(pending.hostId());
        if (online != null) {
            online.sendMessage(LangHelper.component(online, "sw.whitelist-update-timeout"));
            open(online);
        }
    }

    static boolean isValidIdentifier(String value) {
        if (value == null) return false;
        String candidate = value.trim();
        if (PLAYER_NAME.matcher(candidate).matches()) return true;
        try {
            return UUID.fromString(candidate).toString().equalsIgnoreCase(candidate);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static final class MemberHolder implements InventoryHolder {
        private final Map<Integer, UUID> members = new LinkedHashMap<>();
        private Inventory inventory;
        @Override public @NonNull Inventory getInventory() { return inventory; }
    }

    private static final class AnvilHolder implements InventoryHolder {
        private final String placeholder;
        private Inventory inventory;
        private AnvilHolder(String placeholder) { this.placeholder = placeholder; }
        @Override public @NonNull Inventory getInventory() { return inventory; }
    }

    private record PendingUpdate(UUID hostId, BukkitTask timeoutTask) {
    }
}
