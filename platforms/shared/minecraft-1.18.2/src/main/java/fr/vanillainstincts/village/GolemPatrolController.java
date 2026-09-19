package fr.vanillainstincts.village;

import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.SafePositionFinder;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
/** Répartit les golems inactifs sur des secteurs de patrouille temporaires. */
public final class GolemPatrolController {
    private static final Map<ServerLevel, Map<Integer, Reservation>> RESERVATIONS
            = new WeakHashMap<>();

    private GolemPatrolController() {
    }

    public static void contribute(IronGolem golem, MobDecisionPlan plan,
                                  ServerLevel level, long gameTime) {
        if (golem.getTarget() != null || golem.isAggressive()
                || Math.floorMod(gameTime + golem.getId() * 13L, 80L)
                != 0L) return;
        cleanup(level, gameTime);
        BlockPos center = villageCenter(golem, level).orElse(null);
        if (center == null) return;
        int slot = reserveSlot(golem, level, gameTime);
        double angle = slot * Math.PI * 2.0D / 8.0D;
        double radius = 8.0D + (slot % 2) * 5.0D;
        Vec3 requested = Vec3.atBottomCenterOf(center).add(
                Math.cos(angle) * radius, 0.0D,
                Math.sin(angle) * radius);
        SafePositionFinder.resolveGroundDestination(golem, requested)
                .ifPresent(destination -> plan.offerNavigation(
                        VanillaInstinctsState.GOLEM_PATROL,
                        ActionOwner.GOLEM_PATROL,
                        VillageConstructionRules.PRIORITY_GOLEM_PATROL,
                        destination, VillageConstructionRules.GOLEM_PATROL_SPEED,
                        60, null));
    }

    public static int preferredSlot(int entityId) {
        return Math.floorMod(entityId, 8);
    }

    private static int reserveSlot(IronGolem golem, ServerLevel level,
                                   long gameTime) {
        Map<Integer, Reservation> values = RESERVATIONS.computeIfAbsent(level,
                ignored -> new HashMap<>());
        for (int offset = 0; offset < 8; offset++) {
            int slot = Math.floorMod(preferredSlot(golem.getId()) + offset, 8);
            Reservation existing = values.get(slot);
            if (existing == null || existing.until <= gameTime
                    || existing.golemId == golem.getId()) {
                values.put(slot, new Reservation(golem.getId(),
                        gameTime + VillageConstructionRules.GOLEM_PATROL_RESERVATION_TICKS));
                return slot;
            }
        }
        return preferredSlot(golem.getId());
    }

    private static Optional<BlockPos> villageCenter(IronGolem golem,
                                                     ServerLevel level) {
        BlockPos origin = golem.blockPosition();
        int radius = (int) VillageConstructionRules.GOLEM_PATROL_RADIUS;
        Optional<BlockPos> bell = BlockPos.betweenClosedStream(
                        origin.offset(-radius, -3, -radius),
                        origin.offset(radius, 3, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.BELL))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
        if (bell.isPresent()) return bell;
        return level.getEntitiesOfClass(Villager.class,
                        golem.getBoundingBox().inflate(radius),
                        Villager::isAlive)
                .stream()
                .min(Comparator.comparingDouble(golem::distanceToSqr))
                .map(Villager::blockPosition);
    }

    private static void cleanup(ServerLevel level, long gameTime) {
        Map<Integer, Reservation> values = RESERVATIONS.get(level);
        if (values != null) {
            values.entrySet().removeIf(entry -> entry.getValue().until
                    <= gameTime);
        }
    }

    private record Reservation(int golemId, long until) {
    }
}
