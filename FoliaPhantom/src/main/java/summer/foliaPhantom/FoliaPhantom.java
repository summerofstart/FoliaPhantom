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
// sun.misc.Unsafe removed
// java.lang.reflect.Field, Method, Proxy removed
// java.net.URL, URLClassLoader removed
// java.util.concurrent.TimeUnit removed
// java.util.jar.* removed
// io.papermc.paper.threadedregions.scheduler.* removed as they are not directly used by FoliaPhantom class
import summer.foliaPhantom.jar.JarPatcher;
import summer.foliaPhantom.plugin.PluginLoader;
import summer.foliaPhantom.plugin.WrappedPlugin;
// summer.foliaPhantom.scheduler.FoliaSchedulerAdapter import is not directly used by FoliaPhantom
// summer.foliaPhantom.scheduler.FoliaSchedulerProxy import is not directly used by FoliaPhantom
import summer.foliaPhantom.scheduler.SchedulerManager;
// FoliaBukkitTask is used by FoliaSchedulerProxy, direct import might not be needed in FoliaPhantom itself

/**
 * FoliaPhantom – 任意の外部プラグインを Folia（Paper ThreadedRegions）対応に
 * ラップするゴースト・エンジン
 */
public class FoliaPhantom extends JavaPlugin {

    private SchedulerManager schedulerManager;

    // 設定から読み込んだ各プラグインのインスタンスを保持
    private final Map<String, WrappedPlugin> wrappedPlugins = new ConcurrentHashMap<>();
    // 各プラグイン用に作成した URLClassLoader を保持（後で close() するため）
    private PluginLoader pluginLoader;

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad ===");
        // config.yml を生成/ロード
        saveDefaultConfig();

        try {
            this.pluginLoader = new PluginLoader(this);
            // まず Folia Scheduler を差し替える
            this.schedulerManager = new SchedulerManager(this);
            if (!this.schedulerManager.installProxy()) {
                getLogger().severe("[Phantom] Critical error: Failed to install scheduler proxy. FoliaPhantom will not function correctly.");
                // Optionally disable the plugin itself if scheduler is critical
                // getServer().getPluginManager().disablePlugin(this);
                // return; // if inside onLoad and further loading depends on it
            } else {
                getLogger().info("[Phantom] SchedulerManager initialized and proxy installed.");
            }

            // config.yml の wrapped-plugins セクションを読み込む
            List<Map<?, ?>> wrappedList = getConfig().getMapList("wrapped-plugins");
            if (wrappedList == null || wrappedList.isEmpty()) {
                getLogger().warning("[Phantom] config.yml に wrapped-plugins が見つかりません。ラップ対象がありません。");
            } else {
                for (Map<?, ?> rawEntry : wrappedList) {
                    if (rawEntry == null) continue;
                    String name = (rawEntry.get("name") instanceof String)
                            ? (String) rawEntry.get("name")
                            : "<Unknown>";
                    String originalPath = (rawEntry.get("original-jar-path") instanceof String)
                            ? (String) rawEntry.get("original-jar-path")
                            : "";
                    String patchedPath = (rawEntry.get("patched-jar-path") instanceof String)
                            ? (String) rawEntry.get("patched-jar-path")
                            : originalPath;
                    Boolean foliaEnabled = (rawEntry.get("folia-enabled") instanceof Boolean)
                            ? (Boolean) rawEntry.get("folia-enabled")
                            : Boolean.TRUE;

                    // PluginConfig was introduced in a previous step, assuming it's still relevant here.
                    // If loadWrappedPlugin was removed and PluginConfig is not used elsewhere in onLoad,
                    // this part might need adjustment based on where PluginConfig is now instantiated.
                    // For this specific step, we focus on replacing loadWrappedPlugin with WrappedPlugin instantiation.
                    summer.foliaPhantom.config.PluginConfig config = new summer.foliaPhantom.config.PluginConfig(name, originalPath, patchedPath, foliaEnabled);

                    getLogger().info("[Phantom][" + config.name() + "] Processing plugin configuration...");
                    WrappedPlugin wrappedPlugin = new WrappedPlugin(config, pluginLoader, getDataFolder(), getLogger());
                    if (wrappedPlugin.getBukkitPlugin() != null) {
                        wrappedPlugins.put(config.name(), wrappedPlugin);
                    } else {
                        // If plugin failed to load, WrappedPlugin constructor would have logged it.
                        // PluginLoader also attempts to clean up its classloader for this plugin on failure.
                        // No need to add to wrappedPlugins map.
                        getLogger().severe("[Phantom][" + config.name() + "] Was not added to the list of active wrapped plugins due to loading failure.");
                    }
                }
            }

            getLogger().info("[Phantom] onLoad completed.");
        } catch (Exception e) {
            getLogger().severe("[Phantom] FoliaPhantom onLoad 中に例外: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("[Phantom] === FoliaPhantom onEnable ===");

        if (wrappedPlugins.isEmpty()) {
            getLogger().warning("[Phantom] ラップ対象プラグインが存在しません。FoliaPhantom を無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        for (Map.Entry<String, WrappedPlugin> entry : wrappedPlugins.entrySet()) {
            String name = entry.getKey();
            WrappedPlugin wrappedPlugin = entry.getValue();
            Plugin plugin = wrappedPlugin.getBukkitPlugin(); // Get the actual Bukkit Plugin
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info("[Phantom][" + name + "] Enabling wrapped plugin...");
                try {
                    getServer().getPluginManager().enablePlugin(plugin);
                    getLogger().info("[Phantom][" + name + "] 有効化完了.");
                } catch (Throwable ex) { // Catch Throwable to be safe with severe errors
                    getLogger().severe("[Phantom][" + name + "] Exception during enablePlugin() for " + name + ": " + ex.getMessage());
                    ex.printStackTrace();
                    // Do not disable FoliaPhantom itself. Log and continue.
                    // The problematic plugin will likely be left disabled by the server.
                }
            }
        }
        getLogger().info("[Phantom] 全てのラップ対象プラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");

        // 全プラグインを無効化
        for (Map.Entry<String, WrappedPlugin> entry : wrappedPlugins.entrySet()) {
            String name = entry.getKey();
            WrappedPlugin wrappedPlugin = entry.getValue();
            Plugin plugin = wrappedPlugin.getBukkitPlugin();
            if (plugin != null && plugin.isEnabled()) {
                getLogger().info("[Phantom][" + name + "] Disabling wrapped plugin...");
                try {
                    getServer().getPluginManager().disablePlugin(plugin);
                    getLogger().info("[Phantom][" + name + "] 無効化完了.");
                } catch (Exception ex) {
                    getLogger().warning("[Phantom][" + name + "] disablePlugin() 例外: " + ex.getMessage());
                }
            }
            // Optional: if WrappedPlugin had specific cleanup beyond classloader (which PluginLoader handles)
            // wrappedPlugin.unload(); // PluginLoader.closeClassLoader is called by WrappedPlugin.unload()
        }
        // The call to pluginLoader.closeAllClassLoaders() remains in onDisable to ensure all are closed.
        // Individual wrappedPlugin.unload() could be called if we want to close classloaders one by one
        // before the global closeAll. For now, global closeAll is likely sufficient.
        wrappedPlugins.clear();

        // Scheduler を元に戻す
        if (this.schedulerManager != null) {
            this.schedulerManager.restoreOriginalScheduler();
        }
        getLogger().info("[Phantom] Scheduler を復元しました。");

        // ClassLoader を全てクローズ
        pluginLoader.closeAllClassLoaders();
        getLogger().info("[Phantom] 全ての ClassLoader をクローズしました。");
    }

    /**
     * 生成した各 URLClassLoader を閉じる
     */
}

