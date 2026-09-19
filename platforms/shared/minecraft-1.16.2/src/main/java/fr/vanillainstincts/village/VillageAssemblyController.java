package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.vector.Vector3d;
/** Rassemblement calme en cercle lorsque le village manque de protection. */
public final class VillageAssemblyController {
    private static final String READY_AT = "vanillainstincts_assembly_ready_at";
    private static final String REQUEST_UNTIL = "vanillainstincts_golem_request_until";

    private VillageAssemblyController() {
    }

    public static boolean contribute(VillagerEntity villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerWorld level, long gameTime) {
        if (villager.isBaby() || state.danger(gameTime) != null
                || gameTime < villager.getPersistentData().getLong(READY_AT)
                || Math.floorMod(gameTime + villager.getId() * 19L,
                ProfessionRules.VILLAGE_PROFESSION_SCAN_INTERVAL_TICKS) != 0L) {
            return false;
        }
        List<VillagerEntity> villagers = level.getEntitiesOfClass(VillagerEntity.class,
                villager.getBoundingBox().inflate(
                        VillageConstructionRules.VILLAGE_ASSEMBLY_RADIUS),
                other -> other.isAlive()
                        && VillagerStateStore.stateFor(other)
                        .danger(gameTime) == null);
        if (villagers.size() < VillageConstructionRules.VILLAGE_ASSEMBLY_MIN_VILLAGERS) {
            return false;
        }
        boolean protectedVillage = !level.getEntitiesOfClass(IronGolemEntity.class,
                villager.getBoundingBox().inflate(
                        VillageConstructionRules.VILLAGE_ASSEMBLY_RADIUS),
                IronGolemEntity::isAlive).isEmpty();
        if (protectedVillage) return false;

        BlockPos center = nearestBell(level, villager.blockPosition(), 16)
                .orElseGet(() -> state.home(gameTime) == null
                        ? villager.blockPosition()
                        : state.home(gameTime));
        int slot = Math.floorMod(villager.getId(), Math.max(4,
                villagers.size()));
        double angle = slot * Math.PI * 2.0D / Math.max(4, villagers.size());
        Vector3d ring = Vector3d.atBottomCenterOf(center).add(
                Math.cos(angle) * 4.0D, 0.0D,
                Math.sin(angle) * 4.0D);
        Optional<Vector3d> safe = SafePositionFinder.resolveGroundDestination(
                villager, ring);
        if (!safe.isPresent()) return false;

        villager.getPersistentData().putLong(READY_AT,
                gameTime + VillageConstructionRules.VILLAGE_ASSEMBLY_COOLDOWN_TICKS);
        villager.getPersistentData().putLong(REQUEST_UNTIL,
                gameTime + VillageConstructionRules.VILLAGE_ASSEMBLY_TICKS);
        plan.offerNavigation(VanillaInstinctsState.VILLAGE_ASSEMBLE,
                ActionOwner.VILLAGER_SOCIAL,
                VillageConstructionRules.PRIORITY_VILLAGE_ASSEMBLY,
                safe.get(), 0.78D,
                VillageConstructionRules.VILLAGE_ASSEMBLY_TICKS,
                () -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        center.getX() + 0.5D, center.getY() + 1.2D,
                        center.getZ() + 0.5D, 2,
                        0.4D, 0.4D, 0.4D, 0.02D));
        return true;
    }

    public static boolean hasActiveRequest(VillagerEntity villager,
                                           long gameTime) {
        return villager != null && isRequestActive(villager
                .getPersistentData().getLong(REQUEST_UNTIL), gameTime);
    }

    public static boolean isRequestActive(long requestUntil, long gameTime) {
        return requestUntil > gameTime;
    }

    private static Optional<BlockPos> nearestBell(ServerWorld level,
                                                   BlockPos origin,
                                                   int radius) {
        return BlockPos.betweenClosedStream(origin.offset(-radius, -3, -radius),
                        origin.offset(radius, 3, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
    }
}
