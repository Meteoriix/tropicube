package fr.tropicube.core.network;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One-shot private prompts intercepted by Core before its public chat pipeline.
 * Prompt creation, cancellation and callbacks run on Paper; only consume is called by chat threads.
 */
public final class PrivateChatInput implements Listener, AutoCloseable {
    private final TropicubeCore plugin;
    private final PrivateInputRegistry<Prompt> prompts = new PrivateInputRegistry<>();
    private final Map<UUID, BukkitTask> timers = new HashMap<>();
    private volatile boolean closed;

    public PrivateChatInput(TropicubeCore plugin) { this.plugin = plugin; }

    /** Closes the menu before registering input, so InventoryOpen cannot cancel a new prompt. */
    public void begin(Player player, String key, int timeoutSeconds, Consumer<String> answer, Runnable cancelled) {
        if (closed) throw new IllegalStateException("Private input is closed");
        if (timeoutSeconds < 1 || timeoutSeconds > 600) throw new IllegalArgumentException("Input timeout must be 1..600 seconds");
        java.util.Objects.requireNonNull(answer, "answer");
        java.util.Objects.requireNonNull(cancelled, "cancelled");
        cancel(player.getUniqueId());
        player.closeInventory();
        UUID id = player.getUniqueId();
        Prompt prompt = new Prompt(key, answer, cancelled);
        prompts.put(id, prompt);
        repeat(player);
        timers.put(id, Bukkit.getScheduler().runTaskLater(plugin, () -> {
            timers.remove(id);
            if (!prompts.remove(id, prompt)) return;
            Player online = Bukkit.getPlayer(id);
            if (online != null) {
                online.sendMessage(plugin.getLanguageManager().getComponent(id, "guild-ui.input-expired"));
                cancelled.run();
            }
        }, timeoutSeconds * 20L));
    }

    /** Returns true even for cancelled input: it must never reach public chat. */
    public boolean consume(UUID id, String text) {
        Prompt prompt = prompts.get(id);
        if (prompt == null) return false;
        // Keep the reservation until the main thread handles it; duplicate events stay private.
        synchronized (prompt) {
            if (prompt.claimed) return true;
            prompt.claimed = true;
        }
        if (closed || !plugin.isEnabled()) return true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!prompts.remove(id, prompt)) return;
            cancelTimer(id);
            if (Bukkit.getPlayer(id) == null) return;
            if (text.trim().equals("!")) prompt.cancelled.run();
            else prompt.answer.accept(text.trim());
        });
        return true;
    }

    /** Re-renders a pending prompt after a language change without restarting its deadline. */
    public void repeat(Player player) {
        Prompt prompt = prompts.get(player.getUniqueId());
        if (prompt != null) player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), prompt.key));
    }

    public boolean isPending(UUID id) { return prompts.get(id) != null; }

    /** Cancels silently when another screen, logout or plugin shutdown supersedes the operation. */
    public void cancel(UUID id) { prompts.take(id); cancelTimer(id); }

    private void cancelTimer(UUID id) {
        BukkitTask timer = timers.remove(id);
        if (timer != null) timer.cancel();
    }

    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId()); }
    @EventHandler public void open(InventoryOpenEvent event) { cancel(event.getPlayer().getUniqueId()); }

    @Override public void close() {
        closed = true;
        prompts.clear();
        timers.values().forEach(BukkitTask::cancel);
        timers.clear();
    }

    private static final class Prompt {
        private final String key;
        private final Consumer<String> answer;
        private final Runnable cancelled;
        private boolean claimed;
        private Prompt(String key, Consumer<String> answer, Runnable cancelled) {
            this.key = key; this.answer = answer; this.cancelled = cancelled;
        }
    }
}
