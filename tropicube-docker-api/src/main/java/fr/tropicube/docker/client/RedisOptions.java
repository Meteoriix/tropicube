package fr.tropicube.docker.client;


/** Validated per-process pool limits and bounded Redis connection/borrow timeouts. */
public record RedisOptions(int maxTotal, int maxIdle, int minIdle, int connectTimeoutMillis,
                           int socketTimeoutMillis, int borrowTimeoutMillis) {
    public RedisOptions {
        if (maxTotal < 1 || minIdle < 0 || maxIdle < minIdle || maxIdle > maxTotal
                || connectTimeoutMillis < 1 || socketTimeoutMillis < 1 || borrowTimeoutMillis < 1) {
            throw new IllegalArgumentException("Invalid redis pool/timeouts: maxTotal=" + maxTotal
                    + ", maxIdle=" + maxIdle + ", minIdle=" + minIdle + ", connect=" + connectTimeoutMillis
                    + ", socket=" + socketTimeoutMillis + ", borrow=" + borrowTimeoutMillis);
        }
    }

    public static RedisOptions defaults() { return new RedisOptions(20, 10, 2, 2000, 2000, 2000); }

    /** Reads optional integer keys with compatibility defaults supplied to the configuration adapter. */
    public static RedisOptions read(java.util.function.ToIntBiFunction<String, Integer> read) {
        return new RedisOptions(read.applyAsInt("pool.max-total", 20), read.applyAsInt("pool.max-idle", 10),
                read.applyAsInt("pool.min-idle", 2), read.applyAsInt("connect-timeout-millis", 2000),
                read.applyAsInt("socket-timeout-millis", 2000), read.applyAsInt("borrow-timeout-millis", 2000));
    }
}
