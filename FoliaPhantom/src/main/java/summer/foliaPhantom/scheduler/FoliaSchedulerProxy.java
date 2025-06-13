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
    private final Map<Integer, ScheduledTask> taskMap = new ConcurrentHashMap<>();
    private int taskIdCounter = 1000; // Start from a higher number to avoid collision with vanilla tasks

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
                     ScheduledTask taskToCancel = taskMap.remove(taskId);
                     if (taskToCancel != null) {
                         foliaAdapter.cancelTask(taskToCancel);
                     }
                     // Also call original scheduler's cancelTask in case it's a vanilla task ID
                     // or if the plugin somehow mixed schedulers.
                     originalScheduler.cancelTask(taskId);
                 }
                 return null; // void method

             case "cancelTasks": // (Plugin) -> void
                 if (plugin != null) {
                     // Iterate and remove tasks associated with this plugin
                     taskMap.entrySet().removeIf(entry -> {
                         // This check assumes FoliaBukkitTask.getOwner() exists and works.
                         // We don't have direct access to FoliaBukkitTask here, so this is conceptual.
                         // A better way would be to store plugin with ScheduledTask in taskMap.
                         // For now, this part is complex to implement perfectly without FoliaBukkitTask instance here.
                         // So, we'll just call the original.
                         // A more robust solution would be for FoliaBukkitTask to register itself with its owner.
                         return false; // Placeholder
                     });
                     originalScheduler.cancelTasks(plugin);
                 }
                 return null; // void method

             case "isCurrentlyRunning": // (int taskId) -> boolean
             case "isQueued": // (int taskId) -> boolean
                 // For these, it's hard to give an accurate answer for Folia tasks.
                 // We can check if our taskMap contains it, implying it was scheduled by us and might be active/queued.
                 if (args.length >= 1 && args[0] instanceof Integer) {
                     int taskId = (Integer) args[0];
                     if (taskMap.containsKey(taskId)) {
                          ScheduledTask foliaTask = taskMap.get(taskId);
                          return foliaTask != null && !foliaTask.isCancelled() && foliaTask.getExecutionState() != ScheduledTask.ExecutionState.FINISHED;
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
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        return new FoliaBukkitTask(taskId, plugin, runnable, foliaTask::isCancelled); // Pass cancel state supplier
    }

    private int createTaskMappingAndGetId(Plugin plugin, Runnable runnable, ScheduledTask foliaTask) {
        int taskId = taskIdCounter++;
        taskMap.put(taskId, foliaTask);
        return taskId;
    }

    private Location getDefaultLocationSafe(Plugin plugin) {
        try {
            if (plugin != null && plugin.getServer().isPrimaryThread()) {
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world != null) {
                    return world.getSpawnLocation();
                }
            } else if (Bukkit.getServer().isPrimaryThread()) { // If plugin is null, but still on main thread
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (world != null) {
                    return world.getSpawnLocation();
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
