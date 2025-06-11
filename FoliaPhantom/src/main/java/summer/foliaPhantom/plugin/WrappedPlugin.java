package summer.foliaPhantom.plugin;

import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.config.PluginConfig;
import summer.foliaPhantom.jar.JarPatcher; // Assuming JarPatcher is in this package

import java.io.File;
import java.util.logging.Logger;

public class WrappedPlugin {
    private final PluginConfig config;
    private final PluginLoader pluginLoader;
    private final File dataFolder; // For resolving JAR paths
    private final Logger logger;
    private Plugin bukkitPlugin; // The actual loaded Bukkit/Paper plugin instance

    public WrappedPlugin(PluginConfig config, PluginLoader pluginLoader, File dataFolder, Logger logger) {
        this.config = config;
        this.pluginLoader = pluginLoader;
        this.dataFolder = dataFolder;
        this.logger = logger;
        load();
    }

    private void load() {
        File originalJar = new File(dataFolder, config.originalJarPath());
        File patchedJar = new File(dataFolder, config.patchedJarPath());

        logger.info("[Phantom][" + config.name() + "] Processing plugin. Original JAR: " + originalJar.getAbsolutePath());

        if (!originalJar.getParentFile().exists()) {
            if (!originalJar.getParentFile().mkdirs()) {
                logger.warning("[Phantom][" + config.name() + "] Failed to create directory for original JAR: " + originalJar.getParentFile());
                // Continue if possible, maybe JAR is in root of plugins
            }
        }
        if (!originalJar.exists()) {
            logger.severe("[Phantom][" + config.name() + "] ERROR: Original JAR file not found: " + originalJar.getPath());
            return;
        }

        File jarToLoad;
        if (config.foliaEnabled()) {
            if (!patchedJar.getParentFile().exists()) {
                if (!patchedJar.getParentFile().mkdirs()) {
                     logger.warning("[Phantom][" + config.name() + "] Failed to create directory for patched JAR: " + patchedJar.getParentFile());
                }
            }
            // Check if patched JAR needs (re)generation
            if (!patchedJar.exists() || originalJar.lastModified() > patchedJar.lastModified()) {
                logger.info("[Phantom][" + config.name() + "] Generating Folia-supported JAR...");
                try {
                    JarPatcher.createFoliaSupportedJar(originalJar, patchedJar); // Static call
                    logger.info("[Phantom][" + config.name() + "] Folia-supported JAR generated: " + patchedJar.length() + " bytes");
                    jarToLoad = patchedJar;
                } catch (Exception e) {
                    logger.severe("[Phantom][" + config.name() + "] Failed to create patched JAR: " + e.getMessage());
                    e.printStackTrace();
                    // Fallback to original JAR on patching failure
                    logger.warning("[Phantom][" + config.name() + "] Falling back to original JAR.");
                    jarToLoad = originalJar;
                }
            } else {
                logger.info("[Phantom][" + config.name() + "] Using existing patched JAR: " + patchedJar.getAbsolutePath());
                jarToLoad = patchedJar;
            }
        } else {
            logger.info("[Phantom][" + config.name() + "] Folia patching disabled. Using original JAR.");
            jarToLoad = originalJar;
        }

        this.bukkitPlugin = pluginLoader.loadPlugin(config.name(), jarToLoad);
        if (this.bukkitPlugin == null) {
            logger.severe("[Phantom][" + config.name() + "] Failed to load the plugin JAR: " + jarToLoad.getAbsolutePath());
            // pluginLoader.loadPlugin already logs details and attempts cleanup of its own classloader for this pluginName
        }
    }

    public Plugin getBukkitPlugin() {
        return bukkitPlugin;
    }

    public String getName() {
        return config.name();
    }

    // Optional: method to explicitly unload/cleanup this specific wrapped plugin if needed later
    public void unload() {
        if (bukkitPlugin != null) {
            // Bukkit's PluginManager should handle actual disabling.
            // This class's responsibility is more about the loading phase.
            logger.info("[Phantom][" + getName() + "] Unload requested (Note: actual disabling is via PluginManager).");
        }
        // The PluginLoader is responsible for closing the classloader by plugin name
        pluginLoader.closeClassLoader(getName());
    }
}
