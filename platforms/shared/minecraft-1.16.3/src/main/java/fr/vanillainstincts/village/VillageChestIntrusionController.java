package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.util.math.AxisAlignedBB;
/** Alerte locale lorsqu'un joueur ouvre un conteneur dans une maison. */
public final class VillageChestIntrusionController {
    private VillageChestIntrusionController() {
    }

    public static int onContainerOpened(ServerWorld level, BlockPos container,
                                        ServerPlayerEntity player, long gameTime) {
        if (level == null || container == null || player == null
                || player.isCreative() || player.isSpectator()) {
            return 0;
        }
        AxisAlignedBB area = new AxisAlignedBB(container.getX(), container.getY(),
                container.getZ(), container.getX() + 1.0D,
                container.getY() + 1.0D, container.getZ() + 1.0D).inflate(
                VillageSocialRules.VILLAGE_CHEST_WITNESS_RADIUS);
        int alerted = 0;
        for (VillagerEntity witness : level.getEntitiesOfClass(VillagerEntity.class, area,
                VillagerEntity::isAlive)) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(witness);
            VillagePoiScanner.refresh(witness, state, level, gameTime);
            if (!isHouseWitness(witness, state, level, container, gameTime)) {
                VillagerStateStore.save(witness, state);
                continue;
            }
            VillageThreatRegistry.ThreatSnapshot threat =
                    VillageThreatRegistry.reportVillagerAttack(witness, level,
                            player, 0.5F, gameTime);
            if (threat == null) continue;
            if (witness.isBaby()) {
                ChildVillageAlertController.begin(witness, threat, gameTime);
            } else {
                VillagerGolemReportController.beginReport(witness, state,
                        threat, gameTime);
            }
            VillagerSafetyController.rememberDanger(witness, level, container);
            VillagerStateStore.save(witness, state);
            alerted++;
        }
        return alerted;
    }

    public static boolean isHouseWitness(VillagerEntity villager,
                                         VillagerRuntimeState state,
                                         ServerWorld level,
                                         BlockPos container,
                                         long gameTime) {
        BlockPos home = state.home(gameTime);
        boolean nearOwnHome = home != null
                && home.distSqr(container) <= 144.0D;
        boolean indoors = !level.canSeeSky(villager.blockPosition())
                && villager.blockPosition().distSqr(container) <= 64.0D;
        return isHouseContext(nearOwnHome, indoors);
    }

    public static boolean isHouseContext(boolean nearOwnHome,
                                         boolean indoors) {
        return nearOwnHome || indoors;
    }
}
