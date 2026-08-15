package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.social.SocialService;
import fr.tropicube.docker.model.PartyMember;
import fr.tropicube.docker.model.PartySnapshot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Redis-backed network party command. Redis work is always performed away from the Paper thread. */
public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("invite", "accept", "deny", "list", "leave", "kick",
            "promote", "disband", "follow", "warp", "tp", "chat", "help");
    private final TropicubeCore plugin;
    private final SocialService social;

    public PartyCommand(TropicubeCore plugin) {
        this.plugin = plugin;
        this.social = plugin.getSocialService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getComponentForLang("fr", "general.player-only"));
            return true;
        }
        Actor actor = new Actor(player.getUniqueId(), player.getName());
        String[] arguments = args.clone();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try { execute(actor, label, arguments); }
            catch (RuntimeException exception) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Échec de la commande party pour " + actor.id(), exception);
                message(actor.id(), "general.operation-failed");
            }
        });
        return true;
    }

    private void execute(Actor actor, String label, String[] args) {
        if ("pc".equalsIgnoreCase(label)) {
            chat(actor, args);
            return;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            message(actor.id(), "social.party-help");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "invite" -> requirePlayerArgument(actor, args, this::invite);
            case "accept" -> requirePlayerArgument(actor, args, this::accept);
            case "deny" -> requirePlayerArgument(actor, args, this::deny);
            case "kick" -> requirePlayerArgument(actor, args, this::kick);
            case "promote" -> requirePlayerArgument(actor, args, this::promote);
            case "list" -> list(actor);
            case "leave" -> leave(actor);
            case "disband" -> disband(actor);
            case "follow" -> follow(actor, args);
            case "warp", "tp" -> warp(actor);
            case "chat" -> chat(actor, Arrays.copyOfRange(args, 1, args.length));
            default -> message(actor.id(), "social.party-help");
        }
    }

    private void requirePlayerArgument(Actor actor, String[] args, BiConsumer<Actor, String> action) {
        if (args.length != 2) {
            message(actor.id(), "social.party-help");
            return;
        }
        action.accept(actor, args[1]);
    }

    private void invite(Actor actor, String username) {
        UUID target = onlineUuid(username);
        if (target == null) { message(actor.id(), "social.player-not-online", username); return; }
        if (target.equals(actor.id())) { message(actor.id(), "social.self-target"); return; }
        String result = social.inviteParty(actor.id(), target);
        message(actor.id(), "OK".equals(result) ? "social.party-invite-sent" : partyError(result), username);
        if ("OK".equals(result)) social.notifyPlayer(target, "social.party-invite-received", actor.name());
    }

    private void accept(Actor actor, String username) {
        UUID leader = onlineUuid(username);
        if (leader == null) { message(actor.id(), "social.player-not-online", username); return; }
        String result = social.acceptParty(actor.id(), leader);
        if (isUuid(result)) {
            message(actor.id(), "social.party-joined", username);
            social.notifyPlayer(leader, "social.party-member-joined", actor.name());
        } else message(actor.id(), partyError(result), username);
    }

    private void deny(Actor actor, String username) {
        UUID leader = onlineUuid(username);
        if (leader == null) { message(actor.id(), "social.player-not-online", username); return; }
        social.denyParty(actor.id(), leader);
        message(actor.id(), "social.party-invite-denied", username);
    }

    private void list(Actor actor) {
        PartySnapshot party = social.party(actor.id());
        if (party == null) { message(actor.id(), "social.party-none"); return; }
        message(actor.id(), "social.party-list-header", party.members().size());
        for (PartyMember member : party.members()) {
            message(actor.id(), member.playerId().equals(party.leaderId()) ? "social.party-list-leader" : "social.party-list-member",
                    social.displayName(member.playerId()), member.followEnabled() ? "ON" : "OFF");
        }
    }

    private void leave(Actor actor) {
        String result = social.leaveParty(actor.id());
        message(actor.id(), "NOT_MEMBER".equals(result) ? "social.party-none" : "social.party-left");
    }

    private void kick(Actor actor, String username) {
        UUID target = partyMember(actor.id(), username);
        if (target == null) return;
        String result = social.kickParty(actor.id(), target);
        message(actor.id(), "OK".equals(result) ? "social.party-kicked" : partyError(result), username);
        if ("OK".equals(result)) social.notifyPlayer(target, "social.party-you-were-kicked", actor.name());
    }

    private void promote(Actor actor, String username) {
        UUID target = partyMember(actor.id(), username);
        if (target == null) return;
        boolean promoted = social.promoteParty(actor.id(), target);
        message(actor.id(), promoted ? "social.party-promoted" : "social.party-not-leader", username);
        if (promoted) social.notifyPlayer(target, "social.party-you-are-leader");
    }

    private void disband(Actor actor) {
        PartySnapshot party = social.party(actor.id());
        if (party == null) { message(actor.id(), "social.party-none"); return; }
        if (!party.isLeader(actor.id())) { message(actor.id(), "social.party-not-leader"); return; }
        party.followers().forEach(member -> social.notifyPlayer(member.playerId(), "social.party-disbanded"));
        social.disbandParty(actor.id());
        message(actor.id(), "social.party-disbanded");
    }

    private void follow(Actor actor, String[] args) {
        if (args.length != 2 || !("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))) {
            message(actor.id(), "social.party-follow-usage");
            return;
        }
        boolean enabled = "on".equalsIgnoreCase(args[1]);
        message(actor.id(), social.setFollow(actor.id(), enabled)
                ? (enabled ? "social.party-follow-on" : "social.party-follow-off") : "social.party-none");
    }

    private void warp(Actor actor) {
        PartySnapshot party = social.party(actor.id());
        if (party == null) { message(actor.id(), "social.party-none"); return; }
        if (!party.isLeader(actor.id())) { message(actor.id(), "social.party-not-leader"); return; }
        social.requestPartyWarp(actor.id());
        message(actor.id(), "social.party-warp-requested");
    }

    private void chat(Actor actor, String[] words) {
        if (words.length == 0) { message(actor.id(), "social.party-chat-usage"); return; }
        PartySnapshot party = social.party(actor.id());
        if (party == null) { message(actor.id(), "social.party-none"); return; }
        String content = String.join(" ", words);
        party.members().forEach(member -> social.notifyPlayer(member.playerId(), "social.party-chat", actor.name(), content));
    }

    private UUID partyMember(UUID actorId, String username) {
        PartySnapshot party = social.party(actorId);
        if (party == null) { message(actorId, "social.party-none"); return null; }
        return party.members().stream().map(PartyMember::playerId)
                .filter(id -> username.equalsIgnoreCase(social.displayName(id))).findFirst().orElseGet(() -> {
                    message(actorId, "social.party-member-missing", username);
                    return null;
                });
    }

    private UUID onlineUuid(String username) {
        String value = plugin.getRedisManager().get("player:uuid:" + username.toLowerCase(Locale.ROOT));
        try {
            UUID playerId = value == null ? null : UUID.fromString(value);
            return playerId != null && plugin.getRedisManager().exists("player:online:" + playerId) ? playerId : null;
        }
        catch (IllegalArgumentException exception) { return null; }
    }

    private static boolean isUuid(String value) {
        try { UUID.fromString(value); return true; }
        catch (IllegalArgumentException exception) { return false; }
    }

    private static String partyError(String result) {
        return switch (result) {
            case "NOT_LEADER" -> "social.party-not-leader";
            case "ALREADY_MEMBER" -> "social.party-already-member";
            case "FULL" -> "social.party-full";
            case "NO_INVITE", "EXPIRED" -> "social.party-invite-missing";
            case "NOT_MEMBER" -> "social.party-member-missing";
            default -> "general.operation-failed";
        };
    }

    private void message(UUID playerId, String key, Object... arguments) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) player.sendMessage(plugin.getLanguageManager().getComponent(playerId, key, arguments));
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return SUBCOMMANDS.stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && "follow".equalsIgnoreCase(args[0])) return List.of("on", "off");
        return List.of();
    }

    private record Actor(UUID id, String name) { }
}
