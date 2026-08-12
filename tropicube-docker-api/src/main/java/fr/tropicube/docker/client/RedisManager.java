package fr.tropicube.docker.client;

import fr.tropicube.docker.model.ServerInstance;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Central Redis manager for Tropicube inter-service communication.
 * <p>
 * Two main responsibilities:
 * 1. Key-value storage — persist shared state (server instances, player data, templates)
 * 2. Pub/Sub Messaging — broadcast real-time events between services (servers, players, commands)
 * <p>
 * All keys are prefixed with "tropicube:" to avoid collisions
 * with other applications on the same Redis server.
 */
public class RedisManager {

    private static final System.Logger LOGGER = System.getLogger(RedisManager.class.getName());

    // ── Namespace ──────────────────────────────────────────────────────────────

    /** Common prefix applied to all keys and channels to avoid naming conflicts. */
    private static final String KEY_PREFIX = "tropicube:";

    // ── Canaux Pub/Sub ─────────────────────────────────────────────────────────

    /** Channel for server lifecycle events (start, stop, update, etc.). */
    private static final String CHANNEL_SERVERS  = KEY_PREFIX + "servers";

    /** Channel for player events (connection, disconnection, server change, etc.). */
    private static final String CHANNEL_PLAYERS  = KEY_PREFIX + "players";

    /** Channel for remote commands sent to specific server instances. */
    private static final String CHANNEL_COMMANDS = KEY_PREFIX + "commands";
    private static final String SAVE_INSTANCE_SCRIPT = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SADD', KEYS[2], ARGV[2])
            redis.call('SADD', KEYS[3], ARGV[2])
            return 1
            """;
    private static final String REMOVE_INSTANCE_SCRIPT = """
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            redis.call('SREM', KEYS[3], ARGV[1])
            redis.call('DEL', KEYS[4], KEYS[5])
            return 1
            """;
    private static final List<String> INSTANCE_REFERENCE_PATTERNS = List.of(
            "host:*", "player:server:*", "sw:rejoin:*", "sw:left-game:*",
            "sw:next-game:*", "post-game:*");
    private static final String DELETE_IF_VALUE_MATCHES_SCRIPT = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """;
    private static final String RESERVE_UNLESS_BLOCKED_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """;

    // ── Connection settings ──────────────────────── ────────────────────────

    private final String host;
    private final int    port;
    private final String password; // null ou vide → pas d'authentification

    /** The Jedis client encapsulating the connection pool. Initialized by initialize(). */
    private volatile RedisClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    // ── Infrastructure Pub/Sub ─────────────────────────────────────────────────

    /**
     * Virtual thread executor used to isolate blocking subscribe() calls
     * without tying up the main thread or creating an unbounded native thread pool.
     */
    private final ExecutorService subscriberExecutor;

    /**
     * Register of all active message handlers, grouped by channel name.
     * <p>
     * Structure: channel → [handler1, handler2, …]
     * <p>
     * ConcurrentHashMap is used as the main thread (registration of handlers)
     * and subscribed background threads (message delivery) access it at the same time.
     */
    private final Map<String, List<Consumer<String>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, JedisPubSub> activeSubscriptions = new ConcurrentHashMap<>();
    private final Set<String> subscriptionRunners = ConcurrentHashMap.newKeySet();

    // ── Constructeur ───────────────────────────────────────────────────────────

    public RedisManager(String host, int port, String password) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host est obligatoire");
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("port doit être compris entre 1 et 65535");
        this.host     = host;
        this.port     = port;
        this.password = password;
        this.subscriberExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("tropicube-redis-sub-", 0).factory());
    }

    // ── Initialisation ─────────────────────────────────────────────────────────

    /**
     * Builds the Jedis client with a connection pool and connects to Redis.
     * Must be called only once before any other method.
     */
    public synchronized void initialize() {
        if (closed.get()) throw new IllegalStateException("RedisManager est fermé");
        if (client != null) throw new IllegalStateException("RedisManager est déjà initialisé");
        // Build the per-connection configuration (timeouts and optional authentication)
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2000) // délai max pour établir une connexion TCP
                .socketTimeoutMillis(2000);    // délai max pour attendre une réponse

        if (password != null && !password.isEmpty()) {
            configBuilder.password(password);
        }

        JedisClientConfig clientConfig = configBuilder.build();

        // Pool configuration: pre-opens connections and reuses them to avoid the cost of creating each call
        ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
        poolConfig.setMaxTotal(20);       // limite stricte de connexions simultanées
        poolConfig.setMaxIdle(10);        // garde au maximum 10 connexions inactives ouvertes
        poolConfig.setMinIdle(2);         // garde toujours au minimum 2 connexions prêtes
        poolConfig.setTestOnBorrow(true); // vérifie la connexion avant de la prêter (ping)

        RedisClient candidate = RedisClient.builder()
                .hostAndPort(host, port)
                .clientConfig(clientConfig)
                .poolConfig(poolConfig)
                .build();

        // RedisClient is lazy: a PING actually forces the connection and
        // causes startup to fail immediately if Redis is unavailable.
        try {
            String response = candidate.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                throw new IllegalStateException("Réponse Redis inattendue: " + response);
            }
            this.client = candidate;
        } catch (RuntimeException e) {
            candidate.close();
            throw new IllegalStateException("Impossible de se connecter à Redis " + host + ":" + port, e);
        }
    }

    /** Exposes the raw Jedis client for advanced operations not covered by this handler. */
    public RedisClient getClient() {
        return redis();
    }

    private RedisClient redis() {
        RedisClient current = client;
        if (current == null) {
            throw new IllegalStateException(closed.get() ? "RedisManager est fermé" : "RedisManager n'est pas initialisé");
        }
        return current;
    }

    // ===== INSTANCES =====

    /**
     * Persists a ServerInstance in Redis under three structures:
     * - tropicube:instance:<id> → complete JSON data (TTL 24h)
     * - tropicube:instances:active → set of all active instance IDs
     * - tropicube:instances:type:<type> → set of IDs grouped by server type
     * <p>
     * Both sets allow efficient searches like "give me all active LOBBYs"
     * without having to go through all the keys.
     */
    public void saveInstance(ServerInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (instance.getInstanceId() == null || instance.getInstanceId().isBlank()) {
            throw new IllegalArgumentException("instance.instanceId est obligatoire");
        }
        if (instance.getServerType() == null || instance.getServerType().isBlank()) {
            throw new IllegalArgumentException("instance.serverType est obligatoire");
        }
        RedisClient redis = redis();
        String key = KEY_PREFIX + "instance:" + instance.getInstanceId();
        redis.eval(SAVE_INSTANCE_SCRIPT,
                List.of(key, KEY_PREFIX + "instances:active", KEY_PREFIX + "instances:type:" + instance.getServerType()),
                List.of(instance.toJson(), instance.getInstanceId(), "86400"));
    }

    /**
     * Deletes a stopped instance of the three Redis structures.
     * serverType is required because the instance data may already have been deleted.
     */
    public void removeInstance(String instanceId, String serverType) {
        requireText(instanceId, "instanceId");
        requireText(serverType, "serverType");
        RedisClient redis = redis();
        redis.eval(REMOVE_INSTANCE_SCRIPT,
                List.of(KEY_PREFIX + "instance:" + instanceId, KEY_PREFIX + "instances:active",
                        KEY_PREFIX + "instances:type:" + serverType,
                        KEY_PREFIX + "sw:game-started:" + instanceId,
                        KEY_PREFIX + "sw:next-game:" + instanceId),
                List.of(instanceId));
    }

    /**
     * Purge the instance and any known Redis references that could make it visible after deletion.
     * <p>
     * The removal of the main register and direct markers is atomic. Historical inverse indexes
     * not existing, the player/host references are scanned with {@code SCAN} then deleted so
     * idempotent; a new concurrent entry therefore remains visible instead of being deleted by mistake.
     *
     * @return number of secondary references deleted
     */
    public int purgeInstance(String instanceId, String serverType, String serverName) {
        requireText(instanceId, "instanceId");
        requireText(serverType, "serverType");
        requireText(serverName, "serverName");
        removeInstance(instanceId, serverType);

        RedisClient redis = redis();
        int removedReferences = 0;
        for (String pattern : INSTANCE_REFERENCE_PATTERNS) {
            String cursor = "0";
            ScanParams params = new ScanParams().match(KEY_PREFIX + pattern).count(100);
            do {
                var result = redis.scan(cursor, params);
                cursor = result.getCursor();
                for (String key : result.getResult()) {
                    String value = redis.get(key);
                    if (isInstanceReference(key, value, instanceId, serverName)) {
                        Object removed = redis.eval(
                                DELETE_IF_VALUE_MATCHES_SCRIPT, List.of(key), List.of(value));
                        removedReferences += ((Number) removed).intValue();
                    }
                }
            } while (!"0".equals(cursor));
        }
        return removedReferences;
    }

    static boolean isInstanceReference(String key, String value, String instanceId, String serverName) {
        if (key == null || value == null) return false;
        if (key.startsWith(KEY_PREFIX + "sw:next-game:")) return value.equals(serverName);
        if (key.startsWith(KEY_PREFIX + "post-game:")) return value.startsWith(serverName + "|");
        return value.equals(instanceId);
    }

    /**
     * Retrieves and deserializes an instance by its ID.
     * Returns null if the key does not exist or has expired.
     */
    public ServerInstance getInstance(String instanceId) {
        requireText(instanceId, "instanceId");
        String data = redis().get(KEY_PREFIX + "instance:" + instanceId);
        return data != null ? ServerInstance.fromJson(data) : null;
    }

    /**
     * Returns all currently active instances.
     * Reads the "active" set to get the IDs, then retrieves each instance individually.
     * Note: a race condition is possible between reading the set and retrieving the data
     * (an instance could be deleted in between), hence the null check.
     */
    public List<ServerInstance> getAllInstances() {
        List<ServerInstance> instances = new ArrayList<>();
        RedisClient redis = redis();
        Set<String> ids = redis.smembers(KEY_PREFIX + "instances:active");
        for (String id : ids) {
            String data = redis.get(KEY_PREFIX + "instance:" + id);
            if (data == null) {
                redis.srem(KEY_PREFIX + "instances:active", id);
                continue;
            }
            try {
                instances.add(ServerInstance.fromJson(data));
            } catch (RuntimeException e) {
                redis.del(KEY_PREFIX + "instance:" + id);
                redis.srem(KEY_PREFIX + "instances:active", id);
                LOGGER.log(System.Logger.Level.WARNING, "Instance Redis invalide supprimée : " + id, e);
            }
        }
        return instances;
    }

    // ===== PLAYERS =====

    /** Records which instance a player is on. TTL: 1 hour (automatically cleans when disconnected). */
    public void setPlayerServer(String playerUuid, String instanceId) {
        requireText(playerUuid, "playerUuid");
        requireText(instanceId, "instanceId");
        redis().set(KEY_PREFIX + "player:server:" + playerUuid, instanceId, SetParams.setParams().ex(3600L));
    }

    /** Returns the ID of the instance the player is on, or null if the player is not connected. */
    public String getPlayerServer(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        return redis().get(KEY_PREFIX + "player:server:" + playerUuid);
    }

    /** Deletes the player's location (called at logout). */
    public void removePlayerServer(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        redis().del(KEY_PREFIX + "player:server:" + playerUuid);
    }

    /** Saves the player's preferred language. TTL: 24h. */
    public void setPlayerLanguage(String playerUuid, String lang) {
        requireText(playerUuid, "playerUuid");
        requireText(lang, "lang");
        redis().set(KEY_PREFIX + "player:lang:" + playerUuid, lang, SetParams.setParams().ex(86400L));
    }

    /** Returns the player's preferred language, or null if not set. */
    public String getPlayerLanguage(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        return redis().get(KEY_PREFIX + "player:lang:" + playerUuid);
    }

    /** Removes player language from Redis (called at logout).*/
    public void removePlayerLanguage(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        redis().del(KEY_PREFIX + "player:lang:" + playerUuid);
    }

    // ===== PUB/SUB =====

    /**
     * Publishes a server event to the dedicated channel.
     * The message is formatted as "TYPE:payload" (ex: "STARTED:abc123").
     */
    public void publishServerEvent(String eventType, String payload) {
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        publish(CHANNEL_SERVERS, eventType + ":" + payload);
    }

    /**
     * Publish a player event on the dedicated channel.
     * The message is formatted as "TYPE:payload" (ex: "JOIN:player-uuid").
     */
    public void publishPlayerEvent(String eventType, String payload) {
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        publish(CHANNEL_PLAYERS, eventType + ":" + payload);
    }

    /**
     * Sends a command to a specific target server.
     * The message is formatted as "targetserver:command".
     */
    public void publishCommand(String targetServer, String command) {
        requireText(targetServer, "targetServer");
        requireText(command, "command");
        publish(CHANNEL_COMMANDS, targetServer + ":" + command);
    }

    /** Common internal method of sending: Publishes a raw message to a Redis channel. */
    private void publish(String channel, String message) {
        requireText(channel, "channel");
        Objects.requireNonNull(message, "message");
        redis().publish(channel, message);
    }

    /**
     * Subscribes a handler to a Redis channel.
     * <p>
     * Deux choses se passent :
     * 1. The handler is saved in the “subscribers” map under the channel name,
     * alongside any already existing handlers for this same channel.
     * 2. At the first handler of the channel, a dedicated thread is launched to listen to Redis,
     * because client.subscribe() is blocking (it never returns control).
     * When a message arrives, onMessage() distributes the message to all registered handlers.
     * <p>
     * Additional handlers reuse the same subscription to avoid
     * livraisons en double.
     */
    public void subscribe(String channel, Consumer<String> handler) {
        requireText(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        redis();
        // Adds the handler to the existing list, or creates a new list if the channel is new
        subscribers.computeIfAbsent(channel, _ -> new CopyOnWriteArrayList<>()).add(handler);

        // A single runner per channel maintains the subscription and recreates it after an outage.
        if (subscriptionRunners.add(channel)) {
            subscriberExecutor.submit(() -> runSubscriptionLoop(channel));
        }
    }

    private void runSubscriptionLoop(String channel) {
        long retryDelayMillis = 1_000;
        try {
            while (!closed.get() && subscribers.containsKey(channel)) {
                JedisPubSub subscription = new JedisPubSub() {
                    @Override
                    public void onMessage(String receivedChannel, String message) {
                        List<Consumer<String>> handlers = subscribers.get(receivedChannel);
                        if (handlers == null) return;
                        handlers.forEach(h -> {
                            try {
                                h.accept(message);
                            } catch (RuntimeException e) {
                                LOGGER.log(System.Logger.Level.WARNING,
                                        "Un handler Redis a échoué sur le canal " + receivedChannel, e);
                            }
                        });
                    }
                };
                activeSubscriptions.put(channel, subscription);
                try {
                    redis().subscribe(subscription, channel);
                    retryDelayMillis = 1_000;
                } catch (RuntimeException e) {
                    if (!closed.get() && subscribers.containsKey(channel)) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Abonnement Redis interrompu sur le canal " + channel
                                        + "; nouvelle tentative dans " + retryDelayMillis + " ms", e);
                    }
                } finally {
                    activeSubscriptions.remove(channel, subscription);
                }

                if (closed.get() || !subscribers.containsKey(channel)) break;
                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                retryDelayMillis = Math.min(retryDelayMillis * 2, 30_000);
            }
        } finally {
            subscriptionRunners.remove(channel);
            // Covers the run where a handler is added during runner exit.
            if (!closed.get() && subscribers.containsKey(channel) && subscriptionRunners.add(channel)) {
                subscriberExecutor.submit(() -> runSubscriptionLoop(channel));
            }
        }
    }

    /** Removes a local handler and closes the Redis subscription when it is no longer used. */
    public void unsubscribe(String channel, Consumer<String> handler) {
        requireText(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        List<Consumer<String>> handlers = subscribers.get(channel);
        if (handlers == null) return;
        handlers.remove(handler);
        if (handlers.isEmpty() && subscribers.remove(channel, handlers)) {
            JedisPubSub subscription = activeSubscriptions.remove(channel);
            if (subscription != null) subscription.unsubscribe();
        }
    }

    /** Shortcut to subscribe to player events (tropicube:players channel). */
    public void subscribeToPlayerEvents(Consumer<String> handler) {
        subscribe(CHANNEL_PLAYERS, handler);
    }

    /** Shortcut to subscribe to remote commands (tropicube:commands channel). */
    public void subscribeToCommands(Consumer<String> handler) {
        subscribe(CHANNEL_COMMANDS, handler);
    }

    // ===== TEMPLATES =====

    /** Saves the list of server templates in JSON format. TTL: 24h. */
    public void saveTemplatesJson(String json) {
        Objects.requireNonNull(json, "json");
        redis().set(KEY_PREFIX + "templates", json, SetParams.setParams().ex(86400L));
    }

    /** Retrieves the list of server templates in JSON format, or null if absent. */
    public String getTemplatesJson() {
        return redis().get(KEY_PREFIX + "templates");
    }

    // ===== GENERIC KEY-VALUE =====

    /** Stores an arbitrary value under a prefixed key, with a TTL in seconds. */
    public void set(String key, String value, int ttlSeconds) {
        requireText(key, "key");
        Objects.requireNonNull(value, "value");
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds doit être strictement positif");
        redis().set(KEY_PREFIX + key, value, SetParams.setParams().ex(ttlSeconds));
    }

    /**
     * Creates a reservation only if neither it nor the blocking key
     * do not exist. Control and write form a single Redis operation.
     */
    public boolean reserveUnlessBlocked(String reservationKey, String blockingKey,
                                        String value, int ttlSeconds) {
        requireText(reservationKey, "reservationKey");
        requireText(blockingKey, "blockingKey");
        Objects.requireNonNull(value, "value");
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds doit être strictement positif");
        Object result = redis().eval(RESERVE_UNLESS_BLOCKED_SCRIPT,
                List.of(KEY_PREFIX + reservationKey, KEY_PREFIX + blockingKey),
                List.of(value, Integer.toString(ttlSeconds)));
        return result instanceof Number number && number.longValue() == 1L;
    }

    /** Retrieves a value by its prefixed key. Returns null if the key does not exist or has expired. */
    public String get(String key) {
        requireText(key, "key");
        return redis().get(KEY_PREFIX + key);
    }

    /** Removes a prefixed key from Redis. */
    public void delete(String key) {
        requireText(key, "key");
        redis().del(KEY_PREFIX + key);
    }

    /** Checks if a prefixed key exists in Redis. */
    public boolean exists(String key) {
        requireText(key, "key");
        return redis().exists(KEY_PREFIX + key);
    }

    /**
     * Closes the manager cleanly:
     * 1. Immediately stops all Pub/Sub listening threads
     * 2. Close the Redis client and release the connection pool
     */
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        activeSubscriptions.values().forEach(subscription -> {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException ignored) {
                // The connection may already be closed.
            }
        });
        activeSubscriptions.clear();
        subscriptionRunners.clear();
        subscribers.clear();
        subscriberExecutor.shutdownNow();
        RedisClient current = client;
        client = null;
        if (current != null) current.close();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " est obligatoire");
    }
}
