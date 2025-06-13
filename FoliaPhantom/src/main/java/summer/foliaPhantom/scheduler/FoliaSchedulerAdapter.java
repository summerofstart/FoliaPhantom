package summer.foliaPhantom.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class FoliaSchedulerAdapter {
    private final Plugin plugin;
    private final AsyncScheduler asyncScheduler;
    private final RegionScheduler regionScheduler;

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.asyncScheduler = plugin.getServer().getAsyncScheduler();
        this.regionScheduler = plugin.getServer().getRegionScheduler();
    }

    public ScheduledTask runAsyncTask(Runnable runnable, long delayTicks) {
        Location loc = getDefaultLocation();
        long safeDelay = delayTicks <= 0 ? 1 : delayTicks; // Ensure positive delay for some schedulers
        return (loc != null && plugin.getServer().isPrimaryThread()) // Only use region scheduler if called from main thread initially for default loc
                ? regionScheduler.runDelayed(plugin, loc, task -> runnable.run(), safeDelay)
                : asyncScheduler.runDelayed(plugin, task -> runnable.run(),
                safeDelay * 50L, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runAsyncRepeatingTask(Runnable runnable, long initialDelayTicks, long periodTicks) {
        Location loc = getDefaultLocation();
        long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
        long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
        return (loc != null && plugin.getServer().isPrimaryThread())
                ? regionScheduler.runAtFixedRate(plugin, loc, task -> runnable.run(), safeInitial, safePeriod)
                : asyncScheduler.runAtFixedRate(plugin, task -> runnable.run(),
                safeInitial * 50L, safePeriod * 50L, TimeUnit.MILLISECONDS);
    }

    public ScheduledTask runRegionSyncTask(Runnable runnable, Location location) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().severe("[PhantomShed] Task for plugin " + plugin.getName() + " requires a valid location for runRegionSyncTask, but location was invalid. Task not scheduled.");
            return null;
        }
        return regionScheduler.run(plugin, location, task -> runnable.run());
    }

    public ScheduledTask runRegionDelayedTask(Runnable runnable, Location location, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().severe("[PhantomShed] Task for plugin " + plugin.getName() + " requires a valid location for runRegionDelayedTask, but location was invalid. Task not scheduled.");
            return null;
        }
        long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
        return regionScheduler.runDelayed(plugin, location, task -> runnable.run(), safeDelay);
    }

    public ScheduledTask runRegionRepeatingTask(Runnable runnable, Location location,
                                                long initialDelayTicks, long periodTicks) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().severe("[PhantomShed] Task for plugin " + plugin.getName() + " requires a valid location for runRegionRepeatingTask, but location was invalid. Task not scheduled.");
            return null;
        }
        long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
        long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
        return regionScheduler.runAtFixedRate(plugin, location, task -> runnable.run(),
                safeInitial, safePeriod);
    }

    public void cancelTask(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private Location getDefaultLocation() {
        try {
            // Check if plugin.getServer() is null
            if (plugin.getServer() == null) {
                plugin.getLogger().warning("[PhantomShed] plugin.getServer() is null, cannot determine primary thread. Unable to get default location.");
                return null;
            }

            // Ensure this runs on the main thread or is otherwise safe
            if (!plugin.getServer().isPrimaryThread()) {
                plugin.getLogger().warning("[PhantomShed] getDefaultLocation called off main thread for plugin " + plugin.getName() + ". This is not safe. Returning null.");
                return null; // Cannot safely get default location off-main-thread
            }

            if (Bukkit.getWorlds().isEmpty()) {
                plugin.getLogger().warning("[PhantomShed] No worlds are loaded. Cannot get default location for plugin " + plugin.getName() + ".");
                return null;
            }

            World world = Bukkit.getWorlds().get(0);
            if (world == null) {
                plugin.getLogger().warning("[PhantomShed] Primary world (index 0) is null. Cannot get default location for plugin " + plugin.getName() + ".");
                return null;
            }

            Location spawnLocation = world.getSpawnLocation();
            if (spawnLocation == null) {
                plugin.getLogger().warning("[PhantomShed] Primary world's spawn location is null for plugin " + plugin.getName() + ". Cannot get default location.");
                return null;
            }
            return spawnLocation;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "[PhantomShed] Error getting default location for plugin " + plugin.getName(), e);
            return null;
        }
    }
}
