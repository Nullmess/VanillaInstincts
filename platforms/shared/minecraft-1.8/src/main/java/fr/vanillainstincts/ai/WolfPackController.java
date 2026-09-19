package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
/** Meute locale des loups sauvages, sans modifier les loups apprivoisés. */
public final class WolfPackController {
    private static final String TRACK_UNTIL = "vanillainstincts_wolf_track_until";
    private static final String TRACK_X = "vanillainstincts_wolf_track_x";
    private static final String TRACK_Y = "vanillainstincts_wolf_track_y";
    private static final String TRACK_Z = "vanillainstincts_wolf_track_z";
    private static final String TERRITORY_X = "vanillainstincts_wolf_territory_x";
    private static final String TERRITORY_Y = "vanillainstincts_wolf_territory_y";
    private static final String TERRITORY_Z = "vanillainstincts_wolf_territory_z";
    private static final String TERRITORY_UNTIL = "vanillainstincts_wolf_territory_until";

    private WolfPackController() {
    }

    public static boolean supports(EntityWolf wolf) {
        return wolf != null && !fr.vanillainstincts.compat.Minecraft112Compat.isTamed(wolf);
    }

    public static void contribute(EntityWolf wolf, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (!supports(wolf) || !VanillaInstinctsScheduler.claim(level, wolf,
                PerformanceRules.ENTITY_SCAN_COST)) return;

        List<EntityWolf> pack = fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityWolf.class,
                wolf.getEntityBoundingBox().expand(AnimalRules.WOLF_PACK_RADIUS, AnimalRules.WOLF_PACK_RADIUS, AnimalRules.WOLF_PACK_RADIUS),
                other -> other != wolf && other.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft112Compat.isTamed(other));
        EntityLivingBase target = resolvePackTarget(wolf, pack, gameTime);
        if (target == null) return;

        rememberTrack(wolf, entityBlockPos(target), gameTime);
        rememberTerritory(wolf, pack, gameTime);
        if (outsideTerritory(wolf, target, gameTime)) {
            wolf.setAttackTarget(null);
            return;
        }

        EntityWolf injured = mostInjured(pack, wolf);
        double averageRatio = averageHealthRatio(pack, wolf);
        boolean targetDangerous = target.getMaxHealth() >= 40.0F
                || target instanceof EntityPlayer
                && (fr.vanillainstincts.compat.Minecraft112Compat.armorValue((EntityPlayer) target) >= 12
                || ((EntityPlayer) (target)).getHealth() + ((EntityPlayer) (target)).getAbsorptionAmount() >= 28.0F);
        if (averageRatio < AnimalRules.WOLF_ABANDON_HEALTH_RATIO
                && targetDangerous) {
            wolf.setAttackTarget(null);
            offerRetreat(wolf, target.getPositionVector(), plan);
            return;
        }

        if (wolf.getHealth() / wolf.getMaxHealth()
                <= AnimalRules.WOLF_DANGER_HEALTH_RATIO) {
            offerRetreat(wolf, target.getPositionVector(), plan);
            return;
        }

        if (injured != null && injured != wolf
                && injured.getHealth() / injured.getMaxHealth()
                <= AnimalRules.WOLF_DANGER_HEALTH_RATIO
                && wolf.getHealth() / wolf.getMaxHealth() > 0.55F) {
            Vec3 guard = guardPosition(injured, target);
            plan.offerNavigation(VanillaInstinctsState.WOLF_GUARD,
                    ActionOwner.WOLF_PACK,
                    AnimalRules.PRIORITY_WOLF_GUARD,
                    guard, AnimalRules.WOLF_PACK_SPEED,
                    AnimalRules.STATE_HOLD_ANIMAL_TICKS, null);
            return;
        }

        Vec3 flank = flankPosition(target, wolf, pack.size() + 1);
        plan.offerNavigation(VanillaInstinctsState.WOLF_FLANK,
                ActionOwner.WOLF_PACK,
                AnimalRules.PRIORITY_WOLF_FLANK,
                flank, AnimalRules.WOLF_PACK_SPEED,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> wolf.setAttackTarget(target));
    }

    public static Vec3 flankPosition(EntityLivingBase target, EntityWolf wolf,
                                     int packSize) {
        int slots = Math.max(2, Math.min(8, packSize));
        double angle = Math.floorMod(wolf.getEntityId(), slots)
                * Math.PI * 2.0D / slots;
        double radius = AnimalRules.WOLF_FLANK_DISTANCE;
        return fr.vanillainstincts.compat.Minecraft112Compat.add(target.getPositionVector(), Math.cos(angle) * radius, 0.0D,
                Math.sin(angle) * radius);
    }

    public static boolean shouldAbandon(double averageHealthRatio,
                                        boolean targetDangerous) {
        return targetDangerous && averageHealthRatio
                < AnimalRules.WOLF_ABANDON_HEALTH_RATIO;
    }

    private static EntityLivingBase resolvePackTarget(EntityWolf wolf,
                                                   List<EntityWolf> pack,
                                                   long gameTime) {
        if (valid(wolf.getAttackTarget())) return wolf.getAttackTarget();
        for (EntityWolf member : pack) {
            if (valid(member.getAttackTarget())) {
                wolf.setAttackTarget(member.getAttackTarget());
                return member.getAttackTarget();
            }
        }
        if (wolf.getEntityData().getLong(TRACK_UNTIL) > gameTime) {
            BlockPos last = new BlockPos(
                    wolf.getEntityData().getInteger(TRACK_X),
                    wolf.getEntityData().getInteger(TRACK_Y),
                    wolf.getEntityData().getInteger(TRACK_Z));
            wolf.getNavigator().tryMoveToXYZ(last.getX() + 0.5D, last.getY(),
                    last.getZ() + 0.5D, 1.0D);
        }
        return null;
    }

    private static boolean valid(EntityLivingBase target) {
        return target != null && target.isEntityAlive()
                && (!(target instanceof EntityPlayer)
                || !((EntityPlayer) (target)).capabilities.isCreativeMode && !((EntityPlayer) (target)).isSpectator());
    }

    private static void offerRetreat(EntityWolf wolf, Vec3 danger,
                                     MobDecisionPlan plan) {
        Vec3 away = horizontal(wolf.getPositionVector().subtract(danger));
        if (away == null) away = new Vec3(1.0D, 0.0D, 0.0D);
        plan.offerNavigation(VanillaInstinctsState.WOLF_RETREAT,
                ActionOwner.WOLF_PACK,
                AnimalRules.PRIORITY_WOLF_RETREAT,
                wolf.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 12.0D)), 1.18D,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> wolf.setAttackTarget(null));
    }

    private static Vec3 guardPosition(EntityWolf injured, EntityLivingBase target) {
        Vec3 toward = horizontal(target.getPositionVector().subtract(injured.getPositionVector()));
        if (toward == null) toward = new Vec3(1.0D, 0.0D, 0.0D);
        return injured.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(toward, 2.0D));
    }

    private static EntityWolf mostInjured(List<EntityWolf> pack, EntityWolf self) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(self),
                        pack.stream())
                .min(Comparator.comparingDouble(w -> w.getHealth()
                        / Math.max(1.0F, w.getMaxHealth())))
                .orElse(null);
    }

    private static double averageHealthRatio(List<EntityWolf> pack, EntityWolf self) {
        double sum = self.getHealth() / Math.max(1.0F, self.getMaxHealth());
        for (EntityWolf wolf : pack) {
            sum += wolf.getHealth() / Math.max(1.0F, wolf.getMaxHealth());
        }
        return sum / (pack.size() + 1.0D);
    }

    private static void rememberTrack(EntityWolf wolf, BlockPos pos,
                                      long gameTime) {
        wolf.getEntityData().setLong(TRACK_UNTIL,
                gameTime + AnimalRules.WOLF_TRACK_TICKS);
        wolf.getEntityData().setInteger(TRACK_X, pos.getX());
        wolf.getEntityData().setInteger(TRACK_Y, pos.getY());
        wolf.getEntityData().setInteger(TRACK_Z, pos.getZ());
    }

    private static void rememberTerritory(EntityWolf wolf, List<EntityWolf> pack,
                                          long gameTime) {
        if (wolf.getEntityData().getLong(TERRITORY_UNTIL) > gameTime) {
            return;
        }
        double x = wolf.posX;
        double y = wolf.posY;
        double z = wolf.posZ;
        for (EntityWolf member : pack) {
            x += member.posX;
            y += member.posY;
            z += member.posZ;
        }
        int count = pack.size() + 1;
        BlockPos center = new BlockPos(x / count, y / count, z / count);
        wolf.getEntityData().setInteger(TERRITORY_X, center.getX());
        wolf.getEntityData().setInteger(TERRITORY_Y, center.getY());
        wolf.getEntityData().setInteger(TERRITORY_Z, center.getZ());
        wolf.getEntityData().setLong(TERRITORY_UNTIL, gameTime + 1_200L);
    }

    private static boolean outsideTerritory(EntityWolf wolf, EntityLivingBase target,
                                            long gameTime) {
        if (wolf.getEntityData().getLong(TERRITORY_UNTIL) <= gameTime) {
            return false;
        }
        BlockPos center = new BlockPos(
                wolf.getEntityData().getInteger(TERRITORY_X),
                wolf.getEntityData().getInteger(TERRITORY_Y),
                wolf.getEntityData().getInteger(TERRITORY_Z));
        return center.distanceSq(entityBlockPos(target))
                > AnimalRules.WOLF_TERRITORY_RADIUS
                * AnimalRules.WOLF_TERRITORY_RADIUS;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 flat = fr.vanillainstincts.compat.Minecraft112Compat.multiply(value, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(flat) < 1.0E-8D ? null : flat.normalize();
    }
}
