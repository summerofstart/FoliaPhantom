package summer.foliaPhantom.world;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.FoliaPhantom; // メインプラグインの isFoliaServer() を使うため

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class FoliaWorldProxyInvocationHandler implements InvocationHandler {

    private final World originalWorld;
    private final Plugin plugin; // FoliaPhantom plugin instance for logging and context

    // Sets of method names that require special handling for Folia compatibility.
    // These are typically synchronous Bukkit API methods that need to be mapped to
    // Folia's asynchronous or region-specific counterparts.
    private static final Set<String> CHUNK_METHODS_SYNC_GET = new HashSet<>(Arrays.asList(
            "getChunkAt", // Covers signatures: (int x, int z) and (Location loc)
            "isChunkLoaded", // (int x, int z)
            "isChunkGenerated" // (int x, int z) // Paper API
    ));
    private static final Set<String> CHUNK_METHODS_SYNC_MODIFY = new HashSet<>(Arrays.asList(
            "loadChunk", // (int x, int z), (int x, int z, boolean generate)
            "unloadChunk", // (int x, int z), (int x, int z, boolean save)
            "regenerateChunk" // (int x, int z)
    ));


    public FoliaWorldProxyInvocationHandler(World originalWorld, Plugin plugin) {
        this.originalWorld = originalWorld;
        this.plugin = plugin;
    }

    /**
     * Handles method invocations on the proxied World object.
     * If the server is Folia and the method is a known chunk-related synchronous method,
     * it attempts to delegate to a Folia-compatible equivalent.
     * Otherwise, or if not on Folia, it invokes the method on the original World object.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();

        // Uncomment for debugging:
        // plugin.getLogger().info("[FoliaWorldProxy] Invoked: " + originalWorld.getName() + "." + methodName + "(" + (args != null ? Arrays.toString(args) : "") + ")");

        if (FoliaPhantom.isFoliaServer()) {
            // Only apply special handling if on a Folia server
            try {
                if (CHUNK_METHODS_SYNC_GET.contains(methodName)) {
                    return handleChunkSyncGetMethods(method, args);
                } else if (CHUNK_METHODS_SYNC_MODIFY.contains(methodName)) {
                    return handleChunkSyncModifyMethods(method, args);
                }
                // Any other methods fall through to the original invocation
            } catch (Exception e) {
                // Log the error and rethrow to ensure the calling plugin is aware of the failure.
                // This is important as `.join()` or other Folia API calls might throw exceptions.
                plugin.getLogger().log(Level.SEVERE, "[FoliaWorldProxy] Exception during Folia-specific handling of " +
                        methodName + " for world '" + originalWorld.getName() + "': " + e.getMessage(), e);
                throw e;
            }
        }

        // If not Folia, or if the method was not specially handled, invoke on the original world.
        return method.invoke(originalWorld, args);
    }

    /**
     * Handles synchronous GETTER methods related to chunks (e.g., getChunkAt, isChunkLoaded).
     * These methods are wrapped to use Folia's async equivalents and then block for the result
     * to maintain the synchronous contract of the Bukkit API.
     */
    private Object handleChunkSyncGetMethods(Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        // plugin.getLogger().fine("[FoliaWorldProxy] Handling GET method: " + methodName + " for world " + originalWorld.getName());

        switch (methodName) {
            case "getChunkAt":
                // Handles getChunkAt(Location) and getChunkAt(int, int)
                if (args.length == 1 && args[0] instanceof Location) {
                    Location loc = (Location) args[0];
                    // IMPORTANT: This proxy is bound to 'originalWorld'. If loc.getWorld() is a different,
                    // non-proxied world instance, using 'originalWorld' here might be incorrect for Folia's
                    // region logic which expects operations on the actual world of the location.
                    // However, to ensure Folia's async API is used, we operate on 'originalWorld'
                    // (which is the proxied CraftWorld, thus has Folia methods) using coordinates from 'loc'.
                    // This is a known limitation of this proxy approach when Location from another world is passed.
                    plugin.getLogger().finer(() -> "[FoliaWorldProxy] getChunkAt(Location) for " + originalWorld.getName() + " at " + loc.toString());
                    return originalWorld.getChunkAtAsync(loc.getBlockX() >> 4, loc.getBlockZ() >> 4, true).join();
                } else if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    plugin.getLogger().finer(() -> "[FoliaWorldProxy] getChunkAt(" + x + "," + z + ") for " + originalWorld.getName());
                    return originalWorld.getChunkAtAsync(x, z, true).join(); // generate = true to match Bukkit's behavior
                }
                break; // Should not be reached if args match
            case "isChunkLoaded":
                if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    // Folia/Paper's getChunkIfLoaded is a direct equivalent for checking if a chunk is loaded without triggering a load.
                    plugin.getLogger().finer(() -> "[FoliaWorldProxy] isChunkLoaded(" + x + "," + z + ") for " + originalWorld.getName());
                    return originalWorld.getChunkIfLoaded(x, z) != null;
                }
                break; // Should not be reached
            case "isChunkGenerated": // Paper API method
                 if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    plugin.getLogger().finer(() -> "[FoliaWorldProxy] isChunkGenerated(" + x + "," + z + ") for " + originalWorld.getName());
                    // The isChunkGenerated method might not exist or behave differently in all Folia versions.
                    // A common way to check for generation without *causing* generation is to try to get the chunk asynchronously without the generate flag.
                    // If Folia's World implementation (CraftWorld) still has a reliable isChunkGenerated, it will be called.
                    // Otherwise, fall back to an async check.
                    try {
                        // Attempt to invoke the original isChunkGenerated method if it exists on the originalWorld object.
                        // This is preferable if the underlying implementation has an optimized way to check this.
                        Method isChunkGeneratedMethod = originalWorld.getClass().getMethod("isChunkGenerated", int.class, int.class);
                        return isChunkGeneratedMethod.invoke(originalWorld, x, z);
                    } catch (NoSuchMethodException e) {
                        // Fallback if isChunkGenerated is not available (e.g., older Paper or specific Folia changes)
                        plugin.getLogger().warning("[FoliaWorldProxy] isChunkGenerated method not found on " + originalWorld.getClass().getName() +
                                ". Falling back to getChunkAtAsync(x,z,false) check for world '" + originalWorld.getName() + "'. This may have performance implications.");
                        CompletableFuture<Chunk> future = originalWorld.getChunkAtAsync(x, z, false); // generate = false
                        // If the future completes with a non-null chunk, it was generated.
                        return future.thenApply(chunk -> chunk != null).join();
                    }
                }
                break; // Should not be reached
        }
        // This part should ideally not be reached if the method is in CHUNK_METHODS_SYNC_GET
        // and arguments are validated. Log a warning if it is.
        plugin.getLogger().warning("[FoliaWorldProxy] Unhandled SYNC GET method fallthrough in Folia for " +
                methodName + " with args: " + Arrays.toString(args) + " on world '" + originalWorld.getName() + "'. Invoking original.");
        return method.invoke(originalWorld, args);
    }

    /**
     * Handles synchronous MODIFY methods related to chunks (e.g., loadChunk, unloadChunk, regenerateChunk).
     * These methods are wrapped to use Folia's async or region-scheduled equivalents,
     * blocking for completion to maintain the synchronous API contract.
     * This can have significant performance implications and should be used cautiously by plugins.
     */
    private Object handleChunkSyncModifyMethods(Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        // plugin.getLogger().fine("[FoliaWorldProxy] Handling MODIFY method: " + methodName + " for world " + originalWorld.getName());

        switch (methodName) {
            case "loadChunk":
                // Bukkit API: loadChunk(int x, int z) is effectively loadChunk(int x, int z, true) and returns void.
                // Paper API: loadChunk(int x, int z, boolean generate) returns Chunk.
                // This proxy aims to match the original Bukkit signature's void return for the two-arg version,
                // and Paper's Chunk return for the three-arg version if applicable based on the actual method signature.
                // Folia's getChunkAtAsync will be used, which returns a CompletableFuture<Chunk>.
                int xLoad, zLoad;
                boolean genLoad = true; // Default for Bukkit's 2-arg loadChunk

                if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    xLoad = (int) args[0];
                    zLoad = (int) args[1];
                } else if (args.length == 3 && args[0] instanceof Integer && args[1] instanceof Integer && args[2] instanceof Boolean) {
                    xLoad = (int) args[0];
                    zLoad = (int) args[1];
                    genLoad = (boolean) args[2];
                } else {
                    // Invalid arguments for known signatures
                    plugin.getLogger().warning("[FoliaWorldProxy] Invalid arguments for loadChunk: " + Arrays.toString(args));
                    return method.invoke(originalWorld, args); // Fallback
                }
                plugin.getLogger().finer(() -> "[FoliaWorldProxy] loadChunk(" + xLoad + "," + zLoad + ", gen=" + genLoad + ") for " + originalWorld.getName());

                CompletableFuture<Chunk> loadFuture = originalWorld.getChunkAtAsync(xLoad, zLoad, genLoad);
                Chunk loadedChunk = loadFuture.join(); // Block for completion

                // Determine return type based on the actual method signature being proxied
                if (method.getReturnType().equals(void.class) || method.getReturnType().equals(Void.class)) {
                    return null; // Matches Bukkit's void loadChunk(int, int)
                } else if (Chunk.class.isAssignableFrom(method.getReturnType())) {
                    return loadedChunk; // Matches Paper's Chunk loadChunk(int, int, boolean)
                } else if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                    // Some older APIs or custom implementations might return boolean.
                    // We'll assume success if no exception was thrown and chunk is not null.
                    return loadedChunk != null;
                }
                // Fallback for unexpected return types
                plugin.getLogger().warning("[FoliaWorldProxy] loadChunk for " + originalWorld.getName() + " was called with unhandled return type: " + method.getReturnType());
                return loadedChunk; // Or throw an error, or return null based on desired strictness.
            case "unloadChunk":
                // Bukkit API: unloadChunk(int x, int z, [boolean save]) returns boolean.
                // Folia API: typically has unloadChunkRequest(int x, int z) returning CompletableFuture<Void>.
                // The 'save' parameter might not be directly supported by Folia's primary async unload.
                // We adapt to return boolean as per Bukkit.
                int xUnload, zUnload;
                // boolean saveUnload = true; // Bukkit's default for 2-arg unloadChunk is to save. Folia's unload may not have this.

                if (args.length == 1 && args[0] instanceof Integer && args[1] instanceof Integer) { // This is likely a typo, should be args.length == 2
                    xUnload = (int) args[0];
                    zUnload = (int) args[1];
                } else if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) { // Corrected for 2 args
                    xUnload = (int) args[0];
                    zUnload = (int) args[1];
                }
                // TODO: Handle 3-argument version unloadChunk(x,z,save) if necessary, though Folia might ignore 'save'.
                // else if (args.length == 3 && args[0] instanceof Integer && args[1] instanceof Integer && args[2] instanceof Boolean) {
                // xUnload = (int) args[0];
                // zUnload = (int) args[1];
                // saveUnload = (boolean) args[2];
                // }
                else {
                    plugin.getLogger().warning("[FoliaWorldProxy] Invalid arguments for unloadChunk: " + Arrays.toString(args));
                    return method.invoke(originalWorld, args); // Fallback
                }
                plugin.getLogger().finer(() -> "[FoliaWorldProxy] unloadChunk(" + xUnload + "," + zUnload + ") for " + originalWorld.getName());

                try {
                    // Attempt to use Folia's unloadChunkRequest if available.
                    Method unloadRequestMethod = originalWorld.getClass().getMethod("unloadChunkRequest", int.class, int.class);
                    CompletableFuture<Void> unloadFuture = (CompletableFuture<Void>) unloadRequestMethod.invoke(originalWorld, xUnload, zUnload);
                    unloadFuture.join(); // Block for completion
                    return true; // Assume success if no exception, matching Bukkit's boolean return.
                } catch (NoSuchMethodException e) {
                    // Fallback if unloadChunkRequest is not found (e.g., older Folia or non-standard API)
                    plugin.getLogger().warning("[FoliaWorldProxy] unloadChunkRequest method not found on " + originalWorld.getClass().getName() +
                            ". Falling back to original unloadChunk for world '" + originalWorld.getName() + "'. This might cause issues on Folia.");
                    return method.invoke(originalWorld, args); // Invoke original, which might be problematic on Folia.
                }
            case "regenerateChunk":
                // Bukkit API: regenerateChunk(int x, int z) returns boolean.
                // Folia has no direct, simple 'regenerateChunk' that's safe to call synchronously from anywhere.
                // Regeneration is a complex operation typically involving:
                // 1. Unloading the chunk (if loaded).
                // 2. Deleting chunk data (if necessary, though usually handled by server).
                // 3. Forcing the server to regenerate it using its world generator.
                // This must be done on the appropriate region thread.
                if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int xRegen = (int) args[0];
                    int zRegen = (int) args[1];
                    plugin.getLogger().info("[FoliaWorldProxy] Handling regenerateChunk(" + xRegen + "," + zRegen + ") for world " +
                            originalWorld.getName() + ". This is a complex operation wrapped for Folia.");

                    CompletableFuture<Boolean> regenFuture = new CompletableFuture<>();
                    // Execute the regeneration logic on the chunk's region thread.
                    Bukkit.getServer().getRegionScheduler().execute(plugin, originalWorld, xRegen, zRegen, () -> {
                        try {
                            plugin.getLogger().fine("[FoliaWorldProxy] Regenerate task started for chunk " + xRegen + "," + zRegen + " in " + originalWorld.getName());
                            // Step 1: Unload the chunk. We use Folia's async unload and wait for it within the region task.
                            boolean unloadedSuccessfully = false;
                            try {
                                Method unloadReqMethod = originalWorld.getClass().getMethod("unloadChunkRequest", int.class, int.class);
                                CompletableFuture<Void> uFuture = (CompletableFuture<Void>) unloadReqMethod.invoke(originalWorld, xRegen, zRegen);
                                uFuture.get(); // Block for unload within the region task
                                unloadedSuccessfully = true;
                                plugin.getLogger().fine("[FoliaWorldProxy] Chunk " + xRegen + "," + zRegen + " unloaded for regeneration.");
                            } catch (NoSuchMethodException nsme) {
                                plugin.getLogger().warning("[FoliaWorldProxy] No unloadChunkRequest for regenerate, trying Bukkit's unloadChunk for " + xRegen + "," + zRegen);
                                // Fallback to Bukkit's unload, though less ideal in Folia
                                unloadedSuccessfully = originalWorld.unloadChunk(xRegen, zRegen, false); // Don't save, we're regenerating
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "[FoliaWorldProxy] Error during unload phase of regenerate for chunk " + xRegen + "," + zRegen + ": " + e.getMessage(), e);
                                // Continue to attempt generation even if unload failed, as it might not have been loaded.
                            }

                            // Step 2: Request the chunk to be loaded again, with the generate flag set to true.
                            // This should trigger the world generator if the chunk is now considered absent.
                            plugin.getLogger().fine("[FoliaWorldProxy] Requesting chunk " + xRegen + "," + zRegen + " with generation=true for regeneration.");
                            Chunk regeneratedChunk = originalWorld.getChunkAtAsync(xRegen, zRegen, true).get(); // generate=true, block in region task

                            if (regeneratedChunk != null) {
                                plugin.getLogger().fine("[FoliaWorldProxy] Chunk " + xRegen + "," + zRegen + " successfully regenerated.");
                                regenFuture.complete(true);
                            } else {
                                plugin.getLogger().warning("[FoliaWorldProxy] Regeneration of chunk " + xRegen + "," + zRegen + " resulted in null chunk.");
                                regenFuture.complete(false);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "[FoliaWorldProxy] Exception in regenerateChunk region task for " +
                                    xRegen + "," + zRegen + " in " + originalWorld.getName() + ": " + e.getMessage(), e);
                            regenFuture.completeExceptionally(e);
                        }
                    });
                    return regenFuture.join(); // Block for the entire regeneration process to complete.
                }
                break; // Should not be reached
        }

        plugin.getLogger().warning("[FoliaWorldProxy] Unhandled SYNC MODIFY method fallthrough in Folia for " +
                methodName + " with args: " + Arrays.toString(args) + " on world '" + originalWorld.getName() + "'. Invoking original.");
        return method.invoke(originalWorld, args);
                    }
                }
                break; // loadChunk case end

            case "unloadChunk":
                // Bukkit: unloadChunk(int chunkX, int chunkZ) -> boolean
                //         unloadChunk(int chunkX, int chunkZ, boolean save) -> boolean
                // Paper:  unloadChunk(int chunkX, int chunkZ) -> CompletableFuture<Boolean>
                //         unloadChunk(int chunkX, int chunkZ, boolean save) -> CompletableFuture<Boolean>
                // Folia:  unloadChunkRequest(int chunkX, int chunkZ) -> CompletableFuture<Void> (Folia 1.19+?)
                //         Folia には save オプション付きの unload がないかもしれない。
                if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    // Folia の unloadChunkRequest は Void を返す。Bukkit は boolean。
                    // ここでは join() で完了を待ち、成功すれば true とする。
                    try {
                        Method unloadRequestMethod = originalWorld.getClass().getMethod("unloadChunkRequest", int.class, int.class);
                        CompletableFuture<Void> future = (CompletableFuture<Void>) unloadRequestMethod.invoke(originalWorld, x, z);
                        future.join();
                        return true; // Bukkit API は boolean
                    } catch (NoSuchMethodException e) {
                         plugin.getLogger().warning("[FoliaWorldProxy] unloadChunkRequest not found. Falling back to original unloadChunk for " + methodName);
                         return method.invoke(originalWorld, args); // フォールバック
                    }
                } else if (args.length == 3 && args[0] instanceof Integer && args[1] instanceof Integer && args[2] instanceof Boolean) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    // boolean save = (boolean) args[2]; // Folia API に save オプションがない場合、この引数は無視される
                     try {
                        // Folia に save オプション付きの unload がない場合、
                        // unloadChunkRequest を使うか、元のメソッドを呼び出す。
                        // ここでは unloadChunkRequest を試し、なければフォールバック。
                        Method unloadRequestMethod = originalWorld.getClass().getMethod("unloadChunkRequest", int.class, int.class);
                        CompletableFuture<Void> future = (CompletableFuture<Void>) unloadRequestMethod.invoke(originalWorld, x, z);
                        future.join();
                        return true;
                    } catch (NoSuchMethodException e) {
                         plugin.getLogger().warning("[FoliaWorldProxy] unloadChunkRequest not found. Falling back to original unloadChunk for " + methodName);
                         return method.invoke(originalWorld, args); // フォールバック
                    }
                }
                break; // unloadChunk case end

            case "regenerateChunk":
                // Bukkit: regenerateChunk(int chunkX, int chunkZ) -> boolean
                // Folia: 直接的な regenerateChunk API はない可能性が高い。
                //        Chunkをアンロードし、再度ロード(生成付き)することで擬似的に再現するか、
                //        より低レベルな NMS/Folia API を使う必要がある。
                //        RegionScheduler で実行する必要がある。
                if (args.length == 2 && args[0] instanceof Integer && args[1] instanceof Integer) {
                    int x = (int) args[0];
                    int z = (int) args[1];
                    plugin.getLogger().info("[FoliaWorldProxy] regenerateChunk(" + x + "," + z + ") called for world " + originalWorld.getName() + ". This requires proper Folia handling (e.g., via RegionScheduler).");

                    CompletableFuture<Boolean> future = new CompletableFuture<>();
                    // getServer() は Bukkit.getServer() を使うべき
                    Bukkit.getServer().getRegionScheduler().execute(plugin, originalWorld, x, z, () -> {
                        try {
                            // 1. チャンクをアンロード (Folia API で)
                            // originalWorld.unloadChunkRequest(x,z).join(); // これは同期的なので Scheduler 内では注意
                            // 非同期アンロードを待つか、アンロード後にロードを実行するコールバックを使う
                            // もしくは、Foliaに低レベルな再生成APIがあればそれを使う
                            plugin.getLogger().info("[FoliaWorldProxy] Attempting unload for regenerate: " + x + "," + z);
                            boolean unloaded = false;
                            try {
                                Method unloadReq = originalWorld.getClass().getMethod("unloadChunkRequest", int.class, int.class);
                                CompletableFuture<Void> unloadFuture = (CompletableFuture<Void>) unloadReq.invoke(originalWorld, x, z);
                                unloadFuture.get(); // RegionScheduler内なのでブロッキングOK
                                unloaded = true;
                            } catch (NoSuchMethodException nsme) {
                                // unloadChunkRequest がない場合は、BukkitのunloadChunkを試す(非推奨だが最後の手段)
                                plugin.getLogger().warning("[FoliaWorldProxy] unloadChunkRequest not found for regenerate, trying Bukkit's unloadChunk");
                                unloaded = originalWorld.unloadChunk(x,z,false); // save = false でアンロード
                            } catch (Exception e) {
                                plugin.getLogger().log(Level.WARNING, "[FoliaWorldProxy] Error during unload part of regenerate: " + e.getMessage(), e);
                            }

                            if(unloaded) {
                                plugin.getLogger().info("[FoliaWorldProxy] Unloaded for regenerate, attempting load: " + x + "," + z);
                                // 2. チャンクをロード (生成オプション付き)
                                Chunk regeneratedChunk = originalWorld.getChunkAtAsync(x, z, true).get(); // generate = true, RegionScheduler内なのでブロッキングOK
                                future.complete(regeneratedChunk != null);
                            } else {
                                plugin.getLogger().warning("[FoliaWorldProxy] Failed to unload chunk for regenerate: " + x + "," + z);
                                future.complete(false);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "[FoliaWorldProxy] Error during regenerateChunk task for " + x + "," + z + ": " + e.getMessage(), e);
                            future.completeExceptionally(e);
                        }
                    });
                    return future.join(); // Bukkit API は boolean を返す
                }
                break; // regenerateChunk case end
        }

        plugin.getLogger().warning("[FoliaWorldProxy] Unhandled SYNC MODIFY method in Folia: " + methodName + " args: " + Arrays.toString(args));
        return method.invoke(originalWorld, args);
    }
}
