package fr.tropicube.docker.client;

import fr.tropicube.docker.model.ServerInstance;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Gestionnaire Redis central pour la communication inter-services de Tropicube.
 * <p>
 * Deux responsabilités principales :
 *   1. Stockage clé-valeur  — persister l'état partagé (instances de serveurs, données joueurs, templates)
 *   2. Messagerie Pub/Sub   — diffuser des événements en temps réel entre services (serveurs, joueurs, commandes)
 * <p>
 * Toutes les clés sont préfixées par "tropicube:" pour éviter les collisions
 * avec d'autres applications sur le même serveur Redis.
 */
public class RedisManager {

    private static final System.Logger LOGGER = System.getLogger(RedisManager.class.getName());

    // ── Namespace ──────────────────────────────────────────────────────────────

    /** Préfixe commun appliqué à toutes les clés et canaux pour éviter les conflits de nommage. */
    private static final String KEY_PREFIX = "tropicube:";

    // ── Canaux Pub/Sub ─────────────────────────────────────────────────────────

    /** Canal pour les événements du cycle de vie des serveurs (démarrage, arrêt, mise à jour…). */
    private static final String CHANNEL_SERVERS  = KEY_PREFIX + "servers";

    /** Canal pour les événements joueurs (connexion, déconnexion, changement de serveur…). */
    private static final String CHANNEL_PLAYERS  = KEY_PREFIX + "players";

    /** Canal pour les commandes à distance envoyées à des instances de serveurs spécifiques. */
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
            return 1
            """;
    private static final String RESERVE_UNLESS_BLOCKED_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """;

    // ── Paramètres de connexion ────────────────────────────────────────────────

    private final String host;
    private final int    port;
    private final String password; // null ou vide → pas d'authentification

    /** Le client Jedis encapsulant le pool de connexions. Initialisé par initialize(). */
    private volatile RedisClient client;
    private final AtomicBoolean closed = new AtomicBoolean();

    // ── Infrastructure Pub/Sub ─────────────────────────────────────────────────

    /**
     * Exécuteur de threads virtuels utilisé pour isoler les appels bloquants subscribe()
     * sans immobiliser le thread principal ni créer un pool de threads natifs non borné.
     */
    private final ExecutorService subscriberExecutor;

    /**
     * Registre de tous les handlers de messages actifs, regroupés par nom de canal.
     * <p>
     * Structure :  canal → [handler1, handler2, …]
     * <p>
     * ConcurrentHashMap est utilisé car le thread principal (enregistrement des handlers)
     * et les threads abonnés en arrière-plan (distribution des messages) y accèdent en même temps.
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
     * Construit le client Jedis avec un pool de connexions et se connecte à Redis.
     * Doit être appelé une seule fois avant toute autre méthode.
     */
    public synchronized void initialize() {
        if (closed.get()) throw new IllegalStateException("RedisManager est fermé");
        if (client != null) throw new IllegalStateException("RedisManager est déjà initialisé");
        // Construction de la configuration par connexion (timeouts, authentification optionnelle)
        DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(2000) // délai max pour établir une connexion TCP
                .socketTimeoutMillis(2000);    // délai max pour attendre une réponse

        if (password != null && !password.isEmpty()) {
            configBuilder.password(password);
        }

        JedisClientConfig clientConfig = configBuilder.build();

        // Configuration du pool : pré-ouvre des connexions et les réutilise pour éviter le coût de création à chaque appel
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

        // RedisClient est paresseux : un PING force réellement la connexion et
        // fait échouer le démarrage immédiatement si Redis est indisponible.
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

    /** Expose le client Jedis brut pour les opérations avancées non couvertes par ce gestionnaire. */
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
     * Persiste une ServerInstance dans Redis sous trois structures :
     *   - tropicube:instance:<id>           → données JSON complètes (TTL 24h)
     *   - tropicube:instances:active         → ensemble de tous les IDs d'instances actives
     *   - tropicube:instances:type:<type>    → ensemble des IDs groupés par type de serveur
     * <p>
     * Les deux ensembles permettent des recherches efficaces comme "donne-moi tous les LOBBYs actifs"
     * sans avoir à parcourir toutes les clés.
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
     * Supprime une instance arrêtée des trois structures Redis.
     * serverType est requis car les données de l'instance peuvent déjà avoir été supprimées.
     */
    public void removeInstance(String instanceId, String serverType) {
        requireText(instanceId, "instanceId");
        requireText(serverType, "serverType");
        RedisClient redis = redis();
        redis.eval(REMOVE_INSTANCE_SCRIPT,
                List.of(KEY_PREFIX + "instance:" + instanceId, KEY_PREFIX + "instances:active",
                        KEY_PREFIX + "instances:type:" + serverType),
                List.of(instanceId));
    }

    /**
     * Récupère et désérialise une instance par son ID.
     * Retourne null si la clé n'existe pas ou a expiré.
     */
    public ServerInstance getInstance(String instanceId) {
        requireText(instanceId, "instanceId");
        String data = redis().get(KEY_PREFIX + "instance:" + instanceId);
        return data != null ? ServerInstance.fromJson(data) : null;
    }

    /**
     * Retourne toutes les instances actuellement actives.
     * Lit l'ensemble "active" pour obtenir les IDs, puis récupère chaque instance individuellement.
     * Note : une race condition est possible entre la lecture de l'ensemble et la récupération des données
     * (une instance pourrait être supprimée entre les deux), d'où la vérification du null.
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

    // ===== JOUEURS =====

    /** Enregistre sur quelle instance se trouve un joueur. TTL : 1h (se nettoie automatiquement à la déconnexion). */
    public void setPlayerServer(String playerUuid, String instanceId) {
        requireText(playerUuid, "playerUuid");
        requireText(instanceId, "instanceId");
        redis().set(KEY_PREFIX + "player:server:" + playerUuid, instanceId, SetParams.setParams().ex(3600L));
    }

    /** Retourne l'ID de l'instance sur laquelle se trouve le joueur, ou null s'il n'est pas connecté. */
    public String getPlayerServer(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        return redis().get(KEY_PREFIX + "player:server:" + playerUuid);
    }

    /** Supprime la localisation du joueur (appelé à la déconnexion). */
    public void removePlayerServer(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        redis().del(KEY_PREFIX + "player:server:" + playerUuid);
    }

    /** Enregistre la langue préférée du joueur. TTL : 24h. */
    public void setPlayerLanguage(String playerUuid, String lang) {
        requireText(playerUuid, "playerUuid");
        requireText(lang, "lang");
        redis().set(KEY_PREFIX + "player:lang:" + playerUuid, lang, SetParams.setParams().ex(86400L));
    }

    /** Retourne la langue préférée du joueur, ou null si non définie. */
    public String getPlayerLanguage(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        return redis().get(KEY_PREFIX + "player:lang:" + playerUuid);
    }

    /** Supprime la langue du joueur de Redis (appelé à la déconnexion).*/
    public void removePlayerLanguage(String playerUuid) {
        requireText(playerUuid, "playerUuid");
        redis().del(KEY_PREFIX + "player:lang:" + playerUuid);
    }

    // ===== PUB/SUB =====

    /**
     * Publie un événement serveur sur le canal dédié.
     * Le message est formaté comme "TYPE:payload" (ex: "STARTED:abc123").
     */
    public void publishServerEvent(String eventType, String payload) {
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        publish(CHANNEL_SERVERS, eventType + ":" + payload);
    }

    /**
     * Publie un événement joueur sur le canal dédié.
     * Le message est formaté comme "TYPE:payload" (ex: "JOIN:uuid-du-joueur").
     */
    public void publishPlayerEvent(String eventType, String payload) {
        requireText(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        publish(CHANNEL_PLAYERS, eventType + ":" + payload);
    }

    /**
     * Envoie une commande à un serveur cible spécifique.
     * Le message est formaté comme "serveurCible:commande".
     */
    public void publishCommand(String targetServer, String command) {
        requireText(targetServer, "targetServer");
        requireText(command, "command");
        publish(CHANNEL_COMMANDS, targetServer + ":" + command);
    }

    /** Méthode interne commune d'envoi : publie un message brut sur un canal Redis. */
    private void publish(String channel, String message) {
        requireText(channel, "channel");
        Objects.requireNonNull(message, "message");
        redis().publish(channel, message);
    }

    /**
     * Abonne un handler à un canal Redis.
     * <p>
     * Deux choses se passent :
     *   1. Le handler est enregistré dans la map "subscribers" sous le nom du canal,
     *      aux côtés d'éventuels handlers déjà existants pour ce même canal.
     *   2. Au premier handler du canal, un thread dédié est lancé pour écouter Redis,
     *      car client.subscribe() est bloquant (il ne rend jamais la main).
     *      Quand un message arrive, onMessage() distribue le message à tous les handlers enregistrés.
     * <p>
     * Les handlers supplémentaires réutilisent le même abonnement afin d'éviter les
     * livraisons en double.
     */
    public void subscribe(String channel, Consumer<String> handler) {
        requireText(channel, "channel");
        Objects.requireNonNull(handler, "handler");
        redis();
        // Ajoute le handler à la liste existante, ou crée une nouvelle liste si le canal est nouveau
        subscribers.computeIfAbsent(channel, _ -> new CopyOnWriteArrayList<>()).add(handler);

        // Un seul runner par canal maintient l'abonnement et le recrée après une coupure.
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
            // Couvre la course où un handler est ajouté pendant la sortie du runner.
            if (!closed.get() && subscribers.containsKey(channel) && subscriptionRunners.add(channel)) {
                subscriberExecutor.submit(() -> runSubscriptionLoop(channel));
            }
        }
    }

    /** Retire un handler local et ferme l'abonnement Redis lorsque celui-ci n'est plus utilisé. */
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

    /** Raccourci pour s'abonner aux événements joueurs (canal tropicube:players). */
    public void subscribeToPlayerEvents(Consumer<String> handler) {
        subscribe(CHANNEL_PLAYERS, handler);
    }

    /** Raccourci pour s'abonner aux commandes à distance (canal tropicube:commands). */
    public void subscribeToCommands(Consumer<String> handler) {
        subscribe(CHANNEL_COMMANDS, handler);
    }

    // ===== TEMPLATES =====

    /** Sauvegarde la liste des templates de serveurs au format JSON. TTL : 24h. */
    public void saveTemplatesJson(String json) {
        Objects.requireNonNull(json, "json");
        redis().set(KEY_PREFIX + "templates", json, SetParams.setParams().ex(86400L));
    }

    /** Récupère la liste des templates de serveurs au format JSON, ou null si absente. */
    public String getTemplatesJson() {
        return redis().get(KEY_PREFIX + "templates");
    }

    // ===== CLÉ-VALEUR GÉNÉRIQUE =====

    /** Stocke une valeur arbitraire sous une clé préfixée, avec un TTL en secondes. */
    public void set(String key, String value, int ttlSeconds) {
        requireText(key, "key");
        Objects.requireNonNull(value, "value");
        if (ttlSeconds <= 0) throw new IllegalArgumentException("ttlSeconds doit être strictement positif");
        redis().set(KEY_PREFIX + key, value, SetParams.setParams().ex(ttlSeconds));
    }

    /**
     * Crée une réservation seulement si ni celle-ci ni la clé bloquante
     * n'existent. Le contrôle et l'écriture forment une seule opération Redis.
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

    /** Récupère une valeur par sa clé préfixée. Retourne null si la clé n'existe pas ou a expiré. */
    public String get(String key) {
        requireText(key, "key");
        return redis().get(KEY_PREFIX + key);
    }

    /** Supprime une clé préfixée de Redis. */
    public void delete(String key) {
        requireText(key, "key");
        redis().del(KEY_PREFIX + key);
    }

    /** Vérifie si une clé préfixée existe dans Redis. */
    public boolean exists(String key) {
        requireText(key, "key");
        return redis().exists(KEY_PREFIX + key);
    }

    /**
     * Ferme proprement le gestionnaire :
     *   1. Arrête immédiatement tous les threads d'écoute Pub/Sub
     *   2. Ferme le client Redis et libère le pool de connexions
     */
    public synchronized void close() {
        if (!closed.compareAndSet(false, true)) return;
        activeSubscriptions.values().forEach(subscription -> {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException ignored) {
                // La connexion peut déjà être fermée.
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
