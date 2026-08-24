package fr.tropicube.lobby.gui;

import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Paginated two-column view of received and sent party invitations. */
public final class PartyInvitesGUI {
    public static final int SIZE = 54;
    public static final int BACK_SLOT = 45;
    public static final int PREVIOUS_SLOT = 48;
    public static final int NEXT_SLOT = 50;
    public static final int CLOSE_SLOT = 53;
    static final int PAGE_SIZE = 12;
    private static final int[] INCOMING_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39};
    private static final int[] SENT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};

    private PartyInvitesGUI() { }

    public static Inventory build(Player player, List<InviteEntry> incoming,
                                  List<InviteEntry> sent, int requestedPage) {
        int page = normalizedPage(requestedPage, incoming.size(), sent.size());
        boolean hasPrevious = page > 0;
        boolean hasNext = (page + 1) * PAGE_SIZE < Math.max(incoming.size(), sent.size());
        Map<Integer, Action> leftActions = new LinkedHashMap<>();
        Map<Integer, Action> rightActions = new LinkedHashMap<>();
        Holder holder = new Holder(page, hasPrevious, hasNext, leftActions, rightActions);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                LangHelper.component(player, "social.party-requests-title", page + 1));
        holder.inventory = inventory;
        NetworkMenuStyle.frame(inventory);

        inventory.setItem(3, new ItemBuilder(Material.LIME_DYE)
                .name(LangHelper.get(player, "social.party-requests-incoming-title", incoming.size())).build());
        inventory.setItem(5, new ItemBuilder(Material.LIGHT_BLUE_DYE)
                .name(LangHelper.get(player, "social.party-requests-sent-title", sent.size())).build());
        drawEntries(player, inventory, incoming, page, INCOMING_SLOTS, true, leftActions, rightActions);
        drawEntries(player, inventory, sent, page, SENT_SLOTS, false, leftActions, rightActions);

        inventory.setItem(BACK_SLOT, ItemBuilder.backButton(player));
        if (hasPrevious) inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-prev-page")).build());
        if (hasNext) inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-next-page")).build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    private static void drawEntries(Player player, Inventory inventory, List<InviteEntry> entries, int page,
                                    int[] slots, boolean incoming, Map<Integer, Action> leftActions,
                                    Map<Integer, Action> rightActions) {
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, entries.size());
        if (from >= entries.size()) {
            int slot = incoming ? INCOMING_SLOTS[4] : SENT_SLOTS[4];
            inventory.setItem(slot, new ItemBuilder(Material.PAPER)
                    .name(LangHelper.get(player, incoming
                            ? "social.party-requests-incoming-empty" : "social.party-requests-sent-empty")).build());
            return;
        }
        for (int index = from; index < to; index++) {
            InviteEntry invite = entries.get(index);
            int slot = slots[index - from];
            List<String> lore = incoming
                    ? List.of(LangHelper.get(player, "social.party-request-left-click"),
                            LangHelper.get(player, "social.party-request-right-click"))
                    : List.of(LangHelper.get(player, "social.party-request-sent-right-click"));
            inventory.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullProfile(invite.profile())
                    .name(LangHelper.get(player, incoming
                            ? "social.party-request-incoming-entry" : "social.party-request-sent-entry",
                            invite.username()))
                    .lore(lore)
                    .build());
            if (incoming) {
                leftActions.put(slot, new Action(ActionType.ACCEPT, invite.playerId(), invite.username()));
            }
            rightActions.put(slot, new Action(incoming ? ActionType.DENY : ActionType.CANCEL,
                    invite.playerId(), invite.username()));
        }
    }

    static int normalizedPage(int requestedPage, int incomingSize, int sentSize) {
        int lastPage = Math.max(0, (Math.max(incomingSize, sentSize) - 1) / PAGE_SIZE);
        return Math.min(Math.max(requestedPage, 0), lastPage);
    }

    static Action actionForClick(Action accept, Action deny, ClickType click) {
        if (click.isRightClick()) return deny;
        if (click.isLeftClick()) return accept;
        return null;
    }

    public record InviteEntry(UUID playerId, String username, ResolvableProfile profile) {
        public InviteEntry {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(profile, "profile");
        }
    }

    public record Action(ActionType type, UUID playerId, String username) { }
    public enum ActionType { ACCEPT, DENY, CANCEL }

    public static final class Holder implements InventoryHolder {
        private final int page;
        private final boolean hasPrevious;
        private final boolean hasNext;
        private final Map<Integer, Action> leftActions;
        private final Map<Integer, Action> rightActions;
        private Inventory inventory;

        private Holder(int page, boolean hasPrevious, boolean hasNext,
                       Map<Integer, Action> leftActions, Map<Integer, Action> rightActions) {
            this.page = page;
            this.hasPrevious = hasPrevious;
            this.hasNext = hasNext;
            this.leftActions = leftActions;
            this.rightActions = rightActions;
        }

        public int page() { return page; }
        public boolean hasPrevious() { return hasPrevious; }
        public boolean hasNext() { return hasNext; }
        public Action action(int slot, ClickType click) {
            return actionForClick(leftActions.get(slot), rightActions.get(slot), click);
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
