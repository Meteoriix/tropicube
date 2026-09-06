package fr.tropicube.core.network;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Atomic ownership of private input: only one chat event may consume a prompt. */
public final class PrivateInputRegistry<T> {
    private final ConcurrentHashMap<UUID, T> pending = new ConcurrentHashMap<>();

    public void put(UUID player, T value) { pending.put(player, value); }
    public T get(UUID player) { return pending.get(player); }
    public T take(UUID player) { return pending.remove(player); }
    public boolean remove(UUID player, T expected) { return pending.remove(player, expected); }
    public void clear() { pending.clear(); }
}
