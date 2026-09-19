package fr.vanillainstincts.permission;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

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
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import fr.vanillainstincts.compat.LegacyTag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import javax.annotation.Nullable;

/** Central transactional permission gateway for every mod-owned world mutation. */
public final class WorldPermissionService {
    public static class BlockChange {
        private final BlockPos pos;
        private final IBlockState state;
        private final int flags;

        public BlockPos pos() { return this.pos; }

        public IBlockState state() { return this.state; }

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

        public BlockChange(BlockPos pos, IBlockState state, int flags) {
            pos = immutableBlockPos(pos);
        
            this.pos = pos;
            this.state = state;
            this.flags = flags;
        }
    }

    public static final LegacyTag<Block> PROTECTED_FROM_AI = Minecraft115TagCompat.blockTag(new ResourceLocation(
                    VanillaInstincts.MOD_ID, "protected_from_ai"));

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

    private static final IdentityHashMap<WorldServer,
            Map<DeniedKey, Long>> DENIED = new IdentityHashMap<>();

    private WorldPermissionService() {
    }

    public static PermissionDecision check(WorldServer level,
                                           @Nullable Entity actor,
                                           WorldActionType action,
                                           BlockPos pos,
                                           @Nullable IBlockState replacement) {
        return check(level, actor, action, pos, replacement, true);
    }

    private static PermissionDecision check(WorldServer level,
                                            @Nullable Entity actor,
                                            WorldActionType action,
                                            BlockPos pos,
                                            @Nullable IBlockState replacement,
                                            boolean postCustomEvent) {
        if (action == null) return PermissionDecision.MOD_DISABLED;
        ConfigSnapshot config = RuntimeConfig.snapshot();
        boolean chunkLoaded = level.isBlockLoaded(pos);
        boolean mobGriefingAllowed = !config.respectMobGriefing()
                || actor == null
                || actor instanceof EntityPlayer
                || !action.respectsMobGriefing()
                || level.getGameRules().getBoolean("mobGriefing");
        boolean spawnProtected = config.protectSpawnArea()
                && config.spawnProtectionRadius() > 0
                && !(actor instanceof EntityPlayer)
                && level.provider.getDimensionId() == 0
                && horizontalDistance(level.getSpawnPoint(), pos)
                <= config.spawnProtectionRadius();
        boolean retryReady = retryReady(level, pos, action,
                level.getTotalWorldTime());
        boolean blockEntityPresent = chunkLoaded
                && config.protectBlockEntities()
                && level.getTileEntity(pos) != null;
        boolean protectedBlock = chunkLoaded
                && Minecraft115TagCompat.blockStateIs(level.getBlockState(pos), PROTECTED_FROM_AI);

        // allowWorldChanges is the safety switch for AI/mob-driven edits.
        // A real player's explicit action (for example igniting a crying-
        // obsidian portal) must keep the player's normal build permissions.
        boolean worldChangesAllowed = config.allowWorldChanges()
                || actor instanceof EntityPlayer;
        PermissionDecision preliminary = WorldPermissionPolicy.evaluate(action,
                config.enabled(), worldChangesAllowed,
                config.allowItemChanges(), mobGriefingAllowed,
                chunkLoaded, level.getWorldBorder().contains(pos),
                spawnProtected, blockEntityPresent, protectedBlock,
                retryReady, true);
        if (!preliminary.allowed()) {
            if (preliminary != PermissionDecision.RETRY_COOLDOWN) {
                rememberDenial(level, pos, action, level.getTotalWorldTime(),
                        config.permissionDeniedRetryTicks());
            }
            return preliminary;
        }
        if (postCustomEvent && MinecraftForge.EVENT_BUS.post(
                new WorldActionPermissionEvent(level, actor, action,
                        fr.vanillainstincts.compat.LegacyJava8.listOf(pos), replacement))) {
            rememberDenial(level, pos, action, level.getTotalWorldTime(),
                    config.permissionDeniedRetryTicks());
            return PermissionDecision.EVENT_DENIED;
        }
        return PermissionDecision.ALLOWED;
    }

    public static boolean setBlock(WorldServer level, @Nullable Entity actor,
                                   BlockPos pos, IBlockState replacement,
                                   int flags, WorldActionType action) {
        if (!check(level, actor, action, pos, replacement).allowed()) {
            return false;
        }
        if (level.getBlockState(pos).equals(replacement)) {
            clearDenial(level, pos, action);
            return true;
        }
        BlockSnapshot rollbackSnapshot = BlockSnapshot.getBlockSnapshot(level, pos, flags);
        if (!level.setBlockState(pos, replacement, flags)) {
            rememberDenial(level, pos, action, level.getTotalWorldTime(),
                    RuntimeConfig.snapshot().permissionDeniedRetryTicks());
            return false;
        }
        if (action != WorldActionType.TEMPORARY_CLEANUP) {
            /*
             * Forge's EntityPlaceEvent reads getCurrentState() for a EntityPlayer,
             * but reads the snapshot state itself for every other Entity.  A
             * pre-mutation snapshot is therefore correct for players (it also
             * supplies rollback state), while passing that same snapshot for a
             * villager would incorrectly announce AIR when the villager has
             * just planted a crop.  Capture a post-mutation event snapshot for
             * non-player actors, while retaining the original snapshot solely
             * for rollback if another mod cancels the placement event.
             */
            BlockSnapshot eventSnapshot = actor instanceof EntityPlayer
                    ? rollbackSnapshot
                    : BlockSnapshot.getBlockSnapshot(level, pos, flags);
            // Forge 14.22/14.23 do not expose one stable synthetic placement helper
            // signature across both 1.12, 1.12.1 and 1.12.2. Vanilla Instincts' own
            // permission event above remains the common cancellation point.
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
    public static boolean setPlayerBlocksAtomically(WorldServer level,
                                                    EntityPlayer player,
                                                    List<BlockChange> changes) {
        if (level == null || player == null || changes == null
                || changes.isEmpty() || !RuntimeConfig.snapshot().enabled()) {
            return false;
        }
        for (BlockChange change : changes) {
            if (!level.isBlockLoaded(change.pos())
                    || !level.getWorldBorder().contains(change.pos())) {
                return false;
            }
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            snapshots.add(BlockSnapshot.getBlockSnapshot(level, change.pos(), change.flags()));
        }
        for (int i = 0; i < changes.size(); i++) {
            BlockChange change = changes.get(i);
            if (!level.setBlockState(change.pos(), change.state(), change.flags())) {
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
            WorldServer level, List<BlockChange> changes) {
        if (level == null || changes == null || changes.isEmpty()
                || !RuntimeConfig.snapshot().enabled()) {
            return false;
        }
        for (BlockChange change : changes) {
            if (!level.isBlockLoaded(change.pos())
                    || !level.getWorldBorder().contains(change.pos())) {
                return false;
            }
        }
        List<BlockSnapshot> snapshots = new ArrayList<>(changes.size());
        for (BlockChange change : changes) {
            snapshots.add(BlockSnapshot.getBlockSnapshot(level, change.pos(), change.flags()));
        }
        for (int i = 0; i < changes.size(); i++) {
            BlockChange change = changes.get(i);
            if (!level.setBlockState(change.pos(), change.state(), change.flags())) {
                restoreReverse(snapshots, i - 1);
                return false;
            }
        }
        return true;
    }

    public static boolean setBlocksAtomically(WorldServer level,
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
                        level.getTotalWorldTime(), RuntimeConfig.snapshot()
                                .permissionDeniedRetryTicks());
            }
            return false;
        }

        List<BlockSnapshot> snapshots = new ArrayList<>(effective.size());
        for (BlockChange change : effective) {
            snapshots.add(BlockSnapshot.getBlockSnapshot(level, change.pos(), change.flags()));
        }
        for (int i = 0; i < effective.size(); i++) {
            BlockChange change = effective.get(i);
            if (!level.setBlockState(change.pos(), change.state(), change.flags())) {
                restoreReverse(snapshots, i - 1);
                for (BlockChange denied : effective) {
                    rememberDenial(level, denied.pos(), action,
                            level.getTotalWorldTime(), RuntimeConfig.snapshot()
                                    .permissionDeniedRetryTicks());
                }
                return false;
            }
        }
        // Multi-block synthetic placement hooks differ between Forge 14.22 and
        // 14.23; the common Vanilla Instincts permission event is authoritative.
        for (BlockChange change : changes) {
            clearDenial(level, change.pos(), action);
        }
        return true;
    }

    public static boolean destroyBlock(WorldServer level,
                                       EntityLivingBase actor,
                                       BlockPos pos, boolean drop) {
        PermissionDecision decision = check(level, actor,
                WorldActionType.BREAK_BLOCK, pos,
                net.minecraft.init.Blocks.air.getDefaultState());
        if (!decision.allowed()) return false;
        IBlockState state = level.getBlockState(pos);
        if (!legacyEntityDestroyHook(actor, pos, state)) {
            rememberDenial(level, pos, WorldActionType.BREAK_BLOCK,
                    level.getTotalWorldTime(), RuntimeConfig.snapshot()
                            .permissionDeniedRetryTicks());
            return false;
        }
        boolean changed = level.destroyBlock(pos, drop);
        if (changed) {
            clearDenial(level, pos, WorldActionType.BREAK_BLOCK);
        } else {
            rememberDenial(level, pos, WorldActionType.BREAK_BLOCK,
                    level.getTotalWorldTime(), RuntimeConfig.snapshot()
                            .permissionDeniedRetryTicks());
        }
        return changed;
    }

    public static boolean canMutateContainer(WorldServer level,
                                             @Nullable Entity actor,
                                             BlockPos pos) {
        return check(level, actor, WorldActionType.CONTAINER_MUTATION,
                pos, null).allowed();
    }

    public static boolean canSpawnEntity(WorldServer level,
                                         @Nullable Entity actor,
                                         BlockPos pos) {
        return check(level, actor, WorldActionType.ENTITY_SPAWN,
                pos, null).allowed();
    }

    public static void clearLevel(WorldServer level) {
        DENIED.remove(level);
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.max(Math.abs(first.getX() - second.getX()),
                Math.abs(first.getZ() - second.getZ()));
    }

    private static boolean retryReady(WorldServer level, BlockPos pos,
                                      WorldActionType action, long gameTime) {
        Map<DeniedKey, Long> levelEntries = DENIED.get(level);
        if (levelEntries == null) return true;
        return gameTime >= levelEntries.getOrDefault(key(pos, action), 0L);
    }

    private static void rememberDenial(WorldServer level, BlockPos pos,
                                       WorldActionType action, long gameTime,
                                       int retryTicks) {
        DENIED.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(key(pos, action), gameTime + retryTicks);
    }

    private static void clearDenial(WorldServer level, BlockPos pos,
                                    WorldActionType action) {
        Map<DeniedKey, Long> levelEntries = DENIED.get(level);
        if (levelEntries == null) return;
        levelEntries.remove(key(pos, action));
        if (levelEntries.isEmpty()) DENIED.remove(level);
    }

    private static DeniedKey key(BlockPos pos, WorldActionType action) {
        return new DeniedKey(pos.toLong(), action);
    }

    private static void restoreReverse(List<BlockSnapshot> snapshots,
                                       int last) {
        for (int i = last; i >= 0; i--) {
            snapshots.get(i).restore();
        }
    }
    private static boolean legacyEntityDestroyHook(EntityLivingBase actor, BlockPos pos,
                                                   IBlockState state) {
        try {
            java.lang.reflect.Method method = ForgeEventFactory.class.getMethod(
                    "onEntityDestroyBlock", EntityLivingBase.class, BlockPos.class, IBlockState.class);
            Object result = method.invoke(null, actor, pos, state);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (ReflectiveOperationException ignored) {
            // Forge 1.10.x has no equivalent generic living-entity destroy hook.
            return true;
        }
    }
}
