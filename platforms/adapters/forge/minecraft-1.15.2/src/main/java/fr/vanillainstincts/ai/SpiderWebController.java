package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.rules.SpiderRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.GameRules;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.EntityRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
/**
 * Projectile de toile réel. La toile n'apparaît qu'au point d'impact du
 * projectile, jamais directement à la position prédite de la cible.
 */
public final class SpiderWebController {
    private static final String PROJECTILE_MARKER =
            VanillaInstincts.MOD_ID + "_web_projectile";
    private static final String PROJECTILE_OWNER =
            VanillaInstincts.MOD_ID + "_web_projectile_owner";

    private SpiderWebController() {
    }

    public static boolean canLaunchProjectile(SpiderEntity spider,
                                              LivingEntity target,
                                              ServerWorld level) {
        if (spider == null || target == null || level == null
                || !FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !spider.isAlive() || !target.isAlive()) {
            return false;
        }
        double distance = spider.distanceToSqr(target);
        return distance >= SpiderRules.SPIDER_WEB_MIN_DISTANCE_SQR
                && distance <= SpiderRules.SPIDER_WEB_MAX_DISTANCE_SQR
                && spider.canSee(target)
                && level.getGameRules().getBoolean(
                GameRules.RULE_MOBGRIEFING)
                && ForgeEventFactory.getMobGriefingEvent(level, spider)
                && TemporaryWebRegistry.activeCount(level)
                < SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL;
    }

    public static boolean launchProjectile(ServerWorld level, SpiderEntity spider,
                                           LivingEntity target) {
        if (!canLaunchProjectile(spider, target, level)) {
            return false;
        }
        SnowballEntity projectile = new SnowballEntity(level, spider);
        projectile.setItem(new ItemStack(Items.STRING));
        projectile.getPersistentData().putBoolean(PROJECTILE_MARKER, true);
        projectile.getPersistentData().putUUID(PROJECTILE_OWNER, spider.getUUID());

        Vec3d origin = spider.getEyePosition(1.0F).add(
                spider.getLookAngle().scale(0.35D));
        projectile.setPos(origin.x, origin.y, origin.z);
        Vec3d aim = predictedAimPoint(spider, target);
        Vec3d direction = aim.subtract(origin);
        double horizontal = Math.sqrt(direction.x * direction.x
                + direction.z * direction.z);
        projectile.shoot(direction.x,
                direction.y + horizontal
                        * SpiderRules.SPIDER_WEB_PROJECTILE_ARC,
                direction.z,
                SpiderRules.SPIDER_WEB_PROJECTILE_SPEED,
                SpiderRules.SPIDER_WEB_PROJECTILE_INACCURACY);
        if (!level.addFreshEntity(projectile)) {
            return false;
        }
        emitLaunchBurst(level, origin);
        return true;
    }

    public static void handleProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getEntity() instanceof SnowballEntity)) return;
        SnowballEntity projectile = (SnowballEntity) event.getEntity();
        if (!(projectile.level instanceof ServerWorld)
                || !FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, ((ServerWorld) projectile.level))
                || !isWebProjectile(projectile)
                || !projectile.getPersistentData().hasUUID(PROJECTILE_OWNER)) {
            return;
        }
        ServerWorld level = (ServerWorld) projectile.level;
        Entity owner = level.getEntity(projectile.getPersistentData().getUUID(PROJECTILE_OWNER));
        if (!(owner instanceof SpiderEntity) || !owner.isAlive()) return;
        SpiderEntity spider = (SpiderEntity) owner;
        long gameTime = level.getGameTime();
        for (BlockPos candidate : impactCandidates(event.getRayTraceResult())) {
            if (placeTemporaryWeb(level, spider, candidate, gameTime)) {
                emitImpactBurst(level, Minecraft115VectorCompat.atCenterOf(candidate));
                return;
            }
        }
        emitImpactBurst(level, event.getRayTraceResult().getLocation());
    }

    public static boolean isWebProjectile(Entity entity) {
        return entity instanceof SnowballEntity
                && entity.getPersistentData().getBoolean(PROJECTILE_MARKER);
    }

    public static Vec3d predictedAimPoint(SpiderEntity spider,
                                        LivingEntity target) {
        double distance = Math.sqrt(spider.distanceToSqr(target));
        double leadTicks = Math.min(
                SpiderRules.SPIDER_WEB_MAX_LEAD_TICKS,
                distance / Math.max(0.1D,
                        SpiderRules.SPIDER_WEB_PROJECTILE_SPEED));
        Vec3d lead = target.getDeltaMovement().multiply(
                leadTicks, Math.min(1.5D, leadTicks * 0.35D), leadTicks);
        return target.getBoundingBox().getCenter().add(lead);
    }

    public static List<BlockPos> impactCandidates(RayTraceResult hit) {
        List<BlockPos> candidates = new ArrayList<>();
        if (hit instanceof EntityRayTraceResult) { EntityRayTraceResult entityHit = (EntityRayTraceResult) (hit); 
            Entity entity = entityHit.getEntity();
            BlockPos feet = entityBlockPos(entity);
            candidates.add(feet);
            candidates.add(feet.above());
            candidates.add(new BlockPos(hit.getLocation()));
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(feet.relative(direction));
            }
        } else if (hit instanceof BlockRayTraceResult) { BlockRayTraceResult blockHit = (BlockRayTraceResult) (hit); 
            BlockPos adjacent = blockHit.getBlockPos()
                    .relative(blockHit.getDirection());
            candidates.add(adjacent);
            candidates.add(new BlockPos(hit.getLocation()));
            candidates.add(adjacent.above());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(adjacent.relative(direction));
            }
        } else {
            candidates.add(new BlockPos(hit.getLocation()));
        }
        return candidates.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    /**
     * Conservé pour les tests et pour la validation du registre temporaire.
     * L'IA en jeu utilise uniquement {@link #launchProjectile}.
     */
    public static boolean placeTemporaryWeb(ServerWorld level, SpiderEntity spider,
                                            BlockPos pos, long gameTime) {
        if (!FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !canPlaceTemporaryWeb(level, spider, pos)
                || TemporaryWebRegistry.activeCount(level)
                >= SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL
                || !WorldPermissionService.setBlock(level, spider, pos,
                Blocks.COBWEB.defaultBlockState(),
                3,
                WorldActionType.PLACE_BLOCK)) {
            return false;
        }
        if (!TemporaryWebRegistry.register(level, pos,
                gameTime + SpiderRules.SPIDER_WEB_DURATION_TICKS)) {
            WorldPermissionService.setBlock(level, spider, pos,
                    Blocks.AIR.defaultBlockState(),
                    3,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        return true;
    }

    public static boolean canPlaceTemporaryWeb(ServerWorld level,
                                               SpiderEntity spider,
                                               BlockPos pos) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !ForgeEventFactory.getMobGriefingEvent(level, spider)
                || !level.isLoaded(pos)
                || !level.isInWorldBounds(pos)
                || level.getBlockEntity(pos) != null
                || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockState current = level.getBlockState(pos);
        if (!current.isAir()) {
            return false;
        }
        return TemporaryWebRegistry.countNear(level, pos,
                SpiderRules.SPIDER_WEB_LOCAL_LIMIT_RADIUS)
                < SpiderRules.SPIDER_WEB_LOCAL_LIMIT;
    }

    private static void emitLaunchBurst(ServerWorld level, Vec3d origin) {
        level.sendParticles(ParticleTypes.POOF,
                origin.x, origin.y, origin.z,
                5, 0.08D, 0.08D, 0.08D, 0.01D);
    }

    private static void emitImpactBurst(ServerWorld level, Vec3d point) {
        level.sendParticles(ParticleTypes.CLOUD,
                point.x, point.y, point.z,
                9, 0.22D, 0.22D, 0.22D, 0.015D);
    }
}
