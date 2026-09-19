package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.block.BlockDoor;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.util.AxisAlignedBB;
import fr.vanillainstincts.compat.Vec3;
/** Ferme une porte ouverte après le passage réel du villageois. */
public final class VillagerDoorController {
    private VillagerDoorController() {
    }

    public static void maintain(EntityVillager villager,
                                VillagerRuntimeState state,
                                WorldServer level, long gameTime) {
        tryClosePending(villager, state, level, gameTime);
        if (state.doorToClose() != null || !state.doorScanReady(gameTime)) {
            return;
        }
        state.setDoorScanCooldown(gameTime,
                VillageSocialRules.VILLAGER_DOOR_SCAN_INTERVAL_TICKS);
        findNearbyOpenDoor(villager, level).ifPresent(pos -> {
            state.rememberOpenDoor(pos, gameTime
                    + VillageSocialRules.VILLAGER_DOOR_CLOSE_DELAY_TICKS);
            if (state.danger(gameTime) != null) {
                VillageDoorCoordinator.markPassage(level, pos, gameTime);
            }
        });
    }

    public static Optional<BlockPos> findNearbyOpenDoor(EntityVillager villager,
                                                        WorldServer level) {
        BlockPos center = entityBlockPos(villager);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = VillageSocialRules.VILLAGER_DOOR_SCAN_RADIUS;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos candidate = fr.vanillainstincts.compat.Minecraft112Compat.offset(center, x, y, z);
                    if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, candidate)) {
                        continue;
                    }
                    LegacyBlockState candidateState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, candidate);
                    if (!isOpenDoor(candidateState)) {
                        continue;
                    }
                    BlockPos lower = lowerHalf(candidate, candidateState);
                    LegacyBlockState lowerState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, lower);
                    if (!isOpenDoor(lowerState) || isPowered(lowerState)) {
                        continue;
                    }
                    double distance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(villager), 
                            Minecraft115VectorCompat.atCenterOf(lower));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = immutableBlockPos(lower);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isOpenDoor(LegacyBlockState state) {
        return state != null && state.getBlock() instanceof BlockDoor
                && state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorOpen(state);
    }

    public static boolean isPowered(LegacyBlockState state) {
        return state != null
                && state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.mechanismPowered(state);
    }

    private static void tryClosePending(EntityVillager villager,
                                        VillagerRuntimeState runtime,
                                        WorldServer level, long gameTime) {
        BlockPos remembered = runtime.doorToClose();
        if (remembered == null) {
            return;
        }
        LegacyBlockState rememberedState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, remembered);
        BlockPos lower = lowerHalf(remembered, rememberedState);
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, lower);
        if (!isOpenDoor(state)) {
            runtime.clearOpenDoor();
            return;
        }
        if (!runtime.doorCloseReady(gameTime)) {
            return;
        }
        if (isPowered(state) || doorwayOccupied(level, lower)
                || !VillageDoorCoordinator.mayClose(level, lower, gameTime)) {
            runtime.postponeDoorClose(gameTime,
                    VillageSocialRules.VILLAGER_DOOR_RETRY_TICKS);
            return;
        }

        BlockDoor door = (BlockDoor) state.getBlock();
        fr.vanillainstincts.compat.Minecraft17Compat.toggleDoor(door, level, lower, false);
        if (!isOpenDoor(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, lower))) {
            runtime.clearOpenDoor();
        } else {
            runtime.postponeDoorClose(gameTime,
                    VillageSocialRules.VILLAGER_DOOR_RETRY_TICKS);
        }
    }

    private static boolean doorwayOccupied(WorldServer level,
                                             BlockPos lower) {
        AxisAlignedBB doorway = AxisAlignedBB.getBoundingBox(lower.getX(), lower.getY(), lower.getZ(),
                lower.getX() + 1.0D, lower.getY() + 2.0D,
                lower.getZ() + 1.0D).expand(0.15D, 0.15D, 0.15D);
        return !fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityLivingBase.class, doorway,
                EntityLivingBase::isEntityAlive).isEmpty();
    }

    private static BlockPos lowerHalf(BlockPos position, LegacyBlockState state) {
        if (state != null
                && state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorUpper(state)) {
            return position.down();
        }
        return position;
    }
}
