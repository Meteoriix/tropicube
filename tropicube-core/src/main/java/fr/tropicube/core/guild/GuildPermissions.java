package fr.tropicube.core.guild;

/** Pure guild action policy, used by both presentation and persistence boundaries. */
public final class GuildPermissions {
    private GuildPermissions() { }

    /** Inviting requires an officer or owner. */
    public static boolean canInvite(GuildService.Role actor) {
        return actor == GuildService.Role.OWNER || actor == GuildService.Role.OFFICER;
    }

    /** A member can only be removed by a strictly higher role. */
    public static boolean canKick(GuildService.Role actor, GuildService.Role target) {
        return target != null && (actor == GuildService.Role.OWNER && target != GuildService.Role.OWNER
                || actor == GuildService.Role.OFFICER && target == GuildService.Role.MEMBER);
    }

    /** Only the owner can change another member's role or transfer ownership. */
    public static boolean canManage(GuildService.Role actor, GuildService.Role target) {
        return actor == GuildService.Role.OWNER && target != null && target != GuildService.Role.OWNER;
    }
}
