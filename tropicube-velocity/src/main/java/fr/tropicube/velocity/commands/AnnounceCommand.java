package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import fr.tropicube.velocity.managers.AnnouncementManager;
import fr.tropicube.velocity.util.MessageStyle;

/** Broadcasts a configured localization key to a network target. */
public final class AnnounceCommand implements SimpleCommand {
    private final AnnouncementManager announcements;

    public AnnounceCommand(AnnouncementManager announcements) {
        this.announcements = announcements;
    }

    @Override public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length != 2) {
            invocation.source().sendMessage(MessageStyle.component("<tc><yellow>Usage: /announce <network|type|instance> <clé.langue>"));
            return;
        }
        int recipients = announcements.broadcastKey(args[0], args[1]);
        invocation.source().sendMessage(MessageStyle.component("<tc><green>Annonce envoyée à <white>" + recipients + "</white> joueur(s)."));
    }

    @Override public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tropicube.admin.announce");
    }
}
