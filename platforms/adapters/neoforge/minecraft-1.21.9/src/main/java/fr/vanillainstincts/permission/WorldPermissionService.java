package fr.vanillainstincts.permission;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.permission.PermissionDecision;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.core.permission.WorldPermissionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

/** Central transactional permission gateway for every mod-owned world mutation. */
public final class WorldPermissionService {
    public record BlockChange(BlockPos pos, BlockState state, int flags) {
        public BlockChange {
            pos = pos.immutable();
        }
    }

    public static final TagKey<Block> PROTECTED_FROM_AI = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(
                    VanillaInstincts.MOD_ID, "protected_from_ai"));

    private record DeniedKey(long position, WorldActionType action) {
    }

    private static final IdentityHashMap<ServerLevel,
            Map<DeniedKey, Long>> DENIED = new IdentityHashMap<>();

    private WorldPermissionService() {
    }

    public static PermissionDecision check(ServerLevel level,
                                           @Nullable Entity actor,
                                           WorldActionType action,
                                           BlockPos pos,
                                           @Nullable BlockState replacement) {
        return check(level, actor, action, pos, replacement, true);
    }

    private static PermissionDecision check(ServerLevel level,
                                            @Nullable Entity actor,
                                            WorldActionType action,
                                            BlockPos pos,
                                            @Nullable BlockState replacement,
                                            boolean postCustomEvent) {
        if (action == null) return PermissionDecision.MOD_DISABLED;
        ConfigSnapshot config = RuntimeConfig.snapshot();
        boolean chunkLoaded = level.hasChunkAt(pos);
        boolean mobGriefingAllowed = !config.respectMobGriefing()
                || actor == null
                || actor instanceof Player
                || !action.respectsMobGriefing()
                || EventHooks.canEntityGrief(level, actor);
        boolean spawnProtected = config.protectSpawnArea()
                && config.spawnProtectionRadius() > 0
                && !(actor instanceof Player)
                && Level.OVERWORLD.equals(level.dimension())
                && horizontalDistance(level.getRespawnData().pos(), pos)
                <= config.spawnProtectionRadius();
        boolean retryReady = retryReady(level, pos, action,
                level.getGameTime());
        boolean blockEntityPresent = chunkLoaded
                && config.protectBlockEntities()
                && level.getBlockEntity(pos) != null;
        boolean protectedBlock = chunkLoaded
                && level.getBlockState(pos).is(PROTECTED_FROM_AI);

        // allowWorldChanges is the safety switch for AI/mob-driven edits.
        // A real player's explicit action (for example igniting a crying-
        // obsidian portal) must keep the player's normal build permissions.
        boolean worldChangesAllowed = config.allowWorldChanges()
                || actor instanceof Player;
        PermissionDecision preliminary = WorldPermissionPolicy.evaluate(action,
                config.enabled(), worldChangesAllowed,
                config.allowItemChanges(), mobGriefingAllowed,
                chunkLoaded, level.getWorldBorder().isWithinBounds(pos),
                spawnProtected, blockEntityPresent, protectedBlock,
                retryReady, true);
        if (!preliminary.allowed()) {
            if (preliminary != PermissionDecision.RETRY_COOLDOWN) {
                rememberDenial(level, pos, action, level.getGameTime(),
                        config.permissionDeniedRetryTicks());
            }
            return preliminary;
        }
        if (postCustomEvent && NeoForge.EVENT_BUS.post(
                new WorldActionPermissionEvent(level, actor, action,
                        List.of(pos), replacement)).isCanceled()) {
            rememberDenial(level, pos, action, level.getGameTime(),
                    config.permissionDeniedRetryTicks());
            return PermissionDecision.EVENT_DENIED;
        }
        return PermissionDecision.ALLOWED;
    }

    public static boolean setBlock(ServerLevel level, @Nullable Entity actor,
                                   BlockPos pos, BlockState replacement,
                                   int flags, WorldActionType action) {
        if (!check(level, actor, action, pos, replacement).allowed()) {
            return false;
        }
        if (level.getBlockState(pos).equals(replacement)) {
            clearDenial(level, pos, action);
            return true;
        }
        BlockSnapshot rollbackSnapshot = BlockSnapshot.create(level.dimension(),
                level, pos, flags);
        if (!level.setBlock(pos, replacement, flags)) {
            rememberDenial(level, pos, action, level.getGameTime(),
                    RuntimeConfig.snapshot().permissionDeniedRetryTicks());
            return false;
        }
        if (action != WorldActionType.TEMPORARY_CLEANUP) {
            /*
             * NeoForge's EntityPlaceEvent reads getCurrentState() for a Player,
             * but reads the snapshot state itself for every other Entity.  A
             * pre-mutation snapshot is therefore correct for players (it also
             * supplies rollback state), while passing that same snapshot for a
             * villager would incorrectly announce AIR when the villager has
             * just planted a crop.  Capture a post-mutation event snapshot for
             * non-player actors, while retaining the original snapshot solely
             * for rollback if another mod cancels the placement event.
             */
            BlockSnapshot eventSnapshot = actor instanceof Player
                    ? rollbackSnapshot
                    : BlockSnapshot.create(level.dimension(), level, pos, flags);
            if (EventHooks.onBlockPlace(actor, eventSnapshot, Direction.UP)) {
                rollbackSnapshot.restore(flags);
                rememberDenial(level, pos, action, level.getGameTime(),
                        RuntimeConfig.snapshot().permissionDeniedRetryTicks());
                return false;
            }
        }
        clearDenial(level, pos, action);
        return true;
    }

    /**
     * Applies an explicit real-player multi-block action without the AI/mob
     * mutation policy. This is used for vanilla-like interactions such as
     * igniting an already valid portal frame. Feature gating is still done by
     * the caller; this method only prevents AI safety rules and synthetic
     * placement hooks from rejecting the player's own interaction.
     */
    public static boolean setPlayerBlocksAtomically(ServerLevel level,
                                                    Player player,
                                                    List<BlockChange> changes) {
        if (level == null || player == null || changes == null
                || changes.isEmpty() || !RuntimeConfig.snapshot().enabled()) {
            return false;
        }
        for (BlockChange change : changes) {
            if (!level.hasChunkAt(change.pos())
                    || !level.getWorldBorder().isWithinBounds(change.pos())) {
                return false;
            }
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            snapshots.add(BlockSnapshot.create(level.dimension(), level,
                    change.pos(), change.flags()));
        }
        for (int i = 0; i < changes.size(); i++) {
            BlockChange change = changes.get(i);
            if (!level.setBlock(change.pos(), change.state(), change.flags())) {
                restoreReverse(snapshots, i - 1);
                return false;
            }
        }
        return true;
    }

    /**
     * Applies a multi-block change that is the direct continuation of a
     * vanilla world interaction already accepted by Minecraft itself.
     *
     * <p>This is intentionally narrower than AI mutation: it is used for
     * mechanics such as fire inside an already-built portal frame. It keeps
     * chunk/world-border safety and atomic rollback, but it does not apply
     * mob-griefing/spawn-protection rules that are irrelevant to the player's
     * vanilla interaction which caused the change.</p>
     */
    public static boolean setVanillaTriggeredBlocksAtomically(
            ServerLevel level, List<BlockChange> changes) {
        if (level == null || changes == null || changes.isEmpty()
                || !RuntimeConfig.snapshot().enabled()) {
            return false;
        }
        for (BlockChange change : changes) {
            if (!level.hasChunkAt(change.pos())
                    || !level.getWorldBorder().isWithinBounds(change.pos())) {
                return false;
            }
        }
        List<BlockSnapshot> snapshots = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            snapshots.add(BlockSnapshot.create(level.dimension(), level,
                    change.pos(), change.flags()));
        }
        for (int i = 0; i < changes.size(); i++) {
            BlockChange change = changes.get(i);
            if (!level.setBlock(change.pos(), change.state(), change.flags())) {
                restoreReverse(snapshots, i - 1);
                return false;
            }
        }
        return true;
    }

    public static boolean setBlocksAtomically(ServerLevel level,
                                              @Nullable Entity actor,
                                              List<BlockChange> changes,
                                              WorldActionType action) {
        if (changes == null || changes.isEmpty()) return false;
        List<PermissionDecision> decisions = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            decisions.add(check(level, actor, action, change.pos(),
                    change.state(), false));
        }
        if (!WorldPermissionPolicy.allAllowed(decisions)) return false;
        List<BlockChange> effective = changes.stream()
                .filter(change -> !level.getBlockState(change.pos())
                        .equals(change.state()))
                .toList();
        if (effective.isEmpty()) {
            for (BlockChange change : changes) {
                clearDenial(level, change.pos(), action);
            }
            return true;
        }
        List<BlockPos> positions = effective.stream()
                .map(BlockChange::pos).toList();
        if (NeoForge.EVENT_BUS.post(new WorldActionPermissionEvent(level,
                actor, action, positions, null)).isCanceled()) {
            for (BlockChange change : effective) {
                rememberDenial(level, change.pos(), action,
                        level.getGameTime(), RuntimeConfig.snapshot()
                                .permissionDeniedRetryTicks());
            }
            return false;
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(effective.size());
        for (BlockChange change : effective) {
            snapshots.add(BlockSnapshot.create(level.dimension(), level,
                    change.pos(), change.flags()));
        }
        for (int i = 0; i < effective.size(); i++) {
            BlockChange change = effective.get(i);
            if (!level.setBlock(change.pos(), change.state(), change.flags())) {
                restoreReverse(snapshots, i - 1);
                for (BlockChange denied : effective) {
                    rememberDenial(level, denied.pos(), action,
                            level.getGameTime(), RuntimeConfig.snapshot()
                                    .permissionDeniedRetryTicks());
                }
                return false;
            }
        }
        if (action != WorldActionType.TEMPORARY_CLEANUP
                && EventHooks.onMultiBlockPlace(actor, snapshots,
                Direction.UP)) {
            restoreReverse(snapshots, snapshots.size() - 1);
            for (BlockChange change : effective) {
                rememberDenial(level, change.pos(), action,
                        level.getGameTime(), RuntimeConfig.snapshot()
                                .permissionDeniedRetryTicks());
            }
            return false;
        }
        for (BlockChange change : changes) {
            clearDenial(level, change.pos(), action);
        }
        return true;
    }

    public static boolean destroyBlock(ServerLevel level,
                                       LivingEntity actor,
                                       BlockPos pos, boolean drop) {
        PermissionDecision decision = check(level, actor,
                WorldActionType.BREAK_BLOCK, pos,
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        if (!decision.allowed()) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.canEntityDestroy(level, pos, actor)
                || !EventHooks.onEntityDestroyBlock(actor, pos, state)) {
            rememberDenial(level, pos, WorldActionType.BREAK_BLOCK,
                    level.getGameTime(), RuntimeConfig.snapshot()
                            .permissionDeniedRetryTicks());
            return false;
        }
        boolean changed = level.destroyBlock(pos, drop, actor);
        if (changed) {
            clearDenial(level, pos, WorldActionType.BREAK_BLOCK);
        } else {
            rememberDenial(level, pos, WorldActionType.BREAK_BLOCK,
                    level.getGameTime(), RuntimeConfig.snapshot()
                            .permissionDeniedRetryTicks());
        }
        return changed;
    }

    public static boolean canMutateContainer(ServerLevel level,
                                             @Nullable Entity actor,
                                             BlockPos pos) {
        return check(level, actor, WorldActionType.CONTAINER_MUTATION,
                pos, null).allowed();
    }

    public static boolean canSpawnEntity(ServerLevel level,
                                         @Nullable Entity actor,
                                         BlockPos pos) {
        return check(level, actor, WorldActionType.ENTITY_SPAWN,
                pos, null).allowed();
    }

    public static void clearLevel(ServerLevel level) {
        DENIED.remove(level);
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.max(Math.abs(first.getX() - second.getX()),
                Math.abs(first.getZ() - second.getZ()));
    }

    private static boolean retryReady(ServerLevel level, BlockPos pos,
                                      WorldActionType action, long gameTime) {
        Map<DeniedKey, Long> levelEntries = DENIED.get(level);
        if (levelEntries == null) return true;
        return gameTime >= levelEntries.getOrDefault(key(pos, action), 0L);
    }

    private static void rememberDenial(ServerLevel level, BlockPos pos,
                                       WorldActionType action, long gameTime,
                                       int retryTicks) {
        DENIED.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(key(pos, action), gameTime + retryTicks);
    }

    private static void clearDenial(ServerLevel level, BlockPos pos,
                                    WorldActionType action) {
        Map<DeniedKey, Long> levelEntries = DENIED.get(level);
        if (levelEntries == null) return;
        levelEntries.remove(key(pos, action));
        if (levelEntries.isEmpty()) DENIED.remove(level);
    }

    private static DeniedKey key(BlockPos pos, WorldActionType action) {
        return new DeniedKey(pos.asLong(), action);
    }

    private static void restoreReverse(List<BlockSnapshot> snapshots,
                                       int last) {
        for (int i = last; i >= 0; i--) {
            snapshots.get(i).restore();
        }
    }
}
