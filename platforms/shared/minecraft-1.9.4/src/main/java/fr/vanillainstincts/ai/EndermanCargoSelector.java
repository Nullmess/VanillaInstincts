package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.MobPersonality;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.player.EntityPlayer;
import javax.annotation.Nullable;

/** Selects portable cargo without owning movement or teleport execution. */
public final class EndermanCargoSelector {
    private EndermanCargoSelector() {
    }

    public static EndermanCargoRole roleFor(@Nullable Entity cargo) {
        if (cargo instanceof EntitySkeleton) {
            return EndermanCargoRole.ARCHER_PLATFORM;
        }
        if (cargo instanceof EntitySpider) {
            return EndermanCargoRole.SPIDER_PLATFORM;
        }
        if (cargo instanceof EntityItem) {
            return EndermanCargoRole.ITEM_PRESENTATION;
        }
        if (cargo instanceof EntityCreeper) {
            return EndermanCargoRole.CREEPER_DELIVERY;
        }
        if (cargo instanceof EntityMob
                && ((EntityMob) (cargo)).getMaxHealth() > 0.0F
                && ((EntityMob) (cargo)).getHealth() / ((EntityMob) (cargo)).getMaxHealth()
                <= EndermanRules.ENDERMAN_RESCUE_HEALTH_RATIO) { EntityMob monster = (EntityMob) (cargo); 
            return EndermanCargoRole.ALLY_RESCUE;
        }
        if (cargo instanceof EntityPlayer) {
            return EndermanCargoRole.PLAYER_RELOCATION;
        }
        if (cargo instanceof EntityMob) {
            return EndermanCargoRole.MONSTER_DELIVERY;
        }
        if (cargo instanceof EntityLiving) {
            return EndermanCargoRole.CREATURE_GIFT;
        }
        return EndermanCargoRole.NONE;
    }

    public static boolean canCarry(EntityEnderman enderman,
                                   @Nullable Entity cargo) {
        if (!basicCargoChecks(enderman, cargo)) {
            return false;
        }
        if (cargo instanceof EntityPlayer) { EntityPlayer player = (EntityPlayer) (cargo); 
            return !player.isCreative() && !player.isSpectator()
                    &&fr.vanillainstincts.compat.Minecraft112Compat.random(enderman).nextDouble()
                    <= EndermanRules.ENDERMAN_PLAYER_CARRY_CHANCE;
        }
        return cargo instanceof EntityLiving || cargo instanceof EntityItem;
    }

    public static boolean canCarryType(@Nullable Entity cargo) {
        if (cargo == null || cargo instanceof EntityEnderman
                || cargo instanceof EntityWither
                || cargo instanceof EntityDragon) {
            return false;
        }
        if (cargo instanceof EntityPlayer) { EntityPlayer player = (EntityPlayer) (cargo); 
            return !player.isCreative() && !player.isSpectator();
        }
        return cargo instanceof EntityLiving || cargo instanceof EntityItem;
    }

    public static boolean isSafeCargoCandidate(@Nullable Entity cargo) {
        return canCarryType(cargo) && cargo != null && cargo.isEntityAlive()
                && !fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(cargo) && !fr.vanillainstincts.compat.Minecraft112Compat.isVehicle(cargo)
                && (!(cargo instanceof EntityCreeper)
                || !fr.vanillainstincts.compat.Minecraft112Compat.creeperIgnited((EntityCreeper) cargo));
    }

    public static @Nullable Entity chooseCargo(EntityEnderman enderman,
                                                @Nullable EntityLivingBase objective,
                                                WorldServer level) {
        List<EntityLivingBase> candidates = level.getEntitiesWithinAABB(
                EntityLivingBase.class,
                enderman.getEntityBoundingBox().expandXyz(
                        EndermanRules.ENDERMAN_CARGO_SCAN_RADIUS),
                entity -> entity != enderman && entity != objective
                        && !(entity instanceof EntityPlayer)
                        && isSafeCargoCandidate(entity)
                        && entity.width
                        <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                        && entity.height
                        <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT);
        candidates.sort(Comparator.comparingDouble(entity ->
                cargoScore(entity, objective, enderman)));
        for (EntityLivingBase candidate : candidates) {
            if (roleFor(candidate) == EndermanCargoRole.ALLY_RESCUE
                    && candidate instanceof EntityLiving
                    && !EndermanAllyPolicy.canCoordinate(enderman,
                    ((EntityLiving) (candidate)))) { EntityLiving candidateMob = (EntityLiving) (candidate); 
                continue;
            }
            return candidate;
        }
        return level.getEntitiesWithinAABB(EntityItem.class,
                        enderman.getEntityBoundingBox().expandXyz(
                                EndermanRules.ENDERMAN_CARGO_SCAN_RADIUS),
                        item -> item.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft112Compat.isPassenger(item))
                .stream()
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, value))))
                .orElse(null);
    }

    public static double cargoPreferenceBonus(MobPersonality personality,
                                               EndermanCargoRole role) {
        if (role == null) {
            return 20.0D;
        }
        MobPersonality profile = personality == null
                ? MobPersonality.ENDERMAN_CARRIER : personality;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((profile)) { case ENDERMAN_COURIER:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((role)) { case CREEPER_DELIVERY:  return -30.0D; case MONSTER_DELIVERY:  return -18.0D; case ARCHER_PLATFORM:  return -15.0D; case ALLY_RESCUE:  return -6.0D; case CREATURE_GIFT:  return 3.0D; default:  return 12.0D; } }); case ENDERMAN_RESCUER:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((role)) { case ALLY_RESCUE:  return -36.0D; case CREATURE_GIFT:  return -8.0D; case ARCHER_PLATFORM:  return -3.0D; case CREEPER_DELIVERY: case MONSTER_DELIVERY:  return 8.0D; default:  return 12.0D; } }); case ENDERMAN_TRICKSTER:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((role)) { case CREEPER_DELIVERY:  return -32.0D; case MONSTER_DELIVERY:  return -14.0D; case ARCHER_PLATFORM:  return -9.0D; case CREATURE_GIFT:  return -4.0D; case ALLY_RESCUE:  return -2.0D; default:  return 10.0D; } }); default:  return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((role)) { case CREATURE_GIFT:  return -24.0D; case ALLY_RESCUE:  return -18.0D; case ARCHER_PLATFORM:  return -8.0D; case MONSTER_DELIVERY:  return -2.0D; case CREEPER_DELIVERY:  return -10.0D; default:  return 12.0D; } }); } });
    }

    static boolean basicCargoChecks(EntityEnderman enderman,
                                            @Nullable Entity cargo) {
        return enderman != null && cargo != null && cargo != enderman
                && isSafeCargoCandidate(cargo)
                && cargo.width <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                && cargo.height
                <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT;
    }

    private static double cargoScore(Entity cargo,
                                     @Nullable EntityLivingBase objective,
                                     EntityEnderman enderman) {
        EndermanCargoRole role = roleFor(cargo);
        MobPersonality personality =
                MobPersonalityController.profileFor(enderman);
        double roleBonus = cargoPreferenceBonus(personality, role);
        double objectiveDistance = objective == null ? 0.0D
                : fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(objective, cargo) * 0.03D;
        return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(enderman, cargo) * 0.35D
                + objectiveDistance + roleBonus;
    }
}
