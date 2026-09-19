package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.rules.SpiderRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
/**
 * Projectile de toile réel. La toile n'apparaît qu'au point d'impact du
 * projectile, jamais directement à la position prédite de la cible.
 */
public final class SpiderWebController {
    private static final String PROJECTILE_MARKER =
            VanillaInstincts.MOD_ID + "_web_projectile";

    private SpiderWebController() {
    }

    public static boolean canLaunchProjectile(Spider spider,
                                              LivingEntity target,
                                              ServerLevel level) {
        if (spider == null || target == null || level == null
                || !FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !spider.isAlive() || !target.isAlive()) {
            return false;
        }
        double distance = spider.distanceToSqr(target);
        return distance >= SpiderRules.SPIDER_WEB_MIN_DISTANCE_SQR
                && distance <= SpiderRules.SPIDER_WEB_MAX_DISTANCE_SQR
                && spider.hasLineOfSight(target)
                && level.getGameRules().getBoolean(
                GameRules.RULE_MOBGRIEFING)
                && ForgeEventFactory.getMobGriefingEvent(level, spider)
                && TemporaryWebRegistry.activeCount(level)
                < SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL;
    }

    public static boolean launchProjectile(ServerLevel level, Spider spider,
                                           LivingEntity target) {
        if (!canLaunchProjectile(spider, target, level)) {
            return false;
        }
        Snowball projectile = new Snowball(level, spider);
        projectile.setItem(new ItemStack(Items.STRING));
        projectile.getPersistentData().putBoolean(PROJECTILE_MARKER, true);

        Vec3 origin = spider.getEyePosition().add(
                spider.getLookAngle().scale(0.35D));
        projectile.setPos(origin.x, origin.y, origin.z);
        Vec3 aim = predictedAimPoint(spider, target);
        Vec3 direction = aim.subtract(origin);
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
        Projectile projectile = event.getProjectile();
        if (!(projectile.level instanceof ServerLevel level)
                || !FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !isWebProjectile(projectile)
                || !(projectile.getOwner() instanceof Spider spider)) {
            return;
        }
        long gameTime = level.getGameTime();
        for (BlockPos candidate : impactCandidates(event.getRayTraceResult())) {
            if (placeTemporaryWeb(level, spider, candidate, gameTime)) {
                emitImpactBurst(level, Vec3.atCenterOf(candidate));
                return;
            }
        }
        emitImpactBurst(level, event.getRayTraceResult().getLocation());
    }

    public static boolean isWebProjectile(Entity entity) {
        return entity instanceof Projectile
                && entity.getPersistentData().getBoolean(PROJECTILE_MARKER);
    }

    public static Vec3 predictedAimPoint(Spider spider,
                                        LivingEntity target) {
        double distance = Math.sqrt(spider.distanceToSqr(target));
        double leadTicks = Math.min(
                SpiderRules.SPIDER_WEB_MAX_LEAD_TICKS,
                distance / Math.max(0.1D,
                        SpiderRules.SPIDER_WEB_PROJECTILE_SPEED));
        Vec3 lead = target.getDeltaMovement().multiply(
                leadTicks, Math.min(1.5D, leadTicks * 0.35D), leadTicks);
        return target.getBoundingBox().getCenter().add(lead);
    }

    public static List<BlockPos> impactCandidates(HitResult hit) {
        List<BlockPos> candidates = new ArrayList<>();
        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            BlockPos feet = entity.blockPosition();
            candidates.add(feet);
            candidates.add(feet.above());
            candidates.add(BlockPos.containing(hit.getLocation()));
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(feet.relative(direction));
            }
        } else if (hit instanceof BlockHitResult blockHit) {
            BlockPos adjacent = blockHit.getBlockPos()
                    .relative(blockHit.getDirection());
            candidates.add(adjacent);
            candidates.add(BlockPos.containing(hit.getLocation()));
            candidates.add(adjacent.above());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(adjacent.relative(direction));
            }
        } else {
            candidates.add(BlockPos.containing(hit.getLocation()));
        }
        return candidates.stream().distinct().toList();
    }

    /**
     * Conservé pour les tests et pour la validation du registre temporaire.
     * L'IA en jeu utilise uniquement {@link #launchProjectile}.
     */
    public static boolean placeTemporaryWeb(ServerLevel level, Spider spider,
                                            BlockPos pos, long gameTime) {
        if (!FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !canPlaceTemporaryWeb(level, spider, pos)
                || TemporaryWebRegistry.activeCount(level)
                >= SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL
                || !WorldPermissionService.setBlock(level, spider, pos,
                Blocks.COBWEB.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_ALL,
                WorldActionType.PLACE_BLOCK)) {
            return false;
        }
        if (!TemporaryWebRegistry.register(level, pos,
                gameTime + SpiderRules.SPIDER_WEB_DURATION_TICKS)) {
            WorldPermissionService.setBlock(level, spider, pos,
                    Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        level.gameEvent(spider, GameEvent.BLOCK_PLACE, pos);
        return true;
    }

    public static boolean canPlaceTemporaryWeb(ServerLevel level,
                                               Spider spider,
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

    private static void emitLaunchBurst(ServerLevel level, Vec3 origin) {
        level.sendParticles(ParticleTypes.POOF,
                origin.x, origin.y, origin.z,
                5, 0.08D, 0.08D, 0.08D, 0.01D);
    }

    private static void emitImpactBurst(ServerLevel level, Vec3 point) {
        level.sendParticles(ParticleTypes.CLOUD,
                point.x, point.y, point.z,
                9, 0.22D, 0.22D, 0.22D, 0.015D);
    }
}
