package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
/** Alerte locale lorsqu'un joueur ouvre un conteneur dans une maison. */
public final class VillageChestIntrusionController {
    private VillageChestIntrusionController() {
    }

    public static int onContainerOpened(ServerLevel level, BlockPos container,
                                        ServerPlayer player, long gameTime) {
        if (level == null || container == null || player == null
                || player.isCreative() || player.isSpectator()) {
            return 0;
        }
        AABB area = new AABB(container.getX(), container.getY(),
                container.getZ(), container.getX() + 1.0D,
                container.getY() + 1.0D, container.getZ() + 1.0D).inflate(
                VillageSocialRules.VILLAGE_CHEST_WITNESS_RADIUS);
        int alerted = 0;
        for (Villager witness : level.getEntitiesOfClass(Villager.class, area,
                Villager::isAlive)) {
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

    public static boolean isHouseWitness(Villager villager,
                                         VillagerRuntimeState state,
                                         ServerLevel level,
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
