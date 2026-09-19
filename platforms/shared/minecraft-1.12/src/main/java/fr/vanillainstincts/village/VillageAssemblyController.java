package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.Vec3d;
/** Rassemblement calme en cercle lorsque le village manque de protection. */
public final class VillageAssemblyController {
    private static final String READY_AT = "vanillainstincts_assembly_ready_at";
    private static final String REQUEST_UNTIL = "vanillainstincts_golem_request_until";

    private VillageAssemblyController() {
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (villager.isChild() || state.danger(gameTime) != null
                || gameTime < villager.getEntityData().getLong(READY_AT)
                || Math.floorMod(gameTime + villager.getEntityId() * 19L,
                ProfessionRules.VILLAGE_PROFESSION_SCAN_INTERVAL_TICKS) != 0L) {
            return false;
        }
        List<EntityVillager> villagers = level.getEntitiesWithinAABB(EntityVillager.class,
                villager.getEntityBoundingBox().grow(
                        VillageConstructionRules.VILLAGE_ASSEMBLY_RADIUS),
                other -> other.isEntityAlive()
                        && VillagerStateStore.stateFor(other)
                        .danger(gameTime) == null);
        if (villagers.size() < VillageConstructionRules.VILLAGE_ASSEMBLY_MIN_VILLAGERS) {
            return false;
        }
        boolean protectedVillage = !level.getEntitiesWithinAABB(EntityIronGolem.class,
                villager.getEntityBoundingBox().grow(
                        VillageConstructionRules.VILLAGE_ASSEMBLY_RADIUS),
                EntityIronGolem::isEntityAlive).isEmpty();
        if (protectedVillage) return false;

        BlockPos center = nearestBell(level, entityBlockPos(villager), 16)
                .orElseGet(() -> state.home(gameTime) == null
                        ? entityBlockPos(villager)
                        : state.home(gameTime));
        int slot = Math.floorMod(villager.getEntityId(), Math.max(4,
                villagers.size()));
        double angle = slot * Math.PI * 2.0D / Math.max(4, villagers.size());
        Vec3d ring =fr.vanillainstincts.compat.Minecraft112Compat.add(Minecraft115VectorCompat.atBottomCenterOf(center), 
                Math.cos(angle) * 4.0D, 0.0D,
                Math.sin(angle) * 4.0D);
        Optional<Vec3d> safe = SafePositionFinder.resolveGroundDestination(
                villager, ring);
        if (!safe.isPresent()) return false;

        villager.getEntityData().setLong(READY_AT,
                gameTime + VillageConstructionRules.VILLAGE_ASSEMBLY_COOLDOWN_TICKS);
        villager.getEntityData().setLong(REQUEST_UNTIL,
                gameTime + VillageConstructionRules.VILLAGE_ASSEMBLY_TICKS);
        plan.offerNavigation(VanillaInstinctsState.VILLAGE_ASSEMBLE,
                ActionOwner.VILLAGER_SOCIAL,
                VillageConstructionRules.PRIORITY_VILLAGE_ASSEMBLY,
                safe.get(), 0.78D,
                VillageConstructionRules.VILLAGE_ASSEMBLY_TICKS,
                () -> level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                        center.getX() + 0.5D, center.getY() + 1.2D,
                        center.getZ() + 0.5D, 2,
                        0.4D, 0.4D, 0.4D, 0.02D));
        return true;
    }

    public static boolean hasActiveRequest(EntityVillager villager,
                                           long gameTime) {
        return villager != null && isRequestActive(villager
                .getEntityData().getLong(REQUEST_UNTIL), gameTime);
    }

    public static boolean isRequestActive(long requestUntil, long gameTime) {
        return requestUntil > gameTime;
    }

    private static Optional<BlockPos> nearestBell(WorldServer level,
                                                   BlockPos origin,
                                                   int radius) {
        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, -radius, -3, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(origin, radius, 3, radius))
                .filter(level::isBlockLoaded)
                .filter(pos -> fr.vanillainstincts.compat.Minecraft112Compat.isVillageCenterMarker(level.getBlockState(pos)))
                .min(Comparator.comparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(origin, value))))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable);
    }
}
