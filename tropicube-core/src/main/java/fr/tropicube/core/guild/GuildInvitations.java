package fr.tropicube.core.guild;

import fr.tropicube.core.network.NotificationService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared notification delivery for invitations issued through commands or menus. */
public final class GuildInvitations {
    private final GuildService guilds;
    private final NotificationService notifications;

    public GuildInvitations(GuildService guilds, NotificationService notifications) {
        this.guilds = guilds;
        this.notifications = notifications;
    }

    /** Resolves the inviter's persisted name without asynchronous Bukkit access. */
    public CompletableFuture<Void> notifyInvitation(UUID actor, UUID target) {
        return guilds.guild(actor).thenCompose(guild -> {
            if (guild == null) return CompletableFuture.completedFuture(null);
            String name = guild.members().stream().filter(member -> member.playerId().equals(actor))
                    .map(GuildService.Member::username).findFirst().orElse(guild.name());
            return notifications.create(target, "GUILD", "guild.invitation", List.of(name, guild.tag()),
                    new NotificationService.Action(NotificationService.ActionType.SUGGEST_COMMAND,
                            "/guild accept " + guild.tag())).thenApply(ignored -> null);
        });
    }
}
