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
import sun.misc.Unsafe;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

/**
 * FoliaPhantom – 任意の外部プラグインを Folia（Paper ThreadedRegions）対応に
 * ラップするゴースト・エンジン
 */
public class FoliaPhantom extends JavaPlugin {

    // Folia Scheduler Proxy 用
    private FoliaSchedulerAdapter schedulerAdapter;
    private Unsafe unsafeInstance;
    private Object serverInstance;
    private Field targetSchedulerField;
    private long schedulerFieldOffset;
    private BukkitScheduler originalScheduler;
    private BukkitScheduler proxiedScheduler;

    // 設定から読み込んだ各プラグインのインスタンスを保持
    private final Map<String, Plugin> wrappedPlugins = new ConcurrentHashMap<>();
    // 各プラグイン用に作成した URLClassLoader を保持（後で close() するため）
    private final Map<String, URLClassLoader> pluginClassLoaders = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        getLogger().info("[Phantom] === FoliaPhantom onLoad ===");
        // config.yml を生成/ロード
        saveDefaultConfig();

        try {
            // まず Folia Scheduler を差し替える
            this.schedulerAdapter = new FoliaSchedulerAdapter(this);
            obtainUnsafe();
            installSchedulerProxy();
            getLogger().info("[Phantom] Folia Scheduler Proxy installed.");

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

                    getLogger().info("[Phantom][" + name + "] Loading target plugin from configuration...");
                    loadWrappedPlugin(name, originalPath, patchedPath, foliaEnabled);
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

        for (Map.Entry<String, Plugin> entry : wrappedPlugins.entrySet()) {
            String name = entry.getKey();
            Plugin plugin = entry.getValue();
            if (plugin != null && !plugin.isEnabled()) {
                getLogger().info("[Phantom][" + name + "] Enabling wrapped plugin...");
                try {
                    getServer().getPluginManager().enablePlugin(plugin);
                    getLogger().info("[Phantom][" + name + "] 有効化完了.");
                } catch (Exception ex) {
                    getLogger().severe("[Phantom][" + name + "] enablePlugin() 例外: " + ex.getMessage());
                    ex.printStackTrace();
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
            }
        }
        getLogger().info("[Phantom] 全てのラップ対象プラグインを有効化しました。");
    }

    @Override
    public void onDisable() {
        getLogger().info("[Phantom] === FoliaPhantom onDisable ===");

        // 全プラグインを無効化
        for (Map.Entry<String, Plugin> entry : wrappedPlugins.entrySet()) {
            String name = entry.getKey();
            Plugin plugin = entry.getValue();
            if (plugin != null && plugin.isEnabled()) {
                getLogger().info("[Phantom][" + name + "] Disabling wrapped plugin...");
                try {
                    getServer().getPluginManager().disablePlugin(plugin);
                    getLogger().info("[Phantom][" + name + "] 無効化完了.");
                } catch (Exception ex) {
                    getLogger().warning("[Phantom][" + name + "] disablePlugin() 例外: " + ex.getMessage());
                }
            }
        }
        wrappedPlugins.clear();

        // Scheduler を元に戻す
        restoreOriginalScheduler();
        getLogger().info("[Phantom] Scheduler を復元しました。");

        // ClassLoader を全てクローズ
        closeAllClassLoaders();
        getLogger().info("[Phantom] 全ての ClassLoader をクローズしました。");
    }

    /**
     * 設定情報をもとに、1つのプラグインを読み込む（ラップ）
     */
    private void loadWrappedPlugin(String name, String originalPath, String patchedPath, boolean foliaEnabled) {
        File dataFolder = getDataFolder();
        File originalJar = new File(dataFolder, originalPath);
        File patchedJar = new File(dataFolder, patchedPath);

        getLogger().info("[Phantom][" + name + "] 参照 JAR: " + originalJar.getAbsolutePath());

        if (!originalJar.getParentFile().exists()) {
            originalJar.getParentFile().mkdirs();
        }
        if (!originalJar.exists()) {
            getLogger().severe("[Phantom][" + name + "] ERROR: 元の JAR ファイルが見つかりません: " + originalJar.getPath());
            return;
        }

        File jarToLoad;
        if (foliaEnabled) {
            if (!patchedJar.getParentFile().exists()) {
                patchedJar.getParentFile().mkdirs();
            }
            // パッチ JAR を再生成する必要があるか確認
            if (!patchedJar.exists() || originalJar.lastModified() > patchedJar.lastModified()) {
                getLogger().info("[Phantom][" + name + "] Folia 対応 JAR を生成中...");
                try {
                    createFoliaSupportedJar(originalJar, patchedJar);
                    getLogger().info("[Phantom][" + name + "] Folia 対応 JAR の生成完了: " + patchedJar.length() + " bytes");
                } catch (Exception e) {
                    getLogger().severe("[Phantom][" + name + "] パッチ JAR の生成に失敗: " + e.getMessage());
                    e.printStackTrace();
                    jarToLoad = originalJar;
                    getLogger().warning("[Phantom][" + name + "] オリジナル JAR を読み込みます。");
                    Plugin loaded = loadPluginJar(name, jarToLoad);
                    if (loaded != null) {
                        wrappedPlugins.put(name, loaded);
                    }
                    return;
                }
                jarToLoad = patchedJar;
            } else {
                jarToLoad = patchedJar;
            }
        } else {
            jarToLoad = originalJar;
        }

        Plugin loaded = loadPluginJar(name, jarToLoad);
        if (loaded != null) {
            wrappedPlugins.put(name, loaded);
            getLogger().info("[Phantom][" + name + "] プラグイン読み込み成功: " + loaded.getName() + " v" + loaded.getDescription().getVersion());
        }
    }

    /**
     * JAR を PluginManager 経由で読み込み、URLClassLoader を保持する
     * → スレッドのコンテキストクラスローダーを差し替えてから loadPlugin() を呼ぶ
     */
    private Plugin loadPluginJar(String name, File jarFile) {
        URLClassLoader loader = null;
        try {
            URL url = jarFile.toURI().toURL();
            loader = new URLClassLoader(new URL[]{url}, getClassLoader());
            pluginClassLoaders.put(name, loader);

            // **重要**: PluginManager.loadPlugin(...) の呼び出し中のみ、スレッドのコンテキストクラスローダーを自作のものに差し替える
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(loader);
            Plugin plugin = getServer().getPluginManager().loadPlugin(jarFile);
            // 戻す
            Thread.currentThread().setContextClassLoader(previous);

            return plugin;
        } catch (Exception e) {
            getLogger().severe("[Phantom][" + name + "] PluginManager.loadPlugin() 例外: " + e.getMessage());
            e.printStackTrace();
            // 失敗時は作成した URLClassLoader を即座にクローズしておく
            if (loader != null) {
                try {
                    loader.close();
                } catch (IOException ignored) {}
                pluginClassLoaders.remove(name);
            }
            return null;
        }
    }

    /**
     * Folia 用 plugin.yml パッチを施し、patchedJar を作成する
     */
    private void createFoliaSupportedJar(File originalJar, File patchedJar) throws Exception {
        try (JarInputStream jis = new JarInputStream(new FileInputStream(originalJar));
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(patchedJar))) {

            JarEntry entry;
            byte[] buffer = new byte[1024];
            boolean pluginYmlFound = false;

            while ((entry = jis.getNextJarEntry()) != null) {
                String entryName = entry.getName();
                if ("plugin.yml".equals(entryName)) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    String originalContent = baos.toString("UTF-8");
                    String modifiedContent = addFoliaSupport(originalContent);
                    JarEntry newEntry = new JarEntry("plugin.yml");
                    jos.putNextEntry(newEntry);
                    jos.write(modifiedContent.getBytes("UTF-8"));
                    jos.closeEntry();
                    pluginYmlFound = true;
                } else {
                    jos.putNextEntry(new JarEntry(entryName));
                    int len;
                    while ((len = jis.read(buffer)) > 0) {
                        jos.write(buffer, 0, len);
                    }
                    jos.closeEntry();
                }
                jis.closeEntry();
            }

            if (!pluginYmlFound) {
                throw new Exception("patch: plugin.yml が見つかりませんでした。");
            }
        }
        // ※createFoliaSupportedJar 内では patchedJar のストリームを閉じているが、
        //   loadPluginJar 側の URLClassLoader によって内部的に JAR が再オープンされるため問題ありません。
    }

    private String addFoliaSupport(String originalContent) {
        StringBuilder sb = new StringBuilder(originalContent);
        if (!originalContent.contains("folia-supported:")) {
            if (!originalContent.endsWith("\n")) sb.append("\n");
            sb.append("folia-supported: true\n");
        } else {
            sb = new StringBuilder(originalContent.replaceAll(
                    "folia-supported:\\s*false", "folia-supported: true"));
        }
        return sb.toString();
    }

    /**
     * Unsafe を取得
     */
    private void obtainUnsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        this.unsafeInstance = (Unsafe) unsafeField.get(null);
    }

    /**
     * Folia Scheduler Proxy をサーバーにインストール
     */
    private void installSchedulerProxy() throws Exception {
        this.originalScheduler = Bukkit.getScheduler();
        this.proxiedScheduler = (BukkitScheduler) Proxy.newProxyInstance(
                BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class},
                new FoliaSchedulerProxy(originalScheduler, schedulerAdapter)
        );
        this.serverInstance = Bukkit.getServer();
        Field field = findSchedulerField(serverInstance.getClass());
        if (field == null) {
            field = findSchedulerField(serverInstance.getClass().getSuperclass());
        }
        if (field == null) {
            throw new Exception("[Phantom] Failed to install scheduler proxy: BukkitScheduler フィールドが見つかりません");
        }
        this.targetSchedulerField = field;
        field.setAccessible(true);
        long offset = unsafeInstance.objectFieldOffset(field);
        this.schedulerFieldOffset = offset;
        unsafeInstance.putObject(serverInstance, offset, proxiedScheduler);
    }

    /**
     * Scheduler を元に戻す
     */
    private void restoreOriginalScheduler() {
        if (unsafeInstance == null || serverInstance == null || targetSchedulerField == null) {
            return;
        }
        try {
            unsafeInstance.putObject(serverInstance, schedulerFieldOffset, originalScheduler);
            getLogger().info("[Phantom] Restored original scheduler");
        } catch (Throwable t) {
            getLogger().warning("[Phantom] Failed to restore original scheduler: " + t.getMessage());
        }
    }

    private Field findSchedulerField(Class<?> clazz) {
        if (clazz == null) return null;
        for (Field f : clazz.getDeclaredFields()) {
            if (BukkitScheduler.class.isAssignableFrom(f.getType())) {
                return f;
            }
        }
        return null;
    }

    /**
     * Folia 用 Scheduler Adapter
     */
    public static class FoliaSchedulerAdapter {
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
            long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
            return (loc != null)
                    ? regionScheduler.runDelayed(plugin, loc, task -> runnable.run(), safeDelay)
                    : asyncScheduler.runDelayed(plugin, task -> runnable.run(),
                    safeDelay * 50L, TimeUnit.MILLISECONDS);
        }

        public ScheduledTask runAsyncRepeatingTask(Runnable runnable, long initialDelayTicks, long periodTicks) {
            Location loc = getDefaultLocation();
            long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
            long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
            return (loc != null)
                    ? regionScheduler.runAtFixedRate(plugin, loc, task -> runnable.run(), safeInitial, safePeriod)
                    : asyncScheduler.runAtFixedRate(plugin, task -> runnable.run(),
                    safeInitial * 50L, safePeriod * 50L, TimeUnit.MILLISECONDS);
        }

        public ScheduledTask runRegionSyncTask(Runnable runnable, Location location, long delayTicks) {
            long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
            return regionScheduler.runDelayed(plugin, location, task -> runnable.run(), safeDelay);
        }

        public ScheduledTask runRegionRepeatingTask(Runnable runnable, Location location,
                                                    long initialDelayTicks, long periodTicks) {
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
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                return (world != null) ? world.getSpawnLocation() : null;
            } catch (Exception e) {
                return null;
            }
        }
    }

    /**
     * Folia Scheduler Proxy 本体
     */
    public static class FoliaSchedulerProxy implements java.lang.reflect.InvocationHandler {
        private final BukkitScheduler originalScheduler;
        private final FoliaSchedulerAdapter foliaAdapter;
        private final Map<Integer, ScheduledTask> taskMap = new ConcurrentHashMap<>();
        private int taskIdCounter = 1000;

        public FoliaSchedulerProxy(BukkitScheduler originalScheduler, FoliaSchedulerAdapter foliaAdapter) {
            this.originalScheduler = originalScheduler;
            this.foliaAdapter = foliaAdapter;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            try {
                switch (methodName) {
                    case "runTaskLater":
                        return handleRunTaskLater(args);
                    case "runTaskTimer":
                        return handleRunTaskTimer(args);
                    case "runTaskTimerAsynchronously":
                        return handleRunTaskTimerAsync(args);
                    case "runTaskAsynchronously":
                        return handleRunTaskAsync(args);
                    case "runTask":
                        return handleRunTask(args);
                    case "cancelTask":
                        return handleCancelTask(args);
                    case "scheduleSyncDelayedTask":
                        return handleScheduleSyncDelayedTask(args);
                    case "scheduleSyncRepeatingTask":
                        return handleScheduleSyncRepeatingTask(args);
                    case "isCurrentlyRunning":
                    case "isQueued":
                        return false;
                    default:
                        return method.invoke(originalScheduler, args);
                }
            } catch (Exception ignored) {
                return getDefaultReturnValue(method.getReturnType());
            }
        }

        private Object handleRunTaskLater(Object[] args) {
            if (args.length >= 3) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                long delay = ((Number) args[2]).longValue();
                Location loc = getDefaultLocation();
                long safeDelay = delay <= 0 ? 1 : delay;
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionSyncTask(task, loc, safeDelay)
                        : foliaAdapter.runAsyncTask(task, safeDelay);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return new FoliaBukkitTask(taskId, plugin, task);
            }
            return null;
        }

        private Object handleRunTaskTimer(Object[] args) {
            if (args.length >= 4) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                long delay = ((Number) args[2]).longValue();
                long period = ((Number) args[3]).longValue();
                long safeDelay = delay <= 0 ? 1 : delay;
                long safePeriod = period <= 0 ? 1 : period;
                Location loc = getDefaultLocation();
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionRepeatingTask(task, loc, safeDelay, safePeriod)
                        : foliaAdapter.runAsyncRepeatingTask(task, safeDelay, safePeriod);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return new FoliaBukkitTask(taskId, plugin, task);
            }
            return null;
        }

        private Object handleRunTaskTimerAsync(Object[] args) {
            if (args.length >= 4) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                long delay = ((Number) args[2]).longValue();
                long period = ((Number) args[3]).longValue();
                Location loc = getDefaultLocation();
                long safeDelay = delay <= 0 ? 1 : delay;
                long safePeriod = period <= 0 ? 1 : period;
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionRepeatingTask(task, loc, safeDelay, safePeriod)
                        : foliaAdapter.runAsyncRepeatingTask(task, safeDelay, safePeriod);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return new FoliaBukkitTask(taskId, plugin, task);
            }
            return null;
        }

        private Object handleRunTaskAsync(Object[] args) {
            if (args.length >= 2) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                Location loc = getDefaultLocation();
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionSyncTask(task, loc, 0)
                        : foliaAdapter.runAsyncTask(task, 0);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return new FoliaBukkitTask(taskId, plugin, task);
            }
            return null;
        }

        private Object handleRunTask(Object[] args) {
            if (args.length >= 2) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                Location loc = getDefaultLocation();
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionSyncTask(task, loc, 0)
                        : foliaAdapter.runAsyncTask(task, 0);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return new FoliaBukkitTask(taskId, plugin, task);
            }
            return null;
        }

        private Object handleScheduleSyncDelayedTask(Object[] args) {
            if (args.length >= 2) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                long delay = 0L;
                if (args.length >= 3 && args[2] instanceof Number) {
                    delay = ((Number) args[2]).longValue();
                }
                Location loc = getDefaultLocation();
                long safeDelay = delay <= 0 ? 1 : delay;
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionSyncTask(task, loc, safeDelay)
                        : foliaAdapter.runAsyncTask(task, safeDelay);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return taskId;
            }
            return 0;
        }

        private Object handleScheduleSyncRepeatingTask(Object[] args) {
            if (args.length >= 4) {
                Plugin plugin = (Plugin) args[0];
                Runnable task = (Runnable) args[1];
                long delay = ((Number) args[2]).longValue();
                long period = ((Number) args[3]).longValue();
                Location loc = getDefaultLocation();
                long safeDelay = delay <= 0 ? 1 : delay;
                long safePeriod = period <= 0 ? 1 : period;
                ScheduledTask foliaTask = (loc != null)
                        ? foliaAdapter.runRegionRepeatingTask(task, loc, safeDelay, safePeriod)
                        : foliaAdapter.runAsyncRepeatingTask(task, safeDelay, safePeriod);
                int taskId = taskIdCounter++;
                taskMap.put(taskId, foliaTask);
                return taskId;
            }
            return 0;
        }

        private Object handleCancelTask(Object[] args) {
            if (args.length >= 1) {
                int taskId = ((Number) args[0]).intValue();
                ScheduledTask foliaTask = taskMap.remove(taskId);
                if (foliaTask != null) {
                    foliaAdapter.cancelTask(foliaTask);
                }
            }
            return null;
        }

        private Location getDefaultLocation() {
            try {
                World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                return (world != null) ? world.getSpawnLocation() : null;
            } catch (Exception e) {
                return null;
            }
        }

        private Object getDefaultReturnValue(Class<?> returnType) {
            if (returnType == int.class || returnType == Integer.class) {
                return -1;
            } else if (returnType == boolean.class || returnType == Boolean.class) {
                return false;
            } else {
                return null;
            }
        }
    }

    /**
     * Folia 用 BukkitTask 実装
     */
    public static class FoliaBukkitTask implements org.bukkit.scheduler.BukkitTask {
        private final int taskId;
        private final Plugin plugin;
        private final Runnable task;
        private boolean cancelled = false;

        public FoliaBukkitTask(int taskId, Plugin plugin, Runnable task) {
            this.taskId = taskId;
            this.plugin = plugin;
            this.task = task;
        }

        @Override
        public int getTaskId() {
            return taskId;
        }

        @Override
        public Plugin getOwner() {
            return plugin;
        }

        @Override
        public boolean isSync() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        public Runnable getTask() {
            return task;
        }
    }

    /**
     * 生成した各 URLClassLoader を閉じる
     */
    private void closeAllClassLoaders() {
        for (Map.Entry<String, URLClassLoader> entry : pluginClassLoaders.entrySet()) {
            try {
                entry.getValue().close();
                getLogger().info("[Phantom][" + entry.getKey() + "] ClassLoader closed.");
            } catch (IOException ignored) {
            }
        }
        pluginClassLoaders.clear();
    }
}

