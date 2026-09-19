package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.Vec3;

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

    public static boolean supports(EntityAnimal animal) {
        return animal != null
                && !(animal instanceof EntityWolf)

                && !(animal instanceof EntityOcelot)
                && (!(animal instanceof EntityTameable)
                || !fr.vanillainstincts.compat.Minecraft112Compat.isTamed(animal));
    }

    public static void onAnimalDamaged(EntityAnimal animal, EntityLivingBase attacker,
                                       long gameTime) {
        if (!supports(animal) || attacker == null) return;
        rememberDanger(animal, entityBlockPos(attacker), gameTime + 240L);
        MobMovementPolicy.markFrightened(animal, gameTime, 120L);
    }

    public static void contribute(EntityAnimal animal, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (!supports(animal) || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(animal)
                || !VanillaInstinctsScheduler.claim(level, animal,
                PerformanceRules.ENTITY_SCAN_COST)) {
            return;
        }

        List<EntityAnimal> herd = level.getEntitiesWithinAABB(EntityAnimal.class,
                animal.getEntityBoundingBox().expand(AnimalRules.ANIMAL_HERD_RADIUS, AnimalRules.ANIMAL_HERD_RADIUS, AnimalRules.ANIMAL_HERD_RADIUS),
                other -> other != animal && other.isEntityAlive()
                        && other.getClass() == animal.getClass()
                        && supports(other));
        if (herd.isEmpty()) return;

        shareDanger(animal, herd, gameTime);
        EntityLivingBase immediate = nearestDanger(animal, level);
        BlockPos remembered = danger(animal, gameTime);
        Vec3 dangerPosition = immediate != null ? immediate.getPositionVector()
                : remembered == null ? null : Minecraft115VectorCompat.atCenterOf(remembered);

        if (dangerPosition != null) {
            if (!animal.isChild()) {
                EntityAnimal baby = nearestBaby(animal, herd);
                if (baby != null && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, baby)
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

        EntityAnimal nearest = herd.stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, value))))
                .orElse(null);
        if (nearest != null && shouldSeparate(animal, nearest)) {
            Vec3 away = horizontal(animal.getPositionVector().subtract(
                    nearest.getPositionVector()));
            if (away != null) {
                plan.offerNavigation(VanillaInstinctsState.SEPARATE,
                        ActionOwner.ANIMAL_BEHAVIOUR,
                        AnimalRules.PRIORITY_ANIMAL_SEPARATE,
                        animal.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 1.5D)), 0.82D,
                        AnimalRules.STATE_HOLD_ANIMAL_TICKS, null);
                return;
            }
        }

        EntityAnimal leader = stableLeader(animal, herd, level, gameTime);
        if (leader == null) return;

        if (animal == leader) {
            EntityAnimal laggard = herd.stream()
                    .max(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(leader, value))))
                    .orElse(null);
            if (laggard != null && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(leader, laggard)
                    > AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR
                    && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(leader, laggard)
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

        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, leader);
        if (animal.isChild() || distanceSqr
                > AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR) {
            EntityAnimal anchor = relayAnchor(animal, leader, herd);
            Vec3 target = formationTarget(animal, anchor, herd);
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, target)
                    <= AnimalRules.ANIMAL_HERD_ARRIVAL_DISTANCE_SQR) {
                offerStop(plan, animal,
                        AnimalRules.PRIORITY_ANIMAL_HERD + 1);
                return;
            }
            plan.offerNavigation(VanillaInstinctsState.HERD,
                    ActionOwner.ANIMAL_BEHAVIOUR,
                    AnimalRules.PRIORITY_ANIMAL_HERD
                            + (animal.isChild() ? 8 : 0),
                    target, AnimalRules.ANIMAL_HERD_SPEED,
                    AnimalRules.STATE_HOLD_ANIMAL_TICKS, null);
        }
    }

    /** Stable leader shared through persistent memory across overlapping scans. */
    public static EntityAnimal stableLeader(EntityAnimal self, List<EntityAnimal> herd,
                                      WorldServer level, long gameTime) {
        if (self == null || level == null) return null;
        List<EntityAnimal> members = members(self, herd);
        Map<UUID, EntityAnimal> rememberedLeaders = new HashMap<>();
        for (EntityAnimal member : members) {
            if (member.getEntityData().getLong(LEADER_UNTIL) <= gameTime
                    || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(member.getEntityData(), LEADER)) {
                continue;
            }
            Entity remembered = level.getEntityFromUuid(
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(member.getEntityData(), LEADER));
            if (remembered instanceof EntityAnimal
                    && validLeader(((EntityAnimal) (remembered)), self)) { EntityAnimal rememberedAnimal = (EntityAnimal) (remembered); 
                rememberedLeaders.put(rememberedAnimal.getUniqueID(),
                        rememberedAnimal);
            }
        }
        EntityAnimal chosen = rememberedLeaders.values().stream()
                .min(Comparator.comparing(EntityAnimal::getUniqueID))
                .orElseGet(() -> members.stream()
                        .filter(member -> validLeader(member, self))
                        .min(Comparator.comparing(EntityAnimal::getUniqueID))
                        .orElse(null));
        if (chosen == null) {
            clearLeaderMemory(self);
            return null;
        }
        long until = gameTime + AnimalRules.ANIMAL_HERD_LEADER_MEMORY_TICKS;
        for (EntityAnimal member : members) {
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(member.getEntityData(), LEADER, chosen.getUniqueID());
            member.getEntityData().setLong(LEADER_UNTIL, until);
        }
        return chosen;
    }

    /** Pure local fallback retained for deterministic tests and tooling. */
    public static EntityAnimal temporaryLeader(EntityAnimal self, List<EntityAnimal> herd) {
        if (self == null) return null;
        return Stream.concat(Stream.of(self), herd == null
                        ? Stream.empty() : herd.stream())
                .filter(EntityAnimal::isEntityAlive)
                .filter(animal -> !animal.isChild())
                .min(Comparator.comparing(EntityAnimal::getUniqueID))
                .orElse(null);
    }

    /** Distinct multi-ring slot, stable across entity reloads through UUID order. */
    public static Vec3 formationTarget(EntityAnimal animal, EntityAnimal anchor,
                                       List<EntityAnimal> herd) {
        if (animal == null || anchor == null) {
            return animal == null ? new Vec3(0.0D, 0.0D, 0.0D) : animal.getPositionVector();
        }
        List<EntityAnimal> followers = members(animal, herd).stream()
                .filter(member -> member != anchor)
                .sorted(Comparator.comparing(EntityAnimal::getUniqueID))
                .collect(java.util.stream.Collectors.toList());
        int index = Math.max(0, followers.indexOf(animal));
        int ring = 1;
        int capacity = 8;
        while (index >= capacity) {
            index -= capacity;
            ring++;
            capacity = 8 * ring;
        }
        double base = animal.isChild() ? 1.6D : 2.8D;
        double radius = base + (ring - 1) * 1.8D;
        double angle = index * Math.PI * 2.0D / capacity;
        return fr.vanillainstincts.compat.Minecraft112Compat.add(anchor.getPositionVector(), Math.cos(angle) * radius, 0.0D,
                Math.sin(angle) * radius);
    }

    /** Dense groups settle; only one edge animal may bridge a distant cluster. */
    public static boolean settledInCrowd(EntityAnimal animal, List<EntityAnimal> herd,
                                         EntityAnimal leader) {
        if (animal == null || leader == null) return false;
        double clusterDistanceSqr = AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS
                * AnimalRules.ANIMAL_HERD_CLUSTER_RADIUS;
        List<EntityAnimal> cluster = members(animal, herd).stream()
                .filter(member -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, member)
                        <= clusterDistanceSqr)
                .collect(java.util.stream.Collectors.toList());
        if (cluster.size() < AnimalRules.ANIMAL_HERD_CLUSTER_SIZE) {
            return false;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, leader)
                <= AnimalRules.ANIMAL_HERD_REJOIN_DISTANCE_SQR) {
            return true;
        }
        EntityAnimal bridge = cluster.stream()
                .min(Comparator.comparing(EntityAnimal::getUniqueID))
                .orElse(animal);
        return animal != bridge;
    }

    public static Vec3 commonFleeDestination(EntityAnimal animal,
                                             List<EntityAnimal> herd,
                                             Vec3 danger) {
        Vec3 center = animal.getPositionVector();
        int count = 1;
        for (EntityAnimal member : herd) {
            center = center.add(member.getPositionVector());
            count++;
        }
        center = fr.vanillainstincts.compat.Minecraft112Compat.scale(center, 1.0D / count);
        Vec3 away = horizontal(center.subtract(danger));
        if (away == null) {
            away = new Vec3((animal.getEntityId() & 1) == 0 ? 1.0D : -1.0D,
                    0.0D, (animal.getEntityId() & 2) == 0 ? 1.0D : -1.0D)
                    .normalize();
        }
        Vec3 side = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-away.zCoord, 0.0D, away.xCoord), ((animal.getEntityId() % 5) - 2) * 0.7D);
        return animal.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(away, 
                AnimalRules.ANIMAL_FLEE_DISTANCE)).add(side);
    }

    private static boolean validLeader(EntityAnimal candidate, EntityAnimal self) {
        if (candidate == null || self == null || !candidate.isEntityAlive()
                || candidate.isChild()
                || candidate.getClass() != self.getClass()
                || !supports(candidate)) {
            return false;
        }
        double maximum = AnimalRules.ANIMAL_HERD_RADIUS
                * AnimalRules.ANIMAL_HERD_LEADER_SEARCH_MULTIPLIER;
        return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(self, candidate) <= maximum * maximum;
    }

    private static boolean shouldSeparate(EntityAnimal animal, EntityAnimal nearest) {
        double distance = AnimalRules.ANIMAL_COLLISION_DISTANCE;
        return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, nearest) < distance * distance
                && animal.getUniqueID().compareTo(nearest.getUniqueID()) > 0;
    }

    private static EntityAnimal relayAnchor(EntityAnimal animal, EntityAnimal leader,
                                      List<EntityAnimal> herd) {
        double ownLeaderDistance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, leader);
        return herd.stream()
                .filter(member -> member != animal && member != leader)
                .filter(member -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(member, leader)
                        + 4.0D < ownLeaderDistance)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, value))))
                .orElse(leader);
    }

    private static List<EntityAnimal> members(EntityAnimal self, List<EntityAnimal> herd) {
        List<EntityAnimal> members = new ArrayList<>();
        if (self != null && self.isEntityAlive()) members.add(self);
        if (herd != null) {
            for (EntityAnimal member : herd) {
                if (member != null && member.isEntityAlive()
                        && !members.contains(member)) {
                    members.add(member);
                }
            }
        }
        return members;
    }

    private static void offerStop(MobDecisionPlan plan, EntityAnimal animal,
                                  int priority) {
        if (animal.getNavigator().noPath()) return;
        plan.offerSpecial(VanillaInstinctsState.HERD,
                ActionOwner.ANIMAL_BEHAVIOUR, priority,
                AnimalRules.STATE_HOLD_ANIMAL_TICKS,
                () -> animal.getNavigator().clearPathEntity());
    }

    private static void clearLeaderMemory(EntityAnimal animal) {
        animal.getEntityData().removeTag(LEADER);
        animal.getEntityData().removeTag(LEADER_UNTIL);
    }

    private static void shareDanger(EntityAnimal animal, List<EntityAnimal> herd,
                                    long gameTime) {
        BlockPos best = danger(animal, gameTime);
        long bestUntil = animal.getEntityData().getLong(DANGER_UNTIL);
        for (EntityAnimal member : herd) {
            long until = member.getEntityData().getLong(DANGER_UNTIL);
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

    private static EntityLivingBase nearestDanger(EntityAnimal animal,
                                               WorldServer level) {
        return level.getEntitiesWithinAABB(EntityLivingBase.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(animal.getEntityBoundingBox(), 
                                AnimalRules.ANIMAL_DANGER_RADIUS),
                        entity -> entity != animal && entity.isEntityAlive()
                                && (entity instanceof IMob
                                || entity instanceof EntityWolf
                                && !fr.vanillainstincts.compat.Minecraft112Compat.isTamed(entity)
                                || entity instanceof EntityOcelot))
                .stream()
                .filter(entity -> !(entity instanceof EntityLiving)
                        || ((EntityLiving) (entity)).getAttackTarget() == animal
                        || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, entity) <= 25.0D)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, value))))
                .orElse(null);
    }

    private static EntityAnimal nearestBaby(EntityAnimal animal, List<EntityAnimal> herd) {
        return herd.stream().filter(EntityAnimal::isChild)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, value))))
                .orElse(null);
    }

    private static Vec3 betweenYoungAndDanger(EntityAnimal baby, Vec3 danger) {
        Vec3 towardDanger = horizontal(danger.subtract(baby.getPositionVector()));
        return towardDanger == null ? null
                : baby.getPositionVector().add(fr.vanillainstincts.compat.Minecraft112Compat.scale(towardDanger, 1.9D));
    }

    private static Vec3 horizontal(Vec3 value) {
        if (value == null) return null;
        Vec3 flat = fr.vanillainstincts.compat.Minecraft112Compat.multiply(value, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(flat) < 1.0E-8D ? null : flat.normalize();
    }

    private static void rememberDanger(EntityAnimal animal, BlockPos pos,
                                       long until) {
        animal.getEntityData().setLong(DANGER_UNTIL,
                Math.max(0L, until));
        animal.getEntityData().setInteger(DANGER_X, pos.getX());
        animal.getEntityData().setInteger(DANGER_Y, pos.getY());
        animal.getEntityData().setInteger(DANGER_Z, pos.getZ());
    }

    private static BlockPos danger(EntityAnimal animal, long gameTime) {
        if (animal.getEntityData().getLong(DANGER_UNTIL) <= gameTime) {
            return null;
        }
        return new BlockPos(animal.getEntityData().getInteger(DANGER_X),
                animal.getEntityData().getInteger(DANGER_Y),
                animal.getEntityData().getInteger(DANGER_Z));
    }
}
