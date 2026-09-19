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
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.AbstractSkeletonEntity;
import net.minecraft.entity.monster.WitherSkeletonEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.entity.monster.ZombieVillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.vector.Vector3d;

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

    public static void onZombieJoin(ZombieEntity zombie, ServerWorld level,
                                    boolean loadedFromDisk) {
        if (zombie == null || level == null || zombie instanceof ZombieVillagerEntity) {
            return;
        }
        if (isZombieFlanker(zombie)) {
            ensureZombieAxe(zombie);
            return;
        }
        if (AdaptiveProgressionController.isAngler(zombie)
                || AdaptiveProgressionController.isPearlHunter(zombie)
                || loadedFromDisk || zombie.isBaby()
               ) {
            return;
        }
        ServerPlayerEntity owner = nearestPlayerWithAdvancement(
                zombie, level, DEFLECT_ARROW);
        if (owner == null) return;
        double chance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_FLANKER_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        if (countLocalZombieVariant(level, zombie, ZOMBIE_FLANKER)
                >= AdaptiveProgressionRules.ZOMBIE_FLANKER_LOCAL_LIMIT
                || zombie.getRandom().nextDouble() >= chance) {
            return;
        }
        promoteZombieFlanker(zombie, owner.getUUID());
    }

    public static void onSkeletonJoin(AbstractSkeletonEntity skeleton,
                                      ServerWorld level,
                                      boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || skeleton instanceof WitherSkeletonEntity) {
            return;
        }
        if (isShieldSkeleton(skeleton)) {
            equipSkeletonRanged(skeleton);
            return;
        }
        if (SniperSkeletonController.isSniper(skeleton)
                || loadedFromDisk
               
                || !skeleton.getMainHandItem().getItem().equals(Items.BOW)) {
            return;
        }
        ServerPlayerEntity owner = nearestPlayerWithAdvancement(
                skeleton, level, DEFLECT_ARROW);
        if (owner == null) return;
        double chance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.SKELETON_TACTICS,
                        AdaptiveProgressionRules.SKELETON_SHIELD_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        if (countLocalSkeletonVariant(level, skeleton, SKELETON_SHIELD)
                >= AdaptiveProgressionRules.SKELETON_SHIELD_LOCAL_LIMIT
                || skeleton.getRandom().nextDouble() >= chance) {
            return;
        }
        promoteShieldSkeleton(skeleton, owner.getUUID());
    }

    public static void tickSkeleton(AbstractSkeletonEntity skeleton,
                                    ServerWorld level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || skeleton == null
                || level == null || !skeleton.isAlive()) {
            return;
        }
        ServerPlayerEntity target = progressionTarget(skeleton, level);
        if (!validTarget(skeleton, target)) {
            setSkeletonMeleeMode(skeleton, false);
            return;
        }
        skeleton.setTarget(target);
        double distanceSqr = skeleton.distanceToSqr(target);
        boolean melee = skeleton.getPersistentData().getBoolean(
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

    public static void contributeZombie(ZombieEntity zombie, MobDecisionPlan plan,
                                        ServerWorld level, long gameTime) {
        if (!isZombieFlanker(zombie) || plan == null || level == null) return;
        ServerPlayerEntity target = progressionTarget(zombie, level);
        if (!validTarget(zombie, target)) return;
        zombie.setTarget(target);
        double range = AdaptiveProgressionRules.ZOMBIE_FLANKER_TRIGGER_RANGE;
        if (zombie.distanceToSqr(target) > range * range
                || frontLineAllies(zombie, target, level)
                < AdaptiveProgressionRules.ZOMBIE_FLANKER_MIN_FRONT_ALLIES
                || alreadyBehindTarget(zombie, target)) {
            return;
        }
        Vector3d flank = flankDestination(zombie, target);
        plan.offerNavigation(VanillaInstinctsState.FLANK,
                ActionOwner.ZOMBIE_TACTICS,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_PRIORITY,
                flank, AdaptiveProgressionRules.ZOMBIE_FLANKER_SPEED,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_HOLD_TICKS, null);
    }

    public static void contributeSkeleton(AbstractSkeletonEntity skeleton,
                                          MobDecisionPlan plan,
                                          ServerWorld level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || plan == null || level == null
                || !skeleton.getPersistentData().getBoolean(SKELETON_MELEE)) {
            return;
        }
        ServerPlayerEntity target = progressionTarget(skeleton, level);
        if (!validTarget(skeleton, target)) return;
        plan.offerNavigation(VanillaInstinctsState.PURSUE,
                ActionOwner.SKELETON_TACTICS,
                AdaptiveProgressionRules.SKELETON_SHIELD_PRIORITY,
                target.position(),
                AdaptiveProgressionRules.SKELETON_SHIELD_APPROACH_SPEED,
                AdaptiveProgressionRules.SKELETON_SHIELD_HOLD_TICKS, null);
    }

    public static boolean isZombieFlanker(ZombieEntity zombie) {
        return zombie != null
                && zombie.getPersistentData().getBoolean(ZOMBIE_FLANKER);
    }

    public static boolean isShieldSkeleton(AbstractSkeletonEntity skeleton) {
        return skeleton != null
                && skeleton.getPersistentData().getBoolean(SKELETON_SHIELD);
    }

    public static boolean isShieldSkeletonMelee(AbstractSkeletonEntity skeleton) {
        return isShieldSkeleton(skeleton)
                && skeleton.getPersistentData().getBoolean(SKELETON_MELEE);
    }

    public static ResourceLocation deflectArrowAdvancementId() {
        return DEFLECT_ARROW;
    }

    private static void promoteZombieFlanker(ZombieEntity zombie, UUID targetId) {
        CompoundNBT data = zombie.getPersistentData();
        data.putBoolean(ZOMBIE_FLANKER, true);
        data.putUUID(TARGET, targetId);
        ensureZombieAxe(zombie);
    }

    private static void promoteShieldSkeleton(AbstractSkeletonEntity skeleton,
                                              UUID targetId) {
        CompoundNBT data = skeleton.getPersistentData();
        data.putBoolean(SKELETON_SHIELD, true);
        data.putBoolean(SKELETON_MELEE, false);
        data.putUUID(TARGET, targetId);
        data.putLong(SKELETON_NEXT_ATTACK, 0L);
        equipSkeletonRanged(skeleton);
    }

    private static void maintainShieldMelee(AbstractSkeletonEntity skeleton,
                                            ServerPlayerEntity target,
                                            long gameTime) {
        skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double reach = skeleton.getBbWidth() * 2.0D;
        double reachSqr = reach * reach + target.getBbWidth();
        if (skeleton.distanceToSqr(target) <= reachSqr
                && gameTime >= skeleton.getPersistentData().getLong(
                SKELETON_NEXT_ATTACK)) {
            skeleton.stopUsingItem();
            if (skeleton.doHurtTarget(target)) {
                skeleton.swing(Hand.MAIN_HAND);
            }
            skeleton.getPersistentData().putLong(SKELETON_NEXT_ATTACK,
                    gameTime + AdaptiveProgressionRules
                            .SKELETON_SHIELD_ATTACK_COOLDOWN_TICKS);
            return;
        }
        if (!skeleton.isUsingItem()) {
            skeleton.startUsingItem(Hand.OFF_HAND);
        }
    }

    private static void setSkeletonMeleeMode(AbstractSkeletonEntity skeleton,
                                             boolean melee) {
        CompoundNBT data = skeleton.getPersistentData();
        if (data.getBoolean(SKELETON_MELEE) == melee) return;
        data.putBoolean(SKELETON_MELEE, melee);
        skeleton.stopUsingItem();
        if (melee) {
            equipSkeletonMelee(skeleton);
        } else {
            equipSkeletonRanged(skeleton);
        }
    }

    private static void equipSkeletonMelee(AbstractSkeletonEntity skeleton) {
        skeleton.setItemSlot(EquipmentSlotType.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveAxe(skeleton));
        skeleton.setItemSlot(EquipmentSlotType.OFFHAND,
                new ItemStack(Items.SHIELD));
        skeleton.reassessWeaponGoal();
    }

    private static void equipSkeletonRanged(AbstractSkeletonEntity skeleton) {
        skeleton.setItemSlot(EquipmentSlotType.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveBow(skeleton));
        skeleton.setItemSlot(EquipmentSlotType.OFFHAND, ItemStack.EMPTY);
        skeleton.reassessWeaponGoal();
    }

    private static void ensureZombieAxe(ZombieEntity zombie) {
        ItemStack expected = AdaptiveEquipmentProgressionController
                .adaptiveAxe(zombie);
        if (!fr.vanillainstincts.compat.Minecraft116Compat.stackIs(zombie.getMainHandItem(), expected.getItem())) {
            zombie.setItemSlot(EquipmentSlotType.MAINHAND, expected);
        }
    }

    private static int frontLineAllies(ZombieEntity flanker, ServerPlayerEntity player,
                                       ServerWorld level) {
        Vector3d look = horizontal(player.getLookAngle());
        if (look == null) return 0;
        double radius = AdaptiveProgressionRules.ZOMBIE_FRONT_LINE_RADIUS;
        return (int) level.getEntitiesOfClass(ZombieEntity.class,
                player.getBoundingBox().inflate(radius), candidate ->
                        candidate != flanker && candidate.isAlive()
                                && candidate.getTarget() == player
                                && isInFront(player, candidate, look)).size();
    }

    private static boolean isInFront(ServerPlayerEntity player, ZombieEntity zombie,
                                     Vector3d look) {
        Vector3d direction = horizontal(zombie.position().subtract(
                player.position()));
        return direction != null && look.dot(direction)
                >= AdaptiveProgressionRules.ZOMBIE_FRONT_DOT_MIN;
    }

    private static boolean alreadyBehindTarget(ZombieEntity zombie,
                                               ServerPlayerEntity player) {
        if (zombie.distanceToSqr(player) > 36.0D) return false;
        Vector3d look = horizontal(player.getLookAngle());
        Vector3d direction = horizontal(zombie.position().subtract(
                player.position()));
        return look != null && direction != null && look.dot(direction) < -0.35D;
    }

    private static Vector3d flankDestination(ZombieEntity zombie, ServerPlayerEntity player) {
        Vector3d look = horizontal(player.getLookAngle());
        if (look == null) look = horizontal(player.position().subtract(
                zombie.position()));
        if (look == null) look = new Vector3d(0.0D, 0.0D, 1.0D);
        Vector3d behind = look.scale(-AdaptiveProgressionRules
                .ZOMBIE_FLANKER_REAR_DISTANCE);
        Vector3d side = new Vector3d(-look.z, 0.0D, look.x)
                .scale((zombie.getId() & 1) == 0 ? 1.0D : -1.0D)
                .scale(AdaptiveProgressionRules.ZOMBIE_FLANKER_SIDE_DISTANCE);
        return player.position().add(behind).add(side);
    }

    private static ServerPlayerEntity nearestPlayerWithAdvancement(
            MobEntity mob, ServerWorld level, ResourceLocation advancementId) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return level.players().stream()
                .filter(AdaptiveShieldResponseController::isCombatTarget)
                .filter(player -> hasAdvancement(player, advancementId))
                .filter(player -> mob.distanceToSqr(player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static boolean hasAdvancement(ServerPlayerEntity player,
                                          ResourceLocation id) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static ServerPlayerEntity progressionTarget(MobEntity mob,
                                                  ServerWorld level) {
        CompoundNBT data = mob.getPersistentData();
        if (!data.hasUUID(TARGET)) return null;
        Entity entity = level.getEntity(data.getUUID(TARGET));
        return entity instanceof ServerPlayerEntity ? ((ServerPlayerEntity) (entity)) : null;
    }

    private static boolean validTarget(MobEntity mob, ServerPlayerEntity player) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        return isCombatTarget(player) && player.level == mob.level
                && mob.distanceToSqr(player) <= range * range;
    }

    private static boolean isCombatTarget(ServerPlayerEntity player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocalZombieVariant(ServerWorld level,
                                                ZombieEntity zombie,
                                                String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(ZombieEntity.class,
                zombie.getBoundingBox().inflate(radius), candidate ->
                        candidate.getPersistentData().getBoolean(flag)).size();
    }

    private static int countLocalSkeletonVariant(ServerWorld level,
                                                  AbstractSkeletonEntity skeleton,
                                                  String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(AbstractSkeletonEntity.class,
                skeleton.getBoundingBox().inflate(radius), candidate ->
                        candidate.getPersistentData().getBoolean(flag)).size();
    }

    private static Vector3d horizontal(Vector3d value) {
        if (value == null) return null;
        Vector3d horizontal = value.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }
}
