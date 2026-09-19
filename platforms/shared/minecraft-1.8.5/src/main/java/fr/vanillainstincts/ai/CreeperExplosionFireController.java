package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;

/** Optional, bounded post-explosion fire for EntityCreeper Tactics 3.0. */
public final class CreeperExplosionFireController {
    private static class PendingFire {
        private final EntityCreeper creeper;
        private final BlockPos center;
        private final long dueGameTime;

        public PendingFire(EntityCreeper creeper, BlockPos center, long dueGameTime) {
            this.creeper = creeper;
            this.center = center;
            this.dueGameTime = dueGameTime;
        }

        public EntityCreeper creeper() { return this.creeper; }

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

    private static final IdentityHashMap<WorldServer, List<PendingFire>>
            PENDING = new IdentityHashMap<>();
    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };
    private static final int MAX_FIRE_BLOCKS = 4;

    private CreeperExplosionFireController() {
    }

    public static void schedule(EntityCreeper creeper, WorldServer level,
                                BlockPos center, long gameTime) {
        if (creeper == null || level == null || center == null
                || !VanillaInstinctsServerConfig
                .creeperExplosionFireEnabled()) {
            return;
        }
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingFire(creeper, immutableBlockPos(center), gameTime + 1L));
    }

    public static void tick(WorldServer level, long gameTime) {
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

    public static void clearLevel(WorldServer level) {
        if (level != null) PENDING.remove(level);
    }

    private static void ignite(WorldServer level, EntityCreeper creeper,
                               BlockPos center) {
        IBlockState fire = Blocks.fire.getDefaultState();
        int placed = 0;
        int start = Math.floorMod(creeper.getEntityId(), OFFSETS.length);
        for (int i = 0; i < OFFSETS.length && placed < MAX_FIRE_BLOCKS; i++) {
            int[] offset = OFFSETS[(start + i) % OFFSETS.length];
            BlockPos pos = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, offset[0], 0, offset[1]);
            if (!level.isBlockLoaded(pos) || !fr.vanillainstincts.compat.Minecraft112Compat.isAir(level.getBlockState(pos))
                    || !fire.getBlock().canPlaceBlockAt(level, pos)) {
                continue;
            }
            if (WorldPermissionService.setBlock(level, creeper, pos, fire, 3,
                    WorldActionType.PLACE_BLOCK)) {
                placed++;
            }
        }
    }
}
