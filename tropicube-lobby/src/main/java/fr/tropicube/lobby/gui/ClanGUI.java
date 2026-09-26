package fr.tropicube.lobby.gui;

import fr.tropicube.core.clan.ClanPermissions;
import fr.tropicube.core.clan.ClanPresentation;
import fr.tropicube.core.clan.ClanService;
import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.lobby.utils.ItemBuilder;
import fr.tropicube.lobby.utils.LangHelper;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.tropicube.lobby.gui.ClanScreen.Kind.*;

/** Clan inventory rendering. All methods run on Paper; snapshots contain only resolved data. */
public final class ClanGUI {
    private static final int[] ENTRIES = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private ClanGUI() { }

    /** Results loaded off-thread and consumed exclusively by the Paper renderer. */
    public record Snapshot(ClanService.Clan clan, List<ClanService.Invitation> invitations,
                           List<ClanService.Ranking> ranking, Map<UUID, ResolvableProfile> profiles,
                           int capacity, long contributionCap) { }

    /** The holder owns actions; client item metadata never grants permissions. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        final ClanScreen screen;
        final Map<Integer, ClanScreen.Action> actions = new HashMap<>();
        final long clanId;
        Holder(ClanScreen screen, long clanId) { this.screen = screen; this.clanId = clanId; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public static Holder loading(Player player, ClanScreen screen) {
        Holder holder = frame(player, screen, 0);
        item(player, holder, 22, Material.CLOCK, "clan-ui.loading", null);
        return holder;
    }

    public static void error(Player player, Holder holder) {
        item(player, holder, 22, Material.RED_DYE, "clan-ui.error", new ClanScreen.Action(RETRY));
    }

    public static Holder build(Player player, ClanScreen requested, Snapshot data) {
        int count = switch (requested.view()) {
            case MEMBERS -> data.clan() == null ? 0 : data.clan().members().size();
            case INVITATIONS -> data.invitations().size();
            case RANKING -> data.ranking().size();
            default -> 0;
        };
        ClanScreen screen = new ClanScreen(requested.view(), Math.min(requested.page(), ClanScreen.pageCount(count) - 1),
                requested.member(), requested.confirmation());
        Holder holder = frame(player, screen, data.clan() == null ? 0 : data.clan().id());
        ClanService.Clan clan = data.clan();
        ClanService.Member viewer = clan == null ? null : clan.members().stream()
                .filter(member -> member.playerId().equals(player.getUniqueId())).findFirst().orElse(null);
        switch (screen.view()) {
            case HOME -> {
                if (clan == null) {
                    item(player, holder, 13, Material.PAPER, "clan-ui.none", null);
                    item(player, holder, 21, Material.ANVIL, "clan-ui.create", new ClanScreen.Action(CREATE));
                    item(player, holder, 23, Material.WRITABLE_BOOK, "clan-ui.invitations", new ClanScreen.Action(INVITATIONS));
                } else {
                    item(player, holder, 13, Material.SHIELD, "clan-ui.info", null,
                            clan.tag(), clan.name(), clan.level(), clan.experience(), clan.members().size(), data.capacity());
                    if (viewer != null) item(player, holder, 31, Material.EXPERIENCE_BOTTLE, "clan-ui.contribution", null,
                            LangHelper.get(player, ClanPresentation.roleKey(viewer.role())), viewer.weeklyContribution(), data.contributionCap());
                    item(player, holder, 20, Material.BOOK, "clan-ui.members", new ClanScreen.Action(MEMBERS));
                    item(player, holder, 22, Material.WRITABLE_BOOK, "clan-ui.challenges", new ClanScreen.Action(CHALLENGES));
                    if (viewer != null && ClanPermissions.canInvite(viewer.role()))
                        item(player, holder, 24, Material.EMERALD, "clan-ui.invite", new ClanScreen.Action(INVITE));
                    else item(player, holder, 24, Material.GRAY_DYE, "clan-ui.invite-locked", null);
                    boolean mustTransfer = viewer != null && viewer.role() == ClanService.Role.OWNER && clan.members().size() > 1;
                    item(player, holder, 40, mustTransfer ? Material.GRAY_DYE : Material.OAK_DOOR,
                            mustTransfer ? "clan-ui.leave-locked" : clan.members().size() == 1 && viewer != null
                                    && viewer.role() == ClanService.Role.OWNER ? "clan-ui.delete" : "clan-ui.leave",
                            mustTransfer ? null : new ClanScreen.Action(LEAVE, null, "", clan.id()));
                }
                item(player, holder, 39, Material.GOLD_INGOT, "clan-ui.ranking", new ClanScreen.Action(RANKING));
            }
            case MEMBERS -> {
                if (clan != null) for (int i = screen.page() * 21; i < Math.min(clan.members().size(), (screen.page() + 1) * 21); i++) {
                    var member = clan.members().get(i);
                    int slot = ENTRIES[i % 21];
                    holder.inventory.setItem(slot, ItemBuilder.playerProfileIcon(player, data.profiles().get(member.playerId()),
                                    member.role() == ClanService.Role.OWNER ? Material.GOLDEN_HELMET : Material.LIGHT_BLUE_DYE)
                            .name(LangHelper.get(player, "clan-ui.member-name", member.username())).lore(LangHelper.get(player, "clan-ui.member-details",
                                    LangHelper.get(player, ClanPresentation.roleKey(member.role())), member.weeklyContribution()),
                                    LangHelper.get(player, "clan-ui.member-action")).build());
                    holder.actions.put(slot, new ClanScreen.Action(MEMBER, member.playerId(), "", clan.id()));
                }
                if (clan == null) item(player, holder, 22, Material.PAPER, "clan-ui.none", null);
            }
            case MEMBER -> {
                var target = clan == null ? null : clan.members().stream()
                        .filter(member -> member.playerId().equals(screen.member())).findFirst().orElse(null);
                if (target == null || viewer == null) item(player, holder, 22, Material.PAPER, "clan-ui.unavailable", null);
                else {
                    holder.inventory.setItem(13, ItemBuilder.playerProfileIcon(player, data.profiles().get(target.playerId()),
                                    Material.LIGHT_BLUE_DYE).name(LangHelper.get(player, "clan-ui.member-name", target.username()))
                            .lore(LangHelper.get(player, "clan-ui.member-details",
                                    LangHelper.get(player, ClanPresentation.roleKey(target.role())), target.weeklyContribution())).build());
                    int slot = 20;
                    for (var kind : ClanScreen.memberActions(viewer.role(), target.role(), target.playerId().equals(player.getUniqueId()))) {
                        item(player, holder, slot, material(kind), actionKey(kind),
                                new ClanScreen.Action(kind, target.playerId(), target.username(), clan.id()));
                        slot += 2;
                    }
                }
            }
            case INVITATIONS -> {
                for (int i = screen.page() * 21; i < Math.min(data.invitations().size(), (screen.page() + 1) * 21); i++) {
                    var invitation = data.invitations().get(i);
                    item(player, holder, ENTRIES[i % 21], Material.SHIELD, "clan-ui.invitation",
                            clan == null ? new ClanScreen.Action(ACCEPT, null, invitation.tag(), invitation.clanId()) : null,
                            invitation.tag(), invitation.name());
                }
                if (data.invitations().isEmpty()) item(player, holder, 22, Material.PAPER, "clan-ui.empty-invitations", null);
            }
            case RANKING -> {
                for (int i = 0; i < data.ranking().size(); i++) {
                    var value = data.ranking().get(i);
                    item(player, holder, ENTRIES[i], Material.SHIELD, "clan.ranking-entry", null,
                            i + 1, value.tag(), value.name(), Math.round(value.score()), value.rankedMatches());
                }
                if (data.ranking().isEmpty()) item(player, holder, 22, Material.PAPER, "clan-ui.empty-ranking", null);
            }
            case CHALLENGES -> {
                if (clan == null) item(player, holder, 22, Material.PAPER, "clan-ui.none", null);
                else for (int i = 0; i < Math.min(21, clan.challenges().size()); i++) {
                    var challenge = clan.challenges().get(i);
                    item(player, holder, ENTRIES[i], Material.WRITABLE_BOOK, "clan-ui.challenge", null,
                            LangHelper.get(player, ClanPresentation.challengeKey(challenge.id())), challenge.progress(), challenge.target(),
                            LangHelper.get(player, challenge.completed() ? "clan-ui.completed" : "clan-ui.in-progress"));
                }
            }
            case CONFIRM -> {
                var action = screen.confirmation();
                boolean valid = action != null && clan != null && clan.id() == action.clanId();
                item(player, holder, 13, Material.PAPER, "clan-ui.confirm-details", null,
                        action == null || action.player() == null ? player.getName() : action.tag());
                if (valid) holder.inventory.setItem(15, new ItemBuilder(material(action.kind()))
                        .name(LangHelper.get(player, actionKey(action.kind())).split("\n")[0]).build());
                if (valid) item(player, holder, 21, Material.LIME_DYE, "clan-ui.confirm", new ClanScreen.Action(CONFIRM));
                else item(player, holder, 21, Material.GRAY_DYE, "clan-ui.unavailable", null);
                item(player, holder, 23, Material.RED_DYE, "clan-ui.cancel", new ClanScreen.Action(HOME));
                if (valid && action.kind() == LEAVE && viewer != null && viewer.role() == ClanService.Role.OWNER)
                    item(player, holder, 31, Material.TNT, "clan-ui.delete-warning", null);
            }
        }
        if (count > 21) {
            item(player, holder, 47, Material.PAPER, "clan-ui.page", null, screen.page() + 1);
            if (screen.page() > 0) item(player, holder, 48, Material.ARROW, "clan-ui.previous", new ClanScreen.Action(PREVIOUS));
            if (screen.page() + 1 < ClanScreen.pageCount(count)) item(player, holder, 50, Material.ARROW, "clan-ui.next", new ClanScreen.Action(NEXT));
        }
        return holder;
    }

    private static Holder frame(Player player, ClanScreen screen, long clanId) {
        Holder holder = new Holder(screen, clanId);
        holder.inventory = Bukkit.createInventory(holder, LangHelper.menuSize("social"), LangHelper.component(player, titleKey(screen.view())));
        NetworkMenuStyle.applyFrame(holder.inventory, player, LangHelper.menuFrame("social"));
        holder.inventory.setItem(2, SocialGUI.navigationItem(player, SocialGUI.FRIENDS_HEAD_ID, "social.menu-friends", false));
        holder.inventory.setItem(4, SocialGUI.navigationItem(player, SocialGUI.PARTY_HEAD_ID, "social.menu-party", false));
        holder.actions.put(2, new ClanScreen.Action(FRIENDS));
        holder.actions.put(4, new ClanScreen.Action(PARTY));
        holder.inventory.setItem(6, new ItemBuilder(Material.SHIELD).name(LangHelper.get(player, "clan-ui.tab"))
                .lore(LangHelper.get(player, "clan-ui.tab-action"), LangHelper.get(player, "clan-ui.active")).glow(player).build());
        holder.actions.put(6, new ClanScreen.Action(HOME));
        item(player, holder, 45, Material.ARROW, screen.view() == ClanScreen.View.HOME ? "clan-ui.back-social"
                : screen.view() == ClanScreen.View.MEMBER ? "clan-ui.back-members" : "clan-ui.back", new ClanScreen.Action(BACK));
        holder.inventory.setItem(53, ItemBuilder.closeButton(player));
        holder.actions.put(53, new ClanScreen.Action(CLOSE));
        return holder;
    }

    private static void item(Player player, Holder holder, int slot, Material material, String key,
                             ClanScreen.Action action, Object... values) {
        String text = LangHelper.get(player, key, values);
        String[] lines = text.split("\n");
        var builder = new ItemBuilder(material).name(lines[0]);
        if (lines.length > 1) builder.lore(java.util.Arrays.copyOfRange(lines, 1, lines.length));
        holder.inventory.setItem(slot, builder.build());
        if (action != null) holder.actions.put(slot, action);
    }

    private static String titleKey(ClanScreen.View view) {
        return switch (view) {
            case HOME -> "clan-ui.title";
            case MEMBERS -> "clan-ui.title-members";
            case MEMBER -> "clan-ui.title-member";
            case INVITATIONS -> "clan-ui.title-invitations";
            case RANKING -> "clan-ui.title-ranking";
            case CHALLENGES -> "clan-ui.title-challenges";
            case CONFIRM -> "clan-ui.title-confirm";
        };
    }

    static String actionKey(ClanScreen.Kind kind) {
        return switch (kind) {
            case KICK -> "clan-ui.kick";
            case PROMOTE -> "clan-ui.promote";
            case DEMOTE -> "clan-ui.demote";
            case TRANSFER -> "clan-ui.transfer";
            case LEAVE -> "clan-ui.leave";
            default -> throw new IllegalArgumentException("Not a member action: " + kind);
        };
    }

    private static Material material(ClanScreen.Kind kind) {
        return switch (kind) {
            case KICK -> Material.IRON_DOOR;
            case PROMOTE, TRANSFER -> Material.GOLDEN_HELMET;
            case DEMOTE -> Material.IRON_HELMET;
            default -> Material.PAPER;
        };
    }
}
