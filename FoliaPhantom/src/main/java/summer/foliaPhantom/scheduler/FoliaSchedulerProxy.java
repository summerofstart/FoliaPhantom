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

public class FoliaSchedulerProxy implements InvocationHandler {
    private static final Logger LOGGER = Logger.getLogger("FoliaSchedulerProxy"); // Added logger instance
    private final BukkitScheduler originalScheduler;
    private final FoliaSchedulerAdapter foliaAdapter;
    private final Map<Integer, ScheduledTask> taskMap = new ConcurrentHashMap<>();
    private int taskIdCounter = 1000; // Start from a higher number to avoid collision with vanilla tasks

    public FoliaSchedulerProxy(BukkitScheduler originalScheduler, FoliaSchedulerAdapter foliaAdapter) {
        this.originalScheduler = originalScheduler;
        this.foliaAdapter = foliaAdapter;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
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

        // If plugin is not null and the method is a task scheduling one,
        // check if the plugin is enabled. If not, delegate to original scheduler.
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
             case "runTask":
                 if (plugin != null && taskRunnable != null) {
                     ScheduledTask foliaTask = foliaAdapter.runRegionSyncTask(taskRunnable, defaultLoc);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskAsynchronously":
                  if (plugin != null && taskRunnable != null) {
                     ScheduledTask foliaTask = foliaAdapter.runAsyncTask(taskRunnable, 0);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskLater":
                 if (plugin != null && taskRunnable != null && args.length >= 3) {
                     long delay = ((Number) args[2]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(taskRunnable, defaultLoc, delay);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskLaterAsynchronously":
                  if (plugin != null && taskRunnable != null && args.length >= 3) {
                     long delay = ((Number) args[2]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runAsyncTask(taskRunnable, delay);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskTimer":
                 if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(taskRunnable, defaultLoc, delay, period);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "runTaskTimerAsynchronously":
                 if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runAsyncRepeatingTask(taskRunnable, delay, period);
                     return createTaskMapping(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "scheduleSyncDelayedTask":
                 if (plugin != null && taskRunnable != null && args.length >= 2) {
                     long delay = (args.length >= 3) ? ((Number) args[2]).longValue() : 0L;
                     ScheduledTask foliaTask = foliaAdapter.runRegionDelayedTask(taskRunnable, defaultLoc, delay);
                     return createTaskMappingAndGetId(plugin, taskRunnable, foliaTask);
                 }
                 break;
             case "scheduleSyncRepeatingTask":
                  if (plugin != null && taskRunnable != null && args.length >= 4) {
                     long delay = ((Number) args[2]).longValue();
                     long period = ((Number) args[3]).longValue();
                     ScheduledTask foliaTask = foliaAdapter.runRegionRepeatingTask(taskRunnable, defaultLoc, delay, period);
                     return createTaskMappingAndGetId(plugin, taskRunnable, foliaTask);
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

    private FoliaBukkitTask createTaskMapping(Plugin plugin, Runnable runnable, ScheduledTask foliaTask) {
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, runnable, foliaTask::isCancelled); // Pass cancel state supplier
    }

    private int createTaskMappingAndGetId(Plugin plugin, Runnable runnable, ScheduledTask foliaTask) {
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        return taskId;
    }

    private Location getDefaultLocationSafe(Plugin pluginContext) {
        // Delegate to the static method in FoliaSchedulerAdapter
        return FoliaSchedulerAdapter.getSafeDefaultLocation(pluginContext, LOGGER);
    }
}
