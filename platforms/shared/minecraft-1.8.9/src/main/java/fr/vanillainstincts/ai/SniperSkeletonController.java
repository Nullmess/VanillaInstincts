package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.DifficultyTier;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import fr.vanillainstincts.core.rules.SniperSkeletonRules;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.Vec3;
public final class SniperSkeletonController {
    private static final ResourceLocation SNIPER_DUEL =
            new ResourceLocation(
                    "minecraft", "adventure/sniper_duel");
    private static final String FLAG = "vanillainstincts_sniper_skeleton";
    private static final String TARGET = "vanillainstincts_sniper_target";
    private static final String NEXT_SHOT = "vanillainstincts_sniper_next_shot";

    private SniperSkeletonController() {
    }

    public static void onJoin(EntitySkeleton skeleton, WorldServer level,
                              boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || fr.vanillainstincts.compat.Minecraft110Compat.isWitherSkeleton(skeleton)) {
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

        EntityPlayerMP owner = nearestEligiblePlayer(skeleton, level);
        if (owner == null) return;
        int localSnipers = countLocalSnipers(skeleton, level);
        double roll =fr.vanillainstincts.compat.Minecraft112Compat.random(skeleton).nextDouble();
        if (!shouldPromoteConfigured(true, true, localSnipers, roll,
                FeatureGate.difficulty(level))) return;
        promote(skeleton, owner.getUniqueID());
    }

    public static void tick(EntitySkeleton skeleton, WorldServer level,
                            long gameTime) {
        if (!isSniper(skeleton) || fr.vanillainstincts.compat.Minecraft110Compat.isWitherSkeleton(skeleton)) {
            return;
        }
        if (Math.floorMod(gameTime + skeleton.getEntityId(), 200L) == 0L) {
            applyRange(skeleton);
        }

        EntityPlayerMP target = resolveTarget(skeleton, level);
        if (!validTarget(skeleton, target)) {
            if (target != null && skeleton.getAttackTarget() == target) {
                skeleton.setAttackTarget(null);
            }
            return;
        }

        skeleton.setAttackTarget(target);
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(skeleton, target, 30.0F, 30.0F);
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, target);
        boolean visible = fr.vanillainstincts.compat.Minecraft112Compat.canSee(skeleton, target);
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

    public static boolean isSniper(EntitySkeleton skeleton) {
        return skeleton != null
                && skeleton.getEntityData().getBoolean(FLAG);
    }

    public static void promote(EntitySkeleton skeleton, UUID targetId) {
        if (skeleton == null || targetId == null) return;
        NBTTagCompound data = skeleton.getEntityData();
        data.setBoolean(FLAG, true);
        data.setString(TARGET, targetId.toString());
        data.setLong(NEXT_SHOT, 0L);
        applyRange(skeleton);
    }

    public static UUID targetId(EntitySkeleton skeleton) {
        if (skeleton == null) return null;
        String value = skeleton.getEntityData().getString(TARGET);
        if (value.trim().isEmpty()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static EntityPlayerMP nearestEligiblePlayer(
            EntitySkeleton skeleton, WorldServer level) {
        double ownerRange = RuntimeConfig.distance(
                FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_OWNER_SEARCH_RANGE);
        double maxDistanceSqr = ownerRange * ownerRange;
        return fr.vanillainstincts.compat.Minecraft112Compat.players(level).stream()
                .filter(SniperSkeletonController::hasSniperDuel)
                .filter(SniperSkeletonController::isCombatTarget)
                .filter(player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, player)
                        <= maxDistanceSqr)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, value))))
                .orElse(null);
    }

    private static int countLocalSnipers(EntitySkeleton skeleton,
                                         WorldServer level) {
        double radius = RuntimeConfig.distance(FeatureFlag.SNIPER_SKELETONS,
                SniperSkeletonRules.SNIPER_SKELETON_LOCAL_RADIUS);
        return level.getEntitiesWithinAABB(EntitySkeleton.class,
                skeleton.getEntityBoundingBox().expand(radius, radius, radius),
                SniperSkeletonController::isSniper).size();
    }

    private static boolean hasSniperDuel(EntityPlayerMP player) {
        return fr.vanillainstincts.compat.Minecraft112Compat.hasAdvancement(player, SNIPER_DUEL);
    }

    private static EntityPlayerMP resolveTarget(EntitySkeleton skeleton,
                                              WorldServer level) {
        UUID id = targetId(skeleton);
        EntityPlayer player = id == null ? null : fr.vanillainstincts.compat.Minecraft112Compat.player(level, id);
        return player instanceof EntityPlayerMP
                ? ((EntityPlayerMP) (player)) : null;
    }

    private static boolean validTarget(EntitySkeleton skeleton,
                                       EntityPlayerMP player) {
        if (!isCombatTarget(player) || player.worldObj != skeleton.worldObj) {
            return false;
        }
        double max = configuredMaxRange();
        return fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, player) <= max * max;
    }

    private static boolean isCombatTarget(EntityPlayerMP player) {
        return player != null && player.isEntityAlive()
                && !player.capabilities.isCreativeMode && !player.isSpectator();
    }

    private static void shoot(EntitySkeleton skeleton, EntityPlayerMP target,
                              WorldServer level) {
        ItemStack weapon = weapon(skeleton);
        EntityArrow arrow = fr.vanillainstincts.compat.Minecraft112Compat.mobArrow(skeleton, new ItemStack(Items.arrow), 1.0F);
        if (arrow == null) return;
        fr.vanillainstincts.compat.Minecraft112Compat.teleport(arrow, skeleton.posX, (skeleton.posY + skeleton.getEyeHeight()) - 0.1D,
                skeleton.posZ);

        double distance = skeleton.getDistanceToEntity(target);
        double lead = leadTicks(distance);
        Vec3 motion = fr.vanillainstincts.compat.Minecraft112Compat.motion(target);
        double aimX = target.posX + motion.xCoord * lead;
        double aimY = (target.posY + target.height * 0.55D) + motion.yCoord * lead;
        double aimZ = target.posZ + motion.zCoord * lead;
        double x = aimX - arrow.posX;
        double y = aimY - arrow.posY;
        double z = aimZ - arrow.posZ;
        double horizontal = Math.sqrt(x * x + z * z);
        double arc = horizontal * SniperSkeletonRules.SNIPER_SKELETON_ARC_FACTOR;

        arrow.setThrowableHeading(x, y + arc, z,
                SniperSkeletonRules.SNIPER_SKELETON_ARROW_SPEED,
                SniperSkeletonRules.SNIPER_SKELETON_INACCURACY);
        level.spawnEntityInWorld(arrow);
        skeleton.swingItem();
        skeleton.playSound("random.bow", 1.0F,
                0.9F +fr.vanillainstincts.compat.Minecraft112Compat.random(skeleton).nextFloat() * 0.2F);
    }

    private static ItemStack weapon(EntitySkeleton skeleton) {
        ItemStack mainHand = skeleton.getHeldItem();
        return mainHand.getItem().equals(Items.bow) ? mainHand
                : skeleton.getHeldItem();
    }

    private static boolean holdsBow(EntitySkeleton skeleton) {
        return skeleton.getHeldItem().getItem().equals(Items.bow)
                || skeleton.getHeldItem().getItem().equals(Items.bow);
    }

    private static void applyRange(EntitySkeleton skeleton) {
        IAttributeInstance range = skeleton.getEntityAttribute(
                SharedMonsterAttributes.followRange);
        double configured = configuredMaxRange();
        if (range != null && range.getBaseValue() < configured) {
            range.setBaseValue(configured);
        }
    }

    private static long nextShotAt(EntitySkeleton skeleton) {
        return skeleton.getEntityData().getLong(NEXT_SHOT);
    }

    private static void setNextShotAt(EntitySkeleton skeleton, long value) {
        skeleton.getEntityData().setLong(NEXT_SHOT, value);
    }
}
