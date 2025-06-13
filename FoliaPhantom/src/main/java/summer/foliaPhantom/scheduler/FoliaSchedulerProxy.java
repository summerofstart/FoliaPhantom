package summer.foliaPhantom.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// Assuming FoliaBukkitTask will be in the same package
import summer.foliaPhantom.scheduler.FoliaBukkitTask;

public class FoliaSchedulerProxy implements InvocationHandler {
    private final BukkitScheduler originalScheduler;
    private final FoliaSchedulerAdapter foliaAdapter;
    // private final Map<Integer, ScheduledTask> taskMap = new ConcurrentHashMap<>();
    private final Map<Integer, TaskEntry> taskMap = new ConcurrentHashMap<>();
    private int taskIdCounter = 1000; // Start from a higher number to avoid collision with vanilla tasks

    // Inner class to store plugin owner with the task
    private static class TaskEntry {
        final Plugin owner;
        final ScheduledTask foliaTask;

        TaskEntry(Plugin owner, ScheduledTask foliaTask) {
            this.owner = owner;
            this.foliaTask = foliaTask;
        }
    }

    public FoliaSchedulerProxy(BukkitScheduler originalScheduler, FoliaSchedulerAdapter foliaAdapter) {
        this.originalScheduler = originalScheduler;
        this.foliaAdapter = foliaAdapter;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        // Simplified: only intercept methods we are actively proxying to Folia's schedulers.
        // Delegate all other BukkitScheduler methods to the original scheduler.
        // This includes isCurrentlyRunning, isQueued, cancelTasks, getPendingTasks etc.
        // BukkitTask returning methods are the primary ones to intercept.

        // For methods returning BukkitTask (or int for older scheduleSyncXX methods)
        // we create a FoliaBukkitTask and store the Folia ScheduledTask.
        // For cancelTask(int), we look up our stored ScheduledTask and cancel it.

        Plugin plugin = (args != null && args.length > 0 && args[0] instanceof Plugin) ? (Plugin) args[0] : null;
        Runnable taskRunnable = null;
        if (args != null) {
             for (Object arg : args) {
                 if (arg instanceof Runnable) {
                     taskRunnable = (Runnable) arg;
                     break;
                 }
             }
        }

        // Fallback to a default location if one isn't easily derivable or relevant
        Location defaultLoc = getDefaultLocationSafe(plugin);


        switch (methodName) {
             // Methods returning BukkitTask
             case "runTask": // (plugin, task) -> BukkitTask
                 if (plugin != null && taskRunnable != null) {
                     ScheduledTask foliaTask = foliaAdapter.runRegionSyncTask(taskRunnable, defaultLoc);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskAsynchronously": // (plugin, task) -> BukkitTask
                  if (plugin != null && taskRunnable != null) {
                     ScheduledTask foliaTask = foliaAdapter.runAsyncTask(taskRunnable, 0);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskLater": // (plugin, task, delay) -> BukkitTask
                 if (plugin != null && taskRunnable != null && args.length >= 3) {
                     long delay = ((Number) args[2]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(taskRunnable, defaultLoc, delay);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskLaterAsynchronously": // (plugin, task, delay) -> BukkitTask
                  if (plugin != null && taskRunnable != null && args.length >= 3) {
                     long delay = ((Number) args[2]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runAsyncTask(taskRunnable, delay);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskTimer": // (plugin, task, delay, period) -> BukkitTask
                 if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(taskRunnable, defaultLoc, delay, period);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskTimerAsynchronously": // (plugin, task, delay, period) -> BukkitTask
                 if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runAsyncRepeatingTask(taskRunnable, delay, period);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;

             // Methods returning int (task ID) for older plugins
             case "scheduleSyncDelayedTask": // (plugin, task, delay) -> int
                 if (plugin != null && taskRunnable != null && args.length >= 2) { // delay is optional in some BukkitScheduler variants
                     long delay = (args.length >= 3) ? ((Number) args[2]).longValue() : 0L;
                     ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(taskRunnable, defaultLoc, delay);
                     return createTaskMappingAndGetId(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "scheduleSyncRepeatingTask": // (plugin, task, delay, period) -> int
                  if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(taskRunnable, defaultLoc, delay, period);
                     return createTaskMappingAndGetId(plugin, taskRunnable, foliaTask);
                 }
                 break;
             // scheduleAsyncDelayedTask and scheduleAsyncRepeatingTask also exist but are deprecated
             // and less common. For simplicity, we'll rely on runTaskLaterAsynchronously/runTaskTimerAsynchronously.

             case "cancelTask": // (int) -> void
                 if (args.length >= 1 && args[0] instanceof Integer) {
                     int taskId = (Integer) args[0];
                     TaskEntry entry = taskMap.remove(taskId);
                     if (entry != null && entry.foliaTask != null) {
                         foliaAdapter.cancelTask(entry.foliaTask);
                     }
                     // Also call original scheduler's cancelTask in case it's a vanilla task ID
                     // or if the plugin somehow mixed schedulers.
                     originalScheduler.cancelTask(taskId);
                 }
                 return null; // void method

            case "cancelTasks": // (Plugin) -> void
                if (args.length >= 1 && args[0] instanceof Plugin) {
                    Plugin targetPlugin = (Plugin) args[0];
                    if (targetPlugin != null) { // Ensure targetPlugin is not null
                        // Use an iterator to safely remove from ConcurrentHashMap while iterating its values
                        // A simpler approach for ConcurrentHashMap is to iterate keys and remove if matches
                        for (Integer taskId : taskMap.keySet()) {
                            TaskEntry entry = taskMap.get(taskId); // Check entry still exists
                            // Check entry.owner is not null before calling equals
                            if (entry != null && entry.owner != null && entry.owner.equals(targetPlugin)) {
                                if (entry.foliaTask != null) { // Check if foliaTask itself is not null
                                    foliaAdapter.cancelTask(entry.foliaTask);
                                }
                                taskMap.remove(taskId); // Remove after cancellation attempt
                            }
                        }
                        // Also call original scheduler's method
                        originalScheduler.cancelTasks(targetPlugin);
                    }
                }
                return null; // void method

             case "isCurrentlyRunning": // (int taskId) -> boolean
             case "isQueued": // (int taskId) -> boolean
                 // For these, it's hard to give an accurate answer for Folia tasks.
                 // We can check if our taskMap contains it, implying it was scheduled by us and might be active/queued.
                 if (args.length >= 1 && args[0] instanceof Integer) {
                     int taskId = (Integer) args[0];
                     TaskEntry entry = taskMap.get(taskId);
                     if (entry != null && entry.foliaTask != null) {
                          return !entry.foliaTask.isCancelled() && entry.foliaTask.getExecutionState() != ScheduledTask.ExecutionState.FINISHED;
                     }
                 }
                 // Fall through to original scheduler for tasks not in our map
                 break; // Will be handled by method.invoke outside switch

             // Potentially other methods to consider: getPendingTasks, callSyncMethod
         }

        // If not handled above, invoke on original scheduler
        return method.invoke(originalScheduler, args);
    }

    private FoliaBukkitTask createTaskMapping(Plugin plugin, Runnable runnable, ScheduledTask foliaTask) {
        if (foliaTask == null) {
            // Log warning - use plugin's logger if plugin is not null, otherwise Bukkit's logger
            String pluginName = (plugin != null && plugin.getName() != null) ? plugin.getName() : "Unknown";
            java.util.logging.Logger logger = (plugin != null) ? plugin.getLogger() : Bukkit.getLogger();
            logger.warning("[PhantomScheduler] Folia ScheduledTask was null for a task from plugin " + pluginName + ". Returning a pre-cancelled BukkitTask.");

            int preCancelledTaskId = taskIdCounter++;
            // Runnable can be null if it wasn't extracted properly, though it should ideally always be present
            // for task creation. If it's null, the FoliaBukkitTask will just have a null runnable.
            return new FoliaBukkitTask(preCancelledTaskId, plugin, runnable, true); // true for isPreCancelled
        } else {
            int taskId = taskIdCounter++;
            taskMap.put(taskId, new TaskEntry(plugin, foliaTask));
            return new FoliaBukkitTask(taskId, plugin, runnable, foliaTask::isCancelled); // Pass cancel state supplier
        }
    }

    private int createTaskMappingAndGetId(Plugin plugin, Runnable runnable, ScheduledTask foliaTask) {
        if (foliaTask == null) {
            String pluginName = (plugin != null && plugin.getName() != null) ? plugin.getName() : "Unknown";
            java.util.logging.Logger logger = (plugin != null) ? plugin.getLogger() : Bukkit.getLogger();
            logger.warning("[PhantomScheduler] Folia ScheduledTask was null for an ID-based task from plugin " + pluginName + ". Returning a new ID for a pre-cancelled task.");

            int preCancelledTaskId = taskIdCounter++;
            // Create a FoliaBukkitTask instance even if just for ID, to maintain consistency,
            // though it won't be directly returned or stored in taskMap for cancellation via this ID by proxy.
            // The implications of cancelling this ID would need careful thought if it were possible.
            // For now, it's mainly to satisfy the need for an ID.
            // We don't store pre-cancelled tasks in the taskMap as there's no actual Folia task to manage.
            new FoliaBukkitTask(preCancelledTaskId, plugin, runnable, true); // true for isPreCancelled
            return preCancelledTaskId;
        } else {
            int taskId = taskIdCounter++;
            taskMap.put(taskId, new TaskEntry(plugin, foliaTask));
            return taskId;
        }
    }

    private Location getDefaultLocationSafe(Plugin plugin) {
        // Note: Logging here uses Bukkit.getLogger() as we might not have a plugin instance,
        // or the plugin's logger might not be initialized if this is called very early.
        // If plugin is non-null, its logger could be used: plugin.getLogger().warning(...)
        try {
            if (plugin != null && plugin.getServer() == null) {
                Bukkit.getLogger().warning("[PhantomProxy] plugin.getServer() is null for plugin " + plugin.getName() + ". Unable to get default location safely.");
                return null;
            }

            // Prefer using plugin's server instance if plugin is available, otherwise Bukkit.getServer()
            // This check is mainly for Bukkit.getServer().isPrimaryThread()
            boolean onPrimaryThread = (plugin != null) ? plugin.getServer().isPrimaryThread() : Bukkit.getServer().isPrimaryThread();

            if (!onPrimaryThread) {
                 String pluginName = (plugin != null) ? plugin.getName() : "Unknown";
                 Bukkit.getLogger().warning("[PhantomProxy] getDefaultLocationSafe called off main thread for plugin " + pluginName + ". This is not safe. Returning null.");
                return null;
            }

            if (Bukkit.getWorlds().isEmpty()) {
                String pluginName = (plugin != null) ? plugin.getName() : "Unknown";
                Bukkit.getLogger().warning("[PhantomProxy] No worlds are loaded. Cannot get default location for plugin " + pluginName + ".");
                return null;
            }

            World world = Bukkit.getWorlds().get(0);
            if (world == null) {
                String pluginName = (plugin != null) ? plugin.getName() : "Unknown";
                Bukkit.getLogger().warning("[PhantomProxy] Primary world (index 0) is null. Cannot get default location for plugin " + pluginName + ".");
                return null;
            }

            Location spawnLocation = world.getSpawnLocation();
            if (spawnLocation == null) {
                String pluginName = (plugin != null) ? plugin.getName() : "Unknown";
                Bukkit.getLogger().warning("[PhantomProxy] Primary world's spawn location is null for plugin " + pluginName + ". Cannot get default location.");
                return null;
            }
            return spawnLocation;
        } catch (Exception e) {
            String pluginName = (plugin != null) ? plugin.getName() : "Unknown";
            Bukkit.getLogger().log(java.util.logging.Level.WARNING, "[PhantomProxy] Error getting default safe location for plugin " + pluginName, e);
            return null;
        }
    }
}
