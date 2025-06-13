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
            plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                    " was invalid for a region-specific task (runRegionSyncTask). Falling back to GlobalRegionScheduler.");
            return plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            return this.regionScheduler.run(plugin, location, task -> runnable.run());
        }
    }

    public ScheduledTask runRegionDelayedTask(Runnable runnable, Location location, long delayTicks) {
        long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                    " was invalid for a region-specific task (runRegionDelayedTask). Falling back to GlobalRegionScheduler.");
            return plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), safeDelay);
        } else {
            return this.regionScheduler.runDelayed(plugin, location, task -> runnable.run(), safeDelay);
        }
    }

    public ScheduledTask runRegionRepeatingTask(Runnable runnable, Location location,
                                                long initialDelayTicks, long periodTicks) {
        long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
        long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                    " was invalid for a region-specific task (runRegionRepeatingTask). Falling back to GlobalRegionScheduler.");
            return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), safeInitial, safePeriod);
        } else {
            return this.regionScheduler.runAtFixedRate(plugin, location, task -> runnable.run(),
                    safeInitial, safePeriod);
        }
    }

    public void cancelTask(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private Location getDefaultLocation() {
        try {
            // Ensure this runs on the main thread or is otherwise safe
            if (Bukkit.getServer().isPrimaryThread()) {
                 World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                 return (world != null) ? world.getSpawnLocation() : null;
            }
            return null; // Cannot safely get default location off-main-thread
        } catch (Exception e) {
            // plugin.getLogger().log(java.util.logging.Level.WARNING, "Error getting default location", e);
            return null;
        }
    }
}
