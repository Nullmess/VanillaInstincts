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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ITag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;

/** Central transactional permission gateway for every mod-owned world mutation. */
public final class WorldPermissionService {
    public static class BlockChange {
        private final BlockPos pos;
        private final BlockState state;
        private final int flags;

        public BlockPos pos() { return this.pos; }

        public BlockState state() { return this.state; }

        public int flags() { return this.flags; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof BlockChange)) return false;
            BlockChange that = (BlockChange) other;
            return java.util.Objects.equals(this.pos, that.pos) && java.util.Objects.equals(this.state, that.state) && this.flags == that.flags;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.pos, this.state, this.flags); }

        @Override
        public String toString() {
            return "BlockChange[" + "pos=" + this.pos + ", " + "state=" + this.state + ", " + "flags=" + this.flags + "]";
        }

        public BlockChange(BlockPos pos, BlockState state, int flags) {
            pos = pos.immutable();
        
            this.pos = pos;
            this.state = state;
            this.flags = flags;
        }
    }

    public static final ITag.INamedTag<Block> PROTECTED_FROM_AI = BlockTags.bind(new ResourceLocation(
                    VanillaInstincts.MOD_ID, "protected_from_ai").toString());

    private static class DeniedKey {
        private final long position;
        private final WorldActionType action;

        public DeniedKey(long position, WorldActionType action) {
            this.position = position;
            this.action = action;
        }

        public long position() { return this.position; }

        public WorldActionType action() { return this.action; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DeniedKey)) return false;
            DeniedKey that = (DeniedKey) other;
            return this.position == that.position && java.util.Objects.equals(this.action, that.action);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.action); }

        @Override
        public String toString() {
            return "DeniedKey[" + "position=" + this.position + ", " + "action=" + this.action + "]";
        }

    }

    private static final IdentityHashMap<ServerWorld,
            Map<DeniedKey, Long>> DENIED = new IdentityHashMap<>();

    private WorldPermissionService() {
    }

    public static PermissionDecision check(ServerWorld level,
                                           @Nullable Entity actor,
                                           WorldActionType action,
                                           BlockPos pos,
                                           @Nullable BlockState replacement) {
        return check(level, actor, action, pos, replacement, true);
    }

    private static PermissionDecision check(ServerWorld level,
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
                || actor instanceof PlayerEntity
                || !action.respectsMobGriefing()
                || ForgeEventFactory.getMobGriefingEvent(level, actor);
        boolean spawnProtected = config.protectSpawnArea()
                && config.spawnProtectionRadius() > 0
                && !(actor instanceof PlayerEntity)
                && World.OVERWORLD.equals(level.dimension())
                && horizontalDistance(level.getSharedSpawnPos(), pos)
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
                || actor instanceof PlayerEntity;
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
        if (postCustomEvent && MinecraftForge.EVENT_BUS.post(
                new WorldActionPermissionEvent(level, actor, action,
                        fr.vanillainstincts.compat.LegacyJava8.listOf(pos), replacement))) {
            rememberDenial(level, pos, action, level.getGameTime(),
                    config.permissionDeniedRetryTicks());
            return PermissionDecision.EVENT_DENIED;
        }
        return PermissionDecision.ALLOWED;
    }

    public static boolean setBlock(ServerWorld level, @Nullable Entity actor,
                                   BlockPos pos, BlockState replacement,
                                   int flags, WorldActionType action) {
        if (!check(level, actor, action, pos, replacement).allowed()) {
            return false;
        }
        if (level.getBlockState(pos).equals(replacement)) {
            clearDenial(level, pos, action);
            return true;
        }
        BlockSnapshot rollbackSnapshot = BlockSnapshot.create(level, pos, flags);
        if (!level.setBlock(pos, replacement, flags)) {
            rememberDenial(level, pos, action, level.getGameTime(),
                    RuntimeConfig.snapshot().permissionDeniedRetryTicks());
            return false;
        }
        if (action != WorldActionType.TEMPORARY_CLEANUP) {
            /*
             * Forge's EntityPlaceEvent reads getCurrentState() for a PlayerEntity,
             * but reads the snapshot state itself for every other Entity.  A
             * pre-mutation snapshot is therefore correct for players (it also
             * supplies rollback state), while passing that same snapshot for a
             * villager would incorrectly announce AIR when the villager has
             * just planted a crop.  Capture a post-mutation event snapshot for
             * non-player actors, while retaining the original snapshot solely
             * for rollback if another mod cancels the placement event.
             */
            BlockSnapshot eventSnapshot = actor instanceof PlayerEntity
                    ? rollbackSnapshot
                    : BlockSnapshot.create(level, pos, flags);
            if (ForgeEventFactory.onBlockPlace(actor, eventSnapshot, Direction.UP)) {
                rollbackSnapshot.restore(true);
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
    public static boolean setPlayerBlocksAtomically(ServerWorld level,
                                                    PlayerEntity player,
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
            snapshots.add(BlockSnapshot.create(level, change.pos(), change.flags()));
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
            ServerWorld level, List<BlockChange> changes) {
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
            snapshots.add(BlockSnapshot.create(level, change.pos(), change.flags()));
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

    public static boolean setBlocksAtomically(ServerWorld level,
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
                .collect(java.util.stream.Collectors.toList());
        if (effective.isEmpty()) {
            for (BlockChange change : changes) {
                clearDenial(level, change.pos(), action);
            }
            return true;
        }
        List<BlockPos> positions = effective.stream()
                .map(BlockChange::pos).collect(java.util.stream.Collectors.toList());
        if (MinecraftForge.EVENT_BUS.post(new WorldActionPermissionEvent(level,
                actor, action, positions, null))) {
            for (BlockChange change : effective) {
                rememberDenial(level, change.pos(), action,
                        level.getGameTime(), RuntimeConfig.snapshot()
                                .permissionDeniedRetryTicks());
            }
            return false;
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(effective.size());
        for (BlockChange change : effective) {
            snapshots.add(BlockSnapshot.create(level, change.pos(), change.flags()));
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
                && ForgeEventFactory.onMultiBlockPlace(actor, snapshots,
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

    public static boolean destroyBlock(ServerWorld level,
                                       LivingEntity actor,
                                       BlockPos pos, boolean drop) {
        PermissionDecision decision = check(level, actor,
                WorldActionType.BREAK_BLOCK, pos,
                net.minecraft.block.Blocks.AIR.defaultBlockState());
        if (!decision.allowed()) return false;
        BlockState state = level.getBlockState(pos);
        if (!state.canEntityDestroy(level, pos, actor)
                || !ForgeEventFactory.onEntityDestroyBlock(actor, pos, state)) {
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

    public static boolean canMutateContainer(ServerWorld level,
                                             @Nullable Entity actor,
                                             BlockPos pos) {
        return check(level, actor, WorldActionType.CONTAINER_MUTATION,
                pos, null).allowed();
    }

    public static boolean canSpawnEntity(ServerWorld level,
                                         @Nullable Entity actor,
                                         BlockPos pos) {
        return check(level, actor, WorldActionType.ENTITY_SPAWN,
                pos, null).allowed();
    }

    public static void clearLevel(ServerWorld level) {
        DENIED.remove(level);
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.max(Math.abs(first.getX() - second.getX()),
                Math.abs(first.getZ() - second.getZ()));
    }

    private static boolean retryReady(ServerWorld level, BlockPos pos,
                                      WorldActionType action, long gameTime) {
        Map<DeniedKey, Long> levelEntries = DENIED.get(level);
        if (levelEntries == null) return true;
        return gameTime >= levelEntries.getOrDefault(key(pos, action), 0L);
    }

    private static void rememberDenial(ServerWorld level, BlockPos pos,
                                       WorldActionType action, long gameTime,
                                       int retryTicks) {
        DENIED.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(key(pos, action), gameTime + retryTicks);
    }

    private static void clearDenial(ServerWorld level, BlockPos pos,
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
