package fr.tropicube.velocity.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.NickIdentity;
import fr.tropicube.docker.model.PlayerGradeCache;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/** Handles generation, Redis persistence, and deletion of anonymized identities. */
public class NickManager {

    private static final String KEY_ORIGINAL = "nick:original:";
    private static final Pattern COMPACT_UUID = Pattern.compile("[0-9a-fA-F]{32}");

    private static final List<String> ADJECTIVES = List.of(
        "Arcane","Blaze","Cryo","Dark","Ember",
        "Frost","Ghost","Haze","Iron","Jade",
        "King","Lava","Mist","Neon","Onyx",
        "Peak","Rogue","Storm","Toxic","Ultra",
        "Void","Wild","Xenon","Zeal","Lunar"
    );
    private static final List<String> NOUNS = List.of(
        "Blaze","Creep","Drake","Ender","Ghast",
        "Golem","Hydra","Ninja","Ogre","Pixel",
        "Raven","Shade","Tiger","Viper","Wolf",
        "Yeti","Monk","Scout","Rider","Hawk"
    );
    private static final List<String> DEFAULT_SKIN_UUIDS = List.of(
        "069a79f444e94726a5befca90e38aaf5", // Notch
        "853c80ef3c3749fdaa49938b674adae6", // jeb_
        "61699b2ed3274a019f1e0ea8c3f06bc6"  // Dinnerbone
    );

    private final RedisManager redis;
    private final Logger       logger;
    private final HttpClient   http;
    private final List<String> skinUuids;
    private final List<String> allowedGrades;
    private final Gson         gson = new Gson();

    public NickManager(RedisManager redis, Logger logger,
                       List<String> configuredSkinUuids, List<String> allowedGrades) {
        this.redis  = redis;
        this.logger = logger;
        this.http   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        List<String> pool = new ArrayList<>(DEFAULT_SKIN_UUIDS);
        Objects.requireNonNullElse(configuredSkinUuids, List.<String>of()).stream()
            .map(u -> u.replace("-", ""))
            .filter(u -> {
                boolean valid = COMPACT_UUID.matcher(u).matches();
                if (!valid) logger.warn("[Nick] UUID de skin ignoré car invalide : {}", u);
                return valid;
            })
            .forEach(pool::add);
        this.skinUuids = List.copyOf(pool);

        this.allowedGrades = Objects.requireNonNullElse(allowedGrades, List.<String>of()).stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(grade -> !grade.isEmpty())
            .map(grade -> grade.toUpperCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    // Autorisation

    public boolean canUseNick(UUID uuid) {
        if (allowedGrades.isEmpty()) return false;
        String grade = redis.get(PlayerGradeCache.key(uuid));
        return PlayerGradeCache.isAllowed(grade, allowedGrades);
    }

    // Pseudonym generation

    public String generateRandomName() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String adj  = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(random.nextInt(NOUNS.size()));
        int    num  = random.nextInt(10, 100);
        String name = adj + noun + num;
        return name.length() > 16 ? name.substring(0, 16) : name;
    }

    // Recovery of Mojang skins

    public CompletableFuture<Optional<SkinData>> fetchRandomSkin() {
        String uuid = skinUuids.get(ThreadLocalRandom.current().nextInt(skinUuids.size()));
        String url  = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(resp -> {
                if (resp.statusCode() != 200) {
                    logger.warn("[Nick] Mojang returned {} for {}", resp.statusCode(), uuid);
                    return Optional.<SkinData>empty();
                }
                try {
                    JsonObject obj   = JsonParser.parseString(resp.body()).getAsJsonObject();
                    JsonArray  props = obj.getAsJsonArray("properties");
                    for (var el : props) {
                        JsonObject prop = el.getAsJsonObject();
                        if ("textures".equals(prop.get("name").getAsString())) {
                            String value = prop.get("value").getAsString();
                            String sig   = prop.has("signature") ? prop.get("signature").getAsString() : null;
                            return Optional.of(new SkinData(value, sig));
                        }
                    }
                } catch (Exception e) {
                    logger.warn("[Nick] Failed to parse Mojang response: {}", e.getMessage());
                }
                return Optional.<SkinData>empty();
            })
            .exceptionally(e -> {
                logger.warn("[Nick] HTTP error fetching skin: {}", e.getMessage());
                return Optional.empty();
            });
    }

    // Persistance Redis

    public void storeNick(UUID uuid, String nickName, SkinData skin) {
        NickIdentity identity = new NickIdentity(
                nickName, skin.value(), skin.signature(), NickIdentity.DEFAULT_DISPLAY_GRADE);
        redis.set(NickIdentity.key(uuid), identity.toJson(), NickIdentity.TTL_SECONDS);
    }

    public Optional<NickData> getNick(UUID uuid) {
        String raw = redis.get(NickIdentity.key(uuid));
        Optional<NickIdentity> identity = NickIdentity.fromJson(raw);
        if (raw != null && identity.isEmpty()) {
            logger.warn("[Nick] Corrupted nick data for {}", uuid);
            redis.delete(NickIdentity.key(uuid));
        }
        return identity.map(value -> new NickData(
                value.name(), new SkinData(value.skinValue(), value.skinSignature()), value.displayGrade()));
    }

    public void clearNick(UUID uuid) {
        redis.delete(NickIdentity.key(uuid));
    }

    /**
     * Retains the complete identity after disconnection so that
     * {@code GameProfileRequestEvent} restores the nick and its display grade.
     */
    public void parkNick(UUID uuid) {
        String raw = redis.get(NickIdentity.key(uuid));
        if (raw != null) redis.set(NickIdentity.key(uuid), raw, NickIdentity.TTL_SECONDS);
        String origRaw = redis.get(KEY_ORIGINAL + uuid);
        if (origRaw != null) redis.set(KEY_ORIGINAL + uuid, origRaw, NickIdentity.TTL_SECONDS);
    }

    /**
     * Restores full life after quick reconnection.
     */
    public void refreshNickTtl(UUID uuid) {
        String raw = redis.get(NickIdentity.key(uuid));
        if (raw != null) redis.set(NickIdentity.key(uuid), raw, NickIdentity.TTL_SECONDS);
        String origRaw = redis.get(KEY_ORIGINAL + uuid);
        if (origRaw != null) redis.set(KEY_ORIGINAL + uuid, origRaw, NickIdentity.TTL_SECONDS);
    }

    // Original profile used by /nick off

    /** Called at GameProfileRequestEvent to remember the real Mojang skin. */
    public void storeOriginalProfile(UUID uuid, String realName, GameProfile.Property textures) {
        JsonObject obj = new JsonObject();
        obj.addProperty("n", realName);
        obj.addProperty("v", textures.getValue());
        if (textures.getSignature() != null) obj.addProperty("s", textures.getSignature());
        redis.set(KEY_ORIGINAL + uuid, gson.toJson(obj), NickIdentity.TTL_SECONDS);
    }

    public Optional<OriginalProfile> getOriginalProfile(UUID uuid) {
        String raw = redis.get(KEY_ORIGINAL + uuid);
        if (raw == null) return Optional.empty();
        try {
            JsonObject obj  = JsonParser.parseString(raw).getAsJsonObject();
            String     name = obj.get("n").getAsString();
            String     val  = obj.get("v").getAsString();
            String     sig  = obj.has("s") ? obj.get("s").getAsString() : null;
            return Optional.of(new OriginalProfile(name, new SkinData(val, sig)));
        } catch (Exception e) {
            logger.warn("[Nick] Corrupted original profile for {}: {}", uuid, e.getMessage());
            redis.delete(KEY_ORIGINAL + uuid);
            return Optional.empty();
        }
    }

    public void clearOriginalProfile(UUID uuid) {
        redis.delete(KEY_ORIGINAL + uuid);
    }

    // Notifications sent to backends

    public void publishNickApply(UUID uuid) {
        redis.publishPlayerEvent("NICK_APPLY", uuid.toString());
    }

    public void publishNickReset(UUID uuid) {
        redis.publishPlayerEvent("NICK_RESET", uuid.toString());
    }

    /** Full cleanup: restores skin on backend then deletes both nick Redis keys. */
    public void publishNickClear(UUID uuid) {
        redis.publishPlayerEvent("NICK_CLEAR", uuid.toString());
    }

    // Velocity session profile: Isolated compatibility with an internal API.

    /**
     * Attempts to update the internal GameProfile of the Velocity session in order to
     * that transfers retain the skin without completely reconnecting to the proxy.
     * Unavailability of the internal API is not blocking: the Redis channel
     * always applies the pseudonym on Paper servers.
     */
    public void tryUpdateSessionProfile(Player player, GameProfile newProfile) {
        Class<?> cls = player.getClass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                if (f.getType() == GameProfile.class) {
                    try {
                        f.setAccessible(true);
                        f.set(player, newProfile);
                        logger.debug("[Nick] Session profile updated for {}", player.getUsername());
                        return;
                    } catch (Exception ignored) {}
                }
            }
            cls = cls.getSuperclass();
        }
        logger.debug("[Nick] Could not update session profile for {} via reflection", player.getUsername());
    }

    // Serialized data

    public record SkinData(String value, String signature) {}
    public record NickData(String nickName, SkinData skin, String displayGrade) {}
    public record OriginalProfile(String name, SkinData skin) {}
}
