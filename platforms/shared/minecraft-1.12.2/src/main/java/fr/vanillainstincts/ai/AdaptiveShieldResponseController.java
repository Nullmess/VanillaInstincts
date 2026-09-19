package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Rare counters unlocked after a player demonstrates shield usage through the
 * vanilla "Not Today, Thank You" advancement.
 */
public final class AdaptiveShieldResponseController {
    private static final ResourceLocation DEFLECT_ARROW =
            new ResourceLocation(
                    "minecraft", "story/deflect_arrow");
    private static final String TARGET =
            "vanillainstincts_progression_target";
    private static final String ZOMBIE_FLANKER =
            "vanillainstincts_zombie_flanker";
    private static final String SKELETON_SHIELD =
            "vanillainstincts_skeleton_shieldfighter";
    private static final String SKELETON_MELEE =
            "vanillainstincts_skeleton_shieldfighter_melee";
    private static final String SKELETON_NEXT_ATTACK =
            "vanillainstincts_skeleton_shieldfighter_next_attack";

    private AdaptiveShieldResponseController() {
    }

    public static void onZombieJoin(EntityZombie zombie, WorldServer level,
                                    boolean loadedFromDisk) {
        if (zombie == null || level == null || zombie instanceof EntityZombieVillager) {
            return;
        }
        if (isZombieFlanker(zombie)) {
            ensureZombieAxe(zombie);
            return;
        }
        if (AdaptiveProgressionController.isAngler(zombie)
                || AdaptiveProgressionController.isPearlHunter(zombie)
                || loadedFromDisk || zombie.isChild()
               ) {
            return;
        }
        EntityPlayerMP owner = nearestPlayerWithAdvancement(
                zombie, level, DEFLECT_ARROW);
        if (owner == null) return;
        double chance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_FLANKER_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        if (countLocalZombieVariant(level, zombie, ZOMBIE_FLANKER)
                >= AdaptiveProgressionRules.ZOMBIE_FLANKER_LOCAL_LIMIT
                ||fr.vanillainstincts.compat.Minecraft112Compat.random(zombie).nextDouble() >= chance) {
            return;
        }
        promoteZombieFlanker(zombie, owner.getUniqueID());
    }

    public static void onSkeletonJoin(AbstractSkeleton skeleton,
                                      WorldServer level,
                                      boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || skeleton instanceof EntityWitherSkeleton) {
            return;
        }
        if (isShieldSkeleton(skeleton)) {
            equipSkeletonRanged(skeleton);
            return;
        }
        if (SniperSkeletonController.isSniper(skeleton)
                || loadedFromDisk
               
                || !skeleton.getHeldItemMainhand().getItem().equals(Items.BOW)) {
            return;
        }
        EntityPlayerMP owner = nearestPlayerWithAdvancement(
                skeleton, level, DEFLECT_ARROW);
        if (owner == null) return;
        double chance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.SKELETON_TACTICS,
                        AdaptiveProgressionRules.SKELETON_SHIELD_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        if (countLocalSkeletonVariant(level, skeleton, SKELETON_SHIELD)
                >= AdaptiveProgressionRules.SKELETON_SHIELD_LOCAL_LIMIT
                ||fr.vanillainstincts.compat.Minecraft112Compat.random(skeleton).nextDouble() >= chance) {
            return;
        }
        promoteShieldSkeleton(skeleton, owner.getUniqueID());
    }

    public static void tickSkeleton(AbstractSkeleton skeleton,
                                    WorldServer level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || skeleton == null
                || level == null || !skeleton.isEntityAlive()) {
            return;
        }
        EntityPlayerMP target = progressionTarget(skeleton, level);
        if (!validTarget(skeleton, target)) {
            setSkeletonMeleeMode(skeleton, false);
            return;
        }
        skeleton.setAttackTarget(target);
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, target);
        boolean melee = skeleton.getEntityData().getBoolean(
                SKELETON_MELEE);
        double enter = AdaptiveProgressionRules
                .SKELETON_SHIELD_MELEE_ENTER_RANGE;
        double resume = AdaptiveProgressionRules
                .SKELETON_SHIELD_RANGED_RESUME_RANGE;
        if (!melee && distanceSqr <= enter * enter) {
            setSkeletonMeleeMode(skeleton, true);
            melee = true;
        } else if (melee && distanceSqr >= resume * resume) {
            setSkeletonMeleeMode(skeleton, false);
            melee = false;
        }
        if (melee) {
            maintainShieldMelee(skeleton, target, gameTime);
        }
    }

    public static void contributeZombie(EntityZombie zombie, MobDecisionPlan plan,
                                        WorldServer level, long gameTime) {
        if (!isZombieFlanker(zombie) || plan == null || level == null) return;
        EntityPlayerMP target = progressionTarget(zombie, level);
        if (!validTarget(zombie, target)) return;
        zombie.setAttackTarget(target);
        double range = AdaptiveProgressionRules.ZOMBIE_FLANKER_TRIGGER_RANGE;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, target) > range * range
                || frontLineAllies(zombie, target, level)
                < AdaptiveProgressionRules.ZOMBIE_FLANKER_MIN_FRONT_ALLIES
                || alreadyBehindTarget(zombie, target)) {
            return;
        }
        Vec3d flank = flankDestination(zombie, target);
        plan.offerNavigation(VanillaInstinctsState.FLANK,
                ActionOwner.ZOMBIE_TACTICS,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_PRIORITY,
                flank, AdaptiveProgressionRules.ZOMBIE_FLANKER_SPEED,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_HOLD_TICKS, null);
    }

    public static void contributeSkeleton(AbstractSkeleton skeleton,
                                          MobDecisionPlan plan,
                                          WorldServer level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || plan == null || level == null
                || !skeleton.getEntityData().getBoolean(SKELETON_MELEE)) {
            return;
        }
        EntityPlayerMP target = progressionTarget(skeleton, level);
        if (!validTarget(skeleton, target)) return;
        plan.offerNavigation(VanillaInstinctsState.PURSUE,
                ActionOwner.SKELETON_TACTICS,
                AdaptiveProgressionRules.SKELETON_SHIELD_PRIORITY,
                target.getPositionVector(),
                AdaptiveProgressionRules.SKELETON_SHIELD_APPROACH_SPEED,
                AdaptiveProgressionRules.SKELETON_SHIELD_HOLD_TICKS, null);
    }

    public static boolean isZombieFlanker(EntityZombie zombie) {
        return zombie != null
                && zombie.getEntityData().getBoolean(ZOMBIE_FLANKER);
    }

    public static boolean isShieldSkeleton(AbstractSkeleton skeleton) {
        return skeleton != null
                && skeleton.getEntityData().getBoolean(SKELETON_SHIELD);
    }

    public static boolean isShieldSkeletonMelee(AbstractSkeleton skeleton) {
        return isShieldSkeleton(skeleton)
                && skeleton.getEntityData().getBoolean(SKELETON_MELEE);
    }

    public static ResourceLocation deflectArrowAdvancementId() {
        return DEFLECT_ARROW;
    }

    private static void promoteZombieFlanker(EntityZombie zombie, UUID targetId) {
        NBTTagCompound data = zombie.getEntityData();
        data.setBoolean(ZOMBIE_FLANKER, true);
        data.setUniqueId(TARGET, targetId);
        ensureZombieAxe(zombie);
    }

    private static void promoteShieldSkeleton(AbstractSkeleton skeleton,
                                              UUID targetId) {
        NBTTagCompound data = skeleton.getEntityData();
        data.setBoolean(SKELETON_SHIELD, true);
        data.setBoolean(SKELETON_MELEE, false);
        data.setUniqueId(TARGET, targetId);
        data.setLong(SKELETON_NEXT_ATTACK, 0L);
        equipSkeletonRanged(skeleton);
    }

    private static void maintainShieldMelee(AbstractSkeleton skeleton,
                                            EntityPlayerMP target,
                                            long gameTime) {
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(skeleton, target, 30.0F, 30.0F);
        double reach = skeleton.width * 2.0D;
        double reachSqr = reach * reach + target.width;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(skeleton, target) <= reachSqr
                && gameTime >= skeleton.getEntityData().getLong(
                SKELETON_NEXT_ATTACK)) {
            skeleton.stopActiveHand();
            if (skeleton.attackEntityAsMob(target)) {
                skeleton.swingArm(EnumHand.MAIN_HAND);
            }
            skeleton.getEntityData().setLong(SKELETON_NEXT_ATTACK,
                    gameTime + AdaptiveProgressionRules
                            .SKELETON_SHIELD_ATTACK_COOLDOWN_TICKS);
            return;
        }
        if (!skeleton.isHandActive()) {
            skeleton.setActiveHand(EnumHand.OFF_HAND);
        }
    }

    private static void setSkeletonMeleeMode(AbstractSkeleton skeleton,
                                             boolean melee) {
        NBTTagCompound data = skeleton.getEntityData();
        if (data.getBoolean(SKELETON_MELEE) == melee) return;
        data.setBoolean(SKELETON_MELEE, melee);
        skeleton.stopActiveHand();
        if (melee) {
            equipSkeletonMelee(skeleton);
        } else {
            equipSkeletonRanged(skeleton);
        }
    }

    private static void equipSkeletonMelee(AbstractSkeleton skeleton) {
        skeleton.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveAxe(skeleton));
        skeleton.setItemStackToSlot(EntityEquipmentSlot.OFFHAND,
                new ItemStack(Items.SHIELD));
        fr.vanillainstincts.compat.Minecraft112Compat.reassessWeaponGoal(skeleton);
    }

    private static void equipSkeletonRanged(AbstractSkeleton skeleton) {
        skeleton.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveBow(skeleton));
        skeleton.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
        fr.vanillainstincts.compat.Minecraft112Compat.reassessWeaponGoal(skeleton);
    }

    private static void ensureZombieAxe(EntityZombie zombie) {
        ItemStack expected = AdaptiveEquipmentProgressionController
                .adaptiveAxe(zombie);
        if (!fr.vanillainstincts.compat.Minecraft116Compat.stackIs(zombie.getHeldItemMainhand(), expected.getItem())) {
            zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, expected);
        }
    }

    private static int frontLineAllies(EntityZombie flanker, EntityPlayerMP player,
                                       WorldServer level) {
        Vec3d look = horizontal(player.getLookVec());
        if (look == null) return 0;
        double radius = AdaptiveProgressionRules.ZOMBIE_FRONT_LINE_RADIUS;
        return (int) level.getEntitiesWithinAABB(EntityZombie.class,
                player.getEntityBoundingBox().grow(radius), candidate ->
                        candidate != flanker && candidate.isEntityAlive()
                                && candidate.getAttackTarget() == player
                                && isInFront(player, candidate, look)).size();
    }

    private static boolean isInFront(EntityPlayerMP player, EntityZombie zombie,
                                     Vec3d look) {
        Vec3d direction = horizontal(zombie.getPositionVector().subtract(
                player.getPositionVector()));
        return direction != null && fr.vanillainstincts.compat.Minecraft112Compat.dot(look, direction)
                >= AdaptiveProgressionRules.ZOMBIE_FRONT_DOT_MIN;
    }

    private static boolean alreadyBehindTarget(EntityZombie zombie,
                                               EntityPlayerMP player) {
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(zombie, player) > 36.0D) return false;
        Vec3d look = horizontal(player.getLookVec());
        Vec3d direction = horizontal(zombie.getPositionVector().subtract(
                player.getPositionVector()));
        return look != null && direction != null && fr.vanillainstincts.compat.Minecraft112Compat.dot(look, direction) < -0.35D;
    }

    private static Vec3d flankDestination(EntityZombie zombie, EntityPlayerMP player) {
        Vec3d look = horizontal(player.getLookVec());
        if (look == null) look = horizontal(player.getPositionVector().subtract(
                zombie.getPositionVector()));
        if (look == null) look = new Vec3d(0.0D, 0.0D, 1.0D);
        Vec3d behind = look.scale(-AdaptiveProgressionRules
                .ZOMBIE_FLANKER_REAR_DISTANCE);
        Vec3d side = new Vec3d(-look.z, 0.0D, look.x)
                .scale((zombie.getEntityId() & 1) == 0 ? 1.0D : -1.0D)
                .scale(AdaptiveProgressionRules.ZOMBIE_FLANKER_SIDE_DISTANCE);
        return player.getPositionVector().add(behind).add(side);
    }

    private static EntityPlayerMP nearestPlayerWithAdvancement(
            EntityLiving mob, WorldServer level, ResourceLocation advancementId) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return fr.vanillainstincts.compat.Minecraft112Compat.players(level).stream()
                .filter(AdaptiveShieldResponseController::isCombatTarget)
                .filter(player -> hasAdvancement(player, advancementId))
                .filter(player -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, value))))
                .orElse(null);
    }

    private static boolean hasAdvancement(EntityPlayerMP player,
                                          ResourceLocation id) {
        return fr.vanillainstincts.compat.Minecraft112Compat.hasAdvancement(player, id);
    }

    private static EntityPlayerMP progressionTarget(EntityLiving mob,
                                                  WorldServer level) {
        NBTTagCompound data = mob.getEntityData();
        if (!data.hasUniqueId(TARGET)) return null;
        Entity entity = level.getEntityFromUuid(data.getUniqueId(TARGET));
        return entity instanceof EntityPlayerMP ? ((EntityPlayerMP) (entity)) : null;
    }

    private static boolean validTarget(EntityLiving mob, EntityPlayerMP player) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        return isCombatTarget(player) && player.world == mob.world
                && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(mob, player) <= range * range;
    }

    private static boolean isCombatTarget(EntityPlayerMP player) {
        return player != null && player.isEntityAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocalZombieVariant(WorldServer level,
                                                EntityZombie zombie,
                                                String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesWithinAABB(EntityZombie.class,
                zombie.getEntityBoundingBox().grow(radius), candidate ->
                        candidate.getEntityData().getBoolean(flag)).size();
    }

    private static int countLocalSkeletonVariant(WorldServer level,
                                                  AbstractSkeleton skeleton,
                                                  String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesWithinAABB(AbstractSkeleton.class,
                skeleton.getEntityBoundingBox().grow(radius), candidate ->
                        candidate.getEntityData().getBoolean(flag)).size();
    }

    private static Vec3d horizontal(Vec3d value) {
        if (value == null) return null;
        Vec3d horizontal = fr.vanillainstincts.compat.Minecraft112Compat.multiply(value, 1.0D, 0.0D, 1.0D);
        return fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(horizontal) < 1.0E-8D
                ? null : horizontal.normalize();
    }
}
