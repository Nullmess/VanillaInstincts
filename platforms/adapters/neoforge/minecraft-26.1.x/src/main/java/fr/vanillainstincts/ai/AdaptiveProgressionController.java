package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.rules.AdaptiveProgressionRules;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Rare hostile skills unlocked only after the nearby player has demonstrated
 * the matching vanilla skill through an advancement.
 */
public final class AdaptiveProgressionController {
    private static final Identifier FISHY_BUSINESS =
            Identifier.fromNamespaceAndPath(
                    "minecraft", "husbandry/fishy_business");
    private static final Identifier REMOTE_GETAWAY =
            Identifier.fromNamespaceAndPath(
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
    private static final String PEARL_READY =
            "vanillainstincts_zombie_pearl_ready";

    private AdaptiveProgressionController() {
    }

    public static void onZombieJoin(Zombie zombie, ServerLevel level,
                                    boolean loadedFromDisk) {
        if (zombie == null || level == null || zombie instanceof ZombieVillager) {
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
        if (loadedFromDisk || zombie.getSpawnType() != EntitySpawnReason.NATURAL
                || zombie.isBaby()) {
            return;
        }

        double pearlChance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_PEARL_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        ServerPlayer pearlOwner = nearestPlayerWithAdvancement(
                zombie, level, REMOTE_GETAWAY);
        if (pearlOwner != null
                && countLocal(level, zombie, PEARL_HUNTER)
                < AdaptiveProgressionRules.ZOMBIE_PEARL_LOCAL_LIMIT
                && zombie.getRandom().nextDouble() < pearlChance) {
            promotePearlHunter(zombie, pearlOwner.getUUID());
            return;
        }

        double anglerChance = AdaptiveProgressionRules.spawnChance(
                RuntimeConfig.chance(FeatureFlag.ZOMBIE_TACTICS,
                        AdaptiveProgressionRules.ZOMBIE_ANGLER_SPAWN_CHANCE),
                FeatureGate.difficulty(level));
        ServerPlayer anglerOwner = nearestPlayerWithAdvancement(
                zombie, level, FISHY_BUSINESS);
        if (anglerOwner != null
                && countLocal(level, zombie, ANGLER)
                < AdaptiveProgressionRules.ZOMBIE_ANGLER_LOCAL_LIMIT
                && zombie.getRandom().nextDouble() < anglerChance) {
            promoteAngler(zombie, anglerOwner.getUUID());
        }
    }

    public static void tickZombie(Zombie zombie, ServerLevel level,
                                  long gameTime) {
        if (zombie == null || level == null || !zombie.isAlive()) return;
        if (canUseFishingRodCombat(zombie)) {
            tickAngler(zombie, level, gameTime);
        } else if (isPearlHunter(zombie)) {
            tickPearlHunter(zombie, level, gameTime);
        }
    }

    public static void tickHookItem(ItemEntity hook, ServerLevel level,
                                    long gameTime) {
        if (hook == null || level == null || !isAnglerHook(hook)) return;
        CompoundTag data = hook.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(data, ANGLER_HOOK_OWNER)) {
            hook.discard();
            return;
        }
        Entity owner = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(data, ANGLER_HOOK_OWNER));
        if (!(owner instanceof Zombie zombie) || !zombie.isAlive()
                || !canUseFishingRodCombat(zombie)) {
            hook.discard();
            return;
        }
    }

    public static void handleProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof Snowball)
                || !(projectile.level() instanceof ServerLevel level)
                || !fr.vanillainstincts.persistence.NbtCompat.getBoolean(projectile.getPersistentData(), PEARL_PROJECTILE)
                || !(projectile.getOwner() instanceof Zombie zombie)
                || !isPearlHunter(zombie)) {
            return;
        }

        HitResult hit = event.getRayTraceResult();
        Optional<Vec3> safe = SafePositionFinder.resolveGroundDestination(
                zombie, hit.getLocation());
        if (safe.isPresent()) {
            Vec3 before = zombie.position();
            Vec3 destination = safe.get();
            zombie.teleportTo(destination.x, destination.y, destination.z);
            zombie.resetFallDistance();
            emitPearlParticles(level, before);
            emitPearlParticles(level, destination);
            zombie.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }
        projectile.discard();
    }

    public static boolean isAngler(Zombie zombie) {
        return zombie != null && fr.vanillainstincts.persistence.NbtCompat.getBoolean(zombie.getPersistentData(), ANGLER);
    }

    public static boolean isPearlHunter(Zombie zombie) {
        return zombie != null
                && fr.vanillainstincts.persistence.NbtCompat.getBoolean(zombie.getPersistentData(), PEARL_HUNTER);
    }

    public static void promoteAngler(Zombie zombie, UUID targetId) {
        if (zombie == null || targetId == null) return;
        CompoundTag data = zombie.getPersistentData();
        data.putBoolean(ANGLER, true);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(data, TARGET, targetId);
        data.putLong(ANGLER_READY, 0L);
        ensureAnglerEquipment(zombie);
    }

    public static void promotePearlHunter(Zombie zombie, UUID targetId) {
        if (zombie == null || targetId == null) return;
        CompoundTag data = zombie.getPersistentData();
        data.putBoolean(PEARL_HUNTER, true);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(data, TARGET, targetId);
        data.putLong(PEARL_READY, 0L);
        ensurePearlEquipment(zombie);
    }

    public static Identifier fishyBusinessAdvancementId() {
        return FISHY_BUSINESS;
    }

    public static Identifier remoteGetawayAdvancementId() {
        return REMOTE_GETAWAY;
    }

    public static boolean shouldPromote(double chance, int localCount,
                                        int localLimit, double roll) {
        return chance > 0.0D && localCount >= 0 && localCount < localLimit
                && roll >= 0.0D && roll < chance;
    }

    private static void tickAngler(Zombie zombie, ServerLevel level,
                                   long gameTime) {
        if (isAngler(zombie)) ensureAnglerEquipment(zombie);
        ServerPlayer target = anglerTarget(zombie, level);
        FishingHook hook = hookEntity(zombie, level);
        if (hook != null) {
            tickActiveHook(zombie, target, hook, level, gameTime);
            return;
        }
        clearHook(zombie);
        if (!validTarget(zombie, target)) return;
        zombie.setTarget(target);
        if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(zombie.getPersistentData(), ANGLER_READY)) {
            return;
        }
        double distanceSqr = zombie.distanceToSqr(target);
        double min = isAngler(zombie)
                ? AdaptiveProgressionRules.ZOMBIE_ANGLER_MIN_RANGE
                : 0.0D;
        double max = AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE;
        if (distanceSqr < min * min || distanceSqr > max * max
                || !zombie.hasLineOfSight(target)) {
            return;
        }
        zombie.setTarget(target);
        castHook(zombie, target, level, gameTime);
    }

    private static void castHook(Zombie zombie, ServerPlayer target,
                                 ServerLevel level, long gameTime) {
        Vec3 origin = zombie.getEyePosition().add(
                zombie.getLookAngle().scale(0.35D));
        Vec3 aim = target.getBoundingBox().getCenter().add(
                target.getDeltaMovement().scale(4.0D));
        Vec3 direction = aim.subtract(origin);
        if (direction.lengthSqr() < 1.0E-6D) return;

        FishingHook hook = new FishingHook(EntityType.FISHING_BOBBER, level);
        hook.setOwner(zombie);
        hook.setPos(origin.x, origin.y, origin.z);
        hook.setNoGravity(false);
        hook.setDeltaMovement(direction.normalize().scale(
                AdaptiveProgressionRules.ZOMBIE_ANGLER_HOOK_SPEED));
        hook.getPersistentData().putBoolean(ANGLER_HOOK, true);
        if (!level.addFreshEntity(hook)) return;

        fr.vanillainstincts.persistence.NbtCompat.putUuid(zombie.getPersistentData(), ANGLER_HOOK_ID, hook.getUUID());
        zombie.getPersistentData().putLong(ANGLER_HOOK_EXPIRES,
                gameTime
                        + AdaptiveProgressionRules
                        .ZOMBIE_ANGLER_HOOK_LIFETIME_TICKS);
        zombie.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, zombie.blockPosition(),
                SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.HOSTILE,
                0.8F, 1.25F);
    }

    private static void tickActiveHook(Zombie zombie, ServerPlayer target,
                                       FishingHook hook, ServerLevel level,
                                       long gameTime) {
        long expires = fr.vanillainstincts.persistence.NbtCompat.getLong(zombie.getPersistentData(), ANGLER_HOOK_EXPIRES);
        if (!validTarget(zombie, target) || gameTime >= expires
                || hook.distanceToSqr(zombie)
                > AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE
                * AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE * 1.5D) {
            retractHook(zombie, hook, gameTime);
            return;
        }

        double hitRadius = AdaptiveProgressionRules.ZOMBIE_ANGLER_HIT_RADIUS;
        if (hook.distanceToSqr(target) > hitRadius * hitRadius) return;

        Vec3 pull = zombie.getBoundingBox().getCenter()
                .subtract(target.getBoundingBox().getCenter());
        if (pull.lengthSqr() > 1.0E-6D) {
            pull = pull.normalize().scale(
                    AdaptiveProgressionRules.ZOMBIE_ANGLER_PULL_SPEED);
            target.push(pull.x,
                    pull.y + AdaptiveProgressionRules.ZOMBIE_ANGLER_PULL_LIFT,
                    pull.z);
        }
        zombie.setTarget(target);
        retractHook(zombie, hook, gameTime);
    }

    private static void retractHook(Zombie zombie, FishingHook hook,
                                    long gameTime) {
        hook.discard();
        clearHook(zombie);
        zombie.swing(InteractionHand.MAIN_HAND);
        zombie.playSound(SoundEvents.ITEM_PICKUP, 0.8F, 0.75F);
        zombie.getPersistentData().putLong(ANGLER_READY,
                gameTime
                        + AdaptiveProgressionRules
                        .ZOMBIE_ANGLER_COOLDOWN_TICKS);
    }

    private static void renderHookLine(Zombie zombie, ServerLevel level,
                                       Entity hook, long gameTime) {
        if (Math.floorMod(gameTime + zombie.getId(), 2L) != 0L) return;
        Vec3 start = zombie.position().add(0.0D, 1.35D, 0.0D);
        Vec3 delta = hook.position().subtract(start);
        int points = Math.max(2, Math.min(14, (int) delta.length()));
        for (int index = 1; index < points; index++) {
            Vec3 point = start.add(delta.scale(index / (double) points));
            level.sendParticles(ParticleTypes.CRIT,
                    point.x, point.y, point.z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void tickPearlHunter(Zombie zombie, ServerLevel level,
                                        long gameTime) {
        ensurePearlEquipment(zombie);
        ServerPlayer target = progressionTarget(zombie, level);
        if (!validTarget(zombie, target)) return;
        zombie.setTarget(target);
        if (gameTime < fr.vanillainstincts.persistence.NbtCompat.getLong(zombie.getPersistentData(), PEARL_READY)) {
            return;
        }
        double distanceSqr = zombie.distanceToSqr(target);
        double min = AdaptiveProgressionRules.ZOMBIE_PEARL_MIN_RANGE;
        double max = AdaptiveProgressionRules.ZOMBIE_PEARL_MAX_RANGE;
        if (distanceSqr < min * min || distanceSqr > max * max
                || !zombie.hasLineOfSight(target)) {
            return;
        }

        Vec3 origin = zombie.getEyePosition().add(
                zombie.getLookAngle().scale(0.35D));
        Vec3 aim = target.getBoundingBox().getCenter().add(
                target.getDeltaMovement().scale(5.0D));
        Vec3 direction = aim.subtract(origin);
        if (direction.lengthSqr() < 1.0E-6D) return;

        Snowball pearl = new Snowball(level, zombie,
                new ItemStack(Items.ENDER_PEARL));
        pearl.getPersistentData().putBoolean(PEARL_PROJECTILE, true);
        pearl.setPos(origin.x, origin.y, origin.z);
        double horizontal = Math.sqrt(direction.x * direction.x
                + direction.z * direction.z);
        pearl.shoot(direction.x, direction.y + horizontal * 0.08D,
                direction.z,
                AdaptiveProgressionRules.ZOMBIE_PEARL_SPEED,
                AdaptiveProgressionRules.ZOMBIE_PEARL_INACCURACY);
        if (!level.addFreshEntity(pearl)) return;

        zombie.setTarget(target);
        zombie.swing(InteractionHand.MAIN_HAND);
        int cooldown = AdaptiveProgressionRules
                .ZOMBIE_PEARL_COOLDOWN_MIN_TICKS
                + zombie.getRandom().nextInt(
                AdaptiveProgressionRules
                        .ZOMBIE_PEARL_COOLDOWN_VARIATION_TICKS + 1);
        zombie.getPersistentData().putLong(PEARL_READY,
                gameTime + cooldown);
    }

    private static ServerPlayer nearestPlayerWithAdvancement(
            Zombie zombie, ServerLevel level, Identifier advancementId) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        double maxDistanceSqr = range * range;
        return level.players().stream()
                .filter(AdaptiveProgressionController::isCombatTarget)
                .filter(player -> hasAdvancement(player, advancementId))
                .filter(player -> zombie.distanceToSqr(player)
                        <= maxDistanceSqr)
                .min(Comparator.comparingDouble(zombie::distanceToSqr))
                .orElse(null);
    }

    private static boolean hasAdvancement(ServerPlayer player,
                                          Identifier id) {
        MinecraftServer server = player == null ? null : player.level().getServer();
        if (server == null) return false;
        AdvancementHolder advancement = server.getAdvancements().get(id);
        return advancement != null && player.getAdvancements()
                .getOrStartProgress(advancement).isDone();
    }

    private static boolean holdsFishingRod(Zombie zombie) {
        return zombie != null && zombie.getMainHandItem().is(Items.FISHING_ROD);
    }

    private static boolean canUseFishingRodCombat(Zombie zombie) {
        return isAngler(zombie) || holdsFishingRod(zombie);
    }

    private static ServerPlayer anglerTarget(Zombie zombie,
                                                  ServerLevel level) {
        // A manually equipped fishing rod must be sufficient on its own.
        // Possession deliberately clears the mob target while the player is
        // driving it, so after release getTarget() can stay null for a while.
        // Do not require the rare-spawn ANGLER/TARGET NBT in that case: acquire
        // a visible Survival/Adventure player directly inside casting range.
        if (zombie.getTarget() instanceof ServerPlayer current
                && validAnglerTarget(zombie, current)) {
            return current;
        }
        ServerPlayer progression = progressionTarget(zombie, level);
        if (validAnglerTarget(zombie, progression)) {
            return progression;
        }
        return nearestVisibleAnglerTarget(zombie, level);
    }

    private static ServerPlayer nearestVisibleAnglerTarget(
            Zombie zombie, ServerLevel level) {
        double range = AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE;
        double rangeSqr = range * range;
        return level.players().stream()
                .filter(AdaptiveProgressionController::isCombatTarget)
                .filter(player -> player.level() == zombie.level())
                .filter(player -> zombie.distanceToSqr(player) <= rangeSqr)
                .filter(zombie::hasLineOfSight)
                .min(Comparator.comparingDouble(zombie::distanceToSqr))
                .orElse(null);
    }

    private static boolean validAnglerTarget(Zombie zombie,
                                              ServerPlayer player) {
        double range = AdaptiveProgressionRules.ZOMBIE_ANGLER_MAX_RANGE;
        return validTarget(zombie, player)
                && zombie.distanceToSqr(player) <= range * range
                && zombie.hasLineOfSight(player);
    }

    private static ServerPlayer progressionTarget(Zombie zombie,
                                                  ServerLevel level) {
        CompoundTag data = zombie.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(data, TARGET)) return null;
        Entity entity = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(data, TARGET));
        return entity instanceof ServerPlayer player ? player : null;
    }

    private static boolean validTarget(Zombie zombie, ServerPlayer player) {
        double range = AdaptiveProgressionRules.OWNER_SEARCH_RANGE;
        return isCombatTarget(player) && player.level() == zombie.level()
                && zombie.distanceToSqr(player) <= range * range;
    }

    private static boolean isCombatTarget(ServerPlayer player) {
        return player != null && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    private static int countLocal(ServerLevel level, Zombie zombie,
                                  String flag) {
        double radius = AdaptiveProgressionRules.LOCAL_VARIANT_RADIUS;
        return level.getEntitiesOfClass(Zombie.class,
                zombie.getBoundingBox().inflate(radius),
                candidate -> fr.vanillainstincts.persistence.NbtCompat.getBoolean(candidate.getPersistentData(), flag))
                .size();
    }

    private static FishingHook hookEntity(Zombie zombie, ServerLevel level) {
        CompoundTag data = zombie.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.hasUuid(data, ANGLER_HOOK_ID)) return null;
        Entity entity = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(data, ANGLER_HOOK_ID));
        return entity instanceof FishingHook hook
                && hook.getOwner() == zombie
                ? hook : null;
    }

    private static boolean isAnglerHook(ItemEntity item) {
        return item != null
                && fr.vanillainstincts.persistence.NbtCompat.getBoolean(item.getPersistentData(), ANGLER_HOOK);
    }

    private static void clearHook(Zombie zombie) {
        zombie.getPersistentData().remove(ANGLER_HOOK_ID);
        zombie.getPersistentData().remove(ANGLER_HOOK_EXPIRES);
    }

    private static void ensureAnglerEquipment(Zombie zombie) {
        if (!holdsFishingRod(zombie)) {
            zombie.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.FISHING_ROD));
        }
    }

    private static void ensurePearlEquipment(Zombie zombie) {
        if (!zombie.getMainHandItem().is(Items.ENDER_PEARL)) {
            zombie.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(Items.ENDER_PEARL));
        }
        if (!zombie.getOffhandItem().isEmpty()) {
            zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
    }

    private static void emitPearlParticles(ServerLevel level, Vec3 point) {
        level.sendParticles(ParticleTypes.PORTAL,
                point.x, point.y + 0.5D, point.z,
                24, 0.45D, 0.65D, 0.45D, 0.08D);
    }
}
