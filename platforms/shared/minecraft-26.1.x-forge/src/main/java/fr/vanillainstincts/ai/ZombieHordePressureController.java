package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

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

    public static void contribute(Zombie zombie, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (zombie == null || plan == null || level == null
                || zombie.isPassenger() || !zombie.onGround()
                || ZombieTacticsController.stallTicks(zombie)
                < ZombieRules.PRESSURE_STALL_TICKS
                || gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(zombie.getPersistentData(), PRESSURE_READY)) {
            return;
        }

        LivingEntity target = zombie.getTarget();
        Vec3 destination = target != null && target.isAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                ? target.position()
                : MobPerceptionMemory.bestKnownPosition(zombie, gameTime)
                .orElse(null);
        if (destination == null) {
            return;
        }

        Vec3 direction = horizontalDirection(zombie.position(), destination);
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
        List<Zombie> rear = rearAllies(zombie, level, direction);
        Optional<Vec3> impulse = pressureImpulse(zombie, level, direction,
                obstacleHeight, rear.size());
        if (impulse.isEmpty()) {
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
    public static List<Zombie> rearAllies(Zombie zombie, ServerLevel level,
                                          Vec3 direction) {
        if (zombie == null || level == null || direction == null) {
            return List.of();
        }
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return List.of();
        }
        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);

        List<Zombie> candidates = level.getEntitiesOfClass(Zombie.class,
                zombie.getBoundingBox().inflate(ZombieRules.PRESSURE_SCAN_RADIUS),
                other -> other != zombie && other.isAlive()
                        && !other.isPassenger()
                        && Math.abs(other.getY() - zombie.getY()) <= 1.75D);
        List<Zombie> result = new ArrayList<>();
        for (Zombie other : candidates) {
            Vec3 relative = other.position().subtract(zombie.position())
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
            return List.copyOf(result.subList(0,
                    ZombieRules.PRESSURE_MAX_REAR_ALLIES));
        }
        return List.copyOf(result);
    }

    /**
     * Measures the continuous collision wall immediately in front, capped at
     * max+1 so callers can distinguish a forbidden four-block obstacle.
     */
    public static int obstacleHeight(Zombie zombie, ServerLevel level,
                                     Vec3 direction) {
        if (zombie == null || level == null || direction == null) {
            return 0;
        }
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-8D) {
            return 0;
        }
        horizontal = horizontal.normalize();
        BlockPos front = BlockPos.containing(
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
    public static Optional<Vec3> pressureImpulse(Zombie zombie,
                                                  ServerLevel level,
                                                  Vec3 direction,
                                                  int obstacleHeight,
                                                  int rearCount) {
        if (zombie == null || level == null || direction == null
                || rearCount < ZombieRules.PRESSURE_MIN_PUSHERS
                || obstacleHeight <= 0
                || obstacleHeight > ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT) {
            return Optional.empty();
        }
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
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

    public static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        if (from == null || to == null) return null;
        Vec3 direction = to.subtract(from).multiply(1.0D, 0.0D, 1.0D);
        return direction.lengthSqr() < 1.0E-8D ? null : direction.normalize();
    }

    private static boolean hasStackClearance(Zombie zombie,
                                             ServerLevel level,
                                             Vec3 direction,
                                             int obstacleHeight) {
        BlockPos front = BlockPos.containing(
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
