package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.OcelotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.math.vector.Vector3d;

/**
 * Lightweight herd intelligence with a stable shared leader, local crowd
 * settling, young protection and collective flight.
 */
public final class AnimalHerdController {
    private static final String DANGER_UNTIL =
            "vanillainstincts_herd_danger_until";
    private static final String DANGER_X = "vanillainstincts_herd_danger_x";
    private static final String DANGER_Y = "vanillainstincts_herd_danger_y";
    private static final String DANGER_Z = "vanillainstincts_herd_danger_z";
    private static final String LEADER = "vanillainstincts_herd_leader";
    private static final String LEADER_UNTIL =
            "vanillainstincts_herd_leader_until";

    private AnimalHerdController() {
    }

    public static boolean supports(AnimalEntity animal) {
        return animal != null
                && !(animal instanceof WolfEntity)
                && !(animal instanceof FoxEntity)
                && !(animal instanceof OcelotEntity)
                && (!(animal instanceof TameableEntity)
                || !((TameableEntity) (animal)).isTame());
    }

    public static void onAnimalDamaged(AnimalEntity animal, LivingEntity attacker,
                                       long gameTime) {
        if (!supports(animal) || attacker == null) return;
        rememberDanger(animal, attacker.blockPosition(), gameTime + 240L);
        MobMovementPolicy.markFrightened(animal, gameTime, 120L);
    }

    public static void contribute(AnimalEntity animal, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (!supports(animal) || animal.isPassenger()
                || !VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }

        List<AnimalEntity> herd = level.getEntitiesOfClass(AnimalEntity.class,
                animal.getBoundingBox().inflate(AnimalRules.ANIMAL_HERD_RADIUS),
                other -> other != animal && other.isAlive()
                        && other.getType() == animal.getType()
                        && supports(other));
        if (herd.isEmpty()) return;

        shareDanger(animal, herd, gameTime);
        LivingEntity immediate = nearestDanger(animal, level);
        BlockPos remembered = danger(animal, gameTime);
        Vector3d dangerPosition = immediate != null ? immediate.position()
                : remembered == null ? null : Vector3d.atCenterOf(remembered);

        if (dangerPosition != null) {
            if (!animal.isBaby()) {
                AnimalEntity baby = nearestBaby(animal, herd);
                if (baby != null && animal.distanceToSqr(baby)
                        <= AnimalRules.ANIMAL_PROTECT_YOUNG_RADIUS
                        * AnimalRules.ANIMAL_PROTECT_YOUNG_RADIUS) {
                    Vector3d guard = betweenYoungAndDanger(baby, dangerPosition);
                    if (guard != null) {
                        plan.offerNavigation(VanillaInstinctsState.PROTECT_YOUNG,
                                ActionOwner.ANIMAL_BEHAVIOUR,
                                AnimalRules.PRIORITY_PROTECT_YOUNG,
                                guard, 1.08D,
                                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                                null);
                        return;
                    }
                }
            }
            Vector3d commonEscape = commonFleeDestination(animal, herd,
                    dangerPosition);
            plan.offerNavigation(VanillaInstinctsState.FLEE,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_FLEE,
                    commonEscape, AnimalRules.ANIMAL_FLEE_SPEED,
                    AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                    () -> animal.setSprinting(true));
            return;
        }

        AnimalEntity nearest = herd.stream()
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
        if (nearest != null && shouldSeparate(animal, nearest)) {
            Vector3d away = horizontal(animal.position().subtract(
                    nearest.position()));
            if (away != null) {
                plan.offerNavigation(VanillaInstinctsState.SEPARATE,
                        ActionOwner.ANIMAL_BEHAVIOUR,
                        AnimalRules.PRIORITY_ANIMAL_SEPARATE,
                        animal.position().add(away.scale(1.5D)), 0.82D,
                        AnimalRules.STATE_HOLD_ANIMAL_TICKS, null);
                return;
            }
        }

        AnimalEntity leader = stableLeader(animal, herd, level, gameTime);
        if (leader == null) return;

        if (animal == leader) {
            AnimalEntity laggard = herd.stream()
                    .max(Comparator.comparingDouble(leader::distanceToSqr))
                    .orElse(null);
            if (laggard != null && leader.distanceToSqr(laggard)
                    > AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR
                    && leader.distanceToSqr(laggard)
                    <= AnimalRules.ANIMAL_HERD_RADIUS
                    * AnimalRules.ANIMAL_HERD_RADIUS) {
                offerStop(plan, leader,
                        AnimalRules.PRIORITY_ANIMAL_HERD + 4);
            }
            return;
        }

        if (settledInCrowd(animal, herd, leader)) {
            offerStop(plan, animal, AnimalRules.PRIORITY_ANIMAL_HERD + 2);
            return;
        }

        double distanceSqr = animal.distanceToSqr(leader);
        if (animal.isBaby() || distanceSqr
                > AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR) {
            AnimalEntity anchor = relayAnchor(animal, leader, herd);
            Vector3d target = formationTarget(animal, anchor, herd);
            if (animal.distanceToSqr(target)
                    <= AnimalRules.ANIMAL_HERD_ARRIVAL_DISTANCE_SQR) {
                offerStop(plan, animal,
                        AnimalRules.PRIORITY_ANIMAL_HERD + 1);
                return;
            }
            plan.offerNavigation(VanillaInstinctsState.HERD,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_HERD
                            + (animal.isBaby() ? 8 : 0),
                    target, AnimalRules.ANIMAL_HERD_SPEED,
                    AnimalRules.STATE_HOLD_ANIMAL_TICKS, null);
        }
    }

    /** Stable leader shared through persistent memory across overlapping scans. */
    public static AnimalEntity stableLeader(AnimalEntity self, List<AnimalEntity> herd,
                                      ServerWorld level, long gameTime) {
        if (self == null || level == null) return null;
        List<AnimalEntity> members = members(self, herd);
        Map<UUID, AnimalEntity> rememberedLeaders = new HashMap<>();
        for (AnimalEntity member : members) {
            if (member.getPersistentData().getLong(LEADER_UNTIL) <= gameTime
                    || !member.getPersistentData().hasUUID(LEADER)) {
                continue;
            }
            Entity remembered = level.getEntity(
                    member.getPersistentData().getUUID(LEADER));
            if (remembered instanceof AnimalEntity
                    && validLeader(((AnimalEntity) (remembered)), self)) { AnimalEntity rememberedAnimal = (AnimalEntity) (remembered); 
                rememberedLeaders.put(rememberedAnimal.getUUID(),
                        rememberedAnimal);
            }
        }
        AnimalEntity chosen = rememberedLeaders.values().stream()
                .min(Comparator.comparing(AnimalEntity::getUUID))
                .orElseGet(() -> members.stream()
                        .filter(member -> validLeader(member, self))
                        .min(Comparator.comparing(AnimalEntity::getUUID))
                        .orElse(null));
        if (chosen == null) {
            clearLeaderMemory(self);
            return null;
        }
        long until = gameTime + AnimalRules.ANIMAL_HERD_LEADER_MEMORY_TICKS;
        for (AnimalEntity member : members) {
            member.getPersistentData().putUUID(LEADER, chosen.getUUID());
            member.getPersistentData().putLong(LEADER_UNTIL, until);
        }
        return chosen;
    }

    /** Pure local fallback retained for deterministic tests and tooling. */
    public static AnimalEntity temporaryLeader(AnimalEntity self, List<AnimalEntity> herd) {
        if (self == null) return null;
        return Stream.concat(Stream.of(self), herd == null
                        ? Stream.empty() : herd.stream())
                .filter(AnimalEntity::isAlive)
                .filter(animal -> !animal.isBaby())
                .min(Comparator.comparing(AnimalEntity::getUUID))
                .orElse(null);
    }

    /** Distinct multi-ring slot, stable across entity reloads through UUID order. */
    public static Vector3d formationTarget(AnimalEntity animal, AnimalEntity anchor,
                                       List<AnimalEntity> herd) {
        if (animal == null || anchor == null) {
            return animal == null ? Vector3d.ZERO : animal.position();
        }
        List<AnimalEntity> followers = members(animal, herd).stream()
                .filter(member -> member != anchor)
                .sorted(Comparator.comparing(AnimalEntity::getUUID))
                .collect(java.util.stream.Collectors.toList());
        int index = Math.max(0, followers.indexOf(animal));
        int ring = 1;
        int capacity = 8;
        while (index >= capacity) {
            index -= capacity;
            ring++;
            capacity = 8 * ring;
        }
        double base = animal.isBaby() ? 1.6D : 2.8D;
        double radius = base + (ring - 1) * 1.8D;
        double angle = index * Math.PI * 2.0D / capacity;
        return anchor.position().add(Math.cos(angle) * radius, 0.0D,
                Math.sin(angle) * radius);
    }

    /** Dense groups settle; only one edge animal may bridge a distant cluster. */
    public static boolean settledInCrowd(AnimalEntity animal, List<AnimalEntity> herd,
                                         AnimalEntity leader) {
        if (animal == null || leader == null) return false;
        double clusterDistanceSqr = AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS
                * AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS;
        List<AnimalEntity> cluster = members(animal, herd).stream()
                .filter(member -> animal.distanceToSqr(member)
                        <= clusterDistanceSqr)
                .collect(java.util.stream.Collectors.toList());
        if (cluster.size() < AnimalRules.ANIMAL_HERD_CLUSTER_SIZE) {
            return false;
        }
        if (animal.distanceToSqr(leader)
                <= AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR) {
            return true;
        }
        AnimalEntity bridge = cluster.stream()
                .min(Comparator.comparing(AnimalEntity::getUUID))
                .orElse(animal);
        return animal != bridge;
    }

    public static Vector3d commonFleeDestination(AnimalEntity animal,
                                             List<AnimalEntity> herd,
                                             Vector3d danger) {
        Vector3d center = animal.position();
        int count = 1;
        for (AnimalEntity member : herd) {
            center = center.add(member.position());
            count++;
        }
        center = center.scale(1.0D / count);
        Vector3d away = horizontal(center.subtract(danger));
        if (away == null) {
            away = new Vector3d((animal.getId() & 1) == 0 ? 1.0D : -1.0D,
                    0.0D, (animal.getId() & 2) == 0 ? 1.0D : -1.0D)
                    .normalize();
        }
        Vector3d side = new Vector3d(-away.z, 0.0D, away.x)
                .scale(((animal.getId() % 5) - 2) * 0.7D);
        return animal.position().add(away.scale(
                AnimalRules.ANIMAL_FLEE_DISTANCE)).add(side);
    }

    private static boolean validLeader(AnimalEntity candidate, AnimalEntity self) {
        if (candidate == null || self == null || !candidate.isAlive()
                || candidate.isBaby()
                || candidate.getType() != self.getType()
                || !supports(candidate)) {
            return false;
        }
        double maximum = AnimalRules.ANIMAL_HERD_RADIUS
                * AnimalRules.ANIMAL_HERD_LEADER_SEARCH_MULTIPLIER;
        return self.distanceToSqr(candidate) <= maximum * maximum;
    }

    private static boolean shouldSeparate(AnimalEntity animal, AnimalEntity nearest) {
        double distance = AnimalRules.ANIMAL_COLLISION_DISTANCE;
        return animal.distanceToSqr(nearest) < distance * distance
                && animal.getUUID().compareTo(nearest.getUUID()) > 0;
    }

    private static AnimalEntity relayAnchor(AnimalEntity animal, AnimalEntity leader,
                                      List<AnimalEntity> herd) {
        double ownLeaderDistance = animal.distanceToSqr(leader);
        return herd.stream()
                .filter(member -> member != animal && member != leader)
                .filter(member -> member.distanceToSqr(leader)
                        + 4.0D < ownLeaderDistance)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(leader);
    }

    private static List<AnimalEntity> members(AnimalEntity self, List<AnimalEntity> herd) {
        List<AnimalEntity> members = new ArrayList<>();
        if (self != null && self.isAlive()) members.add(self);
        if (herd != null) {
            for (AnimalEntity member : herd) {
                if (member != null && member.isAlive()
                        && !members.contains(member)) {
                    members.add(member);
                }
            }
        }
        return members;
    }

    private static void offerStop(MobDecisionPlan plan, AnimalEntity animal,
                                  int priority) {
        if (animal.getNavigation().isDone()) return;
        plan.offerSpecial(VanillaInstinctsState.HERD,
                ActionOwner.ANIMAL_BEHAVIOUR, priority,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> animal.getNavigation().stop());
    }

    private static void clearLeaderMemory(AnimalEntity animal) {
        animal.getPersistentData().remove(LEADER);
        animal.getPersistentData().remove(LEADER_UNTIL);
    }

    private static void shareDanger(AnimalEntity animal, List<AnimalEntity> herd,
                                    long gameTime) {
        BlockPos best = danger(animal, gameTime);
        long bestUntil = animal.getPersistentData().getLong(DANGER_UNTIL);
        for (AnimalEntity member : herd) {
            long until = member.getPersistentData().getLong(DANGER_UNTIL);
            if (until > gameTime && until > bestUntil) {
                bestUntil = until;
                best = danger(member, gameTime);
            }
        }
        if (best != null) {
            rememberDanger(animal, best, bestUntil);
            MobMovementPolicy.markFrightened(animal, gameTime,
                    Math.min(120L, Math.max(20L, bestUntil - gameTime)));
        }
    }

    private static LivingEntity nearestDanger(AnimalEntity animal,
                                               ServerWorld level) {
        return level.getEntitiesOfClass(LivingEntity.class,
                        animal.getBoundingBox().inflate(
                                AnimalRules.ANIMAL_DANGER_RADIUS),
                        entity -> entity != animal && entity.isAlive()
                                && (entity instanceof IMob
                                || entity instanceof WolfEntity
                                && !((WolfEntity) (entity)).isTame()
                                || entity instanceof FoxEntity
                                || entity instanceof OcelotEntity))
                .stream()
                .filter(entity -> !(entity instanceof MobEntity)
                        || ((MobEntity) (entity)).getTarget() == animal
                        || animal.distanceToSqr(entity) <= 25.0D)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
    }

    private static AnimalEntity nearestBaby(AnimalEntity animal, List<AnimalEntity> herd) {
        return herd.stream().filter(AnimalEntity::isBaby)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
    }

    private static Vector3d betweenYoungAndDanger(AnimalEntity baby, Vector3d danger) {
        Vector3d towardDanger = horizontal(danger.subtract(baby.position()));
        return towardDanger == null ? null
                : baby.position().add(towardDanger.scale(1.9D));
    }

    private static Vector3d horizontal(Vector3d value) {
        if (value == null) return null;
        Vector3d flat = value.multiply(1.0D, 0.0D, 1.0D);
        return flat.lengthSqr() < 1.0E-8D ? null : flat.normalize();
    }

    private static void rememberDanger(AnimalEntity animal, BlockPos pos,
                                       long until) {
        animal.getPersistentData().putLong(DANGER_UNTIL,
                Math.max(0L, until));
        animal.getPersistentData().putInt(DANGER_X, pos.getX());
        animal.getPersistentData().putInt(DANGER_Y, pos.getY());
        animal.getPersistentData().putInt(DANGER_Z, pos.getZ());
    }

    private static BlockPos danger(AnimalEntity animal, long gameTime) {
        if (animal.getPersistentData().getLong(DANGER_UNTIL) <= gameTime) {
            return null;
        }
        return new BlockPos(animal.getPersistentData().getInt(DANGER_X),
                animal.getPersistentData().getInt(DANGER_Y),
                animal.getPersistentData().getInt(DANGER_Z));
    }
}
