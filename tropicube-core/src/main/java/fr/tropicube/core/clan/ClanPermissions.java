package fr.tropicube.core.clan;

/** Pure clan action policy, used by both presentation and persistence boundaries. */
public final class ClanPermissions {
    private ClanPermissions() { }

    /** Inviting requires an officer or owner. */
    public static boolean canInvite(ClanService.Role actor) {
        return actor == ClanService.Role.OWNER || actor == ClanService.Role.OFFICER;
    }

    /** A member can only be removed by a strictly higher role. */
    public static boolean canKick(ClanService.Role actor, ClanService.Role target) {
        return target != null && (actor == ClanService.Role.OWNER && target != ClanService.Role.OWNER
                || actor == ClanService.Role.OFFICER && target == ClanService.Role.MEMBER);
    }

    /** Only the owner can change another member's role or transfer ownership. */
    public static boolean canManage(ClanService.Role actor, ClanService.Role target) {
        return actor == ClanService.Role.OWNER && target != null && target != ClanService.Role.OWNER;
    }
}
