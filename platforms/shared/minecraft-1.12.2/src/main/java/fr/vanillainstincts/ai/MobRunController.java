package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import java.util.UUID;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.SharedMonsterAttributes;

/**
 * Owns the explicit movement-speed boosts used by gameplay controllers.
 * Other systems must request navigation without changing base attributes.
 */
public final class MobRunController {
    private static final UUID COMBAT_SPRINT = UUID.fromString(
            "33b9559e-a3d9-58f4-a999-db8e52ecb83d");
    private static final UUID GOLEM_DEFENSE_SPRINT = UUID.fromString(
            "555c9e3f-1988-5650-b3ba-c8e4654ca480");
    private static final UUID ZOMBIE_PURSUIT_SPRINT = UUID.fromString(
            "5676a02e-ea99-5a67-acb4-afa0b658905f");

    private MobRunController() {
    }

    public static void setCombatSprint(EntityLiving mob, boolean active) {
        if (mob == null) return;
        active = active && MobMovementPolicy.hasGrownFromBaby(mob);
        IAttributeInstance speed = mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            replaceTransientModifier(speed, COMBAT_SPRINT,
                    "vanillainstincts.grown_mob_sprint",
                    PerformanceRules.COMBAT_SPRINT_BONUS);
            mob.setSprinting(true);
        } else {
            speed.removeModifier(COMBAT_SPRINT);
            if (!MobMovementPolicy.hasActiveNetherRun(mob,
                    mob.world.getTotalWorldTime())
                    && !MobMovementPolicy.isFrightened(mob,
                    mob.world.getTotalWorldTime())) {
                mob.setSprinting(false);
            }
        }
    }

    /** Contextual pursuit boost owned by Zombie Tactics 3.0. */
    public static void setZombiePursuitSprint(EntityLiving mob, boolean active) {
        if (mob == null) return;
        IAttributeInstance speed = mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            replaceTransientModifier(speed, ZOMBIE_PURSUIT_SPRINT,
                    "vanillainstincts.zombie_pursuit_sprint",
                    ZombieRules.PURSUIT_SPRINT_BONUS);
            mob.setSprinting(true);
        } else {
            speed.removeModifier(ZOMBIE_PURSUIT_SPRINT);
            if (!MobMovementPolicy.shouldCombatSprint(mob, mob.getAttackTarget(),
                    mob.world.getTotalWorldTime())
                    && !MobMovementPolicy.hasActiveNetherRun(mob,
                    mob.world.getTotalWorldTime())
                    && !MobMovementPolicy.isFrightened(mob,
                    mob.world.getTotalWorldTime())) {
                mob.setSprinting(false);
            }
        }
    }

    public static void setGolemDefenseSprint(EntityLiving mob, boolean active) {
        if (mob == null) return;
        IAttributeInstance speed = mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (active) {
            replaceTransientModifier(speed, GOLEM_DEFENSE_SPRINT,
                    "vanillainstincts.golem_defense_sprint",
                    PerformanceRules.GOLEM_DEFENSE_SPRINT_BONUS);
            mob.setSprinting(true);
        } else {
            speed.removeModifier(GOLEM_DEFENSE_SPRINT);
            mob.setSprinting(false);
        }
    }

    public static void clearRuntimeModifiers(EntityLiving mob) {
        if (mob == null) return;
        IAttributeInstance speed = mob.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(COMBAT_SPRINT);
            speed.removeModifier(GOLEM_DEFENSE_SPRINT);
            speed.removeModifier(ZOMBIE_PURSUIT_SPRINT);
        }
        mob.setSprinting(false);
    }

    private static void replaceTransientModifier(IAttributeInstance attribute,
                                                 UUID id,
                                                 String name,
                                                 double amount) {
        attribute.removeModifier(id);
        attribute.applyModifier(new AttributeModifier(id, name, amount, 2));
    }
}
