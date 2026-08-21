package fr.tropicube.lobby.gui;

import fr.tropicube.core.social.FriendshipRepository;
import fr.tropicube.docker.model.PartyMember;
import fr.tropicube.docker.model.PartySnapshot;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Snapshot-based social menu. Its holder contains immutable actions instead of trusting item text. */
public final class SocialGUI {
    public static final int SIZE = 54;
    public static final int CLOSE_SLOT = 53;
    private static final int[] FRIEND_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int[] REQUEST_SLOTS = {37, 38, 39, 40, 41, 42, 43};

    private SocialGUI() { }

    public static Inventory build(Player player, List<FriendEntry> friends,
                                  List<FriendshipRepository.PendingRequest> requests,
                                  PartySnapshot party, Map<UUID, String> partyInvites,
                                  Map<UUID, String> names) {
        Map<Integer, Action> actions = new LinkedHashMap<>();
        Holder holder = new Holder(actions);
        Inventory inventory = Bukkit.createInventory(holder, SIZE,
                LangHelper.component(player, "social.menu-title"));
        holder.inventory = inventory;

        inventory.setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name(LangHelper.get(player, "social.menu-friends"))
                .lore("<gray>" + friends.size()).build());
        for (int index = 0; index < Math.min(friends.size(), FRIEND_SLOTS.length); index++) {
            FriendEntry friend = friends.get(index);
            int slot = FRIEND_SLOTS[index];
            inventory.setItem(slot, new ItemBuilder(Material.PLAYER_HEAD)
                    .skullProfile(friend.profile())
                    .name(LangHelper.get(player, friend.online() ? "social.friend-list-online" : "social.friend-list-offline", friend.username()))
                    .lore(friend.online() ? "<gray>/friend join " + friend.username() : "<dark_gray>Hors ligne")
                    .build());
            if (friend.online()) actions.put(slot, new Action(ActionType.FRIEND_JOIN, friend.username()));
        }

        inventory.setItem(36, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(LangHelper.get(player, "social.menu-requests"))
                .lore("<gray>" + requests.size()).build());
        for (int index = 0; index < Math.min(requests.size(), REQUEST_SLOTS.length); index++) {
            FriendshipRepository.PendingRequest request = requests.get(index);
            int slot = REQUEST_SLOTS[index];
            inventory.setItem(slot, new ItemBuilder(Material.PAPER).name("<yellow>" + request.username())
                    .lore("<gray>/friend accept " + request.username()).build());
            actions.put(slot, new Action(ActionType.FRIEND_ACCEPT, request.username()));
        }

        List<String> partyLore = new ArrayList<>();
        if (party != null) {
            for (PartyMember member : party.members()) {
                partyLore.add((member.playerId().equals(party.leaderId()) ? "<gold>★ " : "<gray>• ")
                        + names.getOrDefault(member.playerId(), member.playerId().toString().substring(0, 8))
                        + " <dark_gray>follow " + (member.followEnabled() ? "ON" : "OFF"));
            }
        } else partyLore.add(LangHelper.get(player, "social.menu-party-none"));
        inventory.setItem(45, new ItemBuilder(Material.TOTEM_OF_UNDYING)
                .name(LangHelper.get(player, "social.menu-party")).lore(partyLore).build());

        int inviteSlot = 46;
        for (UUID leaderId : partyInvites.keySet()) {
            if (inviteSlot > 49) break;
            String leaderName = names.getOrDefault(leaderId, leaderId.toString().substring(0, 8));
            inventory.setItem(inviteSlot, new ItemBuilder(Material.ENCHANTED_BOOK)
                    .name(LangHelper.get(player, "social.menu-invites"))
                    .lore("<yellow>" + leaderName, "<gray>/party accept " + leaderName).build());
            actions.put(inviteSlot++, new Action(ActionType.PARTY_ACCEPT, leaderName));
        }

        if (party != null) {
            boolean follow = party.members().stream().filter(member -> member.playerId().equals(player.getUniqueId()))
                    .findFirst().map(PartyMember::followEnabled).orElse(false);
            inventory.setItem(51, new ItemBuilder(follow ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(LangHelper.get(player, follow ? "social.menu-follow-on" : "social.menu-follow-off")).build());
            actions.put(51, new Action(ActionType.FOLLOW_TOGGLE, follow ? "off" : "on"));
            if (party.isLeader(player.getUniqueId())) {
                inventory.setItem(52, new ItemBuilder(Material.ENDER_PEARL)
                        .name(LangHelper.get(player, "social.menu-warp")).build());
                actions.put(52, new Action(ActionType.PARTY_WARP, ""));
            }
        }
        inventory.setItem(CLOSE_SLOT, ItemBuilder.closeButton(player));
        return inventory;
    }

    public record FriendEntry(UUID playerId, String username, boolean online, ResolvableProfile profile) {
        public FriendEntry {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(profile, "profile");
        }
    }
    public record Action(ActionType type, String argument) { }
    public enum ActionType { FRIEND_JOIN, FRIEND_ACCEPT, PARTY_ACCEPT, FOLLOW_TOGGLE, PARTY_WARP }

    public static final class Holder implements InventoryHolder {
        private final Map<Integer, Action> actions;
        private Inventory inventory;

        private Holder(Map<Integer, Action> actions) { this.actions = actions; }
        public Action action(int slot) { return actions.get(slot); }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }
}
