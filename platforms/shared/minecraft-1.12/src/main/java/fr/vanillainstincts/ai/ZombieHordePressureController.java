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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.util.math.Vec3d;

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

    public static void contribute(EntityZombie zombie, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (zombie == null || plan == null || level == null
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(zombie) || !zombie.onGround
                || ZombieTacticsController.stallTicks(zombie)
                < ZombieRules.PRESSURE_STALL_TICKS
                || gameTime < zombie.getEntityData().getLong(PRESSURE_READY)) {
            return;
        }

        EntityLivingBase target = zombie.getAttackTarget();
        Vec3d destination = target != null && target.isEntityAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                ? target.getPositionVector()
                : MobPerceptionMemory.bestKnownPosition(zombie, gameTime)
                .orElse(null);
        if (destination == null) {
            return;
        }

        Vec3d direction = horizontalDirection(zombie.getPositionVector(), destination);
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
        List<EntityZombie> rear = rearAllies(zombie, level, direction);
        Optional<Vec3d> impulse = pressureImpulse(zombie, level, direction,
                obstacleHeight, rear.size());
        if (!impulse.isPresent()) {
            return;
        }

        plan.offerImpulse(VanillaInstinctsState.HORDE_PRESSURE,
                ActionOwner.ZOMBIE_TACTICS,
                ZombieRules.PRIORITY_HORDE_PRESSURE,
                impulse.get(), 10,
                () -> zombie.getEntityData().setLong(PRESSURE_READY,
                        gameTime + ZombieRules.PRESSURE_COOLDOWN_TICKS));
    }

    /** Returns at most PRESSURE_MAX_REAR_ALLIES aligned behind this zombie. */
    public static List<EntityZombie> rearAllies(EntityZombie zombie, WorldServer level,
                                          Vec3d direction) {
        if (zombie == null || level == null || direction == null) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        horizontal = horizontal.normalize();
        Vec3d side = new Vec3d(-horizontal.z, 0.0D, horizontal.x);

        List<EntityZombie> candidates = level.getEntitiesWithinAABB(EntityZombie.class,
                zombie.getEntityBoundingBox().grow(ZombieRules.PRESSURE_SCAN_RADIUS),
                other -> other != zombie && other.isEntityAlive()
                        && !fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(other)
                        && Math.abs(other.posY - zombie.posY) <= 1.75D);
        List<EntityZombie> result = new ArrayList<>();
        for (EntityZombie other : candidates) {
            Vec3d relative = fr.vanillainstincts.compat.Minecraft112Compat.multiply(other.getPositionVector().subtract(zombie.getPositionVector()), 1.0D, 0.0D, 1.0D);
            double behind = fr.vanillainstincts.compat.Minecraft112Compat.dot(relative, horizontal);
            double lateral = Math.abs(fr.vanillainstincts.compat.Minecraft112Compat.dot(relative, side));
            if (behind < -0.20D
                    && lateral <= ZombieRules.PRESSURE_MAX_LATERAL_DISTANCE) {
                result.add(other);
            }
        }
        result.sort(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, value))));
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
    public static int obstacleHeight(EntityZombie zombie, WorldServer level,
                                     Vec3d direction) {
        if (zombie == null || level == null || direction == null) {
            return 0;
        }
        Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return 0;
        }
        horizontal = horizontal.normalize();
        BlockPos front = new BlockPos(
                zombie.posX + horizontal.x * 0.9D,
                zombie.posY + 0.15D,
                zombie.posZ + horizontal.z * 0.9D);
        int height = 0;
        int cap = ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT + 1;
        for (int dy = 0; dy < cap; dy++) {
            BlockPos pos = front.up(dy);
            if (!fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(pos), level, pos)) {
                break;
            }
            height++;
        }
        return height;
    }

    /** Pure enough for deterministic GameTests after the fixture is built. */
    public static Optional<Vec3d> pressureImpulse(EntityZombie zombie,
                                                  WorldServer level,
                                                  Vec3d direction,
                                                  int obstacleHeight,
                                                  int rearCount) {
        if (zombie == null || level == null || direction == null
                || rearCount < ZombieRules.PRESSURE_MIN_PUSHERS
                || obstacleHeight <= 0
                || obstacleHeight > ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT) {
            return Optional.empty();
        }
        Vec3d horizontal = new Vec3d(direction.x, 0.0D, direction.z);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
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
        return Optional.of(fr.vanillainstincts.compat.Minecraft112Compat.add(horizontal.scale(ZombieRules.PRESSURE_STACK_HORIZONTAL), 0.0D, vertical, 0.0D));
    }

    public static Vec3d horizontalDirection(Vec3d from, Vec3d to) {
        if (from == null || to == null) return null;
        Vec3d direction = fr.vanillainstincts.compat.Minecraft112Compat.multiply(to.subtract(from), 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-8D ? null : direction.normalize();
    }

    private static boolean hasStackClearance(EntityZombie zombie,
                                             WorldServer level,
                                             Vec3d direction,
                                             int obstacleHeight) {
        BlockPos front = new BlockPos(
                zombie.posX + direction.x * 0.9D,
                zombie.posY + 0.15D,
                zombie.posZ + direction.z * 0.9D);
        BlockPos top = front.up(obstacleHeight);
        // Two clear cells above the obstacle are enough for the zombie's body.
        return !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(top), level, top)
                && !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(level.getBlockState(top.up()), level, top.up())
                && !level.getBlockState(top).getMaterial().isLiquid()
                && !level.getBlockState(top.up()).getMaterial().isLiquid();
    }
}
