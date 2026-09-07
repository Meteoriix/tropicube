package fr.tropicube.core.managers;

/** SQL settings captured on the server thread before the database worker starts. */
record DatabaseOptions(int maxPoolSize, int minIdle, int connectionTimeoutMillis, int socketTimeoutMillis,
                       int maxConcurrent, int queueCapacity, int shutdownTimeoutSeconds) {
    DatabaseOptions {
        if (maxPoolSize < 1 || minIdle < 0 || minIdle > maxPoolSize || connectionTimeoutMillis < 250
                || socketTimeoutMillis < 1 || maxConcurrent < 1 || maxConcurrent > maxPoolSize
                || queueCapacity < 1 || shutdownTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Invalid database settings: pool=" + maxPoolSize + ", idle=" + minIdle
                    + ", connection-timeout-millis=" + connectionTimeoutMillis + ", socket-timeout-millis="
                    + socketTimeoutMillis + ", max-concurrent=" + maxConcurrent + ", queue-capacity=" + queueCapacity
                    + ", shutdown-timeout-seconds=" + shutdownTimeoutSeconds);
        }
    }

    static DatabaseOptions read(java.util.function.ToIntBiFunction<String, Integer> read) {
        return new DatabaseOptions(read.applyAsInt("pool.max-size", 10), read.applyAsInt("pool.min-idle", 2),
                read.applyAsInt("connection-timeout-millis", 30000), read.applyAsInt("socket-timeout-millis", 30000),
                read.applyAsInt("max-concurrent", 10), read.applyAsInt("queue-capacity", 100),
                read.applyAsInt("shutdown-timeout-seconds", 10));
    }
}
