package summer.foliaPhantom.scheduler;

import org.bukkit.plugin.Plugin;
import java.util.function.BooleanSupplier;

public class FoliaBukkitTask implements org.bukkit.scheduler.BukkitTask {
    private final int taskId;
    private final Plugin plugin;
    private final Runnable taskRunnable; // Keep a reference if needed for re-scheduling or inspection
    private boolean cancelled = false; // Internal cancelled state
    private BooleanSupplier externalCancelState; // Supplier for Folia's task cancelled state, now mutable

    public FoliaBukkitTask(int taskId, Plugin plugin, Runnable taskRunnable, BooleanSupplier externalCancelState) {
        this.taskId = taskId;
        this.plugin = plugin;
        this.taskRunnable = taskRunnable;
        this.externalCancelState = externalCancelState;
    }

    // New constructor for pre-cancelled tasks
    public FoliaBukkitTask(int taskId, Plugin plugin, Runnable taskRunnable, boolean isPreCancelled) {
        this.taskId = taskId;
        this.plugin = plugin;
        this.taskRunnable = taskRunnable;
        this.cancelled = isPreCancelled;
        // If pre-cancelled, externalCancelState can be null or a supplier that returns true.
        // Setting to null is simpler and isCancelled() will handle it.
        this.externalCancelState = (isPreCancelled) ? null : () -> false; // Or some other default if not pre-cancelled
    }

    @Override
    public int getTaskId() {
        return taskId;
    }

    @Override
    public Plugin getOwner() {
        return plugin;
    }

    @Override
    public boolean isSync() {
        // This is tricky. Folia's tasks are not strictly "sync" in the Bukkit sense.
        // Most tasks run via FoliaSchedulerProxy are intended to behave like sync tasks
        // by being region-bound or executed on a global region if no location is specified.
        // However, truly async tasks are also proxied.
        // For compatibility, returning true might be safer for plugins expecting sync behavior.
        return true; // Or determine based on how it was scheduled if possible.
    }

    @Override
    public boolean isCancelled() {
        // If internally marked as cancelled, it's cancelled.
        // Otherwise, if externalCancelState is available, check it.
        // This handles the case where externalCancelState is null (e.g., for pre-cancelled tasks).
        return cancelled || (externalCancelState != null && externalCancelState.getAsBoolean());
    }

    @Override
    public void cancel() {
        // This method is called by plugin code. We mark our task as cancelled.
        // The actual cancellation of the Folia ScheduledTask happens in FoliaSchedulerProxy.cancelTask(id)
        // when Bukkit.getScheduler().cancelTask(id) is called by the plugin.
        this.cancelled = true;
        // We don't directly interact with the Folia ScheduledTask here to avoid issues if the task ID
        // in taskMap (in FoliaSchedulerProxy) was removed or changed.
        // The proxy's cancelTask method is the single point of truth for actual cancellation.
    }

    public Runnable getTaskRunnable() { // Added getter for the runnable
        return taskRunnable;
    }
}
