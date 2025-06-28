package summer.foliaPhantom;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File; // Retained for File operations like getDataFolder()
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// import io.papermc.paper.threadedregions.scheduler.AsyncScheduler; // Not directly used
// import io.papermc.paper.threadedregions.scheduler.RegionScheduler; // Not directly used
// import io.papermc.paper.threadedregions.scheduler.ScheduledTask; // Not directly used
import org.bukkit.Bukkit;
// import org.bukkit.Location; // Not directly used
// import org.bukkit.World; // Not directly used
// import org.bukkit.plugin.Plugin; // Not directly used
import org.bukkit.plugin.java.JavaPlugin;
// import org.bukkit.scheduler.BukkitScheduler; // Not directly used

// import java.io.File; // Not directly used
// import java.util.List; // Not directly used
// import java.util.Map; // Not directly used
// import java.util.concurrent.ConcurrentHashMap; // Not directly used

// JarPatcher, PluginLoader, WrappedPlugin are no longer used directly by FoliaPhantom class with configless approach
// import summer.foliaPhantom.jar.JarPatcher;
// import summer.foliaPhantom.plugin.PluginLoader;
// import summer.foliaPhantom.plugin.WrappedPlugin;
// import summer.foliaPhantom.config.PluginConfig; // No longer used here

import summer.foliaPhantom.scheduler.SchedulerManager;
import summer.foliaPhantom.transformer.BukkitApiTransformer;

/**
 * FoliaPhantom – 任意の外部プラグインを Folia（Paper ThreadedRegions）対応に
 * ラップするゴースト・エンジン (v2.0 - コンフィグレス)
 */
public class FoliaPhantom extends JavaPlugin {

    private static boolean isFoliaServer;
    private SchedulerManager schedulerManager;

    // private final Map<String, WrappedPlugin> wrappedPlugins = new ConcurrentHashMap<>(); // No longer used
    // private PluginLoader pluginLoader; // No longer used

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad (v2.0 Configless) ===");
        isFoliaServer = detectServerType();

        // saveDefaultConfig(); // config.yml is no longer actively used for plugin wrapping logic

        try {
            // this.pluginLoader = new PluginLoader(this); // No longer used

            this.schedulerManager = new SchedulerManager(this);
            if (!this.schedulerManager.installProxy()) {
                getLogger().severe("[Phantom] Critical error: Failed to install scheduler proxy. FoliaPhantom will not function correctly.");
                // Consider disabling the plugin if scheduler proxy is essential and failed
                // getServer().getPluginManager().disablePlugin(this);
                // return;
            } else {
                getLogger().info("[Phantom] SchedulerManager initialized and proxy installed.");
            }

            if (this.schedulerManager.getSchedulerAdapter() != null) {
                BukkitApiTransformer.initialize(this, this.schedulerManager.getSchedulerAdapter());
            } else {
                getLogger().severe("[Phantom] SchedulerAdapter is null or SchedulerManager failed. API transformation cannot be initialized.");
                // getServer().getPluginManager().disablePlugin(this);
                // return;
            }

            // Removed config.yml reading and WrappedPlugin processing loop
            getLogger().info("[Phantom] API transformation initialized. No per-plugin configuration needed.");

            getLogger().info("[Phantom] onLoad completed.");
        } catch (Exception e) {
            getLogger().severe("[Phantom] FoliaPhantom onLoad中に例外: " + e.getMessage());
            e.printStackTrace();
            // Critical failure during onLoad might warrant disabling the plugin
            // getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable (v2.0 Configless) ===");
        // Removed WrappedPlugin enabling logic.
        // FoliaPhantom now operates globally via bytecode transformation.
        // No specific plugins are "managed" or "enabled" by FoliaPhantom itself.
        if (schedulerManager == null || !BukkitApiTransformer.areTransformationsApplied()) {
             getLogger().severe("[Phantom] FoliaPhantom did not initialize correctly during onLoad or bytecode transformations failed.");
             getLogger().warning("[Phantom] FoliaPhantom might not function as expected. Please check previous logs for errors.");
             // Optionally disable if critical components failed
             // getServer().getPluginManager().disablePlugin(this);
             // return;
        }

        getLogger().info("[Phantom] FoliaPhantom is active and applying API transformations globally.");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable (v2.0 Configless) ===");

        // Removed WrappedPlugin disabling logic
        // wrappedPlugins.clear(); // No longer used

        if (this.schedulerManager != null) {
            this.schedulerManager.restoreOriginalScheduler();
            getLogger().info("[Phantom] Original BukkitScheduler restored.");
        } else {
            getLogger().warning("[Phantom] SchedulerManager was not initialized, cannot restore scheduler.");
        }

        // if (pluginLoader != null) { // pluginLoader is removed
        //     pluginLoader.closeAllClassLoaders();
        //     getLogger().info("[Phantom] All ClassLoaders (if any were managed) closed.");
        // }
        getLogger().info("[Phantom] FoliaPhantom has been disabled.");
    }

    /**
     * Detects the server type by checking for the existence of a Folia-specific class.
     * This method is called during onLoad to determine if Folia-specific APIs are available.
     * @return true if a Folia-specific class (io.papermc.paper.threadedregions.RegionizedServer) is found, false otherwise.
     */
    private static boolean detectServerType() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            Bukkit.getLogger().info("[Phantom] Detected Folia server environment.");
            return true;
        } catch (ClassNotFoundException e) {
            Bukkit.getLogger().info("[Phantom] Detected Non-Folia server environment.");
            return false;
        }
    }

    /**
     * Gets the detected server type.
     * @return true if the server is determined to be a Folia server, false otherwise.
     */
    public static boolean isFoliaServer() {
        return isFoliaServer;
    }
}

