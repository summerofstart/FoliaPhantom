package summer.foliaPhantom.transformer;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.Bukkit; // Not strictly needed here if not directly calling Bukkit static methods in transformer
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerTeleportEvent; // Needed for TeleportCause
import summer.foliaPhantom.FoliaPhantom;
import summer.foliaPhantom.scheduler.FoliaSchedulerAdapter;

import java.lang.instrument.Instrumentation;
// import java.util.concurrent.CompletableFuture; // No longer returned by Advices
import java.util.logging.Level;
import java.util.logging.Logger;

public class BukkitApiTransformer {

    private static Logger LOGGER;
    private static boolean initialized = false;
    private static FoliaSchedulerAdapter schedulerAdapter;
    private static FoliaPhantom owningPlugin;
    private static boolean byteBuddyTransformationsApplied = false;

    public static void initialize(FoliaPhantom plugin, FoliaSchedulerAdapter adapter) {
        if (initialized) {
            plugin.getLogger().warning("BukkitApiTransformer is already initialized.");
            return;
        }

        owningPlugin = plugin;
        schedulerAdapter = adapter;
        LOGGER = owningPlugin.getLogger(); // Initialize logger here

        Instrumentation instrumentation = null;
        try {
            instrumentation = ByteBuddyAgent.install();
        } catch (IllegalStateException e) {
            LOGGER.info("ByteBuddyAgent.install() indicated an agent is already installed. Attempting to use existing instrumentation.");
            instrumentation = ByteBuddyAgent.getInstrumentation();
        }

        if (instrumentation == null) {
            LOGGER.severe("Failed to get Instrumentation instance. API transformation will not be applied.");
            byteBuddyTransformationsApplied = false;
            return;
        }

        LOGGER.info("Initializing Bukkit API transformations (sync-wait strategy)... This may take a moment.");

        try {
            new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
                .with(AgentBuilder.TypeStrategy.Default.REDEFINE)
                .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
                .ignore(ElementMatchers.nameStartsWith("summer.foliaPhantom."))
                // Bukkit.createWorld(WorldCreator)
                .type(ElementMatchers.named("org.bukkit.Bukkit"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.method(ElementMatchers.named("createWorld")
                                    .and(ElementMatchers.takesArguments(WorldCreator.class))
                                    .and(ElementMatchers.returns(World.class)))
                           .intercept(Advice.to(CreateWorldAdvice.class))
                    // Bukkit.unloadWorld(World, boolean)
                    .method(ElementMatchers.named("unloadWorld")
                                    .and(ElementMatchers.takesArguments(World.class, boolean.class))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(UnloadWorldAdvice.class))
                )
                // World methods
                .type(ElementMatchers.named("org.bukkit.World"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.method(ElementMatchers.named("getChunkAt")
                                    .and(ElementMatchers.takesArguments(int.class, int.class))
                                    .and(ElementMatchers.returns(Chunk.class)))
                           .intercept(Advice.to(GetChunkAtIntAdvice.class))
                    .method(ElementMatchers.named("getChunkAt")
                                    .and(ElementMatchers.takesArguments(Location.class))
                                    .and(ElementMatchers.returns(Chunk.class)))
                           .intercept(Advice.to(GetChunkAtLocationAdvice.class))
                    .method(ElementMatchers.named("spawnEntity")
                                    .and(ElementMatchers.takesArguments(Location.class, org.bukkit.entity.EntityType.class))
                                    .and(ElementMatchers.returns(Entity.class)))
                           .intercept(Advice.to(SpawnEntityAdvice.class))
                    .method(ElementMatchers.named("save")
                                    .and(ElementMatchers.takesArguments(0))
                                    .and(ElementMatchers.returns(void.class)))
                           .intercept(Advice.to(WorldSaveAdvice.class))
                )
                // Chunk methods
                .type(ElementMatchers.named("org.bukkit.Chunk"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.method(ElementMatchers.named("load")
                                    .and(ElementMatchers.takesArguments(0))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(ChunkLoadAdvice.class))
                    .method(ElementMatchers.named("load")
                                    .and(ElementMatchers.takesArguments(boolean.class))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(ChunkLoadWithGenerateAdvice.class))
                    .method(ElementMatchers.named("unload")
                                    .and(ElementMatchers.takesArguments(0))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(ChunkUnloadAdvice.class))
                    .method(ElementMatchers.named("unload")
                                    .and(ElementMatchers.takesArguments(boolean.class))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(ChunkUnloadWithSaveAdvice.class))
                )
                 // Block methods
                .type(ElementMatchers.named("org.bukkit.block.Block"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.method(ElementMatchers.named("setType")
                                    .and(ElementMatchers.takesArguments(Material.class))
                                    .and(ElementMatchers.returns(void.class)))
                           .intercept(Advice.to(SetBlockTypeAdvice.class))
                    .method(ElementMatchers.named("setType")
                                    .and(ElementMatchers.takesArguments(Material.class, boolean.class))
                                    .and(ElementMatchers.returns(void.class)))
                           .intercept(Advice.to(SetBlockTypeWithPhysicsAdvice.class))
                )
                // Entity methods
                .type(ElementMatchers.named("org.bukkit.entity.Entity"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                    builder.method(ElementMatchers.named("teleport")
                                    .and(ElementMatchers.takesArguments(Location.class))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(TeleportEntityAdvice.class))
                    .method(ElementMatchers.named("teleport")
                                    .and(ElementMatchers.takesArguments(Location.class, PlayerTeleportEvent.TeleportCause.class))
                                    .and(ElementMatchers.returns(boolean.class)))
                           .intercept(Advice.to(TeleportEntityWithCauseAdvice.class))
                )
                .installOn(instrumentation);

            byteBuddyTransformationsApplied = true;
            LOGGER.info("Bukkit API transformations (sync-wait strategy) applied successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to apply Bukkit API transformations (sync-wait strategy): " + e.getMessage(), e);
            byteBuddyTransformationsApplied = false;
        }
        initialized = true;
    }

    private static void logTransformed(String methodName, Object... args) {
        if (LOGGER.isLoggable(Level.FINER)) {
            StringBuilder sb = new StringBuilder("[Transformer] Intercepted ");
            sb.append(methodName).append("(");
            for (int i = 0; i < args.length; i++) {
                sb.append(args[i] == null ? "null" : args[i].toString());
                if (i < args.length - 1) sb.append(", ");
            }
            sb.append(")");
            LOGGER.finer(sb.toString());
        }
    }

    private static <T> T handleSchedulerError(String adviceName, String methodName, T defaultValue, Exception e) {
        LOGGER.log(Level.SEVERE, "Error in " + adviceName + " for " + methodName + " during async execution or join: " + e.getMessage(), e);
        // Depending on the method, returning a default or re-throwing might be appropriate.
        // For methods that return boolean, false is often a safe default for "failure".
        // For object-returning methods, null might be acceptable if the API allows it.
        // For void methods, just log.
        if (defaultValue instanceof Boolean) return defaultValue;
        if (defaultValue == null && !(e instanceof NullPointerException)) return null; // if NPE was not the cause for default null
        throw new RuntimeException("Transformation failed for " + methodName, e); // Rethrow if no safe default
    }


    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean areTransformationsApplied() {
        return byteBuddyTransformationsApplied;
    }

    // --- Advice Classes ---

    public static class CreateWorldAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static World exit(@Advice.Argument(0) WorldCreator creator) {
            logTransformed("Bukkit.createWorld", creator.name());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.createWorldAsync(creator).join();
            } catch (Exception e) {
                return handleSchedulerError("CreateWorldAdvice", "Bukkit.createWorld", null, e);
            }
        }
    }

    public static class UnloadWorldAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.Argument(0) World world, @Advice.Argument(1) boolean save) {
            logTransformed("Bukkit.unloadWorld", world.getName(), save);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.unloadWorldAsync(world, save).join();
            } catch (Exception e) {
                return handleSchedulerError("UnloadWorldAdvice", "Bukkit.unloadWorld", false, e);
            }
        }
    }

    public static class GetChunkAtIntAdvice { // Renamed from GetChunkAtAdvice to be specific
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static Chunk exit(@Advice.This World world,
                                 @Advice.Argument(0) int x,
                                 @Advice.Argument(1) int z) {
            logTransformed("World.getChunkAt", world.getName(), x, z);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.getChunkAtAsync(world, x, z).join();
            } catch (Exception e) {
                return handleSchedulerError("GetChunkAtIntAdvice", "World.getChunkAt(int, int)", null, e);
            }
        }
    }

    public static class GetChunkAtLocationAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static Chunk exit(@Advice.This World world,
                                 @Advice.Argument(0) Location location) {
            logTransformed("World.getChunkAt", world.getName(), location);
             try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.getChunkAtAsync(location).join();
            } catch (Exception e) {
                return handleSchedulerError("GetChunkAtLocationAdvice", "World.getChunkAt(Location)", null, e);
            }
        }
    }

    public static class ChunkLoadAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Chunk chunk) {
            logTransformed(chunk.getWorld().getName() + " Chunk.load", chunk.getX(), chunk.getZ());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.loadChunkAsync(chunk).join();
            } catch (Exception e) {
                return handleSchedulerError("ChunkLoadAdvice", "Chunk.load()", false, e);
            }
        }
    }

    public static class ChunkLoadWithGenerateAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Chunk chunk, @Advice.Argument(0) boolean generate) {
            logTransformed(chunk.getWorld().getName() + " Chunk.load", chunk.getX(), chunk.getZ(), generate);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.loadChunkAsync(chunk, generate).join();
            } catch (Exception e) {
                return handleSchedulerError("ChunkLoadWithGenerateAdvice", "Chunk.load(boolean)", false, e);
            }
        }
    }

    public static class ChunkUnloadAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Chunk chunk) {
            logTransformed(chunk.getWorld().getName() + " Chunk.unload", chunk.getX(), chunk.getZ());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.unloadChunkAsync(chunk).join();
            } catch (Exception e) {
                return handleSchedulerError("ChunkUnloadAdvice", "Chunk.unload()", false, e);
            }
        }
    }

    public static class ChunkUnloadWithSaveAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Chunk chunk, @Advice.Argument(0) boolean save) {
            logTransformed(chunk.getWorld().getName() + " Chunk.unload", chunk.getX(), chunk.getZ(), save);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.unloadChunkAsync(chunk, save).join();
            } catch (Exception e) {
                return handleSchedulerError("ChunkUnloadWithSaveAdvice", "Chunk.unload(boolean)", false, e);
            }
        }
    }

    public static class SetBlockTypeAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit // No @Advice.AssignReturned.ToReturned for void methods
        public static void exit(@Advice.This Block block, @Advice.Argument(0) Material type) {
            logTransformed("Block.setType", block.getLocation(), type);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                schedulerAdapter.setBlockTypeAsync(block, type).join();
            } catch (Exception e) {
                handleSchedulerError("SetBlockTypeAdvice", "Block.setType(Material)", null, e); // Default value null for void
            }
        }
    }

    public static class SetBlockTypeWithPhysicsAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit
        public static void exit(@Advice.This Block block, @Advice.Argument(0) Material type, @Advice.Argument(1) boolean applyPhysics) {
            logTransformed("Block.setType", block.getLocation(), type, applyPhysics);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                schedulerAdapter.setBlockTypeAsync(block, type, applyPhysics).join();
            } catch (Exception e) {
                handleSchedulerError("SetBlockTypeWithPhysicsAdvice", "Block.setType(Material, boolean)", null, e);
            }
        }
    }

    public static class TeleportEntityAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Entity entity, @Advice.Argument(0) Location location) {
            logTransformed("Entity.teleport", entity.getName(), location);
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.teleportEntityAsync(entity, location).join();
            } catch (Exception e) {
                return handleSchedulerError("TeleportEntityAdvice", "Entity.teleport(Location)", false, e);
            }
        }
    }

    public static class TeleportEntityWithCauseAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static boolean exit(@Advice.This Entity entity, @Advice.Argument(0) Location location, @Advice.Argument(1) PlayerTeleportEvent.TeleportCause cause) {
            logTransformed("Entity.teleport", entity.getName(), location, cause.name());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.teleportEntityAsync(entity, location, cause).join();
            } catch (Exception e) {
                return handleSchedulerError("TeleportEntityWithCauseAdvice", "Entity.teleport(Location, Cause)", false, e);
            }
        }
    }

    public static class SpawnEntityAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit
        public static Entity exit(@Advice.This World world, @Advice.Argument(0) Location location, @Advice.Argument(1) org.bukkit.entity.EntityType type) {
            logTransformed("World.spawnEntity", world.getName(), location, type.name());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                return schedulerAdapter.spawnEntityAsync(location, type).join();
            } catch (Exception e) {
                return handleSchedulerError("SpawnEntityAdvice", "World.spawnEntity(Location, EntityType)", null, e);
            }
        }
    }

    public static class WorldSaveAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean enter() { return false; }

        @SuppressWarnings("unused")
        @Advice.OnMethodExit
        public static void exit(@Advice.This World world) {
            logTransformed("World.save", world.getName());
            try {
                if (schedulerAdapter == null) throw new IllegalStateException("FoliaSchedulerAdapter not initialized.");
                schedulerAdapter.saveWorldAsync(world).join();
            } catch (Exception e) {
                 handleSchedulerError("WorldSaveAdvice", "World.save()", null, e);
            }
        }
    }
}
