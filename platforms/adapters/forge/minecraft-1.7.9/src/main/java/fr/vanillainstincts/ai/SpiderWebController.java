package fr.vanillainstincts.ai;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.core.rules.SpiderRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.ArrayList;
import java.util.List;
import fr.vanillainstincts.compat.LegacyBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.init.Blocks;
import fr.vanillainstincts.compat.EnumFacing;
import fr.vanillainstincts.compat.EnumParticleTypes;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Minecraft 1.12 server-side spider-web compatibility.
 *
 * The later projectile visual layer is not available in this first 1.12
 * adapter pass, so the same range/cooldown rules resolve to a nearby temporary
 * web placement at the target instead. The temporary-web registry and world
 * permission checks remain authoritative.
 */
public final class SpiderWebController {
    private SpiderWebController() {}

    public static boolean canLaunchProjectile(EntitySpider spider,
                                              EntityLivingBase target,
                                              WorldServer level) {
        if (spider == null || target == null || level == null
                || !FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !spider.isEntityAlive() || !target.isEntityAlive()) {
            return false;
        }
        double distance = spider.getDistanceSqToEntity(target);
        return distance >= SpiderRules.SPIDER_WEB_MIN_DISTANCE_SQR
                && distance <= SpiderRules.SPIDER_WEB_MAX_DISTANCE_SQR
                && spider.canEntityBeSeen(target)
                && level.getGameRules().getGameRuleBooleanValue("mobGriefing")
                && TemporaryWebRegistry.activeCount(level)
                < SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL;
    }

    public static boolean launchProjectile(WorldServer level, EntitySpider spider,
                                           EntityLivingBase target) {
        if (!canLaunchProjectile(spider, target, level)) return false;
        BlockPos feet = new BlockPos(target.posX, target.posY, target.posZ);
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(feet);
        candidates.add(feet.up());
        for (EnumFacing facing : EnumFacing.Plane.HORIZONTAL) {
            candidates.add(feet.offset(facing));
        }
        long gameTime = level.getTotalWorldTime();
        for (BlockPos candidate : candidates) {
            if (placeTemporaryWeb(level, spider, candidate, gameTime)) {
                fr.vanillainstincts.compat.Minecraft17Compat.spawnParticle(level, EnumParticleTypes.CLOUD,
                        candidate.getX() + 0.5D, candidate.getY() + 0.5D,
                        candidate.getZ() + 0.5D, 6, 0.18D, 0.18D, 0.18D, 0.01D);
                return true;
            }
        }
        return false;
    }

    public static boolean placeTemporaryWeb(WorldServer level, EntitySpider spider,
                                            BlockPos pos, long gameTime) {
        if (!FeatureGate.enabled(FeatureFlag.SPIDER_WEBS, level)
                || !canPlaceTemporaryWeb(level, spider, pos)
                || TemporaryWebRegistry.activeCount(level)
                >= SpiderRules.MAX_TEMPORARY_WEBS_PER_LEVEL
                || !WorldPermissionService.setBlock(level, spider, pos,
                fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.web), 3, WorldActionType.PLACE_BLOCK)) {
            return false;
        }
        if (!TemporaryWebRegistry.register(level, pos,
                gameTime + SpiderRules.SPIDER_WEB_DURATION_TICKS)) {
            WorldPermissionService.setBlock(level, spider, pos,
                    fr.vanillainstincts.compat.Minecraft17Compat.defaultState(Blocks.air), 3,
                    WorldActionType.TEMPORARY_CLEANUP);
            return false;
        }
        return true;
    }

    public static boolean canPlaceTemporaryWeb(WorldServer level,
                                               EntitySpider spider,
                                               BlockPos pos) {
        if (level == null || spider == null || pos == null
                || !level.getGameRules().getGameRuleBooleanValue("mobGriefing")
                || !fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos) || !validHeight(pos)
                || fr.vanillainstincts.compat.Minecraft17Compat.getTileEntity(level, pos) != null) {
            return false;
        }
        LegacyBlockState current = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos);
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isAir(level, pos)
                || current.getBlock().getMaterial().isLiquid()) {
            return false;
        }
        return TemporaryWebRegistry.countNear(level, pos,
                SpiderRules.SPIDER_WEB_LOCAL_LIMIT_RADIUS)
                < SpiderRules.SPIDER_WEB_LOCAL_LIMIT;
    }

    private static boolean validHeight(BlockPos pos) {
        return pos.getY() >= 0 && pos.getY() < 256;
    }
}
