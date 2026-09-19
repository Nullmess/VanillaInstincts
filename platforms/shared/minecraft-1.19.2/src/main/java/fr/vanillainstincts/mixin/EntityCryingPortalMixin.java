package fr.vanillainstincts.mixin;

import fr.vanillainstincts.config.FeatureGate;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.19.2 compatibility hook for custom crying-obsidian portal travel.
 * Minecraft 1.19.2 resolves vanilla portal travel through
 * Entity#findDimensionEntryPoint(ServerLevel), which returns PortalInfo.
 */
@Mixin(Entity.class)
public abstract class EntityCryingPortalMixin {
    @Inject(method = "findDimensionEntryPoint", at = @At("HEAD"),
            cancellable = true)
    private void vanillainstincts$cryingPortalDestination(
            ServerLevel destination,
            CallbackInfoReturnable<PortalInfo> callback) {
        Entity self = (Entity) (Object) this;
        if (!(self.level instanceof ServerLevel source)
                || !FeatureGate.enabled(FeatureFlag.CRYING_OBSIDIAN_PORTALS,
                source)) {
            return;
        }

        BlockPos portalPos = findNearbyCryingPortal(source,
                self.blockPosition());
        if (portalPos == null) return;

        PortalInfo portalInfo = CryingObsidianPortalController
                .createRoofPortalInfo(source, self, portalPos);
        if (portalInfo != null) {
            callback.setReturnValue(portalInfo);
        }
    }

    private static BlockPos findNearbyCryingPortal(ServerLevel level,
                                                    BlockPos center) {
        for (int y = -3; y <= 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.is(Blocks.NETHER_PORTAL)
                            || !state.hasProperty(NetherPortalBlock.AXIS)) {
                        continue;
                    }
                    if (CryingObsidianPortalController.isCryingPortal(level,
                            pos, state.getValue(NetherPortalBlock.AXIS))) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }
}
