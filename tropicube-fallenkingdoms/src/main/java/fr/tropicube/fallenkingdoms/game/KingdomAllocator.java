package fr.tropicube.fallenkingdoms.game;

import java.util.*;
import java.util.random.RandomGenerator;

/** Balances public rosters while honoring as many preferred kingdoms as possible. */
public final class KingdomAllocator {
    public static int kingdomCount(int players) {
        if (players < 6 || players > 30) throw new IllegalArgumentException("Une partie Quick Play accepte de 6 à 30 joueurs.");
        return Math.min(5, Math.max(2, (players + 5) / 6));
    }
    public static int kingdomCount(int players, int maximumPlayersPerKingdom, int maximumKingdoms) {
        return kingdomCount(players, 3, maximumPlayersPerKingdom, maximumKingdoms);
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
        int kingdoms = kingdomCount(preferences.size());
        return allocate(preferences, Arrays.asList(KingdomId.values()), kingdoms, 3, 6, new Random(0));
    }
    /** Allocates against the eligible kingdom pool declared by the selected map. */
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences, List<KingdomId> ids) {
        return allocate(preferences, ids, 3, 6);
    }
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences, List<KingdomId> ids,
                                         int minimumPlayersPerKingdom, int maximumPlayersPerKingdom) {
        return allocate(preferences, ids, ids.size(), minimumPlayersPerKingdom, maximumPlayersPerKingdom,
                new Random(0));
    }

    /**
     * Selects the active kingdoms and balances their players while maximizing honored preferences.
     * Equivalent optimal solutions are selected through the supplied per-session random generator.
     */
    public Map<UUID, KingdomId> allocate(Collection<PlayerPreference> preferences, List<KingdomId> eligibleIds,
                                         int kingdomCount, int minimumPlayersPerKingdom,
                                         int maximumPlayersPerKingdom, RandomGenerator random) {
        List<PlayerPreference> players = preferences.stream()
                .sorted(Comparator.comparing(player -> player.player().toString()))
                .toList();
        List<KingdomId> ids = List.copyOf(eligibleIds);
        if (kingdomCount < 2 || kingdomCount > KingdomId.values().length
                || minimumPlayersPerKingdom < 1 || maximumPlayersPerKingdom < minimumPlayersPerKingdom
                || maximumPlayersPerKingdom > 6 || players.size() < kingdomCount * minimumPlayersPerKingdom
                || players.size() > kingdomCount * maximumPlayersPerKingdom)
            throw new IllegalArgumentException("L'agencement ne respecte pas la capacité configurée des royaumes.");
        if (ids.size() < kingdomCount || ids.stream().distinct().count() != ids.size())
            throw new IllegalArgumentException("Le pool doit contenir au moins " + kingdomCount + " royaumes distincts.");
        Objects.requireNonNull(random, "random");

        Map<KingdomId, Integer> demand = new EnumMap<>(KingdomId.class);
        for (PlayerPreference player : players) {
            if (player.preferred() != null && ids.contains(player.preferred())) demand.merge(player.preferred(), 1, Integer::sum);
        }
        int base = players.size() / kingdomCount;
        int extra = players.size() % kingdomCount;
        List<CapacityPlan> optimalPlans = new ArrayList<>();
        int[] bestScore = {-1};
        chooseKingdoms(ids, 0, kingdomCount, new ArrayList<>(), selected ->
                chooseExtraCapacity(selected, 0, extra, new HashSet<>(), bonus -> {
                    EnumMap<KingdomId, Integer> capacity = new EnumMap<>(KingdomId.class);
                    int score = 0;
                    for (KingdomId id : selected) {
                        int value = base + (bonus.contains(id) ? 1 : 0);
                        capacity.put(id, value);
                        score += Math.min(value, demand.getOrDefault(id, 0));
                    }
                    if (score > bestScore[0]) {
                        bestScore[0] = score;
                        optimalPlans.clear();
                    }
                    if (score == bestScore[0]) optimalPlans.add(new CapacityPlan(List.copyOf(selected), capacity));
                }));
        CapacityPlan plan = optimalPlans.get(random.nextInt(optimalPlans.size()));
        Map<KingdomId, Integer> capacity = new EnumMap<>(plan.capacity());
        Map<UUID, KingdomId> result = new LinkedHashMap<>();
        List<PlayerPreference> remaining = new ArrayList<>();
        for (KingdomId id : plan.kingdoms()) {
            List<PlayerPreference> requested = players.stream()
                    .filter(player -> id == player.preferred())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            shuffle(requested, random);
            int accepted = Math.min(requested.size(), capacity.get(id));
            for (int index = 0; index < accepted; index++) result.put(requested.get(index).player(), id);
            capacity.merge(id, -accepted, Integer::sum);
            remaining.addAll(requested.subList(accepted, requested.size()));
        }
        for (PlayerPreference player : players) {
            if (!result.containsKey(player.player()) && !remaining.contains(player)) remaining.add(player);
        }
        shuffle(remaining, random);
        for (PlayerPreference player : remaining) {
            List<KingdomId> available = plan.kingdoms().stream().filter(id -> capacity.get(id) > 0).toList();
            int greatestCapacity = available.stream().mapToInt(capacity::get).max().orElseThrow();
            List<KingdomId> leastFilled = available.stream().filter(id -> capacity.get(id) == greatestCapacity).toList();
            KingdomId next = leastFilled.get(random.nextInt(leastFilled.size()));
            result.put(player.player(), next);
            capacity.merge(next, -1, Integer::sum);
        }
        return Map.copyOf(result);
    }

    private static void chooseKingdoms(List<KingdomId> ids, int index, int remaining, List<KingdomId> selected,
                                       java.util.function.Consumer<List<KingdomId>> consumer) {
        if (remaining == 0) {
            consumer.accept(selected);
            return;
        }
        for (int cursor = index; cursor <= ids.size() - remaining; cursor++) {
            selected.add(ids.get(cursor));
            chooseKingdoms(ids, cursor + 1, remaining - 1, selected, consumer);
            selected.removeLast();
        }
    }

    private static void chooseExtraCapacity(List<KingdomId> ids, int index, int remaining, Set<KingdomId> selected,
                                            java.util.function.Consumer<Set<KingdomId>> consumer) {
        if (remaining == 0) {
            consumer.accept(Set.copyOf(selected));
            return;
        }
        for (int cursor = index; cursor <= ids.size() - remaining; cursor++) {
            selected.add(ids.get(cursor));
            chooseExtraCapacity(ids, cursor + 1, remaining - 1, selected, consumer);
            selected.remove(ids.get(cursor));
        }
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(other));
            values.set(other, value);
        }
    }

    private record CapacityPlan(List<KingdomId> kingdoms, Map<KingdomId, Integer> capacity) {
        private CapacityPlan {
            capacity = Map.copyOf(capacity);
        }
    }
    public record PlayerPreference(UUID player, KingdomId preferred) { }
}
