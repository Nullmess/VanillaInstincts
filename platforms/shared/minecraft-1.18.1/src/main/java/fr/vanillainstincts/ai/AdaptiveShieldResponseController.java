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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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

    public static void onZombieJoin(Zombie zombie, ServerLevel level,
                                    boolean loadedFromDisk) {
        if (zombie == null || level == null || zombie instanceof ZombieVillager) {
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
        ServerPlayer owner = nearestPlayerWithAdvancement(
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

    public static void onSkeletonJoin(AbstractSkeleton skeleton,
                                      ServerLevel level,
                                      boolean loadedFromDisk) {
        if (skeleton == null || level == null
                || skeleton instanceof WitherSkeleton) {
            return;
        }
        if (isShieldSkeleton(skeleton)) {
            equipSkeletonRanged(skeleton);
            return;
        }
        if (SniperSkeletonController.isSniper(skeleton)
                || loadedFromDisk
               
                || !skeleton.getMainHandItem().is(Items.BOW)) {
            return;
        }
        ServerPlayer owner = nearestPlayerWithAdvancement(
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

    public static void tickSkeleton(AbstractSkeleton skeleton,
                                    ServerLevel level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || skeleton == null
                || level == null || !skeleton.isAlive()) {
            return;
        }
        ServerPlayer target = progressionTarget(skeleton, level);
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

    public static void contributeZombie(Zombie zombie, MobDecisionPlan plan,
                                        ServerLevel level, long gameTime) {
        if (!isZombieFlanker(zombie) || plan == null || level == null) return;
        ServerPlayer target = progressionTarget(zombie, level);
        if (!validTarget(zombie, target)) return;
        zombie.setTarget(target);
        double range = AdaptiveProgressionRules.ZOMBIE_FLANKER_TRIGGER_RANGE;
        if (zombie.distanceToSqr(target) > range * range
                || frontLineAllies(zombie, target, level)
                < AdaptiveProgressionRules.ZOMBIE_FLANKER_MIN_FRONT_ALLIES
                || alreadyBehindTarget(zombie, target)) {
            return;
        }
        Vec3 flank = flankDestination(zombie, target);
        plan.offerNavigation(VanillaInstinctsState.FLANK,
                ActionOwner.ZOMBIE_TACTICS,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_PRIORITY,
                flank, AdaptiveProgressionRules.ZOMBIE_FLANKER_SPEED,
                AdaptiveProgressionRules.ZOMBIE_FLANKER_HOLD_TICKS, null);
    }

    public static void contributeSkeleton(AbstractSkeleton skeleton,
                                          MobDecisionPlan plan,
                                          ServerLevel level, long gameTime) {
        if (!isShieldSkeleton(skeleton) || plan == null || level == null
                || !skeleton.getPersistentData().getBoolean(SKELETON_MELEE)) {
            return;
        }
        ServerPlayer target = progressionTarget(skeleton, level);
        if (!validTarget(skeleton, target)) return;
        plan.offerNavigation(VanillaInstinctsState.PURSUE,
                ActionOwner.SKELETON_TACTICS,
                AdaptiveProgressionRules.SKELETON_SHIELD_PRIORITY,
                target.position(),
                AdaptiveProgressionRules.SKELETON_SHIELD_APPROACH_SPEED,
                AdaptiveProgressionRules.SKELETON_SHIELD_HOLD_TICKS, null);
    }

    public static boolean isZombieFlanker(Zombie zombie) {
        return zombie != null
                && zombie.getPersistentData().getBoolean(ZOMBIE_FLANKER);
    }

    public static boolean isShieldSkeleton(AbstractSkeleton skeleton) {
        return skeleton != null
                && skeleton.getPersistentData().getBoolean(SKELETON_SHIELD);
    }

    public static boolean isShieldSkeletonMelee(AbstractSkeleton skeleton) {
        return isShieldSkeleton(skeleton)
                && skeleton.getPersistentData().getBoolean(SKELETON_MELEE);
    }

    public static ResourceLocation deflectArrowAdvancementId() {
        return DEFLECT_ARROW;
    }

    private static void promoteZombieFlanker(Zombie zombie, UUID targetId) {
        CompoundTag data = zombie.getPersistentData();
        data.putBoolean(ZOMBIE_FLANKER, true);
        data.putUUID(TARGET, targetId);
        ensureZombieAxe(zombie);
    }

    private static void promoteShieldSkeleton(AbstractSkeleton skeleton,
                                              UUID targetId) {
        CompoundTag data = skeleton.getPersistentData();
        data.putBoolean(SKELETON_SHIELD, true);
        data.putBoolean(SKELETON_MELEE, false);
        data.putUUID(TARGET, targetId);
        data.putLong(SKELETON_NEXT_ATTACK, 0L);
        equipSkeletonRanged(skeleton);
    }

    private static void maintainShieldMelee(AbstractSkeleton skeleton,
                                            ServerPlayer target,
                                            long gameTime) {
        skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double reach = skeleton.getBbWidth() * 2.0D;
        double reachSqr = reach * reach + target.getBbWidth();
        if (skeleton.distanceToSqr(target) <= reachSqr
                && gameTime >= skeleton.getPersistentData().getLong(
                SKELETON_NEXT_ATTACK)) {
            skeleton.stopUsingItem();
            if (skeleton.doHurtTarget(target)) {
                skeleton.swing(InteractionHand.MAIN_HAND);
            }
            skeleton.getPersistentData().putLong(SKELETON_NEXT_ATTACK,
                    gameTime + AdaptiveProgressionRules
                            .SKELETON_SHIELD_ATTACK_COOLDOWN_TICKS);
            return;
        }
        if (!skeleton.isUsingItem()) {
            skeleton.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private static void setSkeletonMeleeMode(AbstractSkeleton skeleton,
                                             boolean melee) {
        CompoundTag data = skeleton.getPersistentData();
        if (data.getBoolean(SKELETON_MELEE) == melee) return;
        data.putBoolean(SKELETON_MELEE, melee);
        skeleton.stopUsingItem();
        if (melee) {
            equipSkeletonMelee(skeleton);
        } else {
            equipSkeletonRanged(skeleton);
        }
    }

    private static void equipSkeletonMelee(AbstractSkeleton skeleton) {
        skeleton.setItemSlot(EquipmentSlot.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveAxe(skeleton));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND,
                new ItemStack(Items.SHIELD));
        skeleton.reassessWeaponGoal();
    }

    private static void equipSkeletonRanged(AbstractSkeleton skeleton) {
        skeleton.setItemSlot(EquipmentSlot.MAINHAND,
                AdaptiveEquipmentProgressionController.adaptiveBow(skeleton));
        skeleton.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        skeleton.reassessWeaponGoal();
    }

    private static void ensureZombieAxe(Zombie zombie) {
        ItemStack expected = AdaptiveEquipmentProgressionController
                .adaptiveAxe(zombie);
        if (!zombie.getMainHandItem().is(expected.getItem())) {
            zombie.setItemSlot(EquipmentSlot.MAINHAND, expected);
        }
    }

    private static int frontLineAllies(Zombie flanker, ServerPlayer player,
                                       ServerLevel level) {
        Vec3 look = horizontal(player.getLookAngle());
        if (look == null) return 0;
        double radius = AdaptiveProgressionRules.ZOMBIE_FRONT_LINE_RADIUS;
        return (int) level.getEntitiesOfClass(Zombie.class,
                player.getBoundingBox().inflate(radius), candidate ->
                        candidate != flanker && candidate.isAlive()
                                && candidate.getTarget() == player
                                && isInFront(player, candidate, look)).size();
    }

    private static boolean isInFront(ServerPlayer player, Zombie zombie,
                                     Vec3 look) {
        Vec3 direction = horizontal(zombie.position().subtract(
                player.position()));
        return direction != null && look.dot(direction)
                >= AdaptiveProgressionRules.ZOMBIE_FRONT_DOT_MIN;
    }

    private static boolean alreadyBehindTarget(Zombie zombie,
                                               ServerPlayer player) {
        if (zombie.distanceToSqr(player) > 36.0D) return false;
        Vec3 look = horizontal(player.getLookAngle());
        Vec3 direction = horizontal(zombie.position().subtract(
                player.position()));
        return look != null && direction != null && look.dot(direction) < -0.35D;
    }

    private static Vec3 flankDestination(Zombie zombie, ServerPlayer player) {
        Vec3 look = horizontal(player.getLookAngle());
        if (look == null) look = horizontal(player.position().subtract(
                zombie.position()));
        if (look == null) look = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 behind = look.scale(-AdaptiveProgressionRules
                .ZOMBIE_FLANKER_REAR_DISTANCE);
        Vec3 side = new Vec3(-look.z, 0.0D, look.x)
                .scale((zombie.getId() & 1) == 0 ? 1.0D : -1.0D)
                .scale(AdaptiveProgressionRules.ZOMBIE_FLANKER_SIDE_DISTANCE);
        return player.position().add(behind).add(side);
    }

    private static ServerPlayer nearestPlayerWithAdvancement(
            Mob mob, ServerLevel level, ResourceLocation advancementId) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return level.players().stream()
                .filter(AdaptiveShieldResponseController::isCombatTarget)
                .filter(player -> hasAdvancement(player, advancementId))
                .filter(player -> mob.distanceToSqr(player) <= maxDistanceSqr)
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
    }

    private static boolean hasAdvancement(ServerPlayer player,
                                          ResourceLocation id) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return false;
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static ServerPlayer progressionTarget(Mob mob,
                                                  ServerLevel level) {
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TARGET)) return null;
        Entity entity = level.getEntity(data.getUUID(TARGET));
        return entity instanceof ServerPlayer player ? player : null;
    }

    private static boolean validTarget(Mob mob, ServerPlayer player) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        return isCombatTarget(player) && player.level == mob.level
                && mob.distanceToSqr(player) <= range * range;
    }

    private static boolean isCombatTarget(ServerPlayer player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocalZombieVariant(ServerLevel level,
                                                Zombie zombie,
                                                String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(Zombie.class,
                zombie.getBoundingBox().inflate(radius), candidate ->
                        candidate.getPersistentData().getBoolean(flag)).size();
    }

    private static int countLocalSkeletonVariant(ServerLevel level,
                                                  AbstractSkeleton skeleton,
                                                  String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(AbstractSkeleton.class,
                skeleton.getBoundingBox().inflate(radius), candidate ->
                        candidate.getPersistentData().getBoolean(flag)).size();
    }

    private static Vec3 horizontal(Vec3 value) {
        if (value == null) return null;
        Vec3 horizontal = value.multiply(1.0D, 0.0D, 1.0D);
        return horizontal.lengthSqr() < 1.0E-8D
                ? null : horizontal.normalize();
    }
}
