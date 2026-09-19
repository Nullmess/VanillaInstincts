package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

/** Optional, bounded post-explosion fire for CreeperEntity Tactics 3.0. */
public final class CreeperExplosionFireController {
    private static class PendingFire {
        private final CreeperEntity creeper;
        private final BlockPos center;
        private final long dueGameTime;

        public PendingFire(CreeperEntity creeper, BlockPos center, long dueGameTime) {
            this.creeper = creeper;
            this.center = center;
            this.dueGameTime = dueGameTime;
        }

        public CreeperEntity creeper() { return this.creeper; }

        public BlockPos center() { return this.center; }

        public long dueGameTime() { return this.dueGameTime; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingFire)) return false;
            PendingFire that = (PendingFire) other;
            return java.util.Objects.equals(this.creeper, that.creeper) && java.util.Objects.equals(this.center, that.center) && this.dueGameTime == that.dueGameTime;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.creeper, this.center, this.dueGameTime); }

        @Override
        public String toString() {
            return "PendingFire[" + "creeper=" + this.creeper + ", " + "center=" + this.center + ", " + "dueGameTime=" + this.dueGameTime + "]";
        }

    }

    private static final IdentityHashMap<ServerWorld, List<PendingFire>>
            PENDING = new IdentityHashMap<>();
    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };
    private static final int MAX_FIRE_BLOCKS = 4;

    private CreeperExplosionFireController() {
    }

    public static void schedule(CreeperEntity creeper, ServerWorld level,
                                BlockPos center, long gameTime) {
        if (creeper == null || level == null || center == null
                || !VanillaInstinctsServerConfig
                .creeperExplosionFireEnabled()) {
            return;
        }
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingFire(creeper, center.immutable(), gameTime + 1L));
    }

    public static void tick(ServerWorld level, long gameTime) {
        List<PendingFire> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) return;
        Iterator<PendingFire> iterator = pending.iterator();
        while (iterator.hasNext()) {
            PendingFire fire = iterator.next();
            if (fire.dueGameTime() > gameTime) continue;
            ignite(level, fire.creeper(), fire.center());
            iterator.remove();
        }
        if (pending.isEmpty()) PENDING.remove(level);
    }

    public static void clearLevel(ServerWorld level) {
        if (level != null) PENDING.remove(level);
    }

    private static void ignite(ServerWorld level, CreeperEntity creeper,
                               BlockPos center) {
        BlockState fire = Blocks.FIRE.defaultBlockState();
        int placed = 0;
        int start = Math.floorMod(creeper.getId(), OFFSETS.length);
        for (int i = 0; i < OFFSETS.length && placed < MAX_FIRE_BLOCKS; i++) {
            int[] offset = OFFSETS[(start + i) % OFFSETS.length];
            BlockPos pos = center.offset(offset[0], 0, offset[1]);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()
                    || !fire.canSurvive(level, pos)) {
                continue;
            }
            if (WorldPermissionService.setBlock(level, creeper, pos, fire, 3,
                    WorldActionType.PLACE_BLOCK)) {
                placed++;
            }
        }
    }
}
