package fr.tropicube.docker.model;

import java.util.Map;
import java.util.Set;

/** Pure cumulative policy used by Paper and Velocity compatibility adapters. */
public final class AccessPolicy {
    private static final Set<String> RETIRED_PERMISSIONS = Set.of("sheepwars.mapvote.weight.2");
    private static final Map<String, Integer> DEFAULT_VIP_PERMISSIONS = Map.ofEntries(
            Map.entry("tropicube.vip", 1),
            Map.entry("tropicube.kit.vip", 1),
            Map.entry("tropicube.lobby.jump", 1),
            Map.entry("tropicube.vip.plus", 2),
            Map.entry("tropicube.kit.vip_plus", 2),
            Map.entry("tropicube.lobby.jump.double", 2),
            Map.entry("tropicube.custom-game.create", 2),
            Map.entry("tropicube.queue.priority", 2),
            Map.entry("tropicube.premium", 3),
            Map.entry("tropicube.kit.premium", 3),
            Map.entry("tropicube.lobby.infinitejump", 3),
            Map.entry("tropicube.announce.join", 3),
            Map.entry("tropicube.nick", 3)
    );

    private static final Map<String, Integer> DEFAULT_MOD_PERMISSIONS = Map.ofEntries(
            Map.entry("tropicube.staff", 1),
            Map.entry("tropicube.helper", 1),
            Map.entry("tropicube.mute", 1),
            Map.entry("tropicube.kick", 1),
            Map.entry("tropicube.2fa.issue", 1),
            Map.entry("tropicube.moderateur", 2),
            Map.entry("tropicube.warn", 2),
            Map.entry("tropicube.ban", 2),
            Map.entry("tropicube.history", 2),
            Map.entry("tropicube.reports.manage", 2),
            Map.entry("tropicube.moderation.chat-evidence", 2)
    );

    private final Map<String, Integer> vipPermissions;
    private final Map<String, Integer> modPermissions;

    public AccessPolicy(Map<String, Integer> vipOverrides, Map<String, Integer> modOverrides) {
        vipPermissions = merge(DEFAULT_VIP_PERMISSIONS, vipOverrides, PlayerAccessProfile.MAX_VIP_LEVEL, "VIP");
        modPermissions = merge(DEFAULT_MOD_PERMISSIONS, modOverrides, PlayerAccessProfile.MAX_MOD_LEVEL, "mod");
    }

    public static AccessPolicy defaults() { return new AccessPolicy(Map.of(), Map.of()); }

    public boolean hasPermission(PlayerAccessProfile profile, String permission) {
        if (profile == null || permission == null || permission.isBlank()) return false;
        if (profile.modLevel() >= 4) return true;
        Integer vip = vipPermissions.get(permission);
        if (vip != null && profile.vipLevel() >= vip) return true;
        Integer mod = modPermissions.get(permission);
        if (mod != null && profile.modLevel() >= mod) return true;
        return profile.modLevel() >= 3
                && (permission.startsWith("tropicube.") || permission.startsWith("sheepwars."));
    }

    public static boolean canManageVip(PlayerAccessProfile actor) {
        return actor != null && actor.modLevel() >= 3;
    }

    public static boolean canAssignMod(PlayerAccessProfile actor, int targetLevel) {
        return actor != null && targetLevel >= 0 && targetLevel < actor.modLevel();
    }

    private static Map<String, Integer> merge(Map<String, Integer> defaults, Map<String, Integer> overrides,
                                              int maximum, String axis) {
        var merged = new java.util.HashMap<>(defaults);
        if (overrides != null) overrides.forEach((permission, level) -> {
            // Old deployed configurations may still contain retired capabilities.
            if (RETIRED_PERMISSIONS.contains(permission)) return;
            if (!defaults.containsKey(permission)) throw new IllegalArgumentException(
                    "Permission " + axis + " configurable inconnue: " + permission);
            if (level == null || level < 1 || level > maximum) throw new IllegalArgumentException(
                    "Seuil " + axis + " invalide pour " + permission + ": " + level);
            merged.put(permission, level);
        });
        return Map.copyOf(merged);
    }
}
