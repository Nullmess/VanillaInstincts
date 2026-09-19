package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.item.ItemStack;
import fr.vanillainstincts.compat.LegacyBlockState;
import fr.vanillainstincts.compat.Vec3;

/** Group pursuit, sensory investigation and conservative tool use for zombies. */
public final class ZombieTacticsController {
    private static final String LAST_BLOCK = "vanillainstincts_zombie_last_block";
    private static final String STALL = "vanillainstincts_zombie_stall";
    private static final String BREAK_READY = "vanillainstincts_zombie_break_ready";
    private static final String LEAP_READY = "vanillainstincts_zombie_leap_ready";

    private ZombieTacticsController() {
    }

    public static void maintain(EntityZombie zombie, WorldServer level,
                                long gameTime) {
        if (zombie == null || level == null) return;
        updateStall(zombie);
        maintainContextualSprint(zombie);
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(zombie, level,
                    ZombieRules.GROUP_RADIUS, gameTime);
        }
    }

    private static void maintainContextualSprint(EntityZombie zombie) {
        EntityLivingBase target = zombie.getAttackTarget();
        double distanceSqr = target == null
                ? Double.POSITIVE_INFINITY : fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, target);
        boolean active = target != null && target.isEntityAlive()
                && MobPerceptionMemory.canSee(zombie, target)
                && zombie.onGround && !zombie.isInWater()
                && zombie.getHealth() / zombie.getMaxHealth()
                >= ZombieRules.PURSUIT_SPRINT_MIN_HEALTH_RATIO
                && distanceSqr >= ZombieRules.PURSUIT_SPRINT_MIN_DISTANCE_SQR
                && distanceSqr <= ZombieRules.PURSUIT_SPRINT_MAX_DISTANCE_SQR
                && stallTicks(zombie) < ZombieRules.PRESSURE_STALL_TICKS;
        MobRunController.setZombiePursuitSprint(zombie, active);
    }


    public static void contribute(EntityZombie zombie, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (zombie == null || plan == null || level == null
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(zombie)) {
            return;
        }
        EntityLivingBase target = zombie.getAttackTarget();
        boolean visible = target != null && target.isEntityAlive()
                && MobPerceptionMemory.canSee(zombie, target);

        Optional<Vec3> remembered = MobPerceptionMemory.bestKnownPosition(
                zombie, gameTime);
        Vec3 destination = visible && target != null
                ? fr.vanillainstincts.compat.Minecraft17Compat.position(target) : remembered.orElse(null);

        if (!visible && destination != null) {
            plan.offerNavigation(VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.ZOMBIE_TACTICS,
                    ZombieRules.PRIORITY_INVESTIGATE,
                    destination, ZombieRules.INVESTIGATE_SPEED,
                    ZombieRules.INVESTIGATE_HOLD_TICKS, () -> { });
        }

        if (target != null && target.isEntityAlive() && visible) {
            offerContextLeap(zombie, target, plan, gameTime);
        }

        if (destination != null) {
            ZombieHordePressureController.contribute(zombie, plan, level, gameTime);
            offerToolPassage(zombie, destination, plan, level, gameTime);
        }
    }

    private static void offerContextLeap(EntityZombie zombie, EntityLivingBase target,
                                         MobDecisionPlan plan,
                                         long gameTime) {
        double distance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, target);
        if (!zombie.onGround
                || distance < ZombieRules.LEAP_MIN_DISTANCE_SQR
                || distance > ZombieRules.LEAP_MAX_DISTANCE_SQR
                || gameTime < zombie.getEntityData().getLong(LEAP_READY)) {
            return;
        }
        Vec3 horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(fr.vanillainstincts.compat.Minecraft17Compat.position(target).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(zombie)), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-6D) return;
        // Only use the impulse when a small obstacle/step is immediately ahead.
        Vec3 direction = horizontal.normalize();
        BlockPos front = new BlockPos(zombie.posX + direction.xCoord,
                zombie.posY + 0.2D, zombie.posZ + direction.zCoord);
        if (!fr.vanillainstincts.compat.Minecraft112Compat.hasCollision(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(zombie.worldObj, front),
                zombie.worldObj, front)) {
            return;
        }
        Vec3 impulse =fr.vanillainstincts.compat.Minecraft112Compat.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(direction, ZombieRules.LEAP_HORIZONTAL), 0.0D, ZombieRules.LEAP_VERTICAL, 0.0D);
        plan.offerImpulse(VanillaInstinctsState.LEAP,
                ActionOwner.COMBAT_MOBILITY, ZombieRules.PRIORITY_LEAP,
                impulse, 8, () -> zombie.getEntityData().setLong(
                        LEAP_READY, gameTime + ZombieRules.LEAP_COOLDOWN_TICKS));
    }

    private static void offerToolPassage(EntityZombie zombie, Vec3 destination,
                                         MobDecisionPlan plan,
                                         WorldServer level, long gameTime) {
        if (zombie.getEntityData().getInteger(STALL)
                < ZombieRules.PASSAGE_STALL_TICKS
                || gameTime < zombie.getEntityData().getLong(BREAK_READY)) {
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
                        zombie.getEntityData().setInteger(STALL, 0);
                    }
                    zombie.getEntityData().setLong(BREAK_READY,
                            gameTime + ZombieRules.PASSAGE_COOLDOWN_TICKS);
                });
    }

    public static Optional<BlockPos> findToolObstacle(EntityZombie zombie,
                                                       Vec3 destination,
                                                       WorldServer level) {
        Vec3 horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(destination.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(zombie)), 1.0D, 0.0D, 1.0D);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-6D) return Optional.empty();
        horizontal = horizontal.normalize();
        BlockPos feet = new BlockPos(zombie.posX + horizontal.xCoord,
                zombie.posY + 0.2D, zombie.posZ + horizontal.zCoord);
        if (canBreakWithHeldTool(zombie, level, feet)) return Optional.of(feet);
        BlockPos head = feet.up();
        if (canBreakWithHeldTool(zombie, level, head)) return Optional.of(head);
        return Optional.empty();
    }

    public static boolean canBreakWithHeldTool(EntityZombie zombie,
                                                WorldServer level,
                                                BlockPos pos) {
        if (zombie == null || level == null || pos == null
                || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos) || fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, pos) != null) {
            return false;
        }
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
        if (fr.vanillainstincts.compat.Minecraft112Compat.isAir(state) || Minecraft115TagCompat.blockStateIs(state, VanillaInstinctsTags.PROTECTED_BLOCKS)
                || !!state.getBlock().getMaterial().isLiquid()) {
            return false;
        }
        float hardness = state.getBlockHardness(level, pos);
        if (hardness < 0.0F || hardness > ZombieRules.PASSAGE_MAX_HARDNESS) {
            return false;
        }
        ItemStack tool = zombie.getHeldItem();
        return !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(tool) && tool.getStrVsBlock(state.getBlock()) > 1.0F;
    }

    public static int stallTicks(EntityZombie zombie) {
        return zombie == null ? 0 : zombie.getEntityData().getInteger(STALL);
    }

    private static void updateStall(EntityZombie zombie) {
        long current = entityBlockPos(zombie).toLong();
        long previous = zombie.getEntityData().getLong(LAST_BLOCK);
        if (previous == current && zombie.getAttackTarget() != null
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, zombie.getAttackTarget()) > 9.0D) {
            zombie.getEntityData().setInteger(STALL,
                    Math.min(200, zombie.getEntityData().getInteger(STALL) + 1));
        } else {
            zombie.getEntityData().setLong(LAST_BLOCK, current);
            zombie.getEntityData().setInteger(STALL, 0);
        }
    }
}
