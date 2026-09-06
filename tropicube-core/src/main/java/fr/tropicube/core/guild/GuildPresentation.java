package fr.tropicube.core.guild;

/** Stable translation contracts shared by guild commands and inventories. */
public final class GuildPresentation {
    private GuildPresentation() { }

    /** Every service outcome has a translation independent of its enum spelling. */
    public static String resultKey(GuildService.Result result) {
        return switch (result) {
            case SUCCESS -> "guild.result-success";
            case NOT_MEMBER -> "guild.result-not-in-guild";
            case NOT_ALLOWED -> "guild.result-not-authorized";
            case ALREADY_MEMBER -> "guild.result-already-member";
            case FULL -> "guild.result-member-limit";
            case INVALID -> "guild.result-invalid";
            case NOT_FOUND -> "guild.result-not-found";
            case LIMIT_REACHED -> "guild.result-role-limit";
        };
    }

    /** Localizes roles without exposing storage identifiers. */
    public static String roleKey(GuildService.Role role) {
        return switch (role) {
            case OWNER -> "guild.role-owner";
            case OFFICER -> "guild.role-officer";
            case MEMBER -> "guild.role-member";
        };
    }

    /** Unknown historic challenges remain readable without revealing their identifiers. */
    public static String challengeKey(String id) {
        return switch (id) {
            case "CONTRIBUTION" -> "guild.challenge-contribution";
            case "RANKED_MATCHES" -> "guild.challenge-ranked-matches";
            default -> "guild.challenge-unknown";
        };
    }
}
