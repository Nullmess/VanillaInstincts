package fr.vanillainstincts.ai;

import fr.vanillainstincts.compat.Minecraft112Compat;

import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/**
 * Rare hostile skills unlocked only after the nearby player has demonstrated
 * the matching vanilla skill through an advancement.
 */
public final class AdaptiveProgressionController {
    private static final ResourceLocation FISHY_BUSINESS =
            new ResourceLocation(
                    "minecraft", "husbandry/fishy_business");
    private static final ResourceLocation REMOTE_GETAWAY =
            new ResourceLocation(
                    "minecraft", "end/enter_end_gateway");

    private static final String TARGET =
            "vanillainstincts_progression_target";
    private static final String ANGLER =
            "vanillainstincts_zombie_angler";
    private static final String ANGLER_HOOK =
            "vanillainstincts_zombie_angler_hook";
    private static final String ANGLER_HOOK_OWNER =
            "vanillainstincts_zombie_angler_hook_owner";
    private static final String ANGLER_HOOK_ID =
            "vanillainstincts_zombie_angler_hook_id";
    private static final String ANGLER_HOOK_EXPIRES =
            "vanillainstincts_zombie_angler_hook_expires";
    private static final String ANGLER_READY =
            "vanillainstincts_zombie_angler_ready";

    private static final String PEARL_HUNTER =
            "vanillainstincts_zombie_pearl_hunter";
    private static final String PEARL_PROJECTILE =
            "vanillainstincts_zombie_pearl_projectile";
    private static final String PEARL_PROJECTILE_OWNER =
            "vanillainstincts_zombie_pearl_projectile_owner";
    private static final String PEARL_READY =
            "vanillainstincts_zombie_pearl_ready";

    private AdaptiveProgressionController() {
    }

    public static void onZombieJoin(EntityZombie zombie, WorldServer level,
                                    boolean loadedFromDisk) {
        if (zombie == null || level == null || fr.vanillainstincts.compat.Minecraft110Compat.isZombieVillager(zombie)) {
            return;
        }
        if (isAngler(zombie)) {
            ensureAnglerEquipment(zombie);
            return;
        }
        if (isPearlHunter(zombie)) {
            ensurePearlEquipment(zombie);
            return;
        }
        if (loadedFromDisk
                || zombie.isChild()) {
            return;
        }

        double pearlChance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_PEARL_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        EntityPlayerMP pearlOwner = nearestPlayerWithAdvancement(
                zombie, level, REMOTE_GETAWAY);
        if (pearlOwner != null
                && countLocal(level, zombie, PEARL_HUNTER)
                < AdaptiveProgressionRules.ZOMBIE_PEARL_LOCAL_LIMIT
                && Minecraft112Compat.random(zombie).nextDouble() < pearlChance) {
            promotePearlHunter(zombie, pearlOwner.getUniqueID());
            return;
        }

        double anglerChance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_ANGLER_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        EntityPlayerMP anglerOwner = nearestPlayerWithAdvancement(
                zombie, level, FISHY_BUSINESS);
        if (anglerOwner != null
                && countLocal(level, zombie, ANGLER)
                < AdaptiveProgressionRules.ZOMBIE_ANGLER_LOCAL_LIMIT
                && Minecraft112Compat.random(zombie).nextDouble() < anglerChance) {
            promoteAngler(zombie, anglerOwner.getUniqueID());
        }
    }

    public static void tickZombie(EntityZombie zombie, WorldServer level,
                                  long gameTime) {
        if (zombie == null || level == null || !zombie.isEntityAlive()) return;
        if (canUseFishingRodCombat(zombie)) {
            tickAngler(zombie, level, gameTime);
        } else if (isPearlHunter(zombie)) {
            tickPearlHunter(zombie, level, gameTime);
        }
    }

    public static void tickHookItem(EntityItem hook, WorldServer level,
                                    long gameTime) {
        if (hook == null || level == null || !isAnglerHook(hook)) return;
        NBTTagCompound data = hook.getEntityData();
        if (!data.hasUniqueId(ANGLER_HOOK_OWNER)) {
            hook.setDead();
            return;
        }
        Entity owner = level.getEntityFromUuid(data.getUniqueId(ANGLER_HOOK_OWNER));
        if (!(owner instanceof EntityZombie) || !((EntityZombie) (owner)).isEntityAlive()
                || !canUseFishingRodCombat(((EntityZombie) (owner)))) {
            hook.setDead();
            return;
        } EntityZombie zombie = (EntityZombie) (owner);
        renderHookLine(zombie, level, hook, gameTime);
    }

    public static boolean isAngler(EntityZombie zombie) {
        return zombie != null && zombie.getEntityData().getBoolean(ANGLER);
    }

    public static boolean isPearlHunter(EntityZombie zombie) {
        return zombie != null
                && zombie.getEntityData().getBoolean(PEARL_HUNTER);
    }

    public static void promoteAngler(EntityZombie zombie, UUID targetId) {
        if (zombie == null || targetId == null) return;
        NBTTagCompound data = zombie.getEntityData();
        data.setBoolean(ANGLER, true);
        data.setUniqueId(TARGET, targetId);
        data.setLong(ANGLER_READY, 0L);
        ensureAnglerEquipment(zombie);
    }

    public static void promotePearlHunter(EntityZombie zombie, UUID targetId) {
        if (zombie == null || targetId == null) return;
        NBTTagCompound data = zombie.getEntityData();
        data.setBoolean(PEARL_HUNTER, true);
        data.setUniqueId(TARGET, targetId);
        data.setLong(PEARL_READY, 0L);
        ensurePearlEquipment(zombie);
    }

    public static ResourceLocation fishyBusinessAdvancementId() {
        return FISHY_BUSINESS;
    }

    public static ResourceLocation remoteGetawayAdvancementId() {
        return REMOTE_GETAWAY;
    }

    public static boolean shouldPromote(double chance, int localCount,
                                        int localLimit, double roll) {
        return chance > 0.0D && localCount >= 0 && localCount < localLimit
                && roll >= 0.0D && roll < chance;
    }

    private static void tickAngler(EntityZombie zombie, WorldServer level,
                                   long gameTime) {
        if (isAngler(zombie)) ensureAnglerEquipment(zombie);
        EntityPlayerMP target = anglerTarget(zombie, level);
        EntityItem hook = hookEntity(zombie, level);
        if (hook != null) {
            tickActiveHook(zombie, target, hook, level, gameTime);
            return;
        }
        clearHook(zombie);
        if (!validTarget(zombie, target)) return;
        zombie.setAttackTarget(target);
        if (gameTime < zombie.getEntityData().getLong(ANGLER_READY)) {
            return;
        }
        double distanceSqr = Minecraft112Compat.distanceSq(zombie, target);
        double min = AdaptiveProgressionRules.ZOMBIE_ANGLER_MIN_RANGE;
        double max = AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE;
        if (distanceSqr < min * min || distanceSqr > max * max
                || !Minecraft112Compat.canSee(zombie, target)) {
            return;
        }
        zombie.setAttackTarget(target);
        castHook(zombie, target, level, gameTime);
    }

    private static void castHook(EntityZombie zombie, EntityPlayerMP target,
                                 WorldServer level, long gameTime) {
        Vec3d origin = zombie.getPositionEyes(1.0F).add(
                zombie.getLookVec().scale(0.35D));
        Vec3d aim = fr.vanillainstincts.compat.Minecraft110Compat.boxCenter(target.getEntityBoundingBox()).add(
                Minecraft112Compat.motion(target).scale(4.0D));
        Vec3d direction = aim.subtract(origin);
        if (Minecraft112Compat.lengthSq(direction) < 1.0E-6D) return;

        EntityItem hook = new EntityItem(level, origin.xCoord, origin.yCoord, origin.zCoord,
                new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.TRIPWIRE_HOOK)));
        fr.vanillainstincts.compat.Minecraft110Compat.setNoGravity(hook, true);
        Minecraft112Compat.setPickupDelay(hook, 32_767);
        Minecraft112Compat.setMotion(hook, direction.normalize().scale(
                AdaptiveProgressionRules.ZOMBIE_ANGLER_HOOK_SPEED));
        hook.getEntityData().setBoolean(ANGLER_HOOK, true);
        hook.getEntityData().setUniqueId(ANGLER_HOOK_OWNER,
                zombie.getUniqueID());
        if (!level.spawnEntityInWorld(hook)) return;

        zombie.getEntityData().setUniqueId(ANGLER_HOOK_ID, hook.getUniqueID());
        zombie.getEntityData().setLong(ANGLER_HOOK_EXPIRES,
                gameTime
                        + AdaptiveProgressionRules
                        .ZOMBIE_ANGLER_HOOK_LIFETIME_TICKS);
        zombie.swingArm(EnumHand.MAIN_HAND);
        level.playSound(null, entityBlockPos(zombie),
                SoundEvents.ENTITY_BOBBER_SPLASH, SoundCategory.HOSTILE,
                0.8F, 1.25F);
    }

    private static void tickActiveHook(EntityZombie zombie, EntityPlayerMP target,
                                       EntityItem hook, WorldServer level,
                                       long gameTime) {
        renderHookLine(zombie, level, hook, gameTime);
        long expires = zombie.getEntityData().getLong(
                ANGLER_HOOK_EXPIRES);
        if (!validTarget(zombie, target) || gameTime >= expires
                || Minecraft112Compat.distanceSq(hook, zombie)
                > AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE
                * AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE * 1.5D) {
            retractHook(zombie, hook, gameTime);
            return;
        }

        double hitRadius = AdaptiveProgressionRules.ZOMBIE_ANGLER_HIT_RADIUS;
        if (Minecraft112Compat.distanceSq(hook, target) > hitRadius * hitRadius) return;

        Vec3d pull = fr.vanillainstincts.compat.Minecraft110Compat.boxCenter(zombie.getEntityBoundingBox())
                .subtract(fr.vanillainstincts.compat.Minecraft110Compat.boxCenter(target.getEntityBoundingBox()));
        if (Minecraft112Compat.lengthSq(pull) > 1.0E-6D) {
            pull = pull.normalize().scale(
                    AdaptiveProgressionRules.ZOMBIE_ANGLER_PULL_SPEED);
            target.addVelocity(pull.xCoord,
                    pull.yCoord + AdaptiveProgressionRules.ZOMBIE_ANGLER_PULL_LIFT,
                    pull.zCoord);
        }
        zombie.setAttackTarget(target);
        retractHook(zombie, hook, gameTime);
    }

    private static void retractHook(EntityZombie zombie, EntityItem hook,
                                    long gameTime) {
        hook.setDead();
        clearHook(zombie);
        zombie.swingArm(EnumHand.MAIN_HAND);
        zombie.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 0.8F, 0.75F);
        zombie.getEntityData().setLong(ANGLER_READY,
                gameTime
                        + AdaptiveProgressionRules
                        .ZOMBIE_ANGLER_COOLDOWN_TICKS);
    }

    private static void renderHookLine(EntityZombie zombie, WorldServer level,
                                       EntityItem hook, long gameTime) {
        if (Math.floorMod(gameTime + zombie.getEntityId(), 2L) != 0L) return;
        Vec3d start = Minecraft112Compat.add(zombie.getPositionVector(), 0.0D, 1.35D, 0.0D);
        Vec3d delta = hook.getPositionVector().subtract(start);
        int points = Math.max(2, Math.min(14, (int) Minecraft112Compat.length(delta)));
        for (int index = 1; index < points; index++) {
            Vec3d point = start.add(delta.scale(index / (double) points));
            level.spawnParticle(EnumParticleTypes.CRIT,
                    point.xCoord, point.yCoord, point.zCoord,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickPearlHunter(EntityZombie zombie, WorldServer level,
                                        long gameTime) {
        ensurePearlEquipment(zombie);
        EntityPlayerMP target = progressionTarget(zombie, level);
        if (!validTarget(zombie, target)) return;
        zombie.setAttackTarget(target);
        if (gameTime < zombie.getEntityData().getLong(PEARL_READY)) {
            return;
        }
        double distanceSqr = Minecraft112Compat.distanceSq(zombie, target);
        double min = AdaptiveProgressionRules.ZOMBIE_PEARL_MIN_RANGE;
        double max = AdaptiveProgressionRules.ZOMBIE_PEARL_MAX_RANGE;
        if (distanceSqr < min * min || distanceSqr > max * max
                || !Minecraft112Compat.canSee(zombie, target)) {
            return;
        }

        Vec3d origin = zombie.getPositionEyes(1.0F).add(
                zombie.getLookVec().scale(0.35D));
        Vec3d aim = fr.vanillainstincts.compat.Minecraft110Compat.boxCenter(target.getEntityBoundingBox()).add(
                Minecraft112Compat.motion(target).scale(5.0D));
        Vec3d direction = aim.subtract(origin);
        if (Minecraft112Compat.lengthSq(direction) < 1.0E-6D) return;

        EntitySnowball pearl = new EntitySnowball(level, zombie);
        pearl.getEntityData().setBoolean(PEARL_PROJECTILE, true);
        pearl.getEntityData().setUniqueId(PEARL_PROJECTILE_OWNER, zombie.getUniqueID());
        pearl.setPosition(origin.xCoord, origin.yCoord, origin.zCoord);
        double horizontal = Math.sqrt(direction.xCoord * direction.xCoord
                + direction.zCoord * direction.zCoord);
        pearl.setThrowableHeading(direction.xCoord, direction.yCoord + horizontal * 0.08D,
                direction.zCoord,
                AdaptiveProgressionRules.ZOMBIE_PEARL_SPEED,
                AdaptiveProgressionRules.ZOMBIE_PEARL_INACCURACY);
        if (!level.spawnEntityInWorld(pearl)) return;

        zombie.setAttackTarget(target);
        zombie.swingArm(EnumHand.MAIN_HAND);
        int cooldown = AdaptiveProgressionRules
                .ZOMBIE_PEARL_COOLDOWN_MIN_TICKS
                + Minecraft112Compat.random(zombie).nextInt(
                AdaptiveProgressionRules
                        .ZOMBIE_PEARL_COOLDOWN_VARIATION_TICKS + 1);
        zombie.getEntityData().setLong(PEARL_READY,
                gameTime + cooldown);
    }

    private static EntityPlayerMP nearestPlayerWithAdvancement(
            EntityZombie zombie, WorldServer level, ResourceLocation advancementId) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return level.playerEntities.stream()
                .filter(player -> player instanceof EntityPlayerMP)
                .map(player -> (EntityPlayerMP) player)
                .filter(AdaptiveProgressionController::isCombatTarget)
                .filter(player -> hasAdvancement(player, advancementId))
                .filter(player -> Minecraft112Compat.distanceSq(zombie, player)
                        <= maxDistanceSqr)
                .min(Comparator.comparingDouble(player -> Minecraft112Compat.distanceSq(zombie, player)))
                .orElse(null);
    }

    private static boolean hasAdvancement(EntityPlayerMP player,
                                          ResourceLocation id) {
        return fr.vanillainstincts.compat.Minecraft112Compat.hasAdvancement(player, id);
    }

    private static boolean holdsFishingRod(EntityZombie zombie) {
        ItemStack held = zombie == null ? null : zombie.getHeldItemMainhand();
        return !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(held)
                && held.getItem().equals(Items.FISHING_ROD);
    }

    private static boolean canUseFishingRodCombat(EntityZombie zombie) {
        return isAngler(zombie) || holdsFishingRod(zombie);
    }

    private static EntityPlayerMP anglerTarget(EntityZombie zombie,
                                                  WorldServer level) {
        if (zombie.getAttackTarget() instanceof EntityPlayerMP) {
            EntityPlayerMP current = (EntityPlayerMP) zombie.getAttackTarget();
            if (validTarget(zombie, current)) return current;
        }
        return progressionTarget(zombie, level);
    }

    private static EntityPlayerMP progressionTarget(EntityZombie zombie,
                                                  WorldServer level) {
        NBTTagCompound data = zombie.getEntityData();
        if (!data.hasUniqueId(TARGET)) return null;
        Entity entity = level.getEntityFromUuid(data.getUniqueId(TARGET));
        return entity instanceof EntityPlayerMP ? ((EntityPlayerMP) (entity)) : null;
    }

    private static boolean validTarget(EntityZombie zombie, EntityPlayerMP player) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        return isCombatTarget(player) && player.worldObj == zombie.worldObj
                && Minecraft112Compat.distanceSq(zombie, player) <= range * range;
    }

    private static boolean isCombatTarget(EntityPlayerMP player) {
        return player != null && player.isEntityAlive()
                && !player.capabilities.isCreativeMode && !player.isSpectator();
    }

    private static int countLocal(WorldServer level, EntityZombie zombie,
                                  String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesWithinAABB(EntityZombie.class,
                zombie.getEntityBoundingBox().expandXyz(radius),
                candidate -> candidate.getEntityData().getBoolean(flag))
                .size();
    }

    private static EntityItem hookEntity(EntityZombie zombie, WorldServer level) {
        NBTTagCompound data = zombie.getEntityData();
        if (!data.hasUniqueId(ANGLER_HOOK_ID)) return null;
        Entity entity = level.getEntityFromUuid(data.getUniqueId(ANGLER_HOOK_ID));
        return entity instanceof EntityItem && isAnglerHook(((EntityItem) (entity)))
                ? ((EntityItem) (entity)) : null;
    }

    private static boolean isAnglerHook(EntityItem item) {
        return item != null
                && item.getEntityData().getBoolean(ANGLER_HOOK);
    }

    private static void clearHook(EntityZombie zombie) {
        zombie.getEntityData().removeTag(ANGLER_HOOK_ID);
        zombie.getEntityData().removeTag(ANGLER_HOOK_EXPIRES);
    }

    private static void ensureAnglerEquipment(EntityZombie zombie) {
        if (!holdsFishingRod(zombie)) {
            zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,
                    new ItemStack(Items.FISHING_ROD));
        }
    }

    private static void ensurePearlEquipment(EntityZombie zombie) {
        if (!zombie.getHeldItemMainhand().getItem().equals(Items.ENDER_PEARL)) {
            zombie.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,
                    new ItemStack(Items.ENDER_PEARL));
        }
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(zombie.getHeldItemOffhand())) {
            zombie.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, null);
        }
    }

    private static void emitPearlParticles(WorldServer level, Vec3d point) {
        level.spawnParticle(EnumParticleTypes.PORTAL,
                point.xCoord, point.yCoord + 0.5D, point.zCoord,
                24, 0.45D, 0.65D, 0.45D, 0.08D);
    }
}
