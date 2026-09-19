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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

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

    public static boolean supports(Animal animal) {
        return animal != null
                && !(animal instanceof Wolf)
                && !(animal instanceof Fox)
                && !(animal instanceof Ocelot)
                && (!(animal instanceof TamableAnimal tamable)
                || !tamable.isTame());
    }

    public static void onAnimalDamaged(Animal animal, LivingEntity attacker,
                                       long gameTime) {
        if (!supports(animal) || attacker == null) return;
        rememberDanger(animal, attacker.blockPosition(), gameTime + 240L);
        MobMovementPolicy.markFrightened(animal, gameTime, 120L);
    }

    public static void contribute(Animal animal, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (!supports(animal) || animal.isPassenger()
                || !VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }

        List<Animal> herd = level.getEntitiesOfClass(Animal.class,
                animal.getBoundingBox().inflate(AnimalRules.ANIMAL_HERD_RADIUS),
                other -> other != animal && other.isAlive()
                        && other.getType() == animal.getType()
                        && supports(other));
        if (herd.isEmpty()) return;

        shareDanger(animal, herd, gameTime);
        LivingEntity immediate = nearestDanger(animal, level);
        BlockPos remembered = danger(animal, gameTime);
        Vec3 dangerPosition = immediate != null ? immediate.position()
                : remembered == null ? null : Vec3.atCenterOf(remembered);

        if (dangerPosition != null) {
            if (!animal.isBaby()) {
                Animal baby = nearestBaby(animal, herd);
                if (baby != null && animal.distanceToSqr(baby)
                        <= AnimalRules.ANIMAL_PROTECT_YOUNG_RADIUS
                        * AnimalRules.ANIMAL_PROTECT_YOUNG_RADIUS) {
                    Vec3 guard = betweenYoungAndDanger(baby, dangerPosition);
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
            Vec3 commonEscape = commonFleeDestination(animal, herd,
                    dangerPosition);
            plan.offerNavigation(VanillaInstinctsState.FLEE,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_FLEE,
                    commonEscape, AnimalRules.ANIMAL_FLEE_SPEED,
                    AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                    () -> animal.setSprinting(true));
            return;
        }

        Animal nearest = herd.stream()
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
        if (nearest != null && shouldSeparate(animal, nearest)) {
            Vec3 away = horizontal(animal.position().subtract(
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

        Animal leader = stableLeader(animal, herd, level, gameTime);
        if (leader == null) return;

        if (animal == leader) {
            Animal laggard = herd.stream()
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
            Animal anchor = relayAnchor(animal, leader, herd);
            Vec3 target = formationTarget(animal, anchor, herd);
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
    public static Animal stableLeader(Animal self, List<Animal> herd,
                                      ServerLevel level, long gameTime) {
        if (self == null || level == null) return null;
        List<Animal> members = members(self, herd);
        Map<UUID, Animal> rememberedLeaders = new HashMap<>();
        for (Animal member : members) {
            if (fr.vanillainstincts.persistence.NbtCompat.getLong(member.getPersistentData(), LEADER_UNTIL) <= gameTime
                    || !fr.vanillainstincts.persistence.NbtCompat.hasUuid(member.getPersistentData(), LEADER)) {
                continue;
            }
            Entity remembered = level.getEntity(
                    fr.vanillainstincts.persistence.NbtCompat.getUuid(member.getPersistentData(), LEADER));
            if (remembered instanceof Animal rememberedAnimal
                    && validLeader(rememberedAnimal, self)) {
                rememberedLeaders.put(rememberedAnimal.getUUID(),
                        rememberedAnimal);
            }
        }
        Animal chosen = rememberedLeaders.values().stream()
                .min(Comparator.comparing(Animal::getUUID))
                .orElseGet(() -> members.stream()
                        .filter(member -> validLeader(member, self))
                        .min(Comparator.comparing(Animal::getUUID))
                        .orElse(null));
        if (chosen == null) {
            clearLeaderMemory(self);
            return null;
        }
        long until = gameTime + AnimalRules.ANIMAL_HERD_LEADER_MEMORY_TICKS;
        for (Animal member : members) {
            fr.vanillainstincts.persistence.NbtCompat.putUuid(member.getPersistentData(), LEADER, chosen.getUUID());
            member.getPersistentData().putLong(LEADER_UNTIL, until);
        }
        return chosen;
    }

    /** Pure local fallback retained for deterministic tests and tooling. */
    public static Animal temporaryLeader(Animal self, List<Animal> herd) {
        if (self == null) return null;
        return Stream.concat(Stream.of(self), herd == null
                        ? Stream.empty() : herd.stream())
                .filter(Animal::isAlive)
                .filter(animal -> !animal.isBaby())
                .min(Comparator.comparing(Animal::getUUID))
                .orElse(null);
    }

    /** Distinct multi-ring slot, stable across entity reloads through UUID order. */
    public static Vec3 formationTarget(Animal animal, Animal anchor,
                                       List<Animal> herd) {
        if (animal == null || anchor == null) {
            return animal == null ? Vec3.ZERO : animal.position();
        }
        List<Animal> followers = members(animal, herd).stream()
                .filter(member -> member != anchor)
                .sorted(Comparator.comparing(Animal::getUUID))
                .toList();
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
    public static boolean settledInCrowd(Animal animal, List<Animal> herd,
                                         Animal leader) {
        if (animal == null || leader == null) return false;
        double clusterDistanceSqr = AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS
                * AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS;
        List<Animal> cluster = members(animal, herd).stream()
                .filter(member -> animal.distanceToSqr(member)
                        <= clusterDistanceSqr)
                .toList();
        if (cluster.size() < AnimalRules.ANIMAL_HERD_CLUSTER_SIZE) {
            return false;
        }
        if (animal.distanceToSqr(leader)
                <= AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR) {
            return true;
        }
        Animal bridge = cluster.stream()
                .min(Comparator.comparing(Animal::getUUID))
                .orElse(animal);
        return animal != bridge;
    }

    public static Vec3 commonFleeDestination(Animal animal,
                                             List<Animal> herd,
                                             Vec3 danger) {
        Vec3 center = animal.position();
        int count = 1;
        for (Animal member : herd) {
            center = center.add(member.position());
            count++;
        }
        center = center.scale(1.0D / count);
        Vec3 away = horizontal(center.subtract(danger));
        if (away == null) {
            away = new Vec3((animal.getId() & 1) == 0 ? 1.0D : -1.0D,
                    0.0D, (animal.getId() & 2) == 0 ? 1.0D : -1.0D)
                    .normalize();
        }
        Vec3 side = new Vec3(-away.z, 0.0D, away.x)
                .scale(((animal.getId() % 5) - 2) * 0.7D);
        return animal.position().add(away.scale(
                AnimalRules.ANIMAL_FLEE_DISTANCE)).add(side);
    }

    private static boolean validLeader(Animal candidate, Animal self) {
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

    private static boolean shouldSeparate(Animal animal, Animal nearest) {
        double distance = AnimalRules.ANIMAL_COLLISION_DISTANCE;
        return animal.distanceToSqr(nearest) < distance * distance
                && animal.getUUID().compareTo(nearest.getUUID()) > 0;
    }

    private static Animal relayAnchor(Animal animal, Animal leader,
                                      List<Animal> herd) {
        double ownLeaderDistance = animal.distanceToSqr(leader);
        return herd.stream()
                .filter(member -> member != animal && member != leader)
                .filter(member -> member.distanceToSqr(leader)
                        + 4.0D < ownLeaderDistance)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(leader);
    }

    private static List<Animal> members(Animal self, List<Animal> herd) {
        List<Animal> members = new ArrayList<>();
        if (self != null && self.isAlive()) members.add(self);
        if (herd != null) {
            for (Animal member : herd) {
                if (member != null && member.isAlive()
                        && !members.contains(member)) {
                    members.add(member);
                }
            }
        }
        return members;
    }

    private static void offerStop(MobDecisionPlan plan, Animal animal,
                                  int priority) {
        if (animal.getNavigation().isDone()) return;
        plan.offerSpecial(VanillaInstinctsState.HERD,
                ActionOwner.ANIMAL_BEHAVIOUR, priority,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> animal.getNavigation().stop());
    }

    private static void clearLeaderMemory(Animal animal) {
        animal.getPersistentData().remove(LEADER);
        animal.getPersistentData().remove(LEADER_UNTIL);
    }

    private static void shareDanger(Animal animal, List<Animal> herd,
                                    long gameTime) {
        BlockPos best = danger(animal, gameTime);
        long bestUntil = fr.vanillainstincts.persistence.NbtCompat.getLong(animal.getPersistentData(), DANGER_UNTIL);
        for (Animal member : herd) {
            long until = fr.vanillainstincts.persistence.NbtCompat.getLong(member.getPersistentData(), DANGER_UNTIL);
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

    private static LivingEntity nearestDanger(Animal animal,
                                               ServerLevel level) {
        return level.getEntitiesOfClass(LivingEntity.class,
                        animal.getBoundingBox().inflate(
                                AnimalRules.ANIMAL_DANGER_RADIUS),
                        entity -> entity != animal && entity.isAlive()
                                && (entity instanceof Enemy
                                || entity instanceof Wolf wolf
                                && !wolf.isTame()
                                || entity instanceof Fox
                                || entity instanceof Ocelot))
                .stream()
                .filter(entity -> !(entity instanceof Mob mob)
                        || mob.getTarget() == animal
                        || animal.distanceToSqr(entity) <= 25.0D)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
    }

    private static Animal nearestBaby(Animal animal, List<Animal> herd) {
        return herd.stream().filter(Animal::isBaby)
                .min(Comparator.comparingDouble(animal::distanceToSqr))
                .orElse(null);
    }

    private static Vec3 betweenYoungAndDanger(Animal baby, Vec3 danger) {
        Vec3 towardDanger = horizontal(danger.subtract(baby.position()));
        return towardDanger == null ? null
                : baby.position().add(towardDanger.scale(1.9D));
    }

    private static Vec3 horizontal(Vec3 value) {
        if (value == null) return null;
        Vec3 flat = value.multiply(1.0D, 0.0D, 1.0D);
        return flat.lengthSqr() < 1.0E-8D ? null : flat.normalize();
    }

    private static void rememberDanger(Animal animal, BlockPos pos,
                                       long until) {
        animal.getPersistentData().putLong(DANGER_UNTIL,
                Math.max(0L, until));
        animal.getPersistentData().putInt(DANGER_X, pos.getX());
        animal.getPersistentData().putInt(DANGER_Y, pos.getY());
        animal.getPersistentData().putInt(DANGER_Z, pos.getZ());
    }

    private static BlockPos danger(Animal animal, long gameTime) {
        if (fr.vanillainstincts.persistence.NbtCompat.getLong(animal.getPersistentData(), DANGER_UNTIL) <= gameTime) {
            return null;
        }
        return new BlockPos(fr.vanillainstincts.persistence.NbtCompat.getInt(animal.getPersistentData(), DANGER_X),
                fr.vanillainstincts.persistence.NbtCompat.getInt(animal.getPersistentData(), DANGER_Y),
                fr.vanillainstincts.persistence.NbtCompat.getInt(animal.getPersistentData(), DANGER_Z));
    }
}
