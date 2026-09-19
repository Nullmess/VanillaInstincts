package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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

    public static boolean supports(Wolf wolf) {
        return wolf != null && !wolf.isTame();
    }

    public static void contribute(Wolf wolf, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (!supports(wolf) || !VanillaInstinctsScheduler.claim(level, wolf,
                PerformanceRules.ENTITY_SCAN_COST)) return;

        List<Wolf> pack = level.getEntitiesOfClass(Wolf.class,
                wolf.getBoundingBox().inflate(AnimalRules.WOLF_PACK_RADIUS),
                other -> other != wolf && other.isAlive() && !other.isTame());
        LivingEntity target = resolvePackTarget(wolf, pack, gameTime);
        if (target == null) return;

        rememberTrack(wolf, target.blockPosition(), gameTime);
        rememberTerritory(wolf, pack, gameTime);
        if (outsideTerritory(wolf, target, gameTime)) {
            wolf.setTarget(null);
            return;
        }

        Wolf injured = mostInjured(pack, wolf);
        double averageRatio = averageHealthRatio(pack, wolf);
        boolean targetDangerous = target.getMaxHealth() >= 40.0F
                || target instanceof Player player
                && (player.getArmorValue() >= 12
                || player.getHealth() + player.getAbsorptionAmount() >= 28.0F);
        if (averageRatio < AnimalRules.WOLF_ABANDON_HEALTH_RATIO
                && targetDangerous) {
            wolf.setTarget(null);
            offerRetreat(wolf, target.position(), plan);
            return;
        }

        if (wolf.getHealth() / wolf.getMaxHealth()
                <= AnimalRules.WOLF_DANGER_HEALTH_RATIO) {
            offerRetreat(wolf, target.position(), plan);
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
                () -> wolf.setTarget(target));
    }

    public static Vec3 flankPosition(LivingEntity target, Wolf wolf,
                                     int packSize) {
        int slots = Math.max(2, Math.min(8, packSize));
        double angle = Math.floorMod(wolf.getId(), slots)
                * Math.PI * 2.0D / slots;
        double radius = AnimalRules.WOLF_FLANK_DISTANCE;
        return target.position().add(Math.cos(angle) * radius, 0.0D,
                Math.sin(angle) * radius);
    }

    public static boolean shouldAbandon(double averageHealthRatio,
                                        boolean targetDangerous) {
        return targetDangerous && averageHealthRatio
                < AnimalRules.WOLF_ABANDON_HEALTH_RATIO;
    }

    private static LivingEntity resolvePackTarget(Wolf wolf,
                                                   List<Wolf> pack,
                                                   long gameTime) {
        if (valid(wolf.getTarget())) return wolf.getTarget();
        for (Wolf member : pack) {
            if (valid(member.getTarget())) {
                wolf.setTarget(member.getTarget());
                return member.getTarget();
            }
        }
        if (wolf.getPersistentData().getLong(TRACK_UNTIL) > gameTime) {
            BlockPos last = new BlockPos(
                    wolf.getPersistentData().getInt(TRACK_X),
                    wolf.getPersistentData().getInt(TRACK_Y),
                    wolf.getPersistentData().getInt(TRACK_Z));
            wolf.getNavigation().moveTo(last.getX() + 0.5D, last.getY(),
                    last.getZ() + 0.5D, 1.0D);
        }
        return null;
    }

    private static boolean valid(LivingEntity target) {
        return target != null && target.isAlive()
                && (!(target instanceof Player player)
                || !player.isCreative() && !player.isSpectator());
    }

    private static void offerRetreat(Wolf wolf, Vec3 danger,
                                     MobDecisionPlan plan) {
        Vec3 away = horizontal(wolf.position().subtract(danger));
        if (away == null) away = new Vec3(1.0D, 0.0D, 0.0D);
        plan.offerNavigation(VanillaInstinctsState.WOLF_RETREAT,
                ActionOwner.WOLF_PACK,
                AnimalRules.PRIORITY_WOLF_RETREAT,
                wolf.position().add(away.scale(12.0D)), 1.18D,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> wolf.setTarget(null));
    }

    private static Vec3 guardPosition(Wolf injured, LivingEntity target) {
        Vec3 toward = horizontal(target.position().subtract(injured.position()));
        if (toward == null) toward = new Vec3(1.0D, 0.0D, 0.0D);
        return injured.position().add(toward.scale(2.0D));
    }

    private static Wolf mostInjured(List<Wolf> pack, Wolf self) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(self),
                        pack.stream())
                .min(Comparator.comparingDouble(w -> w.getHealth()
                        / Math.max(1.0F, w.getMaxHealth())))
                .orElse(null);
    }

    private static double averageHealthRatio(List<Wolf> pack, Wolf self) {
        double sum = self.getHealth() / Math.max(1.0F, self.getMaxHealth());
        for (Wolf wolf : pack) {
            sum += wolf.getHealth() / Math.max(1.0F, wolf.getMaxHealth());
        }
        return sum / (pack.size() + 1.0D);
    }

    private static void rememberTrack(Wolf wolf, BlockPos pos,
                                      long gameTime) {
        wolf.getPersistentData().putLong(TRACK_UNTIL,
                gameTime + AnimalRules.WOLF_TRACK_TICKS);
        wolf.getPersistentData().putInt(TRACK_X, pos.getX());
        wolf.getPersistentData().putInt(TRACK_Y, pos.getY());
        wolf.getPersistentData().putInt(TRACK_Z, pos.getZ());
    }

    private static void rememberTerritory(Wolf wolf, List<Wolf> pack,
                                          long gameTime) {
        if (wolf.getPersistentData().getLong(TERRITORY_UNTIL) > gameTime) {
            return;
        }
        double x = wolf.getX();
        double y = wolf.getY();
        double z = wolf.getZ();
        for (Wolf member : pack) {
            x += member.getX();
            y += member.getY();
            z += member.getZ();
        }
        int count = pack.size() + 1;
        BlockPos center = BlockPos.containing(x / count, y / count, z / count);
        wolf.getPersistentData().putInt(TERRITORY_X, center.getX());
        wolf.getPersistentData().putInt(TERRITORY_Y, center.getY());
        wolf.getPersistentData().putInt(TERRITORY_Z, center.getZ());
        wolf.getPersistentData().putLong(TERRITORY_UNTIL, gameTime + 1_200L);
    }

    private static boolean outsideTerritory(Wolf wolf, LivingEntity target,
                                            long gameTime) {
        if (wolf.getPersistentData().getLong(TERRITORY_UNTIL) <= gameTime) {
            return false;
        }
        BlockPos center = new BlockPos(
                wolf.getPersistentData().getInt(TERRITORY_X),
                wolf.getPersistentData().getInt(TERRITORY_Y),
                wolf.getPersistentData().getInt(TERRITORY_Z));
        return center.distSqr(target.blockPosition())
                > AnimalRules.WOLF_TERRITORY_RADIUS
                * AnimalRules.WOLF_TERRITORY_RADIUS;
    }

    private static Vec3 horizontal(Vec3 value) {
        Vec3 flat = value.multiply(1.0D, 0.0D, 1.0D);
        return flat.lengthSqr() < 1.0E-8D ? null : flat.normalize();
    }
}
