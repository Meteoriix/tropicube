package fr.tropicube.fallenkingdoms.game;

import java.util.*;

/** Balances public rosters while honoring as many preferred kingdoms as possible. */
public final class KingdomAllocator {
    public static int kingdomCount(int players) {
        if (players < 8 || players > 30) throw new IllegalArgumentException("Une partie publique accepte de 8 à 30 joueurs.");
        return Math.min(5, Math.max(2, (players + 5) / 6));
    }
    public static int kingdomCount(int players, int maximumPlayersPerKingdom, int maximumKingdoms) {
        return kingdomCount(players, 4, maximumPlayersPerKingdom, maximumKingdoms);
    }
    public static int kingdomCount(int players, int minimumPlayersPerKingdom,
                                   int maximumPlayersPerKingdom, int maximumKingdoms) {
        if (minimumPlayersPerKingdom < 1 || maximumPlayersPerKingdom < minimumPlayersPerKingdom
                || maximumPlayersPerKingdom > 6 || maximumKingdoms < 2 || maximumKingdoms > 5)
            throw new IllegalArgumentException("Les limites de royaumes doivent respecter le profil Fallen Kingdoms.");
        int kingdoms = Math.max(2, (players + maximumPlayersPerKingdom - 1) / maximumPlayersPerKingdom);
        if (players < kingdoms * minimumPlayersPerKingdom || kingdoms > maximumKingdoms)
            throw new IllegalArgumentException("L'effectif ne peut pas être réparti avec ces limites.");
        return kingdoms;
    }
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences) {
        List<PlayerPreference> players = preferences.stream().sorted(Comparator.comparing(p -> p.player().toString())).toList();
        int kingdoms = kingdomCount(players.size());
        return allocate(players, Arrays.asList(KingdomId.values()).subList(0, kingdoms));
    }
    /** Allocates against the exact kingdom layout declared by the selected map. */
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences, List<KingdomId> ids) {
        return allocate(preferences, ids, 4, 6);
    }
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences, List<KingdomId> ids,
                                         int minimumPlayersPerKingdom, int maximumPlayersPerKingdom) {
        List<PlayerPreference> players = preferences.stream().sorted(Comparator.comparing(p -> p.player().toString())).toList();
        int kingdoms = ids.size();
        if (kingdoms < 2 || kingdoms > KingdomId.values().length
                || minimumPlayersPerKingdom < 1 || maximumPlayersPerKingdom < minimumPlayersPerKingdom
                || maximumPlayersPerKingdom > 6 || players.size() < kingdoms * minimumPlayersPerKingdom
                || players.size() > kingdoms * maximumPlayersPerKingdom)
            throw new IllegalArgumentException("L'agencement ne respecte pas la capacité configurée des royaumes.");
        if (ids.size() != kingdoms || ids.stream().distinct().count() != kingdoms)
            throw new IllegalArgumentException("L'agencement doit contenir exactement " + kingdoms + " royaumes distincts.");
        int base = players.size() / kingdoms, extra = players.size() % kingdoms;
        Map<KingdomId, Integer> capacity = new EnumMap<>(KingdomId.class);
        for (int index = 0; index < kingdoms; index++) capacity.put(ids.get(index), base + (index < extra ? 1 : 0));
        Map<UUID, KingdomId> result = new LinkedHashMap<>();
        for (PlayerPreference player : players) {
            if (player.preferred() != null && capacity.getOrDefault(player.preferred(), 0) > 0) {
                result.put(player.player(), player.preferred()); capacity.merge(player.preferred(), -1, Integer::sum);
            }
        }
        for (PlayerPreference player : players) if (!result.containsKey(player.player())) {
            KingdomId next = ids.stream().filter(id -> capacity.get(id) > 0).max(Comparator.comparing(capacity::get)).orElseThrow();
            result.put(player.player(), next); capacity.merge(next, -1, Integer::sum);
        }
        return Map.copyOf(result);
    }
    public record PlayerPreference(UUID player, KingdomId preferred) { }
}
