package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import fr.vanillainstincts.core.rules.SniperSkeletonRules;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
public final class SniperSkeletonController {
    private static final ResourceLocation SNIPER_DUEL =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "adventure/sniper_duel");
    private static final String FLAG = "vanillainstincts_sniper_skeleton";
    private static final String TARGET = "vanillainstincts_sniper_target";
    private static final String NEXT_SHOT = "vanillainstincts_sniper_next_shot";

    private SniperSkeletonController() {
    }

    public static void onJoin(AbstractSkeleton skeleton, ServerLevel level,
                              boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || skeleton instanceof WitherSkeleton) {
            return;
        }
        if (isSniper(skeleton)) {
            applyRange(skeleton);
            return;
        }
        if (loadedFromDisk || skeleton.getSpawnReason() != EntitySpawnReason.NATURAL
                || !holdsBow(skeleton)) {
            return;
        }

        ServerPlayer owner = nearestEligiblePlayer(skeleton, level);
        if (owner == null) return;
        int localSnipers = countLocalSnipers(skeleton, level);
        double roll = skeleton.getRandom().nextDouble();
        if (!shouldPromoteConfigured(true, true, localSnipers, roll,
                FeatureGate.difficulty(level))) return;
        promote(skeleton, owner.getUUID());
    }

    public static void tick(AbstractSkeleton skeleton, ServerLevel level,
                            long gameTime) {
        if (!isSniper(skeleton) || skeleton instanceof WitherSkeleton) {
            return;
        }
        if (Math.floorMod(gameTime + skeleton.getId(), 200L) == 0L) {
            applyRange(skeleton);
        }

        ServerPlayer target = resolveTarget(skeleton, level);
        if (!validTarget(skeleton, target)) {
            if (target != null && skeleton.getTarget() == target) {
                skeleton.setTarget(null);
            }
            return;
        }

        skeleton.setTarget(target);
        skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distanceSqr = skeleton.distanceToSqr(target);
        boolean visible = skeleton.getSensing().hasLineOfSight(target);
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

    public static boolean isSniper(AbstractSkeleton skeleton) {
        return skeleton != null
                && skeleton.getPersistentData().getBoolean(FLAG);
    }

    public static void promote(AbstractSkeleton skeleton, UUID targetId) {
        if (skeleton == null || targetId == null) return;
        CompoundTag data = skeleton.getPersistentData();
        data.putBoolean(FLAG, true);
        data.putString(TARGET, targetId.toString());
        data.putLong(NEXT_SHOT, 0L);
        applyRange(skeleton);
    }

    public static UUID targetId(AbstractSkeleton skeleton) {
        if (skeleton == null) return null;
        String value = skeleton.getPersistentData().getString(TARGET);
        if (value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ServerPlayer nearestEligiblePlayer(
            AbstractSkeleton skeleton, ServerLevel level) {
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

    private static int countLocalSnipers(AbstractSkeleton skeleton,
                                         ServerLevel level) {
        double radius = RuntimeConfig.distance(FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_LOCAL_RADIUS);
        return level.getEntitiesOfClass(AbstractSkeleton.class,
                skeleton.getBoundingBox().inflate(radius),
                SniperSkeletonController::isSniper).size();
    }

    private static boolean hasSniperDuel(ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        AdvancementHolder advancement = server.getAdvancements()
                .get(SNIPER_DUEL);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static ServerPlayer resolveTarget(AbstractSkeleton skeleton,
                                              ServerLevel level) {
        UUID id = targetId(skeleton);
        Player player = id == null ? null : level.getPlayerByUUID(id);
        return player instanceof ServerPlayer serverPlayer
                ? serverPlayer : null;
    }

    private static boolean validTarget(AbstractSkeleton skeleton,
                                       ServerPlayer player) {
        if (!isCombatTarget(player) || player.level() != skeleton.level()) {
            return false;
        }
        double max = configuredMaxRange();
        return skeleton.distanceToSqr(player) <= max * max;
    }

    private static boolean isCombatTarget(ServerPlayer player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static void shoot(AbstractSkeleton skeleton, ServerPlayer target,
                              ServerLevel level) {
        ItemStack weapon = weapon(skeleton);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(skeleton,
                Items.ARROW.getDefaultInstance(), 1.0F, weapon);
        arrow.setPos(skeleton.getX(), skeleton.getEyeY() - 0.1D,
                skeleton.getZ());

        double distance = skeleton.distanceTo(target);
        double lead = leadTicks(distance);
        Vec3 motion = target.getDeltaMovement();
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
        skeleton.swing(InteractionHand.MAIN_HAND);
        skeleton.playSound(SoundEvents.SKELETON_SHOOT, 1.0F,
                0.9F + skeleton.getRandom().nextFloat() * 0.2F);
    }

    private static ItemStack weapon(AbstractSkeleton skeleton) {
        ItemStack mainHand = skeleton.getMainHandItem();
        return mainHand.is(Items.BOW) ? mainHand
                : skeleton.getOffhandItem();
    }

    private static boolean holdsBow(AbstractSkeleton skeleton) {
        return skeleton.getMainHandItem().is(Items.BOW)
                || skeleton.getOffhandItem().is(Items.BOW);
    }

    private static void applyRange(AbstractSkeleton skeleton) {
        AttributeInstance range = skeleton.getAttribute(
                Attributes.FOLLOW_RANGE);
        double configured = configuredMaxRange();
        if (range != null && range.getBaseValue() < configured) {
            range.setBaseValue(configured);
        }
    }

    private static long nextShotAt(AbstractSkeleton skeleton) {
        return skeleton.getPersistentData().getLong(NEXT_SHOT);
    }

    private static void setNextShotAt(AbstractSkeleton skeleton, long value) {
        skeleton.getPersistentData().putLong(NEXT_SHOT, value);
    }
}
