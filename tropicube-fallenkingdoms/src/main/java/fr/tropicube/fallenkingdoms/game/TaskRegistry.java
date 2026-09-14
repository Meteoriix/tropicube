package fr.tropicube.fallenkingdoms.game;

import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.Set;

/** Owns every scheduled task of a session so shutdown is idempotent. */
public final class TaskRegistry {
    private final Set<BukkitTask> tasks = new HashSet<>();
    public <T extends BukkitTask> T register(T task) { tasks.add(task); return task; }
    public void forget(BukkitTask task) { tasks.remove(task); }
    public void cancelAll() { tasks.forEach(BukkitTask::cancel); tasks.clear(); }
}
