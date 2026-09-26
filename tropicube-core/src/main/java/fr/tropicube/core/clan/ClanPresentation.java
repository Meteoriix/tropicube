package fr.tropicube.core.clan;

/** Stable translation contracts shared by clan commands and inventories. */
public final class ClanPresentation {
    private ClanPresentation() { }

    /** Every service outcome has a translation independent of its enum spelling. */
    public static String resultKey(ClanService.Result result) {
        return switch (result) {
            case SUCCESS -> "clan.result-success";
            case NOT_MEMBER -> "clan.result-not-in-clan";
            case NOT_ALLOWED -> "clan.result-not-authorized";
            case ALREADY_MEMBER -> "clan.result-already-member";
            case FULL -> "clan.result-member-limit";
            case INVALID -> "clan.result-invalid";
            case NOT_FOUND -> "clan.result-not-found";
            case LIMIT_REACHED -> "clan.result-role-limit";
        };
    }

    /** Localizes roles without exposing storage identifiers. */
    public static String roleKey(ClanService.Role role) {
        return switch (role) {
            case OWNER -> "clan.role-owner";
            case OFFICER -> "clan.role-officer";
            case MEMBER -> "clan.role-member";
        };
    }

    /** Unknown historic challenges remain readable without revealing their identifiers. */
    public static String challengeKey(String id) {
        return switch (id) {
            case "CONTRIBUTION" -> "clan.challenge-contribution";
            case "RANKED_MATCHES" -> "clan.challenge-ranked-matches";
            default -> "clan.challenge-unknown";
        };
    }
}
