package fr.tropicube.lobby.gui;

import fr.tropicube.core.guild.GuildService;
import fr.tropicube.core.guild.GuildPermissions;
import java.util.List;
import java.util.UUID;

/** Immutable navigation state and pure guild action visibility. */
public record GuildScreen(View view, int page, UUID member, Action confirmation) {
    /** Guild subpages retained while a language change reloads the inventory. */
    public enum View { HOME, MEMBERS, MEMBER, INVITATIONS, CHALLENGES, RANKING, CONFIRM }
    /** Safe action identifiers, never rendered directly to players. */
    public enum Kind { HOME, BACK, FRIENDS, PARTY, MEMBERS, MEMBER, INVITATIONS, CHALLENGES, RANKING,
        CREATE, INVITE, ACCEPT, LEAVE, KICK, PROMOTE, DEMOTE, TRANSFER, CONFIRM, REFRESH, PREVIOUS, NEXT, CLOSE }
    /** Target identity is captured from trusted server data, not client item metadata. */
    public record Action(Kind kind, UUID player, String tag, long guildId) {
        public Action(Kind kind) { this(kind, null, "", 0); }
    }

    public GuildScreen {
        page = Math.max(0, page);
    }

    /** Validates the configured input deadline without silently truncating fractional or string values. */
    public static int inputTimeout(Object configured) {
        if (configured == null) return 120;
        if (!(configured instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 10 || number.intValue() > 600)
            throw new IllegalArgumentException("guilds.input-timeout-seconds=" + configured + "; expected integer 10..600");
        return number.intValue();
    }

    public static GuildScreen home() { return new GuildScreen(View.HOME, 0, null, null); }

    public static int pageCount(int size) { return Math.max(1, (size + 20) / 21); }

    /** Never offers self-targeting or a role transition already in effect. */
    public static List<Kind> memberActions(GuildService.Role actor, GuildService.Role target, boolean self) {
        if (self) return List.of();
        var actions = new java.util.ArrayList<Kind>();
        if (GuildPermissions.canKick(actor, target)) actions.add(Kind.KICK);
        if (GuildPermissions.canManage(actor, target)) {
            actions.add(target == GuildService.Role.MEMBER ? Kind.PROMOTE : Kind.DEMOTE);
            actions.add(Kind.TRANSFER);
        }
        return List.copyOf(actions);
    }
}
