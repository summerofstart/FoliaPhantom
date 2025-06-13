package summer.foliaPhantom.plugin;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin; // For getLogger, or pass Logger instance

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PluginLoader {
    private final PluginManager pluginManager;
    private final Logger logger;
    private final File dataFolder; // To resolve relative paths for JARs if needed, though PluginConfig has full paths

    // Each loaded plugin will have its own classloader
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    public PluginLoader(JavaPlugin hostingPlugin) {
        this.pluginManager = hostingPlugin.getServer().getPluginManager();
        this.logger = hostingPlugin.getLogger();
        this.dataFolder = hostingPlugin.getDataFolder(); // Useful for context if ever needed
    }

    /**
     * Loads a plugin JAR using a dedicated URLClassLoader.
     * The ClassLoader is stored for later cleanup.
     */
    public Plugin loadPlugin(String pluginName, File jarFile) {
        if (!jarFile.exists()) {
            logger.severe("[Phantom][" + pluginName + "] JAR file not found for loading: " + jarFile.getAbsolutePath());
            return null;
        }

        URLClassLoader loader = null;
        try {
            URL url = jarFile.toURI().toURL();
            // Parent classloader should be the FoliaPhantom's classloader to allow access to Bukkit/Paper APIs
            loader = new URLClassLoader(new URL[]{url}, getClass().getClassLoader());
            pluginClassLoaders.put(pluginName, loader);

            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            Plugin plugin = pluginManager.loadPlugin(jarFile);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);

            if (plugin != null) {
                logger.info("[Phantom][" + pluginName + "] Plugin loaded successfully: " + plugin.getName() + " v" + plugin.getDescription().getVersion());
            } else {
                logger.severe("[Phantom][" + pluginName + "] Failed to load plugin from JAR: " + jarFile.getName());
                // ClassLoader is no longer closed here immediately.
                // It will be closed on FoliaPhantom disable or explicit unload.
            }
            return plugin;
        } catch (Exception e) {
            logger.severe("[Phantom][" + pluginName + "] Exception during PluginManager.loadPlugin(): " + e.getMessage());
            e.printStackTrace();
            // ClassLoader is no longer closed here immediately.
            // It will be closed on FoliaPhantom disable or explicit unload.
            return null;
        }
    }

    /**
     * Closes and removes the ClassLoader for a specific plugin.
     */
    public void closeClassLoader(String pluginName) {
        URLClassLoader loader = pluginClassLoaders.remove(pluginName);
        if (loader != null) {
            try {
                loader.close();
                logger.info("[Phantom][" + pluginName + "] ClassLoader closed.");
            } catch (IOException e) {
                logger.warning("[Phantom][" + pluginName + "] Error closing ClassLoader: " + e.getMessage());
            }
        }
    }

    /**
     * Closes all managed ClassLoaders.
     */
    public void closeAllClassLoaders() {
        for (String pluginName : pluginClassLoaders.keySet()) {
            // Create a copy of keys to avoid ConcurrentModificationException if closeClassLoader modifies the map
            closeClassLoader(new String(pluginName));
        }
        // Ensure map is clear, though closeClassLoader should remove entries
        pluginClassLoaders.clear();
        logger.info("[Phantom] All managed plugin ClassLoaders have been requested to close.");
    }

    public Map<String, URLClassLoader> getPluginClassLoaders() {
         return pluginClassLoaders;
    }
}
