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
import java.util.logging.Logger; // Added import
// Assuming FoliaBukkitTask will be in the same package
import summer.foliaPhantom.scheduler.FoliaBukkitTask;

/**
 * A dynamic proxy for {@link BukkitScheduler}.
 * This proxy intercepts scheduler calls. If the server is detected as a Folia server,
 * it adapts these calls to Folia's scheduling system using {@link FoliaSchedulerAdapter}.
 * If the server is not Folia, it passes all calls directly to the original {@link BukkitScheduler},
 * ensuring native behavior and compatibility.
 */
public class FoliaSchedulerProxy implements InvocationHandler {
    private static final Logger LOGGER = Logger.getLogger("FoliaSchedulerProxy"); // Added logger instance
    private final BukkitScheduler originalScheduler;
    private final FoliaSchedulerAdapter foliaAdapter;
    // Flag indicating if the current server environment is Folia-based.
    private final boolean isFoliaServer;
    private final Map<Integer, ScheduledTask> taskMap = new ConcurrentHashMap<>();
    private int taskIdCounter = 1000; // Start from a higher number to avoid collision with vanilla tasks

    // Modified constructor
    public FoliaSchedulerProxy(BukkitScheduler originalScheduler, FoliaSchedulerAdapter foliaAdapter, boolean isFoliaServer) {
        this.originalScheduler = originalScheduler;
        this.foliaAdapter = foliaAdapter;
        this.isFoliaServer = isFoliaServer;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Conditional scheduling logic based on detected server type.
        // If the server is not Folia, all scheduler method calls are passed directly to the
        // original BukkitScheduler. This ensures that on non-Folia platforms (like Spigot or Paper
        // without Folia's threaded regions), tasks are handled by the native scheduler,
        // preventing errors from attempting to use Folia-specific APIs and maintaining
        // standard Bukkit plugin behavior. The taskMap and Folia-specific handling below
        // will be skipped entirely.
        if (!this.isFoliaServer) {
            return method.invoke(originalScheduler, args);
        }

        String methodName = method.getName();
        Plugin plugin = (args != null && args.length > 0 && args[0] instanceof Plugin) ? (Plugin) args[0] : null;
        // Removed generic taskRunnable extraction loop

        // If plugin is not null and the method is a task scheduling one,
        // check if the plugin is enabled. If not, delegate to original scheduler.
        // This check uses the 'plugin' variable extracted above.
        if (plugin != null &&
            (methodName.equals("runTask") ||
             methodName.equals("runTaskAsynchronously") ||
             methodName.equals("runTaskLater") ||
             methodName.equals("runTaskLaterAsynchronously") ||
             methodName.equals("runTaskTimer") ||
             methodName.equals("runTaskTimerAsynchronously") ||
             methodName.equals("scheduleSyncDelayedTask") ||
             methodName.equals("scheduleSyncRepeatingTask")) &&
            !plugin.isEnabled()) {

            // Log this delegation for clarity
            String pluginName = (plugin.getName() != null) ? plugin.getName() : "Unknown Plugin";
            LOGGER.info("[FoliaSchedulerProxy] Plugin " + pluginName +
                        " is not fully enabled (likely in onEnable/onLoad). Delegating scheduler call '" +
                        methodName + "' to original BukkitScheduler.");
            return method.invoke(originalScheduler, args);
        }

        // If plugin is enabled, or method is not one of the above, or plugin is null,
        // proceed with existing Folia handling logic or delegation to originalScheduler at the end.
        Location defaultLoc = getDefaultLocationSafe(plugin);

        switch (methodName) {
            case "runTask": // runTask(Plugin plugin, Runnable task)
                if (plugin != null && args.length >= 2 && args[1] instanceof Runnable) {
                    Runnable task = (Runnable) args[1];
                    ScheduledTask foliaTask = foliaAdapter.runRegionSyncTask(task, defaultLoc);
                    return createTaskMapping(plugin, task, foliaTask, true); // isSync = true
                }
                break;
            case "runTaskAsynchronously": // runTaskAsynchronously(Plugin plugin, Runnable task)
                 if (plugin != null && args.length >= 2 && args[1] instanceof Runnable) {
                    Runnable task = (Runnable) args[1];
                    ScheduledTask foliaTask = foliaAdapter.runAsyncTask(task, 0);
                    return createTaskMapping(plugin, task, foliaTask, false); // isSync = false
                }
                break;
            case "runTaskLater": // runTaskLater(Plugin plugin, Runnable task, long delay)
                if (plugin != null && args.length >= 3 && args[1] instanceof Runnable && args[2] instanceof Number) {
                    Runnable task = (Runnable) args[1];
                    long delay = ((Number) args[2]).longValue();
                    ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(task, defaultLoc, delay);
                    return createTaskMapping(plugin, task, foliaTask, true); // isSync = true
                }
                break;
            case "runTaskLaterAsynchronously": // runTaskLaterAsynchronously(Plugin plugin, Runnable task, long delay)
                 if (plugin != null && args.length >= 3 && args[1] instanceof Runnable && args[2] instanceof Number) {
                    Runnable task = (Runnable) args[1];
                    long delay = ((Number) args[2]).longValue();
                    ScheduledTask foliaTask = foliaAdapter.runAsyncTask(task, delay);
                    return createTaskMapping(plugin, task, foliaTask, false); // isSync = false
                }
                break;
            case "runTaskTimer": // runTaskTimer(Plugin plugin, Runnable task, long delay, long period)
                if (plugin != null && args.length >= 4 && args[1] instanceof Runnable && args[2] instanceof Number && args[3] instanceof Number) {
                    Runnable task = (Runnable) args[1];
                    long delay = ((Number) args[2]).longValue();
                    long period = ((Number) args[3]).longValue();
                    ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(task, defaultLoc, delay, period);
                    return createTaskMapping(plugin, task, foliaTask, true); // isSync = true
                }
                break;
            case "runTaskTimerAsynchronously": // runTaskTimerAsynchronously(Plugin plugin, Runnable task, long delay, long period)
                if (plugin != null && args.length >= 4 && args[1] instanceof Runnable && args[2] instanceof Number && args[3] instanceof Number) {
                    Runnable task = (Runnable) args[1];
                    long delay = ((Number) args[2]).longValue();
                    long period = ((Number) args[3]).longValue();
                    ScheduledTask foliaTask = foliaAdapter.runAsyncRepeatingTask(task, delay, period);
                    return createTaskMapping(plugin, task, foliaTask, false); // isSync = false
                }
                break;
            case "scheduleSyncDelayedTask": // scheduleSyncDelayedTask(Plugin plugin, Runnable task, long delay) or (Plugin plugin, Runnable task)
                if (plugin != null && args.length >= 2 && args[1] instanceof Runnable) {
                    Runnable task = (Runnable) args[1];
                    long delayValue = 0L; // Renamed to avoid conflict with local var 'delay' if method signature changes
                    if (args.length >= 3 && args[2] instanceof Number) {
                        delayValue = ((Number) args[2]).longValue();
                    }
                    ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(task, defaultLoc, delayValue);
                    return createTaskMappingAndGetId(plugin, task, foliaTask, true); // isSync = true
                }
                break;
            case "scheduleSyncRepeatingTask": // scheduleSyncRepeatingTask(Plugin plugin, Runnable task, long delay, long period)
                 if (plugin != null && args.length >= 4 && args[1] instanceof Runnable && args[2] instanceof Number && args[3] instanceof Number) {
                    Runnable task = (Runnable) args[1];
                    long delay = ((Number) args[2]).longValue();
                    long period = ((Number) args[3]).longValue();
                    ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(task, defaultLoc, delay, period);
                    return createTaskMappingAndGetId(plugin, task, foliaTask, true); // isSync = true
                }
                break;
            case "cancelTask": // (int) -> void
                 if (args.length >= 1 && args[0] instanceof Integer) {
                     int taskId = (Integer) args[0];
                     ScheduledTask taskToCancel = taskMap.remove(taskId); // remove it if we are cancelling it
                     if (taskToCancel != null) {
                         foliaAdapter.cancelTask(taskToCancel);
                     } else {
                         // If it wasn't our task, pass it to the original scheduler
                         originalScheduler.cancelTask(taskId);
                     }
                 }
                 return null; // void method
             case "cancelTasks": // (Plugin) -> void
                 if (plugin != null) {
                     // This is a simplified version. A more robust version would iterate taskMap
                     // and cancel tasks associated with 'plugin'.
                     // For now, just pass to originalScheduler and accept it might not cancel Folia tasks correctly.
                     originalScheduler.cancelTasks(plugin);
                 }
                 return null;
             case "isCurrentlyRunning":
             case "isQueued":
                 if (args.length >= 1 && args[0] instanceof Integer) {
                     int taskId = (Integer) args[0];
                     if (taskMap.containsKey(taskId)) { // Check if we know this task
                          ScheduledTask foliaTask = taskMap.get(taskId);
                          // Check if task is active (not cancelled and not finished)
                          return foliaTask != null && !foliaTask.isCancelled() && foliaTask.getExecutionState() != ScheduledTask.ExecutionState.FINISHED;
                     }
                 }
                 // If not in our map, it might be an original scheduler's task. Fall through.
                 break;
         }

        return method.invoke(originalScheduler, args);
    }

    private FoliaBukkitTask createTaskMapping(Plugin plugin, Runnable runnable, ScheduledTask foliaTask, boolean isSync) {
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, runnable, foliaTask::isCancelled, isSync);
    }

    private int createTaskMappingAndGetId(Plugin plugin, Runnable runnable, ScheduledTask foliaTask, boolean isSync) {
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        // Create a FoliaBukkitTask to be consistent, though it's not directly returned by these Bukkit API methods.
        // The isSync status is primarily for BukkitTask objects that are returned and inspected.
        new FoliaBukkitTask(taskId, plugin, runnable, foliaTask::isCancelled, isSync);
        return taskId;
    }

    private Location getDefaultLocationSafe(Plugin pluginContext) {
        // Delegate to the static method in FoliaSchedulerAdapter
        return FoliaSchedulerAdapter.getSafeDefaultLocation(pluginContext, LOGGER);
    }
}
