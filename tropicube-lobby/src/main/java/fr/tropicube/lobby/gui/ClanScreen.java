package fr.tropicube.lobby.gui;

import fr.tropicube.core.clan.ClanService;
import fr.tropicube.core.clan.ClanPermissions;
import java.util.List;
import java.util.UUID;

/** Immutable navigation state and pure clan action visibility. */
public record ClanScreen(View view, int page, UUID member, Action confirmation) {
    /** Clan subpages retained while a language change reloads the inventory. */
    public enum View { HOME, MEMBERS, MEMBER, INVITATIONS, CHALLENGES, RANKING, CONFIRM }
    /** Safe action identifiers, never rendered directly to players. */
    public enum Kind { HOME, BACK, FRIENDS, PARTY, MEMBERS, MEMBER, INVITATIONS, CHALLENGES, RANKING,
        CREATE, INVITE, ACCEPT, LEAVE, KICK, PROMOTE, DEMOTE, TRANSFER, CONFIRM, RETRY, PREVIOUS, NEXT, CLOSE }
    /** Target identity is captured from trusted server data, not client item metadata. */
    public record Action(Kind kind, UUID player, String tag, long clanId) {
        public Action(Kind kind) { this(kind, null, "", 0); }
    }

    public ClanScreen {
        page = Math.max(0, page);
    }

    /** Validates the configured input deadline without silently truncating fractional or string values. */
    public static int inputTimeout(Object configured) {
        if (configured == null) return 120;
        if (!(configured instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 10 || number.intValue() > 600)
            throw new IllegalArgumentException("clans.input-timeout-seconds=" + configured + "; expected integer 10..600");
        return number.intValue();
    }

    public static ClanScreen home() { return new ClanScreen(View.HOME, 0, null, null); }

    public static int pageCount(int size) { return Math.max(1, (size + 20) / 21); }

    /** Never offers self-targeting or a role transition already in effect. */
    public static List<Kind> memberActions(ClanService.Role actor, ClanService.Role target, boolean self) {
        if (self) return List.of();
        var actions = new java.util.ArrayList<Kind>();
        if (ClanPermissions.canKick(actor, target)) actions.add(Kind.KICK);
        if (ClanPermissions.canManage(actor, target)) {
            actions.add(target == ClanService.Role.MEMBER ? Kind.PROMOTE : Kind.DEMOTE);
            actions.add(Kind.TRANSFER);
        }
        return List.copyOf(actions);
    }
}
