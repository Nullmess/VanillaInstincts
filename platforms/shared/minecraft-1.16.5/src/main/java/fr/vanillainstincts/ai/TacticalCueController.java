package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.MobIntent;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.util.math.vector.Vector3d;
/** Signaux visuels limités aux systèmes conservés. */
public final class TacticalCueController {
    private static final String READY_AT = VanillaInstincts.MOD_ID + "_cue_ready_at";

    private TacticalCueController() {
    }

    public static void onActionAccepted(MobEntity mob, VanillaInstinctsState previousState,
                                        VanillaInstinctsState nextState,
                                        MobIntent previousIntent,
                                        MobIntent nextIntent,
                                        long gameTime) {
        if (!(mob.level instanceof ServerWorld)
                || mob.removed
                || previousState == nextState && previousIntent == nextIntent
                || gameTime < mob.getPersistentData().getLong(READY_AT)) {
            return;
        } ServerWorld level = (ServerWorld) (mob.level);
        mob.getPersistentData().putLong(READY_AT, gameTime + 12L);
        Vector3d pos = mob.position().add(0.0D,
                mob.getBbHeight() * 0.72D, 0.0D);
        if (mob instanceof EndermanEntity
                || nextIntent == MobIntent.ENDERMAN_CARGO) {
            level.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y, pos.z, 10,
                    0.28D, 0.45D, 0.28D, 0.05D);
        } else if (mob instanceof VillagerEntity
                && (nextState == VanillaInstinctsState.VILLAGE_FLEE
                || nextState == VanillaInstinctsState.VILLAGE_REPORT)) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    pos.x, pos.y, pos.z, 2,
                    0.18D, 0.25D, 0.18D, 0.0D);
        } else if (mob instanceof IronGolemEntity
                && nextIntent == MobIntent.SIEGE) {
            level.sendParticles(ParticleTypes.CLOUD,
                    pos.x, pos.y, pos.z, 6,
                    0.28D, 0.18D, 0.28D, 0.02D);
        }
    }

    public static void emitEndermanDeliveryWarning(EndermanEntity enderman,
                                                    ServerWorld level) {
        if (enderman == null || level == null) return;
        Vector3d pos = enderman.position().add(0.0D, 1.45D, 0.0D);
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y, pos.z, 18,
                0.4D, 0.65D, 0.4D, 0.1D);
        level.sendParticles(ParticleTypes.SMOKE,
                pos.x, pos.y, pos.z, 5,
                0.25D, 0.35D, 0.25D, 0.01D);
    }

    public static void emitGolemReport(VillagerEntity villager, IronGolemEntity golem,
                                       ServerWorld level) {
        if (villager == null || golem == null || level == null) return;
        Vector3d from = villager.position().add(0.0D,
                villager.getBbHeight() + 0.2D, 0.0D);
        Vector3d to = golem.position().add(0.0D, 1.7D, 0.0D);
        Vector3d delta = to.subtract(from);
        for (int step = 0; step <= 5; step++) {
            Vector3d point = from.add(delta.scale(step / 5.0D));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    point.x, point.y, point.z, 1,
                    0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    public static void emitGolemRole(IronGolemEntity golem, GolemDefenseRole role,
                                     ServerWorld level) {
        if (golem == null || role == null || level == null) return;
        Vector3d pos = golem.position().add(0.0D,
                golem.getBbHeight() + 0.2D, 0.0D);
        int count = role == GolemDefenseRole.PURSUER ? 8
                : role == GolemDefenseRole.INTERCEPTOR ? 6 : 4;
        level.sendParticles(role == GolemDefenseRole.GUARDIAN
                        ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.CRIT,
                pos.x, pos.y, pos.z, count,
                0.3D, 0.25D, 0.3D, 0.03D);
    }

    public static void emitVillageAlert(VillagerEntity villager,
                                        ServerWorld level) {
        if (villager == null || level == null) return;
        Vector3d pos = villager.position().add(0.0D,
                villager.getBbHeight() + 0.15D, 0.0D);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                pos.x, pos.y, pos.z, 3,
                0.25D, 0.25D, 0.25D, 0.0D);
    }
}
