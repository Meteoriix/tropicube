package fr.tropicube.lobby.gui;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.docker.model.PartySnapshot;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Three-tab social menu whose holder contains immutable actions instead of trusting item text. */
public final class SocialGUI {
    static final String FRIENDS_HEAD_ID = "117085";
    static final String PARTY_HEAD_ID = "117095";
    public static final int SIZE = 54;
    public static final int FRIENDS_TAB_SLOT = 2;
    public static final int PARTY_TAB_SLOT = 4;
    public static final int GUILDS_TAB_SLOT = 6;
    public static final int REQUESTS_SLOT = 49;
    public static final int CLOSE_SLOT = 53;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private SocialGUI() { }

    public static Inventory build(Player player, View view, List<FriendEntry> friends,
                                   List<PartyEntry> partyMembers, int friendRequestCount,
                                   int receivedPartyInviteCount, int sentPartyInviteCount,
                                   PartySnapshot party) {
        Map<Integer, Action> actions = new LinkedHashMap<>();
        Map<Integer, Action> rightClickActions = new LinkedHashMap<>();
        Holder holder = new Holder(view, actions, rightClickActions);
        Inventory inventory = Bukkit.createInventory(holder, LangHelper.menuSize("social"),
                LangHelper.component(player, view == View.FRIENDS
                        ? "social.menu-title" : "social.menu-party-title"));
        holder.inventory = inventory;
        NetworkMenuStyle.frame(inventory, player);

        inventory.setItem(FRIENDS_TAB_SLOT, navigationItem(player, FRIENDS_HEAD_ID,
                "social.menu-friends", view == View.FRIENDS));
        inventory.setItem(PARTY_TAB_SLOT, navigationItem(player, PARTY_HEAD_ID,
                "social.menu-party", view == View.PARTY));
        inventory.setItem(GUILDS_TAB_SLOT, new ItemBuilder(Material.SHIELD)
                .name(LangHelper.get(player, "guild-ui.tab"))
                .lore(LangHelper.get(player, "guild-ui.tab-action")).build());
        actions.put(GUILDS_TAB_SLOT, new Action(ActionType.OPEN_GUILDS, ""));
        actions.put(FRIENDS_TAB_SLOT, new Action(ActionType.OPEN_FRIENDS, ""));
        actions.put(PARTY_TAB_SLOT, new Action(ActionType.OPEN_PARTY, ""));

        if (view == View.FRIENDS) {
            drawFriends(player, inventory, friends, actions, rightClickActions);
            inventory.setItem(REQUESTS_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(LangHelper.get(player, "social.menu-requests"))
                    .lore(LangHelper.get(player, "social.menu-requests-count", friendRequestCount),
                            LangHelper.get(player, "social.menu-requests-action")).build());
            actions.put(REQUESTS_SLOT, new Action(ActionType.OPEN_FRIEND_REQUESTS, ""));
        } else {
            drawParty(player, inventory, partyMembers, party, actions, rightClickActions);
            inventory.setItem(REQUESTS_SLOT, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(LangHelper.get(player, "social.menu-party-requests"))
                    .lore(LangHelper.get(player, "social.menu-party-requests-count", receivedPartyInviteCount),
                            LangHelper.get(player, "social.menu-party-requests-sent-count", sentPartyInviteCount),
                            LangHelper.get(player, "social.menu-party-requests-action")).build());
            actions.put(REQUESTS_SLOT, new Action(ActionType.OPEN_PARTY_REQUESTS, ""));
            drawPartyControls(player, inventory, party, actions);
        }

        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    /** Displays navigation immediately while SQL and Redis are being read. */
    public static Inventory loading(Player player, View view) {
        Inventory inventory = build(player, view, List.of(), List.of(), 0, 0, 0, null);
        Holder holder = (Holder) inventory.getHolder();
        holder.actions.keySet().removeIf(slot -> slot != FRIENDS_TAB_SLOT && slot != PARTY_TAB_SLOT && slot != GUILDS_TAB_SLOT);
        inventory.setItem(REQUESTS_SLOT, null);
        inventory.setItem(22, new ItemBuilder(Material.CLOCK).name(LangHelper.get(player, "guild-ui.loading")).build());
        return inventory;
    }

    private static void drawFriends(Player player, Inventory inventory, List<FriendEntry> friends,
                                    Map<Integer, Action> actions, Map<Integer, Action> rightClickActions) {
        for (int index = 0; index < Math.min(friends.size(), ENTRY_SLOTS.length); index++) {
            FriendEntry friend = friends.get(index);
            int slot = ENTRY_SLOTS[index];
            inventory.setItem(slot, ItemBuilder.playerProfileIcon(player, friend.profile(),
                            friend.online() ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(LangHelper.get(player, friend.online()
                            ? "social.friend-list-online" : "social.friend-list-offline", friend.username()))
                    .lore(friend.online()
                            ? List.of(LangHelper.get(player, "social.menu-friend-left-click"),
                                    LangHelper.get(player, "social.menu-friend-right-click"))
                            : List.of(LangHelper.get(player, "social.menu-friend-offline")))
                    .build());
            if (friend.online()) {
                actions.put(slot, new Action(ActionType.FRIEND_JOIN, friend.username()));
                rightClickActions.put(slot, new Action(ActionType.PARTY_INVITE, friend.username()));
            }
        }
    }

    private static void drawParty(Player player, Inventory inventory, List<PartyEntry> members,
                                  PartySnapshot party, Map<Integer, Action> actions,
                                  Map<Integer, Action> rightClickActions) {
        if (party == null) {
            inventory.setItem(22, new ItemBuilder(Material.PAPER)
                    .name(LangHelper.get(player, "social.menu-party-none")).build());
            return;
        }
        boolean viewerIsLeader = party.isLeader(player.getUniqueId());
        for (int index = 0; index < Math.min(members.size(), ENTRY_SLOTS.length); index++) {
            PartyEntry member = members.get(index);
            int slot = ENTRY_SLOTS[index];
            boolean self = member.playerId().equals(player.getUniqueId());
            List<String> lore;
            if (viewerIsLeader && !self) {
                lore = member.online()
                        ? List.of(LangHelper.get(player, "social.menu-party-member-left-click"),
                                LangHelper.get(player, "social.menu-party-member-right-click"))
                        : List.of(LangHelper.get(player, "social.menu-party-member-offline"),
                                LangHelper.get(player, "social.menu-party-member-right-click"));
                if (member.online()) {
                    actions.put(slot, new Action(ActionType.PARTY_WARP_MEMBER, member.username()));
                }
                rightClickActions.put(slot, new Action(ActionType.PARTY_KICK, member.username()));
            } else {
                lore = List.of(LangHelper.get(player, self
                        ? "social.menu-party-member-self" : "social.menu-party-member-leader-only"));
            }
            Material bedrockIcon = member.leader() ? Material.GOLDEN_HELMET
                    : member.online() ? Material.LIGHT_BLUE_DYE : Material.GRAY_DYE;
            inventory.setItem(slot, ItemBuilder.playerProfileIcon(player, member.profile(), bedrockIcon)
                    .name(LangHelper.get(player, member.leader()
                            ? "social.menu-party-member-leader" : "social.menu-party-member", member.username()))
                    .lore(lore).build());
        }
    }

    private static void drawPartyControls(Player player, Inventory inventory, PartySnapshot party,
                                          Map<Integer, Action> actions) {
        if (party == null) return;
        boolean follow = party.members().stream()
                .filter(member -> member.playerId().equals(player.getUniqueId()))
                .findFirst().map(member -> member.followEnabled()).orElse(false);
        inventory.setItem(51, new ItemBuilder(follow ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(LangHelper.get(player, follow
                        ? "social.menu-follow-on" : "social.menu-follow-off")).build());
        actions.put(51, new Action(ActionType.FOLLOW_TOGGLE, follow ? "off" : "on"));
        if (party.isLeader(player.getUniqueId())) {
            inventory.setItem(52, new ItemBuilder(Material.ENDER_PEARL)
                    .name(LangHelper.get(player, "social.menu-warp")).build());
            actions.put(52, new Action(ActionType.PARTY_WARP, ""));
        }
    }

    static ItemStack navigationItem(Player player, String headId, String nameKey, boolean active) {
        ItemBuilder item = new ItemBuilder(headDatabaseIcon(headId)).name(LangHelper.get(player, nameKey));
        item.lore(LangHelper.get(player, headId.equals(FRIENDS_HEAD_ID)
                ? "social.menu-friends-action" : "social.menu-party-action"),
                LangHelper.get(player, active ? "guild-ui.active" : "guild-ui.available"));
        if (active) item.glow(player);
        return item.build();
    }

    private static ItemStack headDatabaseIcon(String headId) {
        try {
            if (Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) {
                HeadDatabaseAPI api = core.getHeadDatabaseManager().getHeadDatabaseAPI();
                ItemStack icon = api == null ? null : api.getItemHead(headId);
                if (icon != null) return icon;
            }
        } catch (RuntimeException _) {
            // The navigation remains usable when HeadDatabase is temporarily unavailable.
        }
        return new ItemStack(Material.PLAYER_HEAD);
    }

    public enum View { FRIENDS, PARTY }

    public record FriendEntry(UUID playerId, String username, boolean online, ResolvableProfile profile) {
        public FriendEntry {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(profile, "profile");
        }
    }

    public record PartyEntry(UUID playerId, String username, boolean online,
                             boolean leader, ResolvableProfile profile) {
        public PartyEntry {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(profile, "profile");
        }
    }

    public record Action(ActionType type, String argument) { }

    public enum ActionType {
        OPEN_FRIENDS, OPEN_PARTY, OPEN_GUILDS, FRIEND_JOIN, PARTY_INVITE, OPEN_FRIEND_REQUESTS,
        OPEN_PARTY_REQUESTS, FOLLOW_TOGGLE, PARTY_WARP, PARTY_WARP_MEMBER, PARTY_KICK
    }

    static Action actionForClick(Action primary, Action rightClick, ClickType click) {
        if (!click.isLeftClick() && !click.isRightClick()) return null;
        return click.isRightClick() && rightClick != null ? rightClick : primary;
    }

    public static final class Holder implements InventoryHolder {
        private final View view;
        private final Map<Integer, Action> actions;
        private final Map<Integer, Action> rightClickActions;
        private Inventory inventory;

        private Holder(View view, Map<Integer, Action> actions, Map<Integer, Action> rightClickActions) {
            this.view = view;
            this.actions = actions;
            this.rightClickActions = rightClickActions;
        }

        public View view() { return view; }
        public Action action(int slot, ClickType click) {
            if ((slot == FRIENDS_TAB_SLOT || slot == PARTY_TAB_SLOT || slot == GUILDS_TAB_SLOT) && click != ClickType.LEFT) return null;
            return actionForClick(actions.get(slot), rightClickActions.get(slot), click);
        }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
