package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
/**
 * Owns the explicit movement-speed boosts used by gameplay controllers.
 * Other systems must request navigation without changing base attributes.
 */
public final class MobRunController {
    private static final ResourceLocation COMBAT_SPRINT =
            VanillaInstincts.id("grown_mob_sprint");
    private static final ResourceLocation GOLEM_DEFENSE_SPRINT =
            VanillaInstincts.id("golem_defense_sprint");
    private static final ResourceLocation ZOMBIE_PURSUIT_SPRINT =
            VanillaInstincts.id("zombie_pursuit_sprint");

    private MobRunController() {
    }

    public static void setCombatSprint(Mob mob, boolean active) {
        if (mob == null) return;
        active = active && MobMovementPolicy.hasGrownFromBaby(mob);
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    COMBAT_SPRINT, PerformanceRules.COMBAT_SPRINT_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setSprinting(true);
        } else {
            speed.removeModifier(COMBAT_SPRINT);
            if (!MobMovementPolicy.hasActiveNetherRun(mob,
                    mob.level().getGameTime())
                    && !MobMovementPolicy.isFrightened(mob,
                    mob.level().getGameTime())) {
                mob.setSprinting(false);
            }
        }
    }

    /** Contextual pursuit boost owned by Zombie Tactics 3.0. */
    public static void setZombiePursuitSprint(Mob mob, boolean active) {
        if (mob == null) return;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    ZOMBIE_PURSUIT_SPRINT, ZombieRules.PURSUIT_SPRINT_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setSprinting(true);
        } else {
            speed.removeModifier(ZOMBIE_PURSUIT_SPRINT);
            if (!MobMovementPolicy.shouldCombatSprint(mob, mob.getTarget(),
                    mob.level().getGameTime())
                    && !MobMovementPolicy.hasActiveNetherRun(mob,
                    mob.level().getGameTime())
                    && !MobMovementPolicy.isFrightened(mob,
                    mob.level().getGameTime())) {
                mob.setSprinting(false);
            }
        }
    }

    public static void setGolemDefenseSprint(Mob mob, boolean active) {
        if (mob == null) return;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    GOLEM_DEFENSE_SPRINT,
                    PerformanceRules.GOLEM_DEFENSE_SPRINT_BONUS,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setSprinting(true);
        } else {
            speed.removeModifier(GOLEM_DEFENSE_SPRINT);
            mob.setSprinting(false);
        }
    }

    public static void clearRuntimeModifiers(Mob mob) {
        if (mob == null) return;
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(COMBAT_SPRINT);
            speed.removeModifier(GOLEM_DEFENSE_SPRINT);
            speed.removeModifier(ZOMBIE_PURSUIT_SPRINT);
        }
        mob.setSprinting(false);
    }
}
