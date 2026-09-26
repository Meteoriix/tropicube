package fr.tropicube.core.clan;

import fr.tropicube.core.network.NotificationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared notification delivery for invitations issued through commands or menus. */
public final class ClanInvitations {
    private final ClanService clans;
    private final NotificationService notifications;

    public ClanInvitations(ClanService clans, NotificationService notifications) {
        this.clans = clans;
        this.notifications = notifications;
    }

    /** Resolves the inviter's persisted name without asynchronous Bukkit access. */
    public CompletableFuture<Void> notifyInvitation(UUID actor, UUID target) {
        return clans.clan(actor).thenCompose(clan -> {
            if (clan == null) return CompletableFuture.completedFuture(null);
            String name = clan.members().stream().filter(member -> member.playerId().equals(actor))
                    .map(ClanService.Member::username).findFirst().orElse(clan.name());
            return notifications.create(target, "CLAN", "clan.invitation", List.of(name, clan.tag()),
                    new NotificationService.Action(NotificationService.ActionType.SUGGEST_COMMAND,
                            "/clan accept " + clan.tag())).thenApply(ignored -> null);
        });
    }
}
