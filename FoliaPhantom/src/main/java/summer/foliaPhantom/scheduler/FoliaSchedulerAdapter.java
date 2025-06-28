package summer.foliaPhantom.scheduler;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.FoliaPhantom; // FoliaPhantom.isFoliaServer() を使うため

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier; // Chunk.load/unload用

public class FoliaSchedulerAdapter {
    private final Plugin plugin; // このアダプターを使用するプラグインのコンテキスト
    // private final AsyncScheduler asyncScheduler; // Bukkit.getServer().getAsyncScheduler() で取得可能
    // private final RegionScheduler regionScheduler; // Bukkit.getServer().getRegionScheduler() で取得可能
    // GlobalRegionScheduler は Bukkit.getServer().getGlobalRegionScheduler() で取得
    // EntityScheduler は entity.getScheduler() で取得

    public FoliaSchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
        // Schedulers are now fetched on demand to ensure they are correct for the context
    }

    // --- 既存のスケジューラメソッド ---
    public ScheduledTask runAsyncTask(Runnable runnable, long delayTicks) {
        Location loc = getDefaultLocationForPlugin();
        long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
        if (FoliaPhantom.isFoliaServer()) {
            return (loc != null && Bukkit.isPrimaryThread()) // Foliaではメインスレッドからの呼び出し時のみRegionScheduler
                    ? Bukkit.getServer().getRegionScheduler().runDelayed(plugin, loc, task -> runnable.run(), safeDelay)
                    : Bukkit.getServer().getAsyncScheduler().runDelayed(plugin, task -> runnable.run(),
                    safeDelay * 50L, TimeUnit.MILLISECONDS);
        } else {
            // Non-Folia: BukkitScheduler (FoliaSchedulerProxy経由で処理される想定)
            return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, safeDelay);
        }
    }

    public ScheduledTask runAsyncRepeatingTask(Runnable runnable, long initialDelayTicks, long periodTicks) {
        Location loc = getDefaultLocationForPlugin();
        long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
        long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
        if (FoliaPhantom.isFoliaServer()) {
            return (loc != null && Bukkit.isPrimaryThread())
                    ? Bukkit.getServer().getRegionScheduler().runAtFixedRate(plugin, loc, task -> runnable.run(), safeInitial, safePeriod)
                    : Bukkit.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> runnable.run(),
                    safeInitial * 50L, safePeriod * 50L, TimeUnit.MILLISECONDS);
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, safeInitial, safePeriod);
        }
    }

    public ScheduledTask runRegionSyncTask(Runnable runnable, Location location) {
        if (FoliaPhantom.isFoliaServer()) {
            if (location == null || location.getWorld() == null) {
                plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                        " was invalid for a region-specific task (runRegionSyncTask). Falling back to GlobalRegionScheduler.");
                return Bukkit.getServer().getGlobalRegionScheduler().run(plugin, task -> runnable.run());
            } else {
                return Bukkit.getServer().getRegionScheduler().run(plugin, location, task -> runnable.run());
            }
        } else {
            return Bukkit.getScheduler().runTask(plugin, runnable); // BukkitSchedulerはLocationを直接取らない
        }
    }

    public ScheduledTask runRegionDelayedTask(Runnable runnable, Location location, long delayTicks) {
        long safeDelay = delayTicks <= 0 ? 1 : delayTicks;
        if (FoliaPhantom.isFoliaServer()) {
            if (location == null || location.getWorld() == null) {
                plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                        " was invalid for a region-specific task (runRegionDelayedTask). Falling back to GlobalRegionScheduler.");
                return Bukkit.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), safeDelay);
            } else {
                return Bukkit.getServer().getRegionScheduler().runDelayed(plugin, location, task -> runnable.run(), safeDelay);
            }
        } else {
            return Bukkit.getScheduler().runTaskLater(plugin, runnable, safeDelay);
        }
    }

    public ScheduledTask runRegionRepeatingTask(Runnable runnable, Location location,
                                                long initialDelayTicks, long periodTicks) {
        long safeInitial = initialDelayTicks <= 0 ? 1 : initialDelayTicks;
        long safePeriod = periodTicks <= 0 ? 1 : periodTicks;
        if (FoliaPhantom.isFoliaServer()) {
            if (location == null || location.getWorld() == null) {
                plugin.getLogger().warning("[PhantomScheduler] Location for plugin " + plugin.getName() +
                        " was invalid for a region-specific task (runRegionRepeatingTask). Falling back to GlobalRegionScheduler.");
                return Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> runnable.run(), safeInitial, safePeriod);
            } else {
                return Bukkit.getServer().getRegionScheduler().runAtFixedRate(plugin, location, task -> runnable.run(),
                        safeInitial, safePeriod);
            }
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, safeInitial, safePeriod);
        }
    }

    public void cancelTask(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private Location getDefaultLocationForPlugin() {
        return getSafeDefaultLocation(this.plugin, this.plugin.getLogger());
    }

    public static Location getSafeDefaultLocation(Plugin pluginContextForLog, java.util.logging.Logger logger) {
        // 実装は変更なし (前のステップで提示されたものと同じ)
        try {
            if (FoliaPhantom.isFoliaServer() && !Bukkit.isPrimaryThread()) {
                // Folia環境で非同期から呼ばれた場合、デフォルトLocationの取得は危険なことがある
                // 特にワールドのロード状態が保証されないため
                // ただし、このメソッドは主に非同期タスクのフォールバック用なので、警告を出す程度に留める
                String pluginName = (pluginContextForLog != null && pluginContextForLog.getName() != null) ? pluginContextForLog.getName() : "System";
                logger.warning("[" + pluginName + "] getSafeDefaultLocation called off main thread in Folia. Default location might not be reliable.");
                // Foliaではメインスレッド以外からのワールドアクセスが厳しいため、nullを返す方が安全か検討
                // return null;
            }
            // Bukkit.getWorlds()の呼び出し自体がFoliaの非メインスレッドでは推奨されない
            // が、ここではベストエフォートとして試みる
            if (Bukkit.getWorlds().isEmpty()) {
                // logger.severe("No worlds available (Bukkit.getWorlds() is empty). Cannot determine a default location for scheduling.");
                return null; // No worlds, no default location
            }
            World world = Bukkit.getWorlds().get(0);
            return (world != null) ? world.getSpawnLocation() : null;
        } catch (Exception e) {
            String pluginName = (pluginContextForLog != null && pluginContextForLog.getName() != null) ? pluginContextForLog.getName() : "System";
            logger.log(java.util.logging.Level.WARNING, "Exception in getSafeDefaultLocation (plugin context: " + pluginName + "): " + e.getMessage() + ". This may be due to off-main-thread access in Folia.", e);
            return null;
        }
    }

    // --- ここから新しい非同期APIラッパーメソッド ---

    public CompletableFuture<World> createWorldAsync(WorldCreator creator) {
        CompletableFuture<World> future = new CompletableFuture<>();
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                try {
                    World world = Bukkit.createWorld(creator);
                    future.complete(world);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error creating world asynchronously: " + creator.name() + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            // 非Folia環境ではBukkitSchedulerでメインスレッド実行を推奨
            // ただし、createWorld自体が重い処理なので、ここも非同期にすべきか検討の余地あり
            // シンプルにするため、ここでは同期的に実行し、呼び出し側がさらに非同期化することを期待
            try {
                World world = Bukkit.createWorld(creator);
                future.complete(world);
            } catch (Exception e) {
                plugin.getLogger().severe("Error creating world: " + creator.name() + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Boolean> unloadWorldAsync(World world, boolean save) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (world == null) {
            future.completeExceptionally(new IllegalArgumentException("World cannot be null for unloadWorldAsync"));
            return future;
        }
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                try {
                    boolean result = Bukkit.unloadWorld(world, save);
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error unloading world asynchronously: " + world.getName() + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                boolean result = Bukkit.unloadWorld(world, save);
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().severe("Error unloading world: " + world.getName() + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Chunk> getChunkAtAsync(World world, int x, int z) {
        CompletableFuture<Chunk> future = new CompletableFuture<>();
        if (world == null) {
            future.completeExceptionally(new IllegalArgumentException("World cannot be null for getChunkAtAsync"));
            return future;
        }
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getRegionScheduler().execute(plugin, world, x, z, () -> { // chunkX, chunkZ を使用
                try {
                    // このAPIはFoliaでは既にスレッドセーフかもしれないが、明示的にRegionSchedulerで実行
                    Chunk chunk = world.getChunkAt(x, z);
                    future.complete(chunk);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error getting chunk asynchronously: world " + world.getName() + " at " + x + "," + z + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            // 非FoliaでもgetChunkAtは比較的安全だが、メインスレッドでの実行を推奨するプラグインもある
            // ここでは同期的に実行
            try {
                Chunk chunk = world.getChunkAt(x, z);
                future.complete(chunk);
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting chunk: world " + world.getName() + " at " + x + "," + z + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Chunk> getChunkAtAsync(Location location) {
        if (location == null || location.getWorld() == null) {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Location and its world cannot be null for getChunkAtAsync"));
            return future;
        }
        return getChunkAtAsync(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private CompletableFuture<Boolean> operateChunkLoadStateAsync(Chunk chunk, boolean load, BooleanSupplier operation) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (chunk == null) {
            future.completeExceptionally(new IllegalArgumentException("Chunk cannot be null for chunk operation"));
            return future;
        }
        World world = chunk.getWorld();
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getRegionScheduler().execute(plugin, world, chunk.getX(), chunk.getZ(), () -> {
                try {
                    boolean result = operation.getAsBoolean();
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error " + (load ? "loading" : "unloading") + " chunk asynchronously: " + chunk.getX() + "," + chunk.getZ() + " in " + world.getName() + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                boolean result = operation.getAsBoolean();
                future.complete(result);
            } catch (Exception e) {
                 plugin.getLogger().severe("Error " + (load ? "loading" : "unloading") + " chunk: " + chunk.getX() + "," + chunk.getZ() + " in " + world.getName() + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Boolean> loadChunkAsync(Chunk chunk, boolean generate) {
        return operateChunkLoadStateAsync(chunk, true, () -> chunk.load(generate));
    }

    public CompletableFuture<Boolean> loadChunkAsync(Chunk chunk) {
        return operateChunkLoadStateAsync(chunk, true, chunk::load);
    }

    public CompletableFuture<Boolean> unloadChunkAsync(Chunk chunk, boolean save) {
        return operateChunkLoadStateAsync(chunk, false, () -> chunk.unload(save));
    }

    public CompletableFuture<Boolean> unloadChunkAsync(Chunk chunk) {
        return operateChunkLoadStateAsync(chunk, false, chunk::unload);
    }

    public CompletableFuture<Void> setBlockTypeAsync(Block block, Material type, boolean applyPhysics) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (block == null) {
            future.completeExceptionally(new IllegalArgumentException("Block cannot be null for setBlockTypeAsync"));
            return future;
        }
        World world = block.getWorld();
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getRegionScheduler().execute(plugin, block.getLocation(), () -> {
                try {
                    block.setType(type, applyPhysics);
                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error setting block type asynchronously: " + type + " at " + block.getLocation() + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                block.setType(type, applyPhysics);
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().severe("Error setting block type: " + type + " at " + block.getLocation() + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Void> setBlockTypeAsync(Block block, Material type) {
        return setBlockTypeAsync(block, type, true); // Bukkitのデフォルトに合わせる
    }

    public CompletableFuture<Boolean> teleportEntityAsync(Entity entity, Location location, PlayerTeleportEvent.TeleportCause cause) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (entity == null || location == null) {
            future.completeExceptionally(new IllegalArgumentException("Entity and location cannot be null for teleportEntityAsync"));
            return future;
        }
        if (FoliaPhantom.isFoliaServer()) {
            // EntityScheduler を使用
            entity.getScheduler().execute(plugin, () -> {
                try {
                    boolean result = entity.teleport(location, cause);
                    future.complete(result);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error teleporting entity asynchronously: " + entity.getName() + " to " + location + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            }, null, 0L); // No execution task, no delay
        } else {
            try {
                boolean result = entity.teleport(location, cause);
                future.complete(result);
            } catch (Exception e) {
                plugin.getLogger().severe("Error teleporting entity: " + entity.getName() + " to " + location + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<Boolean> teleportEntityAsync(Entity entity, Location location) {
        // PlayerTeleportEvent.TeleportCause.PLUGIN が一般的
        return teleportEntityAsync(entity, location, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    public CompletableFuture<Entity> spawnEntityAsync(Location location, EntityType type) {
        CompletableFuture<Entity> future = new CompletableFuture<>();
        if (location == null || location.getWorld() == null || type == null) {
            future.completeExceptionally(new IllegalArgumentException("Location, world, and entity type cannot be null for spawnEntityAsync"));
            return future;
        }
        World world = location.getWorld();
        if (FoliaPhantom.isFoliaServer()) {
            // エンティティが存在しないため、RegionScheduler を使用
            Bukkit.getServer().getRegionScheduler().execute(plugin, location, () -> {
                try {
                    Entity spawnedEntity = world.spawnEntity(location, type);
                    future.complete(spawnedEntity);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error spawning entity asynchronously: " + type + " at " + location + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                Entity spawnedEntity = world.spawnEntity(location, type);
                future.complete(spawnedEntity);
            } catch (Exception e) {
                plugin.getLogger().severe("Error spawning entity: " + type + " at " + location + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    // World.spawn(Location, Class<T>) のラッパーも同様に作成可能だが、ここでは省略

    public CompletableFuture<Void> saveWorldAsync(World world) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (world == null) {
            future.completeExceptionally(new IllegalArgumentException("World cannot be null for saveWorldAsync"));
            return future;
        }
        if (FoliaPhantom.isFoliaServer()) {
            Bukkit.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                try {
                    world.save();
                    future.complete(null);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error saving world asynchronously: " + world.getName() + " - " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                world.save();
                future.complete(null);
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving world: " + world.getName() + " - " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<List<World>> getWorldsAsync() {
        CompletableFuture<List<World>> future = new CompletableFuture<>();
        if (FoliaPhantom.isFoliaServer()) {
            // Bukkit.getWorlds() はメインスレッドで呼び出すのが安全
            // GlobalRegionScheduler はメインスレッドで実行されるタスクもあるが、executeはそうではない可能性がある
            // Foliaのドキュメントでは、getServer().getWorlds() はメインスレッドでのみ安全とされている
            // そのため、GlobalRegionSchedulerの run (メインスレッド実行を保証) を使うか、
            // Bukkit.getScheduler().callSyncMethod を使うのがより安全
             Bukkit.getServer().getGlobalRegionScheduler().run(plugin, (task) -> { // run ensures it runs on the main thread for the world if possible, or global main thread
                try {
                    future.complete(Bukkit.getWorlds()); // リストのコピーを返すのが望ましいが、Bukkit.getWorlds()の仕様による
                } catch (Exception e) {
                    plugin.getLogger().severe("Error getting worlds asynchronously: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                future.complete(Bukkit.getWorlds());
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting worlds: " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    public CompletableFuture<List<Player>> getPlayersInWorldAsync(World world) {
        CompletableFuture<List<Player>> future = new CompletableFuture<>();
        if (world == null) {
            future.completeExceptionally(new IllegalArgumentException("World cannot be null for getPlayersInWorldAsync"));
            return future;
        }
        if (FoliaPhantom.isFoliaServer()) {
            // ワールド内のプレイヤーリスト取得はRegionSchedulerでそのワールドのTick内で行うのが安全
            Bukkit.getServer().getRegionScheduler().execute(plugin, world, 0, 0, () -> { // チャンク座標はダミーで良い
                try {
                    future.complete(world.getPlayers()); // リストのコピーを返すのが望ましい
                } catch (Exception e) {
                    plugin.getLogger().severe("Error getting players in world " + world.getName() + " asynchronously: " + e.getMessage());
                    future.completeExceptionally(e);
                }
            });
        } else {
            try {
                future.complete(world.getPlayers());
            } catch (Exception e) {
                plugin.getLogger().severe("Error getting players in world " + world.getName() + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        }
        return future;
    }
}
