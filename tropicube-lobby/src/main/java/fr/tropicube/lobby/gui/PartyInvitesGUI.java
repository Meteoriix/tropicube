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

/** Displays pending party invitations with explicit accept and deny actions. */
public final class PartyInvitesGUI {
    public static final int BACK_SLOT = 45;
    public static final int PREVIOUS_SLOT = 48;
    public static final int NEXT_SLOT = 50;
    public static final int CLOSE_SLOT = 53;
    static final int PAGE_SIZE = 28;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private PartyInvitesGUI() { }

    public static Inventory build(Player player, List<InviteEntry> invites, int requestedPage) {
        int page = normalizedPage(requestedPage, invites.size());
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, invites.size());
        boolean hasPrevious = page > 0;
        boolean hasNext = to < invites.size();
        Map<Integer, Action> acceptActions = new LinkedHashMap<>();
        Map<Integer, Action> denyActions = new LinkedHashMap<>();
        Holder holder = new Holder(page, hasPrevious, hasNext, acceptActions, denyActions);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                LangHelper.component(player, "social.party-requests-title", page + 1));
        holder.inventory = inventory;
        NetworkMenuStyle.frame(inventory);

        if (invites.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.PAPER)
                    .name(LangHelper.get(player, "social.party-requests-empty")).build());
        } else {
            for (int index = from; index < to; index++) {
                InviteEntry invite = invites.get(index);
                int slot = ENTRY_SLOTS[index - from];
                inventory.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                        .skullProfile(invite.profile())
                        .name(LangHelper.get(player, "social.party-request-entry", invite.username()))
                        .lore(LangHelper.get(player, "social.party-request-left-click"),
                                LangHelper.get(player, "social.party-request-right-click"))
                        .build());
                acceptActions.put(slot, new Action(ActionType.ACCEPT, invite.username()));
                denyActions.put(slot, new Action(ActionType.DENY, invite.username()));
            }
        }

        inventory.setItem(BACK_SLOT, ItemBuilder.backButton(player));
        if (hasPrevious) inventory.setItem(PREVIOUS_SLOT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-prev-page")).build());
        if (hasNext) inventory.setItem(NEXT_SLOT, new ItemBuilder(Material.ARROW)
                .name(LangHelper.get(player, "lobby.server-next-page")).build());
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    static int normalizedPage(int requestedPage, int inviteCount) {
        int lastPage = Math.max(0, (inviteCount - 1) / PAGE_SIZE);
        return Math.min(Math.max(requestedPage, 0), lastPage);
    }

    static Action actionForClick(Action accept, Action deny, ClickType click) {
        if (click.isRightClick()) return deny;
        if (click.isLeftClick()) return accept;
        return null;
    }

    public record InviteEntry(UUID leaderId, String username, ResolvableProfile profile) {
        public InviteEntry {
            Objects.requireNonNull(leaderId, "leaderId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(profile, "profile");
        }
    }

    public record Action(ActionType type, String username) { }
    public enum ActionType { ACCEPT, DENY }

    public static final class Holder implements InventoryHolder {
        private final int page;
        private final boolean hasPrevious;
        private final boolean hasNext;
        private final Map<Integer, Action> acceptActions;
        private final Map<Integer, Action> denyActions;
        private Inventory inventory;

        private Holder(int page, boolean hasPrevious, boolean hasNext,
                       Map<Integer, Action> acceptActions, Map<Integer, Action> denyActions) {
            this.page = page;
            this.hasPrevious = hasPrevious;
            this.hasNext = hasNext;
            this.acceptActions = acceptActions;
            this.denyActions = denyActions;
        }

        public int page() { return page; }
        public boolean hasPrevious() { return hasPrevious; }
        public boolean hasNext() { return hasNext; }
        public Action action(int slot, ClickType click) {
            return actionForClick(acceptActions.get(slot), denyActions.get(slot), click);
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
