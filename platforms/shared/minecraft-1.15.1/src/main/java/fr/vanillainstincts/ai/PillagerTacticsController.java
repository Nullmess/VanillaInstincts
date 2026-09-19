package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.PillagerTacticsRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.PillagerEntity;
import net.minecraft.util.math.Vec3d;

/** Small-squad spacing and reload repositioning for pillagers. */
public final class PillagerTacticsController {
    private static final String NEXT_REPOSITION =
            "vanillainstincts_pillager_reposition_ready";

    private PillagerTacticsController() {
    }

    public static void maintain(PillagerEntity pillager, ServerWorld level,
                                long gameTime) {
        if (pillager == null || level == null) return;
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(pillager, level,
                    PillagerTacticsRules.SQUAD_RADIUS, gameTime);
        }
    }

    public static void contribute(PillagerEntity pillager, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (pillager == null || plan == null || level == null
                || pillager.isPassenger()) {
            return;
        }
        LivingEntity target = pillager.getTarget();
        boolean visible = target != null && target.isAlive()
                && MobPerceptionMemory.canSee(pillager, target);

        if (!visible) {
            Optional<Vec3d> remembered = MobPerceptionMemory.bestKnownPosition(
                    pillager, gameTime);
            remembered.ifPresent(position -> plan.offerNavigation(
                    VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_INVESTIGATE,
                    position, 0.90D, PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> { }));
            return;
        }

        List<PillagerEntity> squad = level.getEntitiesOfClass(PillagerEntity.class,
                pillager.getBoundingBox().inflate(
                        PillagerTacticsRules.SQUAD_RADIUS),
                other -> other != pillager && other.isAlive());
        double distanceSqr = pillager.distanceToSqr(target);

        if (pillager.isUsingItem()
                || distanceSqr <= PillagerTacticsRules.CLOSE_RANGE_SQR) {
            Vec3d cover = coverDestination(pillager, target);
            plan.offerNavigation(VanillaInstinctsState.WAIT_COVER,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_RELOAD_COVER,
                    cover, PillagerTacticsRules.RELOAD_SPEED,
                    PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> markReposition(pillager, gameTime));
            return;
        }

        if (!squad.isEmpty()
                && gameTime >= pillager.getPersistentData()
                        .getLong(NEXT_REPOSITION)) {
            Vec3d flank = flankDestination(pillager, target,
                    squad.size() + 1);
            plan.offerNavigation(VanillaInstinctsState.FLANK,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_FLANK,
                    flank, PillagerTacticsRules.FLANK_SPEED,
                    PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> markReposition(pillager, gameTime));
        }
    }

    public static Vec3d coverDestination(PillagerEntity pillager,
                                        LivingEntity target) {
        Vec3d away = pillager.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            away = away.normalize();
        }
        Vec3d side = new Vec3d(-away.z, 0.0D, away.x);
        if ((pillager.getUUID().hashCode() & 1) != 0) side = side.scale(-1.0D);
        return pillager.position()
                .add(away.scale(PillagerTacticsRules.COVER_DISTANCE))
                .add(side.scale(2.0D));
    }

    public static Vec3d flankDestination(PillagerEntity pillager,
                                        LivingEntity target,
                                        int squadSize) {
        Vec3d radial = pillager.position().subtract(target.position())
                .multiply(1.0D, 0.0D, 1.0D);
        if (radial.lengthSqr() < 1.0E-8D) {
            radial = new Vec3d(1.0D, 0.0D, 0.0D);
        } else {
            radial = radial.normalize();
        }
        Vec3d side = new Vec3d(-radial.z, 0.0D, radial.x);
        int slot = Math.floorMod(pillager.getId(),
                Math.max(2, Math.min(5, squadSize)));
        double signed = (slot % 2 == 0 ? 1.0D : -1.0D)
                * (1.0D + slot / 2.0D);
        return target.position()
                .add(radial.scale(8.0D))
                .add(side.scale(PillagerTacticsRules.FLANK_DISTANCE * signed));
    }

    private static void markReposition(PillagerEntity pillager, long gameTime) {
        pillager.getPersistentData().putLong(NEXT_REPOSITION,
                gameTime + PillagerTacticsRules.REPOSITION_INTERVAL_TICKS);
    }
}
