package summer.foliaPhantom.world;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import summer.foliaPhantom.FoliaPhantom; // For isFoliaServer
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the proxying of Bukkit World objects to intercept and handle
 * chunk-related method calls for Folia compatibility.
 * This class uses Unsafe and reflection to replace the server's internal
 * world instances with proxies.
 */
public class WorldProxyManager {

    private final FoliaPhantom plugin; // Main plugin instance, used for logging and context.
    private final Logger logger;
    private Unsafe unsafeInstance;

    // Stores original world instances before they are proxied. Keyed by world name.
    private final Map<String, World> originalWorlds = new HashMap<>();
    // Stores the proxied world instances. Keyed by world name. (Currently not used externally but can be useful for debugging)
    private final Map<String, World> proxiedWorlds = new HashMap<>();

    // Reflection-related fields for accessing and modifying CraftServer's 'worlds' map.
    private Object craftServerInstance; // The CraftServer instance.
    private Field worldsFieldInCraftServer; // The Field object for CraftServer.worlds.
    private long worldsFieldOffset; // Memory offset for the CraftServer.worlds field, used by Unsafe.

    public WorldProxyManager(FoliaPhantom plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Initializes the WorldProxyManager and attempts to proxy all currently loaded worlds
     * if the server is detected as a Folia server.
     * This involves obtaining Unsafe, finding the CraftServer's internal 'worlds' map,
     * and replacing its contents with proxied World instances.
     *
     * @return true if initialization and proxying were successful (or if not on Folia),
     *         false if a critical error occurred.
     */
    public boolean initializeAndProxyWorlds() {
        if (!FoliaPhantom.isFoliaServer()) {
            logger.info("[WorldProxyManager] Not a Folia server, skipping World proxying.");
            return true; // Successfully did nothing, as intended.
        }

        logger.info("[WorldProxyManager] Initializing for Folia server. Attempting to proxy worlds...");
        try {
            obtainUnsafeInstance();
            this.craftServerInstance = Bukkit.getServer(); // Get the current server instance (CraftServer).

            // Locate the 'worlds' field within the CraftServer class (or its superclasses).
            // This field typically holds a Map<String, World> mapping world names to WorldServer instances.
            try {
                worldsFieldInCraftServer = craftServerInstance.getClass().getDeclaredField("worlds");
            } catch (NoSuchFieldException e) {
                // Fallback: search in superclasses if not found directly in CraftServer.
                // This increases robustness against minor CraftBukkit refactors.
                Class<?> currentClass = craftServerInstance.getClass().getSuperclass();
                while (currentClass != null && currentClass != Object.class) {
                    try {
                        worldsFieldInCraftServer = currentClass.getDeclaredField("worlds");
                        break;
                    } catch (NoSuchFieldException ignored) {}
                    currentClass = currentClass.getSuperclass();
                }
                if (worldsFieldInCraftServer == null) {
                    throw new NoSuchFieldException("Failed to find 'worlds' field in CraftServer or its superclasses. This is critical for proxying.");
                }
            }
            worldsFieldInCraftServer.setAccessible(true); // Make the field accessible.
            worldsFieldOffset = unsafeInstance.objectFieldOffset(worldsFieldInCraftServer); // Get memory offset.

            // Retrieve the original 'worlds' map from CraftServer using Unsafe.
            @SuppressWarnings("unchecked")
            Map<String, World> currentWorldsMap = (Map<String, World>) unsafeInstance.getObject(craftServerInstance, worldsFieldOffset);
            if (currentWorldsMap == null) {
                logger.severe("[WorldProxyManager] CraftServer's 'worlds' map is null. Cannot proxy worlds. This indicates a severe server issue or incompatibility.");
                return false;
            }

            // Create a new map that will hold the proxied World objects.
            // We replace the entire map instance in CraftServer to ensure atomicity of the update if possible,
            // though direct field manipulation is inherently risky.
            Map<String, World> newWorldsMap = new HashMap<>();
            logger.fine("[WorldProxyManager] Original worlds map obtained. Iterating and proxying " + currentWorldsMap.size() + " worlds.");

            for (Map.Entry<String, World> entry : currentWorldsMap.entrySet()) {
                String worldName = entry.getKey();
                World originalWorld = entry.getValue();

                if (originalWorld == null) {
                    logger.warning("[WorldProxyManager] Original world object for name '" + worldName + "' is null in CraftServer's map. Skipping proxy for this entry.");
                    newWorldsMap.put(worldName, null); // Preserve null entries if they exist.
                    continue;
                }

                this.originalWorlds.put(worldName, originalWorld); // Store the original for restoration.

                // Determine the set of interfaces to proxy. Using originalWorld.getClass().getInterfaces()
                // is generally robust as it captures all interfaces the actual World object implements (e.g., World, PaperWorld, FoliaWorld if applicable).
                // This is crucial for avoiding ClassCastExceptions when plugins cast the World object.
                Class<?>[] interfacesToProxy = originalWorld.getClass().getInterfaces();
                if (interfacesToProxy.length == 0) {
                    // This case should be rare for Bukkit World objects but handle defensively.
                    // Fallback to just org.bukkit.World if no interfaces are found, though this is likely an issue.
                    logger.warning("[WorldProxyManager] World object " + originalWorld.getName() + " (" + originalWorld.getClass().getName() +
                                   ") reported no interfaces. Proxying only org.bukkit.World. This might lead to ClassCastExceptions.");
                    interfacesToProxy = new Class<?>[]{World.class};
                }

                logger.fine(() -> "[WorldProxyManager] Proxying world '" + worldName + "' (" + originalWorld.getClass().getName() +
                                ") implementing interfaces: " + Arrays.toString(interfacesToProxy));

                World proxiedWorld = (World) Proxy.newProxyInstance(
                        originalWorld.getClass().getClassLoader(), // Use the original world's classloader.
                        interfacesToProxy,
                        new FoliaWorldProxyInvocationHandler(originalWorld, this.plugin) // The handler doing the actual work.
                );
                this.proxiedWorlds.put(worldName, proxiedWorld); // Store the proxy (mainly for debugging or potential future use).
                newWorldsMap.put(worldName, proxiedWorld); // Add the proxied world to our new map.
                logger.info("[WorldProxyManager] Successfully created proxy for world: " + worldName);
            }

            // Atomically replace the 'worlds' map in CraftServer with our new map containing the proxies.
            unsafeInstance.putObject(craftServerInstance, worldsFieldOffset, newWorldsMap);

            // Note: This proxies worlds obtained via Bukkit.getWorld(String) or iterating Bukkit.getWorlds().
            // It does NOT automatically proxy worlds obtained via Player.getWorld() if CraftPlayer directly holds
            // an unproxied CraftWorld. Further work would be needed for that (e.g., proxying CraftPlayer.world field).
            logger.info("[WorldProxyManager] All detected worlds have been proxied. Bukkit.getWorld(s) will now return proxied instances on Folia.");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[WorldProxyManager] CRITICAL FAILURE during world proxy initialization: " + e.getMessage() +
                       ". Chunk operations for wrapped plugins will likely NOT be Folia-compatible.", e);
            return false;
        }
    }

    /**
     * Restores the original World objects to the server, removing the proxies.
     * This should be called when FoliaPhantom is disabled.
     * It attempts to revert the changes made to CraftServer's internal 'worlds' map.
     */
    public void restoreOriginalWorlds() {
        if (!FoliaPhantom.isFoliaServer()) {
            return; // Nothing was proxied if not on Folia.
        }
        if (originalWorlds.isEmpty() || unsafeInstance == null || craftServerInstance == null || worldsFieldOffset == 0) {
            logger.fine("[WorldProxyManager] No original worlds to restore, or manager not fully initialized. Skipping restoration.");
            return;
        }

        logger.info("[WorldProxyManager] Attempting to restore original world instances...");
        try {
            // Construct the map to restore. This should be identical to the original map state.
            Map<String, World> mapToRestore = new HashMap<>(this.originalWorlds);

            // Sanity check: verify that the current worlds map in CraftServer seems to be our proxied one.
            // This is a best-effort check.
            @SuppressWarnings("unchecked")
            Map<String, World> currentWorldsInServer = (Map<String, World>) unsafeInstance.getObject(craftServerInstance, worldsFieldOffset);

            boolean isCurrentlyProxied = false;
            if (currentWorldsInServer != null && !currentWorldsInServer.isEmpty()) {
                // Find first non-null world to check if it's a proxy.
                World firstWorld = currentWorldsInServer.values().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
                if (firstWorld != null && Proxy.isProxyClass(firstWorld.getClass())) {
                    isCurrentlyProxied = true;
                }
            }

            if (isCurrentlyProxied) {
                unsafeInstance.putObject(craftServerInstance, worldsFieldOffset, mapToRestore);
                logger.info("[WorldProxyManager] Original worlds map has been restored in CraftServer.");
            } else {
                logger.warning("[WorldProxyManager] The current worlds map in CraftServer does not appear to be managed by this proxy (or is empty/unexpected). Restoration skipped to avoid potential conflicts.");
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[WorldProxyManager] CRITICAL FAILURE during restoration of original worlds: " + e.getMessage(), e);
        } finally {
            // Clear local caches.
            originalWorlds.clear();
            proxiedWorlds.clear();
            // Consider whether to null out craftServerInstance, worldsFieldOffset etc.
            // If the manager is to be reused, these might need to persist or be re-acquired.
            // For a typical plugin lifecycle (one enable/disable), clearing is fine.
            logger.fine("[WorldProxyManager] Finished restoration process.");
        }
    }

    /**
     * Obtains an instance of sun.misc.Unsafe using reflection.
     * This is necessary for direct memory manipulation.
     * @throws NoSuchFieldException if the 'theUnsafe' field cannot be found.
     * @throws IllegalAccessException if the 'theUnsafe' field cannot be accessed.
     */
    private void obtainUnsafeInstance() throws NoSuchFieldException, IllegalAccessException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        this.unsafeInstance = (Unsafe) field.get(null);
    }
}
