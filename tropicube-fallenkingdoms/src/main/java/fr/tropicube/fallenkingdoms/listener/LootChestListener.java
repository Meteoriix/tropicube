package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.fallenkingdoms.game.GameSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;

import java.util.Set;

/** Allows withdrawal from map loot chests while rejecting every storage path. */
public final class LootChestListener implements Listener {
    private static final Set<InventoryAction> WITHDRAWAL_ACTIONS = Set.of(
            InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF, InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME, InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.DROP_ALL_SLOT, InventoryAction.DROP_ONE_SLOT,
            InventoryAction.COLLECT_TO_CURSOR);

    private final GameSession session;

    public LootChestListener(GameSession session) {
        this.session = session;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void click(InventoryClickEvent event) {
        if (!session.isLootInventory(event.getView().getTopInventory())) return;
        boolean topSlot = event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize();
        boolean allowed = topSlot
                ? WITHDRAWAL_ACTIONS.contains(event.getAction())
                : event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY;
        if (!allowed) {
            event.setCancelled(true);
            session.warnLootDeposit(event.getWhoClicked().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void drag(InventoryDragEvent event) {
        if (!session.isLootInventory(event.getView().getTopInventory())) return;
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
            session.warnLootDeposit(event.getWhoClicked().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void move(InventoryMoveItemEvent event) {
        if (session.isLootInventory(event.getSource()) || session.isLootInventory(event.getDestination())) {
            event.setCancelled(true);
        }
    }
}
