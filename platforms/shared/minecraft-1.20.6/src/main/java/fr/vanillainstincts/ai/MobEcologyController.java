package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.MobRelationType;
import fr.vanillainstincts.core.rules.EcologyRules;
import fr.vanillainstincts.data.MobRelationManager;
import fr.vanillainstincts.data.MobRelationManager.RelationDecision;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Data-driven ecological reactions shared by passive animals. Predator HUNT
 * relations are handled by TargetAcquisitionController; this controller owns
 * the defensive AVOID side so prey can flee a threat they genuinely perceive.
 */
public final class MobEcologyController {
    private MobEcologyController() {
    }

    public static void contribute(Animal animal, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (animal == null || plan == null || level == null
                || !animal.isAlive() || animal.isPassenger()
                || (animal instanceof TamableAnimal tamable && tamable.isTame())
                || !VanillaInstinctsScheduler.isScheduled(animal,
                EcologyRules.SCAN_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, animal,
                EcologyRules.SCAN_COST)) {
            return;
        }
        Optional<EcologyThreat> threat = nearestAvoidedThreat(animal, level);
        if (threat.isEmpty()) return;
        LivingEntity source = threat.get().entity();
        Vec3 requested = fleeDestination(animal, source);
        if (requested == null) return;
        Vec3 destination = SafePositionFinder.resolveGroundDestination(
                animal, requested).orElse(null);
        if (destination == null) return;
        MobPerceptionMemory.rememberInterest(animal,
                source.blockPosition(),
                fr.vanillainstincts.core.model.StimulusType.VISUAL,
                gameTime, EcologyRules.FLEE_HOLD_TICKS, 1.0D);
        plan.offerNavigation(VanillaInstinctsState.FLEE,
                ActionOwner.ANIMAL_BEHAVIOUR,
                EcologyRules.FLEE_PRIORITY,
                destination, EcologyRules.FLEE_SPEED,
                EcologyRules.FLEE_HOLD_TICKS,
                () -> MobMovementPolicy.markFrightened(animal, gameTime,
                        EcologyRules.FLEE_HOLD_TICKS));
    }

    public static Optional<EcologyThreat> nearestAvoidedThreat(
            Animal animal, ServerLevel level) {
        if (animal == null || level == null) return Optional.empty();
        double radius = MobRelationManager.maximumRelationDistance(animal);
        if (radius <= 0.0D) return Optional.empty();
        int limit = VanillaInstinctsScheduler.precisionLimit(level, animal,
                EcologyRules.MAX_CANDIDATES,
                EcologyRules.MIN_CANDIDATES_UNDER_LOAD);
        AABB area = animal.getBoundingBox().inflate(radius);
        List<LivingEntity> nearby = new ArrayList<>(level.getEntitiesOfClass(
                LivingEntity.class, area,
                other -> other != animal && other.isAlive()));
        nearby.sort(Comparator.comparingDouble(animal::distanceToSqr));
        int inspected = 0;
        for (LivingEntity candidate : nearby) {
            if (inspected++ >= limit) break;
            Optional<RelationDecision> relation = MobRelationManager.relation(
                    animal, candidate);
            if (relation.isEmpty()
                    || relation.get().relation() != MobRelationType.AVOID) {
                continue;
            }
            RelationDecision decision = relation.get();
            if (animal.distanceToSqr(candidate)
                    > decision.maxDistance() * decision.maxDistance()) {
                continue;
            }
            // Defensive ecology is also perception-bound: no fleeing a wolf
            // through a mountain because a datapack knows its UUID.
            if (!MobPerceptionMemory.canSee(animal, candidate)) continue;
            return Optional.of(new EcologyThreat(candidate, decision,
                    inspected));
        }
        return Optional.empty();
    }

    public static boolean isHuntRelation(Mob predator,
                                         LivingEntity prey) {
        return MobRelationManager.relation(predator, prey)
                .map(decision -> decision.relation() == MobRelationType.HUNT)
                .orElse(false);
    }

    public static Vec3 fleeDestination(Animal animal, LivingEntity threat) {
        if (animal == null || threat == null) return null;
        Vec3 away = animal.position().subtract(threat.position());
        Vec3 horizontal = new Vec3(away.x, 0.0D, away.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            double sign = (animal.getId() & 1) == 0 ? 1.0D : -1.0D;
            horizontal = new Vec3(sign, 0.0D, 0.5D);
        }
        horizontal = horizontal.normalize();
        double side = Math.floorMod(animal.getId(), 3) - 1;
        Vec3 lateral = new Vec3(-horizontal.z, 0.0D, horizontal.x)
                .scale(side * 0.8D);
        return animal.position().add(horizontal.scale(
                EcologyRules.FLEE_DISTANCE)).add(lateral);
    }

    public record EcologyThreat(LivingEntity entity,
                                RelationDecision relation,
                                int inspectedCandidates) {
    }
}
