package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import java.util.List;
import java.util.Map;

/** Fully validated immutable map definition consumed by every game service. */
public record MapDefinition(String id, String displayNameKey, String world, Position lobby, Position spectator,
                            BlockRegion playableRegion, double borderX, double borderZ,
                            double initialBorderSize, Map<Integer, List<KingdomId>> layouts,
                            Map<KingdomId, BaseDefinition> bases) {
    public MapDefinition {
        layouts = layouts.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        bases = Map.copyOf(bases);
        if (id == null || id.isBlank() || displayNameKey == null || displayNameKey.isBlank())
            throw new IllegalArgumentException("Identifiant ou traduction de carte manquant");
        if (initialBorderSize <= 50) throw new IllegalArgumentException("La bordure initiale doit dépasser 50 blocs");
        if (!playableRegion.contains(lobby) || !playableRegion.contains(spectator))
            throw new IllegalArgumentException("Les accueils doivent être dans la région jouable");
        double half = initialBorderSize / 2.0;
        if (borderX - half > playableRegion.minX() || borderX + half < playableRegion.maxX()
                || borderZ - half > playableRegion.minZ() || borderZ + half < playableRegion.maxZ())
            throw new IllegalArgumentException("La bordure initiale ne couvre pas la région jouable");
        for (var entry : layouts.entrySet()) {
            if (entry.getKey() < 2 || entry.getKey() > 5 || entry.getValue().size() < entry.getKey()
                    || entry.getValue().stream().distinct().count() != entry.getValue().size()) throw new IllegalArgumentException("Layout invalide: " + entry.getKey());
            if (!bases.keySet().containsAll(entry.getValue())) throw new IllegalArgumentException("Layout référençant une base absente: " + entry.getKey());
        }
        var values = bases.values().stream().toList();
        for (var entry : bases.entrySet()) {
            BaseDefinition base = entry.getValue();
            if (!playableRegion.contains(base.spawn()) || !playableRegion.contains(base.heart())
                    || !base.region().contains(base.spawn()) || !base.region().contains(base.heart())
                    || base.region().minX() < playableRegion.minX() || base.region().maxX() > playableRegion.maxX()
                    || base.region().minY() < playableRegion.minY() || base.region().maxY() > playableRegion.maxY()
                    || base.region().minZ() < playableRegion.minZ() || base.region().maxZ() > playableRegion.maxZ())
                throw new IllegalArgumentException("Positions hors région pour " + entry.getKey());
        }
        for (int left = 0; left < values.size(); left++) for (int right = left + 1; right < values.size(); right++)
            if (values.get(left).region().overlaps(values.get(right).region())) throw new IllegalArgumentException("Les régions de base se chevauchent");
    }
}
