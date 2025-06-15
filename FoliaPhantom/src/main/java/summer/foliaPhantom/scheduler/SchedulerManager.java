package summer.foliaPhantom.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import summer.foliaPhantom.FoliaPhantom; // Import FoliaPhantom
import sun.misc.Unsafe; // Required for Unsafe operations

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public class SchedulerManager {
    private final Plugin owningPlugin; // The plugin instance (FoliaPhantom)
    private final Logger logger;
    private FoliaSchedulerAdapter schedulerAdapter; // Renamed from foliaAdapter for clarity

    private Unsafe unsafeInstance;
    private Object serverInstance; // Typically CraftServer or similar
    private Field schedulerFieldInServer; // The BukkitScheduler field within the Server instance
    private long schedulerFieldOffset;
    private BukkitScheduler originalBukkitScheduler;
    private BukkitScheduler proxiedBukkitScheduler;

    public SchedulerManager(Plugin owningPlugin) {
        this.owningPlugin = owningPlugin;
        this.logger = owningPlugin.getLogger();
    }

    /**
     * Initializes and installs the Folia scheduler proxy.
     * @return True if successful, false otherwise.
     */
    public boolean installProxy() {
        try {
            // Determine server type (Folia or Non-Folia) to configure the proxy accordingly.
            boolean isFolia = FoliaPhantom.isFoliaServer();
            logger.info("[Phantom] Installing scheduler proxy for " + (isFolia ? "Folia" : "Non-Folia") + " environment.");

            this.schedulerAdapter = new FoliaSchedulerAdapter(this.owningPlugin);
            obtainUnsafeInstance();

            this.originalBukkitScheduler = Bukkit.getScheduler();
            this.proxiedBukkitScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                    BukkitScheduler.class.getClassLoader(),
                    new Class<?>[]{BukkitScheduler.class},
                    // Pass the detected server type to the FoliaSchedulerProxy.
                    // This allows the proxy to adapt its behavior (e.g., pass-through on Non-Folia).
                    new FoliaSchedulerProxy(this.originalBukkitScheduler, this.schedulerAdapter, isFolia)
            );

            this.serverInstance = Bukkit.getServer(); // Get current server instance

            // Try to find the scheduler field in the server instance's class or its superclasses
            Class<?> currentClass = serverInstance.getClass();
            this.schedulerFieldInServer = null;
            while (currentClass != null && currentClass != Object.class) {
                this.schedulerFieldInServer = findSchedulerFieldInClass(currentClass);
                if (this.schedulerFieldInServer != null) break;
                currentClass = currentClass.getSuperclass();
            }

            if (this.schedulerFieldInServer == null) {
                throw new NoSuchFieldException("Failed to find BukkitScheduler field in Server instance or its superclasses.");
            }

            this.schedulerFieldInServer.setAccessible(true);
            this.schedulerFieldOffset = unsafeInstance.objectFieldOffset(this.schedulerFieldInServer);

            // Perform the swap
            unsafeInstance.putObject(serverInstance, this.schedulerFieldOffset, this.proxiedBukkitScheduler);
            logger.info("[Phantom] Folia Scheduler Proxy successfully installed.");
            return true;
        } catch (Exception e) {
            logger.severe("[Phantom] Failed to install Folia Scheduler Proxy: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Restores the original BukkitScheduler.
     */
    public void restoreOriginalScheduler() {
        if (unsafeInstance == null || serverInstance == null || schedulerFieldInServer == null || originalBukkitScheduler == null) {
            logger.warning("[Phantom] SchedulerManager not fully initialized or already restored. Cannot restore scheduler.");
            return;
        }
        try {
            // Check if the current scheduler is our proxy before restoring
            Object currentScheduler = unsafeInstance.getObject(serverInstance, schedulerFieldOffset);
            if (currentScheduler == proxiedBukkitScheduler) {
                unsafeInstance.putObject(serverInstance, schedulerFieldOffset, originalBukkitScheduler);
                logger.info("[Phantom] Original BukkitScheduler restored successfully.");
            } else {
                logger.warning("[Phantom] Current scheduler is not our proxy. Original scheduler not restored to prevent conflicts.");
            }
        } catch (Throwable t) {
            logger.severe("[Phantom] Critical error while restoring original scheduler: " + t.getMessage());
            t.printStackTrace();
        } finally {
            // Clear references
            this.originalBukkitScheduler = null;
            this.proxiedBukkitScheduler = null;
            this.schedulerAdapter = null;
            // unsafeInstance, serverInstance, schedulerFieldInServer, schedulerFieldOffset could be kept if re-installation is possible
            // but for a one-shot install/restore, nulling them out is cleaner.
        }
    }

    private void obtainUnsafeInstance() throws NoSuchFieldException, IllegalAccessException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        this.unsafeInstance = (Unsafe) field.get(null);
    }

    private Field findSchedulerFieldInClass(Class<?> clazz) {
        if (clazz == null) return null;
        for (Field f : clazz.getDeclaredFields()) {
            if (BukkitScheduler.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    // Getter for the adapter if other parts of FoliaPhantom need it (e.g. for direct Folia scheduling)
    // This might not be needed if all scheduling is meant to go through the proxy.
    public FoliaSchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }
}
