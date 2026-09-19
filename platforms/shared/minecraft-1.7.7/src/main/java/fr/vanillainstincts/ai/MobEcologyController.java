package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
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
import net.minecraft.world.WorldServer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.util.AxisAlignedBB;
import fr.vanillainstincts.compat.Vec3;

/**
 * Data-driven ecological reactions shared by passive animals. Predator HUNT
 * relations are handled by TargetAcquisitionController; this controller owns
 * the defensive AVOID side so prey can flee a threat they genuinely perceive.
 */
public final class MobEcologyController {
    private MobEcologyController() {
    }

    public static void contribute(EntityAnimal animal, MobDecisionPlan plan,
                                  WorldServer level, long gameTime) {
        if (animal == null || plan == null || level == null
                || !animal.isEntityAlive() || fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(animal)
                || (animal instanceof EntityTameable && fr.vanillainstincts.compat.Minecraft112Compat.isTamed(animal))
                || !VanillaInstinctsScheduler.isScheduled(animal,
                EcologyRules.SCAN_INTERVAL_TICKS)
                || !VanillaInstinctsScheduler.claim(level, animal,
                EcologyRules.SCAN_COST)) {
            return;
        }
        Optional<EcologyThreat> threat = nearestAvoidedThreat(animal, level);
        if (!threat.isPresent()) return;
        EntityLivingBase source = threat.get().entity();
        Vec3 requested = fleeDestination(animal, source);
        if (requested == null) return;
        Vec3 destination = SafePositionFinder.resolveGroundDestination(
                animal, requested).orElse(null);
        if (destination == null) return;
        MobPerceptionMemory.rememberInterest(animal,
                entityBlockPos(source),
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
            EntityAnimal animal, WorldServer level) {
        if (animal == null || level == null) return Optional.empty();
        double radius = MobRelationManager.maximumRelationDistance(animal);
        if (radius <= 0.0D) return Optional.empty();
        int limit = VanillaInstinctsScheduler.precisionLimit(level, animal,
                EcologyRules.MAX_CANDIDATES,
                EcologyRules.MIN_CANDIDATES_UNDER_LOAD);
        AxisAlignedBB area = fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(animal).expand(radius, radius, radius);
        List<EntityLivingBase> nearby = new ArrayList<>(fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, 
                EntityLivingBase.class, area,
                other -> other != animal && other.isEntityAlive()));
        nearby.sort(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, value))));
        int inspected = 0;
        for (EntityLivingBase candidate : nearby) {
            if (inspected++ >= limit) break;
            Optional<RelationDecision> relation = MobRelationManager.relation(
                    animal, candidate);
            if (!relation.isPresent()
                    || relation.get().relation() != MobRelationType.AVOID) {
                continue;
            }
            RelationDecision decision = relation.get();
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(animal, candidate)
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

    public static boolean isHuntRelation(EntityLiving predator,
                                         EntityLivingBase prey) {
        return MobRelationManager.relation(predator, prey)
                .map(decision -> decision.relation() == MobRelationType.HUNT)
                .orElse(false);
    }

    public static Vec3 fleeDestination(EntityAnimal animal, EntityLivingBase threat) {
        if (animal == null || threat == null) return null;
        Vec3 away = fr.vanillainstincts.compat.Minecraft17Compat.position(animal).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(threat));
        Vec3 horizontal = new Vec3(away.xCoord, 0.0D, away.zCoord);
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-6D) {
            double sign = (animal.getEntityId() & 1) == 0 ? 1.0D : -1.0D;
            horizontal = new Vec3(sign, 0.0D, 0.5D);
        }
        horizontal = horizontal.normalize();
        double side = Math.floorMod(animal.getEntityId(), 3) - 1;
        Vec3 lateral = fr.vanillainstincts.compat.Minecraft112Compat.scale(new Vec3(-horizontal.zCoord, 0.0D, horizontal.xCoord), side * 0.8D);
        return fr.vanillainstincts.compat.Minecraft17Compat.position(animal).add(fr.vanillainstincts.compat.Minecraft112Compat.scale(horizontal, 
                EcologyRules.FLEE_DISTANCE)).add(lateral);
    }

    public static class EcologyThreat {
        private final EntityLivingBase entity;
        private final RelationDecision relation;
        private final int inspectedCandidates;

        public EcologyThreat(EntityLivingBase entity, RelationDecision relation, int inspectedCandidates) {
            this.entity = entity;
            this.relation = relation;
            this.inspectedCandidates = inspectedCandidates;
        }

        public EntityLivingBase entity() { return this.entity; }

        public RelationDecision relation() { return this.relation; }

        public int inspectedCandidates() { return this.inspectedCandidates; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EcologyThreat)) return false;
            EcologyThreat that = (EcologyThreat) other;
            return java.util.Objects.equals(this.entity, that.entity) && java.util.Objects.equals(this.relation, that.relation) && this.inspectedCandidates == that.inspectedCandidates;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.entity, this.relation, this.inspectedCandidates); }

        @Override
        public String toString() {
            return "EcologyThreat[" + "entity=" + this.entity + ", " + "relation=" + this.relation + ", " + "inspectedCandidates=" + this.inspectedCandidates + "]";
        }

    }
}
