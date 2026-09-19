package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.decision.MobPersonality;
import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Selects portable cargo without owning movement or teleport execution. */
public final class EndermanCargoSelector {
    private EndermanCargoSelector() {
    }

    public static EndermanCargoRole roleFor(@Nullable Entity cargo) {
        if (cargo instanceof AbstractSkeleton) {
            return EndermanCargoRole.ARCHER_PLATFORM;
        }
        if (cargo instanceof Spider) {
            return EndermanCargoRole.SPIDER_PLATFORM;
        }
        if (cargo instanceof ItemEntity) {
            return EndermanCargoRole.ITEM_PRESENTATION;
        }
        if (cargo instanceof Creeper) {
            return EndermanCargoRole.CREEPER_DELIVERY;
        }
        if (cargo instanceof Monster monster
                && monster.getMaxHealth() > 0.0F
                && monster.getHealth() / monster.getMaxHealth()
                <= EndermanRules.ENDERMAN_RESCUE_HEALTH_RATIO) {
            return EndermanCargoRole.ALLY_RESCUE;
        }
        if (cargo instanceof Player) {
            return EndermanCargoRole.PLAYER_RELOCATION;
        }
        if (cargo instanceof Monster) {
            return EndermanCargoRole.MONSTER_DELIVERY;
        }
        if (cargo instanceof Mob) {
            return EndermanCargoRole.CREATURE_GIFT;
        }
        return EndermanCargoRole.NONE;
    }

    public static boolean canCarry(EnderMan enderman,
                                   @Nullable Entity cargo) {
        if (!basicCargoChecks(enderman, cargo)) {
            return false;
        }
        if (cargo instanceof Player player) {
            return !player.isCreative() && !player.isSpectator()
                    && enderman.getRandom().nextDouble()
                    <= EndermanRules.ENDERMAN_PLAYER_CARRY_CHANCE;
        }
        return cargo instanceof Mob || cargo instanceof ItemEntity;
    }

    public static boolean canCarryType(@Nullable Entity cargo) {
        if (cargo == null || cargo instanceof EnderMan
                || cargo instanceof Warden
                || cargo.getType() == EntityTypes.WITHER
                || cargo.getType() == EntityTypes.ENDER_DRAGON) {
            return false;
        }
        if (cargo instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return cargo instanceof Mob || cargo instanceof ItemEntity;
    }

    public static boolean isSafeCargoCandidate(@Nullable Entity cargo) {
        return canCarryType(cargo) && cargo != null && cargo.isAlive()
                && !cargo.isPassenger() && !cargo.isVehicle()
                && (!(cargo instanceof Creeper creeper)
                || !creeper.isIgnited());
    }

    public static @Nullable Entity chooseCargo(EnderMan enderman,
                                                @Nullable LivingEntity objective,
                                                ServerLevel level) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                enderman.getBoundingBox().inflate(
                        EndermanRules.ENDERMAN_CARGO_SCAN_RADIUS),
                entity -> entity != enderman && entity != objective
                        && !(entity instanceof Player)
                        && isSafeCargoCandidate(entity)
                        && entity.getBbWidth()
                        <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                        && entity.getBbHeight()
                        <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT);
        candidates.sort(Comparator.comparingDouble(entity ->
                cargoScore(entity, objective, enderman)));
        for (LivingEntity candidate : candidates) {
            if (roleFor(candidate) == EndermanCargoRole.ALLY_RESCUE
                    && candidate instanceof Mob candidateMob
                    && !EndermanAllyPolicy.canCoordinate(enderman,
                    candidateMob)) {
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
        return switch (profile) {
            case ENDERMAN_COURIER -> switch (role) {
                case CREEPER_DELIVERY -> -30.0D;
                case MONSTER_DELIVERY -> -18.0D;
                case ARCHER_PLATFORM -> -15.0D;
                case ALLY_RESCUE -> -6.0D;
                case CREATURE_GIFT -> 3.0D;
                default -> 12.0D;
            };
            case ENDERMAN_RESCUER -> switch (role) {
                case ALLY_RESCUE -> -36.0D;
                case CREATURE_GIFT -> -8.0D;
                case ARCHER_PLATFORM -> -3.0D;
                case CREEPER_DELIVERY, MONSTER_DELIVERY -> 8.0D;
                default -> 12.0D;
            };
            case ENDERMAN_TRICKSTER -> switch (role) {
                case CREEPER_DELIVERY -> -32.0D;
                case MONSTER_DELIVERY -> -14.0D;
                case ARCHER_PLATFORM -> -9.0D;
                case CREATURE_GIFT -> -4.0D;
                case ALLY_RESCUE -> -2.0D;
                default -> 10.0D;
            };
            default -> switch (role) {
                case CREATURE_GIFT -> -24.0D;
                case ALLY_RESCUE -> -18.0D;
                case ARCHER_PLATFORM -> -8.0D;
                case MONSTER_DELIVERY -> -2.0D;
                case CREEPER_DELIVERY -> -10.0D;
                default -> 12.0D;
            };
        };
    }

    static boolean basicCargoChecks(EnderMan enderman,
                                            @Nullable Entity cargo) {
        return enderman != null && cargo != null && cargo != enderman
                && isSafeCargoCandidate(cargo)
                && cargo.getBbWidth() <= EndermanRules.ENDERMAN_MAX_CARGO_WIDTH
                && cargo.getBbHeight()
                <= EndermanRules.ENDERMAN_MAX_CARGO_HEIGHT;
    }

    private static double cargoScore(Entity cargo,
                                     @Nullable LivingEntity objective,
                                     EnderMan enderman) {
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
