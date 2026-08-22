package fr.tropicube.sheepwars.competitive;

/** Limits a party to half of one team in public queues. */
public final class PartyQueuePolicy {
    private PartyQueuePolicy() {}
    public static int maximumPartySize(SheepWarsMode mode) { return Math.max(1, mode.teamSize() / 2); }
    public static boolean accepts(SheepWarsMode mode, int partySize) {
        return partySize > 0 && partySize <= maximumPartySize(mode);
    }
}
