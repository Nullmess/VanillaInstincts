package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.PerceptionRules;
import fr.vanillainstincts.core.rules.PillagerTacticsRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.PillagerEntity;
import fr.vanillainstincts.compat.Vec3;

/** Small-squad spacing and reload repositioning for pillagers. */
public final class PillagerTacticsController {
    private static final String NEXT_REPOSITION =
            "vanillainstincts_pillager_reposition_ready";

    private PillagerTacticsController() {
    }

    public static void maintain(PillagerEntity pillager, WorldServer level,
                                long gameTime) {
        if (pillager == null || level == null) return;
        if (gameTime % PerceptionRules.ALLY_MEMORY_SHARE_INTERVAL_TICKS == 0L) {
            MobPerceptionMemory.shareWithNearbySameType(pillager, level,
                    PillagerTacticsRules.SQUAD_RADIUS, gameTime);
        }
    }

    public static void contribute(PillagerEntity pillager, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (pillager == null || plan == null || level == null
                || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(pillager)) {
            return;
        }
        EntityLivingBase target = pillager.getAttackTarget();
        boolean visible = target != null && target.isEntityAlive()
                && MobPerceptionMemory.canSee(pillager, target);

        if (!visible) {
            Optional<Vec3> remembered = MobPerceptionMemory.bestKnownPosition(
                    pillager, gameTime);
            remembered.ifPresent(position -> plan.offerNavigation(
                    VanillaInstinctsState.INVESTIGATE,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_INVESTIGATE,
                    position, 0.90D, PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> { }));
            return;
        }

        List<PillagerEntity> squad = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, PillagerEntity.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(pillager), 
                        PillagerTacticsRules.SQUAD_RADIUS),
                other -> other != pillager && other.isEntityAlive());
        double distanceSqr = pillager.distanceToSqr(target);

        if (pillager.isUsingItem()
                || distanceSqr <= PillagerTacticsRules.CLOSE_RANGE_SQR) {
            Vec3 cover = coverDestination(pillager, target);
            plan.offerNavigation(VanillaInstinctsState.WAIT_COVER,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_RELOAD_COVER,
                    cover, PillagerTacticsRules.RELOAD_SPEED,
                    PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> markReposition(pillager, gameTime));
            return;
        }

        if (!squad.isEmpty()
                && gameTime >= pillager.getEntityData()
                        .getLong(NEXT_REPOSITION)) {
            Vec3 flank = flankDestination(pillager, target,
                    squad.size() + 1);
            plan.offerNavigation(VanillaInstinctsState.FLANK,
                    ActionOwner.PILLAGER_TACTICS,
                    PillagerTacticsRules.PRIORITY_FLANK,
                    flank, PillagerTacticsRules.FLANK_SPEED,
                    PillagerTacticsRules.STATE_HOLD_TICKS,
                    () -> markReposition(pillager, gameTime));
        }
    }

    public static Vec3 coverDestination(PillagerEntity pillager,
                                        EntityLivingBase target) {
        Vec3 away = fr.vanillainstincts.compat.Minecraft17Compat.position(pillager).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(target))
                .multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-8D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            away = away.normalize();
        }
        Vec3 side = new Vec3(-away.zCoord, 0.0D, away.xCoord);
        if ((pillager.getUniqueID().hashCode() & 1) != 0) side = fr.vanillainstincts.compat.Minecraft112Compat.scale(side, -1.0D);
        return fr.vanillainstincts.compat.Minecraft17Compat.position(pillager)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, PillagerTacticsRules.COVER_DISTANCE))
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, 2.0D));
    }

    public static Vec3 flankDestination(PillagerEntity pillager,
                                        EntityLivingBase target,
                                        int squadSize) {
        Vec3 radial = fr.vanillainstincts.compat.Minecraft17Compat.position(pillager).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(target))
                .multiply(1.0D, 0.0D, 1.0D);
        if (radial.lengthSqr() < 1.0E-8D) {
            radial = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            radial = radial.normalize();
        }
        Vec3 side = new Vec3(-radial.zCoord, 0.0D, radial.xCoord);
        int slot = Math.floorMod(pillager.getEntityId(),
                Math.max(2, Math.min(5, squadSize)));
        double signed = (slot % 2 == 0 ? 1.0D : -1.0D)
                * (1.0D + slot / 2.0D);
        return fr.vanillainstincts.compat.Minecraft17Compat.position(target)
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(radial, 8.0D))
                .add(fr.vanillainstincts.compat.Minecraft112Compat.scale(side, PillagerTacticsRules.FLANK_DISTANCE * signed));
    }

    private static void markReposition(PillagerEntity pillager, long gameTime) {
        pillager.getEntityData().setLong(NEXT_REPOSITION,
                gameTime + PillagerTacticsRules.REPOSITION_INTERVAL_TICKS);
    }
}
