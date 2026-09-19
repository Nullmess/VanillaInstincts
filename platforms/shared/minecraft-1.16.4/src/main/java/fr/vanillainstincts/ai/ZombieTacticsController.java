package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.vector.Vector3d;

/** Group pursuit, sensory investigation and conservative tool use for zombies. */
public final class ZombieTacticsController {
    private static final String LAST_BLOCK = "vanillainstincts_zombie_last_block";
    private static final String STALL = "vanillainstincts_zombie_stall";
    private static final String BREAK_READY = "vanillainstincts_zombie_break_ready";
    private static final String LEAP_READY = "vanillainstincts_zombie_leap_ready";

    private ZombieTacticsController() {
    }

    public static void maintain(ZombieEntity zombie, ServerWorld level,
                                long gameTime) {
        if (zombie == null || level == null) return;
        updateStall(zombie);
        maintainContextualSprint(zombie);
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(zombie, level,
                    ZombieRules.GROUP_RADIUS, gameTime);
        }
    }

    private static void maintainContextualSprint(ZombieEntity zombie) {
        LivingEntity target = zombie.getTarget();
        double distanceSqr = target == null
                ? Double.POSITIVE_INFINITY : zombie.distanceToSqr(target);
        boolean active = target != null && target.isAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                && zombie.isOnGround() && !zombie.isInWater()
                && zombie.getHealth() / zombie.getMaxHealth()
                >= ZombieRules.PURSUIT_SPRINT_MIN_HEALTH_RATIO
                && distanceSqr >= ZombieRules.PURSUIT_SPRINT_MIN_DISTANCE_SQR
                && distanceSqr <= ZombieRules.PURSUIT_SPRINT_MAX_DISTANCE_SQR
                && stallTicks(zombie) < ZombieRules.PRESSURE_STALL_TICKS;
        MobRunController.setZombiePursuitSprint(zombie, active);
    }


    public static void contribute(ZombieEntity zombie, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (zombie == null || plan == null || level == null
                || zombie.isPassenger()) {
            return;
        }
        LivingEntity target = zombie.getTarget();
        boolean visible = target != null && target.isAlive()
                && MobPerceptionMemory.canSee(zombie, target);

        Optional<Vector3d> remembered = MobPerceptionMemory.bestKnownPosition(
                zombie, gameTime);
        Vector3d destination = visible && target != null
                ? target.position() : remembered.orElse(null);

        if (!visible && destination != null) {
            plan.offerNavigation(VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.ZOMBIE_TACTICS,
                    ZombieRules.PRIORITY_INVESTIGATE,
                    destination, ZombieRules.INVESTIGATE_SPEED,
                    ZombieRules.INVESTIGATE_HOLD_TICKS, () -> { });
        }

        if (target != null && target.isAlive() && visible) {
            offerContextLeap(zombie, target, plan, gameTime);
        }

        if (destination != null) {
            ZombieHordePressureController.contribute(zombie, plan, level, gameTime);
            offerToolPassage(zombie, destination, plan, level, gameTime);
        }
    }

    private static void offerContextLeap(ZombieEntity zombie, LivingEntity target,
                                         MobDecisionPlan plan,
                                         long gameTime) {
        double distance = zombie.distanceToSqr(target);
        if (!zombie.isOnGround()
                || distance < ZombieRules.LEAP_MIN_DISTANCE_SQR
                || distance > ZombieRules.LEAP_MAX_DISTANCE_SQR
                || gameTime < zombie.getPersistentData().getLong(LEAP_READY)) {
            return;
        }
        Vector3d horizontal = target.position().subtract(zombie.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) return;
        // Only use the impulse when a small obstacle/step is immediately ahead.
        Vector3d direction = horizontal.normalize();
        BlockPos front = new BlockPos(zombie.getX() + direction.x,
                zombie.getY() + 0.2D, zombie.getZ() + direction.z);
        if (zombie.level.getBlockState(front).getCollisionShape(
                zombie.level, front).isEmpty()) {
            return;
        }
        Vector3d impulse = direction.scale(ZombieRules.LEAP_HORIZONTAL)
                .add(0.0D, ZombieRules.LEAP_VERTICAL, 0.0D);
        plan.offerImpulse(VanillaInstinctsState.LEAP,
                ActionOwner.COMBAT_MOBILITY, ZombieRules.PRIORITY_LEAP,
                impulse, 8, () -> zombie.getPersistentData().putLong(
                        LEAP_READY, gameTime + ZombieRules.LEAP_COOLDOWN_TICKS));
    }

    private static void offerToolPassage(ZombieEntity zombie, Vector3d destination,
                                         MobDecisionPlan plan,
                                         ServerWorld level, long gameTime) {
        if (zombie.getPersistentData().getInt(STALL)
                < ZombieRules.PASSAGE_STALL_TICKS
                || gameTime < zombie.getPersistentData().getLong(BREAK_READY)) {
            return;
        }
        Optional<BlockPos> obstacle = findToolObstacle(zombie, destination,
                level);
        if (!obstacle.isPresent()) return;
        BlockPos pos = obstacle.get();
        plan.offerSpecial(VanillaInstinctsState.CONSTRUCTION_APPROACH,
                ActionOwner.ZOMBIE_TACTICS, ZombieRules.PRIORITY_PASSAGE,
                24, () -> {
                    if (WorldPermissionService.destroyBlock(level, zombie,
                            pos, true)) {
                        zombie.getPersistentData().putInt(STALL, 0);
                    }
                    zombie.getPersistentData().putLong(BREAK_READY,
                            gameTime + ZombieRules.PASSAGE_COOLDOWN_TICKS);
                });
    }

    public static Optional<BlockPos> findToolObstacle(ZombieEntity zombie,
                                                       Vector3d destination,
                                                       ServerWorld level) {
        Vector3d horizontal = destination.subtract(zombie.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-6D) return Optional.empty();
        horizontal = horizontal.normalize();
        BlockPos feet = new BlockPos(zombie.getX() + horizontal.x,
                zombie.getY() + 0.2D, zombie.getZ() + horizontal.z);
        if (canBreakWithHeldTool(zombie, level, feet)) return Optional.of(feet);
        BlockPos head = feet.above();
        if (canBreakWithHeldTool(zombie, level, head)) return Optional.of(head);
        return Optional.empty();
    }

    public static boolean canBreakWithHeldTool(ZombieEntity zombie,
                                                ServerWorld level,
                                                BlockPos pos) {
        if (zombie == null || level == null || pos == null
                || !level.hasChunkAt(pos) || level.getBlockEntity(pos) != null) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(VanillaInstinctsTags.PROTECTED_BLOCKS)
                || !state.getFluidState().isEmpty()) {
            return false;
        }
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F || hardness > ZombieRules.PASSAGE_MAX_HARDNESS) {
            return false;
        }
        ItemStack tool = zombie.getMainHandItem();
        return !tool.isEmpty() && tool.isCorrectToolForDrops(state);
    }

    public static int stallTicks(ZombieEntity zombie) {
        return zombie == null ? 0 : zombie.getPersistentData().getInt(STALL);
    }

    private static void updateStall(ZombieEntity zombie) {
        long current = zombie.blockPosition().asLong();
        long previous = zombie.getPersistentData().getLong(LAST_BLOCK);
        if (previous == current && zombie.getTarget() != null
                && zombie.distanceToSqr(zombie.getTarget()) > 9.0D) {
            zombie.getPersistentData().putInt(STALL,
                    Math.min(200, zombie.getPersistentData().getInt(STALL) + 1));
        } else {
            zombie.getPersistentData().putLong(LAST_BLOCK, current);
            zombie.getPersistentData().putInt(STALL, 0);
        }
    }
}
