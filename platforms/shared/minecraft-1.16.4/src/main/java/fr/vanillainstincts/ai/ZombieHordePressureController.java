package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Local physical cooperation for stalled zombies.  No shared omniscience and
 * no generated blocks: zombies only gain a small push or a bounded body-stack
 * boost when real allies are physically pressing from behind.
 */
public final class ZombieHordePressureController {
    private static final String PRESSURE_READY =
            "vanillainstincts_zombie_pressure_ready";

    private ZombieHordePressureController() {
    }

    public static void contribute(ZombieEntity zombie, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (zombie == null || plan == null || level == null
                || zombie.isPassenger() || !zombie.isOnGround()
                || ZombieTacticsController.stallTicks(zombie)
                < ZombieRules.PRESSURE_STALL_TICKS
                || gameTime < zombie.getPersistentData().getLong(PRESSURE_READY)) {
            return;
        }

        LivingEntity target = zombie.getTarget();
        Vector3d destination = target != null && target.isAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                ? target.position()
                : MobPerceptionMemory.bestKnownPosition(zombie, gameTime)
                .orElse(null);
        if (destination == null) {
            return;
        }

        Vector3d direction = horizontalDirection(zombie.position(), destination);
        if (direction == null) {
            return;
        }
        int obstacleHeight = obstacleHeight(zombie, level, direction);
        if (obstacleHeight <= 0
                || obstacleHeight > ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT) {
            return;
        }

        if (!VanillaInstinctsScheduler.claim(level, zombie,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }
        List<ZombieEntity> rear = rearAllies(zombie, level, direction);
        Optional<Vector3d> impulse = pressureImpulse(zombie, level, direction,
                obstacleHeight, rear.size());
        if (!impulse.isPresent()) {
            return;
        }

        plan.offerImpulse(VanillaInstinctsState.HORDE_PRESSURE,
                ActionOwner.ZOMBIE_TACTICS,
                ZombieRules.PRIORITY_HORDE_PRESSURE,
                impulse.get(), 10,
                () -> zombie.getPersistentData().putLong(PRESSURE_READY,
                        gameTime + ZombieRules.PRESSURE_COOLDOWN_TICKS));
    }

    /** Returns at most PRESSURE_MAX_REAR_ALLIES aligned behind this zombie. */
    public static List<ZombieEntity> rearAllies(ZombieEntity zombie, ServerWorld level,
                                          Vector3d direction) {
        if (zombie == null || level == null || direction == null) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        Vector3d horizontal = new Vector3d(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        horizontal = horizontal.normalize();
        Vector3d side = new Vector3d(-horizontal.z, 0.0D, horizontal.x);

        List<ZombieEntity> candidates = level.getEntitiesOfClass(ZombieEntity.class,
                zombie.getBoundingBox().inflate(ZombieRules.PRESSURE_SCAN_RADIUS),
                other -> other != zombie && other.isAlive()
                        && !other.isPassenger()
                        && Math.abs(other.getY() - zombie.getY()) <= 1.75D);
        List<ZombieEntity> result = new ArrayList<>();
        for (ZombieEntity other : candidates) {
            Vector3d relative = other.position().subtract(zombie.position())
                    .multiply(1.0D, 0.0D, 1.0D);
            double behind = relative.dot(horizontal);
            double lateral = Math.abs(relative.dot(side));
            if (behind < -0.20D
                    && lateral <= ZombieRules.PRESSURE_MAX_LATERAL_DISTANCE) {
                result.add(other);
            }
        }
        result.sort(Comparator.comparingDouble(zombie::distanceToSqr));
        if (result.size() > ZombieRules.PRESSURE_MAX_REAR_ALLIES) {
            return fr.vanillainstincts.compat.LegacyJava8.copyList(result.subList(0,
                    ZombieRules.PRESSURE_MAX_REAR_ALLIES));
        }
        return fr.vanillainstincts.compat.LegacyJava8.copyList(result);
    }

    /**
     * Measures the continuous collision wall immediately in front, capped at
     * max+1 so callers can distinguish a forbidden four-block obstacle.
     */
    public static int obstacleHeight(ZombieEntity zombie, ServerWorld level,
                                     Vector3d direction) {
        if (zombie == null || level == null || direction == null) {
            return 0;
        }
        Vector3d horizontal = new Vector3d(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return 0;
        }
        horizontal = horizontal.normalize();
        BlockPos front = new BlockPos(
                zombie.getX() + horizontal.x * 0.9D,
                zombie.getY() + 0.15D,
                zombie.getZ() + horizontal.z * 0.9D);
        int height = 0;
        int cap = ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT + 1;
        for (int dy = 0; dy < cap; dy++) {
            BlockPos pos = front.above(dy);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            height++;
        }
        return height;
    }

    /** Pure enough for deterministic GameTests after the fixture is built. */
    public static Optional<Vector3d> pressureImpulse(ZombieEntity zombie,
                                                  ServerWorld level,
                                                  Vector3d direction,
                                                  int obstacleHeight,
                                                  int rearCount) {
        if (zombie == null || level == null || direction == null
                || rearCount < ZombieRules.PRESSURE_MIN_PUSHERS
                || obstacleHeight <= 0
                || obstacleHeight > ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT) {
            return Optional.empty();
        }
        Vector3d horizontal = new Vector3d(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return Optional.empty();
        }
        horizontal = horizontal.normalize();

        if (obstacleHeight == 1) {
            return Optional.of(horizontal.scale(ZombieRules.PRESSURE_HORIZONTAL));
        }
        if (rearCount < ZombieRules.PRESSURE_MIN_STACKERS
                || !hasStackClearance(zombie, level, horizontal,
                obstacleHeight)) {
            return Optional.empty();
        }
        double vertical = ZombieRules.PRESSURE_STACK_VERTICAL
                + (obstacleHeight - 2)
                * ZombieRules.PRESSURE_STACK_EXTRA_VERTICAL;
        return Optional.of(horizontal.scale(ZombieRules.PRESSURE_STACK_HORIZONTAL)
                .add(0.0D, vertical, 0.0D));
    }

    public static Vector3d horizontalDirection(Vector3d from, Vector3d to) {
        if (from == null || to == null) return null;
        Vector3d direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 1.0E-8D ? null : direction.normalize();
    }

    private static boolean hasStackClearance(ZombieEntity zombie,
                                             ServerWorld level,
                                             Vector3d direction,
                                             int obstacleHeight) {
        BlockPos front = new BlockPos(
                zombie.getX() + direction.x * 0.9D,
                zombie.getY() + 0.15D,
                zombie.getZ() + direction.z * 0.9D);
        BlockPos top = front.above(obstacleHeight);
        // Two clear cells above the obstacle are enough for the zombie's body.
        return level.getBlockState(top).getCollisionShape(level, top).isEmpty()
                && level.getBlockState(top.above())
                .getCollisionShape(level, top.above()).isEmpty()
                && level.getFluidState(top).isEmpty()
                && level.getFluidState(top.above()).isEmpty();
    }
}
