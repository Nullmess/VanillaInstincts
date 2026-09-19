package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Optional, bounded post-explosion fire for Creeper Tactics 3.0. */
public final class CreeperExplosionFireController {
    private record PendingFire(Creeper creeper, BlockPos center,
                               long dueGameTime) {
    }

    private static final IdentityHashMap<ServerLevel, List<PendingFire>>
            PENDING = new IdentityHashMap<>();
    private static final int[][] OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };
    private static final int MAX_FIRE_BLOCKS = 4;

    private CreeperExplosionFireController() {
    }

    public static void schedule(Creeper creeper, ServerLevel level,
                                BlockPos center, long gameTime) {
        if (creeper == null || level == null || center == null
                || !VanillaInstinctsServerConfig
                .creeperExplosionFireEnabled()) {
            return;
        }
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingFire(creeper, center.immutable(), gameTime + 1L));
    }

    public static void tick(ServerLevel level, long gameTime) {
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

    public static void clearLevel(ServerLevel level) {
        if (level != null) PENDING.remove(level);
    }

    private static void ignite(ServerLevel level, Creeper creeper,
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
