package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.AxisAlignedBB;
/** Alerte locale lorsqu'un joueur ouvre un conteneur dans une maison. */
public final class VillageChestIntrusionController {
    private VillageChestIntrusionController() {
    }

    public static int onContainerOpened(WorldServer level, BlockPos container,
                                        EntityPlayerMP player, long gameTime) {
        if (level == null || container == null || player == null
                || player.capabilities.isCreativeMode || player.isSpectator()) {
            return 0;
        }
        AxisAlignedBB area = fr.vanillainstincts.compat.Minecraft112Compat.expandBox(new AxisAlignedBB(container.getX(), container.getY(),
                container.getZ(), container.getX() + 1.0D,
                container.getY() + 1.0D, container.getZ() + 1.0D), 
                VillageSocialRules.VILLAGE_CHEST_WITNESS_RADIUS);
        int alerted = 0;
        for (EntityVillager witness : fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class, area,
                EntityVillager::isEntityAlive)) {
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
            if (witness.isChild()) {
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

    public static boolean isHouseWitness(EntityVillager villager,
                                         VillagerRuntimeState state,
                                         WorldServer level,
                                         BlockPos container,
                                         long gameTime) {
        BlockPos home = state.home(gameTime);
        boolean nearOwnHome = home != null
                && home.distanceSq(container) <= 144.0D;
        boolean indoors = !level.canSeeSky(entityBlockPos(villager))
                && entityBlockPos(villager).distanceSq(container) <= 64.0D;
        return isHouseContext(nearOwnHome, indoors);
    }

    public static boolean isHouseContext(boolean nearOwnHome,
                                         boolean indoors) {
        return nearOwnHome || indoors;
    }
}
