package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.MobIntent;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.GolemDefenseRole;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.Vec3;
/** Signaux visuels limités aux systèmes conservés. */
public final class TacticalCueController {
    private static final String READY_AT = VanillaInstincts.MOD_ID + "_cue_ready_at";

    private TacticalCueController() {
    }

    public static void onActionAccepted(EntityLiving mob, VanillaInstinctsState previousState,
                                        VanillaInstinctsState nextState,
                                        MobIntent previousIntent,
                                        MobIntent nextIntent,
                                        long gameTime) {
        if (!(mob.worldObj instanceof WorldServer)
                || fr.vanillainstincts.compat.Minecraft112Compat.removed(mob)
                || previousState == nextState && previousIntent == nextIntent
                || gameTime < mob.getEntityData().getLong(READY_AT)) {
            return;
        } WorldServer level = (WorldServer) (mob.worldObj);
        mob.getEntityData().setLong(READY_AT, gameTime + 12L);
        Vec3 pos =fr.vanillainstincts.compat.Minecraft112Compat.add(mob.getPositionVector(), 0.0D,
                mob.height * 0.72D, 0.0D);
        if (mob instanceof EntityEnderman
                || nextIntent == MobIntent.ENDERMAN_CARGO) {
            level.spawnParticle(EnumParticleTypes.PORTAL,
                    pos.xCoord, pos.yCoord, pos.zCoord, 10,
                    0.28D, 0.45D, 0.28D, 0.05D);
        } else if (mob instanceof EntityVillager
                && (nextState == VanillaInstinctsState.VILLAGE_FLEE
                || nextState == VanillaInstinctsState.VILLAGE_REPORT)) {
            level.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    pos.xCoord, pos.yCoord, pos.zCoord, 2,
                    0.18D, 0.25D, 0.18D, 0.0D);
        } else if (mob instanceof EntityIronGolem
                && nextIntent == MobIntent.SIEGE) {
            level.spawnParticle(EnumParticleTypes.CLOUD,
                    pos.xCoord, pos.yCoord, pos.zCoord, 6,
                    0.28D, 0.18D, 0.28D, 0.02D);
        }
    }

    public static void emitEndermanDeliveryWarning(EntityEnderman enderman,
                                                    WorldServer level) {
        if (enderman == null || level == null) return;
        Vec3 pos =fr.vanillainstincts.compat.Minecraft112Compat.add(enderman.getPositionVector(), 0.0D, 1.45D, 0.0D);
        level.spawnParticle(EnumParticleTypes.PORTAL,
                pos.xCoord, pos.yCoord, pos.zCoord, 18,
                0.4D, 0.65D, 0.4D, 0.1D);
        level.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                pos.xCoord, pos.yCoord, pos.zCoord, 5,
                0.25D, 0.35D, 0.25D, 0.01D);
    }

    public static void emitGolemReport(EntityVillager villager, EntityIronGolem golem,
                                       WorldServer level) {
        if (villager == null || golem == null || level == null) return;
        Vec3 from =fr.vanillainstincts.compat.Minecraft112Compat.add(villager.getPositionVector(), 0.0D,
                villager.height + 0.2D, 0.0D);
        Vec3 to =fr.vanillainstincts.compat.Minecraft112Compat.add(golem.getPositionVector(), 0.0D, 1.7D, 0.0D);
        Vec3 delta = to.subtract(from);
        for (int step = 0; step <= 5; step++) {
            Vec3 point = from.add(fr.vanillainstincts.compat.Minecraft112Compat.scale(delta, step / 5.0D));
            level.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                    point.xCoord, point.yCoord, point.zCoord, 1,
                    0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    public static void emitGolemRole(EntityIronGolem golem, GolemDefenseRole role,
                                     WorldServer level) {
        if (golem == null || role == null || level == null) return;
        Vec3 pos =fr.vanillainstincts.compat.Minecraft112Compat.add(golem.getPositionVector(), 0.0D,
                golem.height + 0.2D, 0.0D);
        int count = role == GolemDefenseRole.PURSUER ? 8
                : role == GolemDefenseRole.INTERCEPTOR ? 6 : 4;
        level.spawnParticle(role == GolemDefenseRole.GUARDIAN
                        ? EnumParticleTypes.VILLAGER_HAPPY : EnumParticleTypes.CRIT,
                pos.xCoord, pos.yCoord, pos.zCoord, count,
                0.3D, 0.25D, 0.3D, 0.03D);
    }

    public static void emitVillageAlert(EntityVillager villager,
                                        WorldServer level) {
        if (villager == null || level == null) return;
        Vec3 pos =fr.vanillainstincts.compat.Minecraft112Compat.add(villager.getPositionVector(), 0.0D,
                villager.height + 0.15D, 0.0D);
        level.spawnParticle(EnumParticleTypes.VILLAGER_ANGRY,
                pos.xCoord, pos.yCoord, pos.zCoord, 3,
                0.25D, 0.25D, 0.25D, 0.0D);
    }
}
