package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import fr.vanillainstincts.compat.Vec3;

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
        Vec3 destination = target != null && target.isEntityAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                ? fr.vanillainstincts.compat.Minecraft17Compat.position(target)
                : MobPerceptionMemory.bestKnownPosition(zombie, gameTime)
                .orElse(null);
        if (destination == null) {
            return;
        }

        Vec3 direction = horizontalDirection(fr.vanillainstincts.compat.Minecraft17Compat.position(zombie), destination);
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
        Optional<Vec3> impulse = pressureImpulse(zombie, level, direction,
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
                                          Vec3 direction) {
        if (zombie == null || level == null || direction == null) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        Vec3 horizontal = new Vec3(direction.xCoord, 0.0D, direction.zCoord);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return fr.vanillainstincts.compat.LegacyJava8.listOf();
        }
        horizontal = horizontal.normalize();
        Vec3 side = new Vec3(-horizontal.zCoord, 0.0D, horizontal.xCoord);

        List<EntityZombie> candidates = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityZombie.class,
                fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(zombie).expand(ZombieRules.PRESSURE_SCAN_RADIUS, ZombieRules.PRESSURE_SCAN_RADIUS, ZombieRules.PRESSURE_SCAN_RADIUS),
                other -> other != zombie && other.isEntityAlive()
                        && !fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(other)
                        && Math.abs(other.posY - zombie.posY) <= 1.75D);
        List<EntityZombie> result = new ArrayList<>();
        for (EntityZombie other : candidates) {
            Vec3 relative = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(other).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(zombie)), 1.0D, 0.0D, 1.0D);
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
                                     Vec3 direction) {
        if (zombie == null || level == null || direction == null) {
            return 0;
        }
        Vec3 horizontal = new Vec3(direction.xCoord, 0.0D, direction.zCoord);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return 0;
        }
        horizontal = horizontal.normalize();
        BlockPos front = new BlockPos(
                zombie.posX + horizontal.xCoord * 0.9D,
                zombie.posY + 0.15D,
                zombie.posZ + horizontal.zCoord * 0.9D);
        int height = 0;
        int cap = ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT + 1;
        for (int dy = 0; dy < cap; dy++) {
            BlockPos pos = front.up(dy);
            if (!fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos), level, pos)) {
                break;
            }
            height++;
        }
        return height;
    }

    /** Pure enough for deterministic GameTests after the fixture is built. */
    public static Optional<Vec3> pressureImpulse(EntityZombie zombie,
                                                  WorldServer level,
                                                  Vec3 direction,
                                                  int obstacleHeight,
                                                  int rearCount) {
        if (zombie == null || level == null || direction == null
                || rearCount < ZombieRules.PRESSURE_MIN_PUSHERS
                || obstacleHeight <= 0
                || obstacleHeight > ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT) {
            return Optional.empty();
        }
        Vec3 horizontal = new Vec3(direction.xCoord, 0.0D, direction.zCoord);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D) {
            return Optional.empty();
        }
        horizontal = horizontal.normalize();

        if (obstacleHeight == 1) {
            return Optional.of(fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontal, ZombieRules.PRESSURE_HORIZONTAL));
        }
        if (rearCount < ZombieRules.PRESSURE_MIN_STACKERS
                || !hasStackClearance(zombie, level, horizontal,
                obstacleHeight)) {
            return Optional.empty();
        }
        double vertical = ZombieRules.PRESSURE_STACK_VERTICAL
                + (obstacleHeight - 2)
                * ZombieRules.PRESSURE_STACK_EXTRA_VERTICAL;
        return Optional.of(fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontal, ZombieRules.PRESSURE_STACK_HORIZONTAL), 0.0D, vertical, 0.0D));
    }

    public static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        if (from == null || to == null) return null;
        Vec3 direction = fr.vanillainstincts.compat.Minecraft112Compat.multiply(to.subtract(from), 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-8D ? null : direction.normalize();
    }

    private static boolean hasStackClearance(EntityZombie zombie,
                                             WorldServer level,
                                             Vec3 direction,
                                             int obstacleHeight) {
        BlockPos front = new BlockPos(
                zombie.posX + direction.xCoord * 0.9D,
                zombie.posY + 0.15D,
                zombie.posZ + direction.zCoord * 0.9D);
        BlockPos top = front.up(obstacleHeight);
        // Two clear cells above the obstacle are enough for the zombie's body.
        return !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, top), level, top)
                && !fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, top.up()), level, top.up())
                && !fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, top).getBlock().getMaterial().isLiquid()
                && !fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, top.up()).getBlock().getMaterial().isLiquid();
    }
}
