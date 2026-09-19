package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.MobPersonality;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.monster.EndermanEntity;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.entity.player.PlayerEntity;
import javax.annotation.Nullable;

/** Selects portable cargo without owning movement or teleport execution. */
public final class EndermanCargoSelector {
    private EndermanCargoSelector() {
    }

    public static EndermanCargoRole roleFor(@Nullable Entity cargo) {
        if (cargo instanceof AbstractSkeletonEntity) {
            return EndermanCargoRole.ARCHER_PLATFORM;
        }
        if (cargo instanceof SpiderEntity) {
            return EndermanCargoRole.SPIDER_PLATFORM;
        }
        if (cargo instanceof ItemEntity) {
            return EndermanCargoRole.ITEM_PRESENTATION;
        }
        if (cargo instanceof CreeperEntity) {
            return EndermanCargoRole.CREEPER_DELIVERY;
        }
        if (cargo instanceof MonsterEntity
                && ((MonsterEntity) (cargo)).getMaxHealth() > 0.0F
                && ((MonsterEntity) (cargo)).getHealth() / ((MonsterEntity) (cargo)).getMaxHealth()
                <= EndermanRules.ENDERMAN_RESCUE_HEALTH_RATIO) { MonsterEntity monster = (MonsterEntity) (cargo); 
            return EndermanCargoRole.ALLY_RESCUE;
        }
        if (cargo instanceof PlayerEntity) {
            return EndermanCargoRole.PLAYER_RELOCATION;
        }
        if (cargo instanceof MonsterEntity) {
            return EndermanCargoRole.MONSTER_DELIVERY;
        }
        if (cargo instanceof MobEntity) {
            return EndermanCargoRole.CREATURE_GIFT;
        }
        return EndermanCargoRole.NONE;
    }

    public static boolean canCarry(EndermanEntity enderman,
                                   @Nullable Entity cargo) {
        if (!basicCargoChecks(enderman, cargo)) {
            return false;
        }
        if (cargo instanceof PlayerEntity) { PlayerEntity player = (PlayerEntity) (cargo); 
            return !player.isCreative() && !player.isSpectator()
                    && enderman.getRandom().nextDouble()
                    <= EndermanRules.ENDERMAN_PLAYER_CARRY_CHANCE;
        }
        return cargo instanceof MobEntity || cargo instanceof ItemEntity;
    }

    public static boolean canCarryType(@Nullable Entity cargo) {
        if (cargo == null || cargo instanceof EndermanEntity
                || cargo.getType() == EntityType.WITHER
                || cargo.getType() == EntityType.ENDER_DRAGON) {
            return false;
        }
        if (cargo instanceof PlayerEntity) { PlayerEntity player = (PlayerEntity) (cargo); 
            return !player.isCreative() && !player.isSpectator();
        }
        return cargo instanceof MobEntity || cargo instanceof ItemEntity;
    }

    public static boolean isSafeCargoCandidate(@Nullable Entity cargo) {
        return canCarryType(cargo) && cargo != null && cargo.isAlive()
                && !cargo.isPassenger() && !cargo.isVehicle()
                && (!(cargo instanceof CreeperEntity)
                || !((CreeperEntity) (cargo)).isIgnited());
    }

    public static @Nullable Entity chooseCargo(EndermanEntity enderman,
                                                @Nullable LivingEntity objective,
                                                ServerWorld level) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                enderman.getBoundingBox().inflate(
                        EndermanRules.ENDERMAN_CARGO_SCAN_RADIUS),
                entity -> entity != enderman && entity != objective
                        && !(entity instanceof PlayerEntity)
                        && isSafeCargoCandidate(entity)
                        && entity.getBbWidth()
                        <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                        && entity.getBbHeight()
                        <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT);
        candidates.sort(Comparator.comparingDouble(entity ->
                cargoScore(entity, objective, enderman)));
        for (LivingEntity candidate : candidates) {
            if (roleFor(candidate) == EndermanCargoRole.ALLY_RESCUE
                    && candidate instanceof MobEntity
                    && !EndermanAllyPolicy.canCoordinate(enderman,
                    ((MobEntity) (candidate)))) { MobEntity candidateMob = (MobEntity) (candidate); 
                continue;
            }
            return candidate;
        }
        return level.getEntitiesOfClass(ItemEntity.class,
                        enderman.getBoundingBox().inflate(
                                EndermanRules.ENDERMAN_CARGO_SCAN_RADIUS),
                        item -> item.isAlive() && !item.isPassenger())
                .stream()
                .min(Comparator.comparingDouble(enderman::distanceToSqr))
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

    static boolean basicCargoChecks(EndermanEntity enderman,
                                            @Nullable Entity cargo) {
        return enderman != null && cargo != null && cargo != enderman
                && isSafeCargoCandidate(cargo)
                && cargo.getBbWidth() <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                && cargo.getBbHeight()
                <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT;
    }

    private static double cargoScore(Entity cargo,
                                     @Nullable LivingEntity objective,
                                     EndermanEntity enderman) {
        EndermanCargoRole role = roleFor(cargo);
        MobPersonality personality =
                MobPersonalityController.profileFor(enderman);
        double roleBonus = cargoPreferenceBonus(personality, role);
        double objectiveDistance = objective == null ? 0.0D
                : objective.distanceToSqr(cargo) * 0.03D;
        return enderman.distanceToSqr(cargo) * 0.35D
                + objectiveDistance + roleBonus;
    }
}
