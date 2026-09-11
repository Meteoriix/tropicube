package fr.tropicube.lobby.gui;

import fr.tropicube.core.guild.GuildPermissions;
import fr.tropicube.core.guild.GuildPresentation;
import fr.tropicube.core.guild.GuildService;
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

import static fr.tropicube.lobby.gui.GuildScreen.Kind.*;

/** Guild inventory rendering. All methods run on Paper; snapshots contain only resolved data. */
public final class GuildGUI {
    private static final int[] ENTRIES = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private GuildGUI() { }

    /** Results loaded off-thread and consumed exclusively by the Paper renderer. */
    public record Snapshot(GuildService.Guild guild, List<GuildService.Invitation> invitations,
                           List<GuildService.Ranking> ranking, Map<UUID, ResolvableProfile> profiles,
                           int capacity, long contributionCap) { }

    /** The holder owns actions; client item metadata never grants permissions. */
    public static final class Holder implements InventoryHolder {
        private Inventory inventory;
        final GuildScreen screen;
        final Map<Integer, GuildScreen.Action> actions = new HashMap<>();
        final long guildId;
        Holder(GuildScreen screen, long guildId) { this.screen = screen; this.guildId = guildId; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public static Holder loading(Player player, GuildScreen screen) {
        Holder holder = frame(player, screen, 0);
        item(player, holder, 22, Material.CLOCK, "guild-ui.loading", null);
        return holder;
    }

    public static void error(Player player, Holder holder) {
        item(player, holder, 22, Material.RED_DYE, "guild-ui.error", new GuildScreen.Action(REFRESH));
    }

    public static Holder build(Player player, GuildScreen requested, Snapshot data) {
        int count = switch (requested.view()) {
            case MEMBERS -> data.guild() == null ? 0 : data.guild().members().size();
            case INVITATIONS -> data.invitations().size();
            case RANKING -> data.ranking().size();
            default -> 0;
        };
        GuildScreen screen = new GuildScreen(requested.view(), Math.min(requested.page(), GuildScreen.pageCount(count) - 1),
                requested.member(), requested.confirmation());
        Holder holder = frame(player, screen, data.guild() == null ? 0 : data.guild().id());
        GuildService.Guild guild = data.guild();
        GuildService.Member viewer = guild == null ? null : guild.members().stream()
                .filter(member -> member.playerId().equals(player.getUniqueId())).findFirst().orElse(null);
        switch (screen.view()) {
            case HOME -> {
                if (guild == null) {
                    item(player, holder, 13, Material.PAPER, "guild-ui.none", null);
                    item(player, holder, 21, Material.ANVIL, "guild-ui.create", new GuildScreen.Action(CREATE));
                    item(player, holder, 23, Material.WRITABLE_BOOK, "guild-ui.invitations", new GuildScreen.Action(INVITATIONS));
                } else {
                    item(player, holder, 13, Material.SHIELD, "guild-ui.info", null,
                            guild.tag(), guild.name(), guild.level(), guild.experience(), guild.members().size(), data.capacity());
                    if (viewer != null) item(player, holder, 31, Material.EXPERIENCE_BOTTLE, "guild-ui.contribution", null,
                            LangHelper.get(player, GuildPresentation.roleKey(viewer.role())), viewer.weeklyContribution(), data.contributionCap());
                    item(player, holder, 20, Material.BOOK, "guild-ui.members", new GuildScreen.Action(MEMBERS));
                    item(player, holder, 22, Material.WRITABLE_BOOK, "guild-ui.challenges", new GuildScreen.Action(CHALLENGES));
                    if (viewer != null && GuildPermissions.canInvite(viewer.role()))
                        item(player, holder, 24, Material.EMERALD, "guild-ui.invite", new GuildScreen.Action(INVITE));
                    else item(player, holder, 24, Material.GRAY_DYE, "guild-ui.invite-locked", null);
                    boolean mustTransfer = viewer != null && viewer.role() == GuildService.Role.OWNER && guild.members().size() > 1;
                    item(player, holder, 40, mustTransfer ? Material.GRAY_DYE : Material.OAK_DOOR,
                            mustTransfer ? "guild-ui.leave-locked" : guild.members().size() == 1 && viewer != null
                                    && viewer.role() == GuildService.Role.OWNER ? "guild-ui.delete" : "guild-ui.leave",
                            mustTransfer ? null : new GuildScreen.Action(LEAVE, null, "", guild.id()));
                }
                item(player, holder, 39, Material.GOLD_INGOT, "guild-ui.ranking", new GuildScreen.Action(RANKING));
            }
            case MEMBERS -> {
                if (guild != null) for (int i = screen.page() * 21; i < Math.min(guild.members().size(), (screen.page() + 1) * 21); i++) {
                    var member = guild.members().get(i);
                    int slot = ENTRIES[i % 21];
                    holder.inventory.setItem(slot, ItemBuilder.playerProfileIcon(player, data.profiles().get(member.playerId()),
                                    member.role() == GuildService.Role.OWNER ? Material.GOLDEN_HELMET : Material.LIGHT_BLUE_DYE)
                            .name(LangHelper.get(player, "guild-ui.member-name", member.username())).lore(LangHelper.get(player, "guild-ui.member-details",
                                    LangHelper.get(player, GuildPresentation.roleKey(member.role())), member.weeklyContribution()),
                                    LangHelper.get(player, "guild-ui.member-action")).build());
                    holder.actions.put(slot, new GuildScreen.Action(MEMBER, member.playerId(), "", guild.id()));
                }
                if (guild == null) item(player, holder, 22, Material.PAPER, "guild-ui.none", null);
            }
            case MEMBER -> {
                var target = guild == null ? null : guild.members().stream()
                        .filter(member -> member.playerId().equals(screen.member())).findFirst().orElse(null);
                if (target == null || viewer == null) item(player, holder, 22, Material.PAPER, "guild-ui.unavailable", null);
                else {
                    holder.inventory.setItem(13, ItemBuilder.playerProfileIcon(player, data.profiles().get(target.playerId()),
                                    Material.LIGHT_BLUE_DYE).name(LangHelper.get(player, "guild-ui.member-name", target.username()))
                            .lore(LangHelper.get(player, "guild-ui.member-details",
                                    LangHelper.get(player, GuildPresentation.roleKey(target.role())), target.weeklyContribution())).build());
                    int slot = 20;
                    for (var kind : GuildScreen.memberActions(viewer.role(), target.role(), target.playerId().equals(player.getUniqueId()))) {
                        item(player, holder, slot, material(kind), actionKey(kind),
                                new GuildScreen.Action(kind, target.playerId(), target.username(), guild.id()));
                        slot += 2;
                    }
                }
            }
            case INVITATIONS -> {
                for (int i = screen.page() * 21; i < Math.min(data.invitations().size(), (screen.page() + 1) * 21); i++) {
                    var invitation = data.invitations().get(i);
                    item(player, holder, ENTRIES[i % 21], Material.SHIELD, "guild-ui.invitation",
                            guild == null ? new GuildScreen.Action(ACCEPT, null, invitation.tag(), invitation.guildId()) : null,
                            invitation.tag(), invitation.name());
                }
                if (data.invitations().isEmpty()) item(player, holder, 22, Material.PAPER, "guild-ui.empty-invitations", null);
            }
            case RANKING -> {
                for (int i = 0; i < data.ranking().size(); i++) {
                    var value = data.ranking().get(i);
                    item(player, holder, ENTRIES[i], Material.SHIELD, "guild.ranking-entry", null,
                            i + 1, value.tag(), value.name(), Math.round(value.score()), value.rankedMatches());
                }
                if (data.ranking().isEmpty()) item(player, holder, 22, Material.PAPER, "guild-ui.empty-ranking", null);
            }
            case CHALLENGES -> {
                if (guild == null) item(player, holder, 22, Material.PAPER, "guild-ui.none", null);
                else for (int i = 0; i < Math.min(21, guild.challenges().size()); i++) {
                    var challenge = guild.challenges().get(i);
                    item(player, holder, ENTRIES[i], Material.WRITABLE_BOOK, "guild-ui.challenge", null,
                            LangHelper.get(player, GuildPresentation.challengeKey(challenge.id())), challenge.progress(), challenge.target(),
                            LangHelper.get(player, challenge.completed() ? "guild-ui.completed" : "guild-ui.in-progress"));
                }
            }
            case CONFIRM -> {
                var action = screen.confirmation();
                boolean valid = action != null && guild != null && guild.id() == action.guildId();
                item(player, holder, 13, Material.PAPER, "guild-ui.confirm-details", null,
                        action == null || action.player() == null ? player.getName() : action.tag());
                if (valid) holder.inventory.setItem(15, new ItemBuilder(material(action.kind()))
                        .name(LangHelper.get(player, actionKey(action.kind())).split("\n")[0]).build());
                if (valid) item(player, holder, 21, Material.LIME_DYE, "guild-ui.confirm", new GuildScreen.Action(CONFIRM));
                else item(player, holder, 21, Material.GRAY_DYE, "guild-ui.unavailable", null);
                item(player, holder, 23, Material.RED_DYE, "guild-ui.cancel", new GuildScreen.Action(HOME));
                if (valid && action.kind() == LEAVE && viewer != null && viewer.role() == GuildService.Role.OWNER)
                    item(player, holder, 31, Material.TNT, "guild-ui.delete-warning", null);
            }
        }
        if (count > 21) {
            item(player, holder, 47, Material.PAPER, "guild-ui.page", null, screen.page() + 1);
            if (screen.page() > 0) item(player, holder, 48, Material.ARROW, "guild-ui.previous", new GuildScreen.Action(PREVIOUS));
            if (screen.page() + 1 < GuildScreen.pageCount(count)) item(player, holder, 50, Material.ARROW, "guild-ui.next", new GuildScreen.Action(NEXT));
        }
        return holder;
    }

    private static Holder frame(Player player, GuildScreen screen, long guildId) {
        Holder holder = new Holder(screen, guildId);
        holder.inventory = Bukkit.createInventory(holder, LangHelper.menuSize("social"), LangHelper.component(player, titleKey(screen.view())));
        NetworkMenuStyle.applyFrame(holder.inventory, player, LangHelper.menuFrame("social"));
        holder.inventory.setItem(2, SocialGUI.navigationItem(player, SocialGUI.FRIENDS_HEAD_ID, "social.menu-friends", false));
        holder.inventory.setItem(4, SocialGUI.navigationItem(player, SocialGUI.PARTY_HEAD_ID, "social.menu-party", false));
        holder.actions.put(2, new GuildScreen.Action(FRIENDS));
        holder.actions.put(4, new GuildScreen.Action(PARTY));
        holder.inventory.setItem(6, new ItemBuilder(Material.SHIELD).name(LangHelper.get(player, "guild-ui.tab"))
                .lore(LangHelper.get(player, "guild-ui.tab-action"), LangHelper.get(player, "guild-ui.active")).glow(player).build());
        holder.actions.put(6, new GuildScreen.Action(HOME));
        item(player, holder, 45, Material.ARROW, screen.view() == GuildScreen.View.HOME ? "guild-ui.back-social"
                : screen.view() == GuildScreen.View.MEMBER ? "guild-ui.back-members" : "guild-ui.back", new GuildScreen.Action(BACK));
        item(player, holder, 49, Material.SUNFLOWER, "guild-ui.refresh", new GuildScreen.Action(REFRESH));
        holder.inventory.setItem(53, ItemBuilder.closeButton(player));
        holder.actions.put(53, new GuildScreen.Action(CLOSE));
        return holder;
    }

    private static void item(Player player, Holder holder, int slot, Material material, String key,
                             GuildScreen.Action action, Object... values) {
        String text = LangHelper.get(player, key, values);
        String[] lines = text.split("\n");
        var builder = new ItemBuilder(material).name(lines[0]);
        if (lines.length > 1) builder.lore(java.util.Arrays.copyOfRange(lines, 1, lines.length));
        holder.inventory.setItem(slot, builder.build());
        if (action != null) holder.actions.put(slot, action);
    }

    private static String titleKey(GuildScreen.View view) {
        return switch (view) {
            case HOME -> "guild-ui.title";
            case MEMBERS -> "guild-ui.title-members";
            case MEMBER -> "guild-ui.title-member";
            case INVITATIONS -> "guild-ui.title-invitations";
            case RANKING -> "guild-ui.title-ranking";
            case CHALLENGES -> "guild-ui.title-challenges";
            case CONFIRM -> "guild-ui.title-confirm";
        };
    }

    static String actionKey(GuildScreen.Kind kind) {
        return switch (kind) {
            case KICK -> "guild-ui.kick";
            case PROMOTE -> "guild-ui.promote";
            case DEMOTE -> "guild-ui.demote";
            case TRANSFER -> "guild-ui.transfer";
            case LEAVE -> "guild-ui.leave";
            default -> throw new IllegalArgumentException("Not a member action: " + kind);
        };
    }

    private static Material material(GuildScreen.Kind kind) {
        return switch (kind) {
            case KICK -> Material.IRON_DOOR;
            case PROMOTE, TRANSFER -> Material.GOLDEN_HELMET;
            case DEMOTE -> Material.IRON_HELMET;
            default -> Material.PAPER;
        };
    }
}
