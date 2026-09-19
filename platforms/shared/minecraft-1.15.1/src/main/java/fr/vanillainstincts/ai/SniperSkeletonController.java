package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import fr.vanillainstincts.core.rules.SniperSkeletonRules;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.WitherSkeletonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.ProjectileHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
public final class SniperSkeletonController {
    private static final ResourceLocation SNIPER_DUEL =
            new ResourceLocation(
                    "minecraft", "adventure/sniper_duel");
    private static final String FLAG = "vanillainstincts_sniper_skeleton";
    private static final String TARGET = "vanillainstincts_sniper_target";
    private static final String NEXT_SHOT = "vanillainstincts_sniper_next_shot";

    private SniperSkeletonController() {
    }

    public static void onJoin(AbstractSkeletonEntity skeleton, ServerWorld level,
                              boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || skeleton instanceof WitherSkeletonEntity) {
            return;
        }
        if (isSniper(skeleton)) {
            applyRange(skeleton);
            return;
        }
        if (loadedFromDisk
                || !holdsBow(skeleton)) {
            return;
        }

        ServerPlayerEntity owner = nearestEligiblePlayer(skeleton, level);
        if (owner == null) return;
        int localSnipers = countLocalSnipers(skeleton, level);
        double roll = skeleton.getRandom().nextDouble();
        if (!shouldPromoteConfigured(true, true, localSnipers, roll,
                FeatureGate.difficulty(level))) return;
        promote(skeleton, owner.getUUID());
    }

    public static void tick(AbstractSkeletonEntity skeleton, ServerWorld level,
                            long gameTime) {
        if (!isSniper(skeleton) || skeleton instanceof WitherSkeletonEntity) {
            return;
        }
        if (Math.floorMod(gameTime + skeleton.getId(), 200L) == 0L) {
            applyRange(skeleton);
        }

        ServerPlayerEntity target = resolveTarget(skeleton, level);
        if (!validTarget(skeleton, target)) {
            if (target != null && skeleton.getTarget() == target) {
                skeleton.setTarget(null);
            }
            return;
        }

        skeleton.setTarget(target);
        skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distanceSqr = skeleton.distanceToSqr(target);
        boolean visible = skeleton.getSensing().canSee(target);
        if (!canCustomShootConfigured(distanceSqr, visible)
                || gameTime < nextShotAt(skeleton)) {
            return;
        }

        shoot(skeleton, target, level);
        setNextShotAt(skeleton, gameTime
                + RuntimeConfig.interval(FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_SHOT_COOLDOWN_TICKS));
    }

    public static boolean shouldPromote(boolean advancementUnlocked,
                                        boolean naturalSpawn,
                                        int localSnipers,
                                        double roll) {
        return advancementUnlocked && naturalSpawn
                && localSnipers >= 0
                && localSnipers < SniperSkeletonRules.SNIPER_SKELETON_LOCAL_LIMIT
                && roll >= 0.0D
                && roll < SniperSkeletonRules.SNIPER_SKELETON_SPAWN_CHANCE;
    }

    public static boolean canCustomShoot(double distanceSqr,
                                         boolean lineOfSight) {
        double min = SniperSkeletonRules.SNIPER_SKELETON_CUSTOM_MIN_RANGE;
        double max = SniperSkeletonRules.SNIPER_SKELETON_MAX_RANGE;
        return lineOfSight && distanceSqr >= min * min
                && distanceSqr <= max * max;
    }

    public static double leadTicks(double distance) {
        if (distance <= 0.0D) return 0.0D;
        double flight = distance / SniperSkeletonRules.SNIPER_SKELETON_ARROW_SPEED;
        return Math.min(SniperSkeletonRules.SNIPER_SKELETON_MAX_LEAD_TICKS,
                flight * SniperSkeletonRules.SNIPER_SKELETON_LEAD_FACTOR);
    }


    private static boolean shouldPromoteConfigured(boolean advancementUnlocked,
                                                   boolean naturalSpawn,
                                                   int localSnipers,
                                                   double roll,
                                                   DifficultyTier difficulty) {
        int limit = RuntimeConfig.snapshot().sniperLocalLimit();
        double normalChance = RuntimeConfig.chance(
                FeatureFlag.SNIPER_SKELETONS,
                RuntimeConfig.snapshot().sniperSpawnChance());
        double chance = AdaptiveProgressionRules.spawnChance(
                normalChance, difficulty);
        return advancementUnlocked && naturalSpawn
                && localSnipers >= 0 && localSnipers < limit
                && roll >= 0.0D && roll < chance;
    }

    private static boolean canCustomShootConfigured(double distanceSqr,
                                                     boolean lineOfSight) {
        double min = RuntimeConfig.distance(FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_CUSTOM_MIN_RANGE);
        double max = configuredMaxRange();
        return lineOfSight && distanceSqr >= min * min
                && distanceSqr <= max * max;
    }

    private static double configuredMaxRange() {
        double configured = RuntimeConfig.distance(
                FeatureFlag.SNIPER_SKELETONS,
                RuntimeConfig.snapshot().sniperMaxRange());
        return Math.max(SniperSkeletonRules.SNIPER_SKELETON_MAX_RANGE,
                configured);
    }

    public static ResourceLocation advancementId() {
        return SNIPER_DUEL;
    }

    public static boolean isSniper(AbstractSkeletonEntity skeleton) {
        return skeleton != null
                && skeleton.getPersistentData().getBoolean(FLAG);
    }

    public static void promote(AbstractSkeletonEntity skeleton, UUID targetId) {
        if (skeleton == null || targetId == null) return;
        CompoundNBT data = skeleton.getPersistentData();
        data.putBoolean(FLAG, true);
        data.putString(TARGET, targetId.toString());
        data.putLong(NEXT_SHOT, 0L);
        applyRange(skeleton);
    }

    public static UUID targetId(AbstractSkeletonEntity skeleton) {
        if (skeleton == null) return null;
        String value = skeleton.getPersistentData().getString(TARGET);
        if (value.trim().isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ServerPlayerEntity nearestEligiblePlayer(
            AbstractSkeletonEntity skeleton, ServerWorld level) {
        double ownerRange = RuntimeConfig.distance(
                FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_OWNER_SEARCH_RANGE);
        double maxDistanceSqr = ownerRange * ownerRange;
        return level.players().stream()
                .filter(SniperSkeletonController::hasSniperDuel)
                .filter(SniperSkeletonController::isCombatTarget)
                .filter(player -> skeleton.distanceToSqr(player)
                        <= maxDistanceSqr)
                .min(Comparator.comparingDouble(skeleton::distanceToSqr))
                .orElse(null);
    }

    private static int countLocalSnipers(AbstractSkeletonEntity skeleton,
                                         ServerWorld level) {
        double radius = RuntimeConfig.distance(FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_LOCAL_RADIUS);
        return level.getEntitiesOfClass(AbstractSkeletonEntity.class,
                skeleton.getBoundingBox().inflate(radius),
                SniperSkeletonController::isSniper).size();
    }

    private static boolean hasSniperDuel(ServerPlayerEntity player) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        Advancement advancement = server.getAdvancements()
                .getAdvancement(SNIPER_DUEL);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static ServerPlayerEntity resolveTarget(AbstractSkeletonEntity skeleton,
                                              ServerWorld level) {
        UUID id = targetId(skeleton);
        PlayerEntity player = id == null ? null : level.getPlayerByUUID(id);
        return player instanceof ServerPlayerEntity
                ? ((ServerPlayerEntity) (player)) : null;
    }

    private static boolean validTarget(AbstractSkeletonEntity skeleton,
                                       ServerPlayerEntity player) {
        if (!isCombatTarget(player) || player.level != skeleton.level) {
            return false;
        }
        double max = configuredMaxRange();
        return skeleton.distanceToSqr(player) <= max * max;
    }

    private static boolean isCombatTarget(ServerPlayerEntity player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static void shoot(AbstractSkeletonEntity skeleton, ServerPlayerEntity target,
                              ServerWorld level) {
        ItemStack weapon = weapon(skeleton);
        AbstractArrowEntity arrow = ProjectileHelper.getMobArrow(skeleton,
                Items.ARROW.getDefaultInstance(), 1.0F);
        arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());

        double distance = skeleton.distanceTo(target);
        double lead = leadTicks(distance);
        Vec3d motion = target.getDeltaMovement();
        double aimX = target.getX() + motion.x * lead;
        double aimY = target.getY(0.55D) + motion.y * lead;
        double aimZ = target.getZ() + motion.z * lead;
        double x = aimX - arrow.getX();
        double y = aimY - arrow.getY();
        double z = aimZ - arrow.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        double arc = horizontal * SniperSkeletonRules.SNIPER_SKELETON_ARC_FACTOR;

        arrow.shoot(x, y + arc, z,
                SniperSkeletonRules.SNIPER_SKELETON_ARROW_SPEED,
                SniperSkeletonRules.SNIPER_SKELETON_INACCURACY);
        level.addFreshEntity(arrow);
        skeleton.swing(Hand.MAIN_HAND);
        skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                0.9F + skeleton.getRandom().nextFloat() * 0.2F);
    }

    private static ItemStack weapon(AbstractSkeletonEntity skeleton) {
        ItemStack mainHand = skeleton.getMainHandItem();
        return mainHand.getItem().equals(Items.BOW) ? mainHand
                : skeleton.getOffhandItem();
    }

    private static boolean holdsBow(AbstractSkeletonEntity skeleton) {
        return skeleton.getMainHandItem().getItem().equals(Items.BOW)
                || skeleton.getOffhandItem().getItem().equals(Items.BOW);
    }

    private static void applyRange(AbstractSkeletonEntity skeleton) {
        IAttributeInstance range = skeleton.getAttribute(
                SharedMonsterAttributes.FOLLOW_RANGE);
        double configured = configuredMaxRange();
        if (range != null && range.getBaseValue() < configured) {
            range.setBaseValue(configured);
        }
    }

    private static long nextShotAt(AbstractSkeletonEntity skeleton) {
        return skeleton.getPersistentData().getLong(NEXT_SHOT);
    }

    private static void setNextShotAt(AbstractSkeletonEntity skeleton, long value) {
        skeleton.getPersistentData().putLong(NEXT_SHOT, value);
    }
}
