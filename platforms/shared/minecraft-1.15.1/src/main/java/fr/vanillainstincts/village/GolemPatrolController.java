package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;
/** Répartit les golems inactifs sur des secteurs de patrouille temporaires. */
public final class GolemPatrolController {
    private static final Map<ServerWorld, Map<Integer, Reservation>> RESERVATIONS
            = new WeakHashMap<>();

    private GolemPatrolController() {
    }

    public static void contribute(IronGolemEntity golem, MobDecisionPlan plan,
                                  ServerWorld level, long gameTime) {
        if (golem.getTarget() != null || golem.isAggressive()
                || Math.floorMod(gameTime + golem.getId() * 13L, 80L)
                != 0L) return;
        cleanup(level, gameTime);
        BlockPos center = villageCenter(golem, level).orElse(null);
        if (center == null) return;
        int slot = reserveSlot(golem, level, gameTime);
        double angle = slot * Math.PI * 2.0D / 8.0D;
        double radius = 8.0D + (slot % 2) * 5.0D;
        Vec3d requested = Minecraft115VectorCompat.atBottomCenterOf(center).add(
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

    private static int reserveSlot(IronGolemEntity golem, ServerWorld level,
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

    private static Optional<BlockPos> villageCenter(IronGolemEntity golem,
                                                     ServerWorld level) {
        BlockPos origin = entityBlockPos(golem);
        int radius = (int) VillageConstructionRules.GOLEM_PATROL_RADIUS;
        Optional<BlockPos> bell = BlockPos.betweenClosedStream(
                        origin.offset(-radius, -3, -radius),
                        origin.offset(radius, 3, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).getBlock().equals(Blocks.BELL))
                .min(Comparator.comparingDouble(origin::distSqr))
                .map(BlockPos::immutable);
        if (bell.isPresent()) return bell;
        return level.getEntitiesOfClass(VillagerEntity.class,
                        golem.getBoundingBox().inflate(radius),
                        VillagerEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(golem::distanceToSqr))
                .map(villager -> entityBlockPos(villager));
    }

    private static void cleanup(ServerWorld level, long gameTime) {
        Map<Integer, Reservation> values = RESERVATIONS.get(level);
        if (values != null) {
            values.entrySet().removeIf(entry -> entry.getValue().until
                    <= gameTime);
        }
    }

    private static class Reservation {
        private final int golemId;
        private final long until;

        public Reservation(int golemId, long until) {
            this.golemId = golemId;
            this.until = until;
        }

        public int golemId() { return this.golemId; }

        public long until() { return this.until; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Reservation)) return false;
            Reservation that = (Reservation) other;
            return this.golemId == that.golemId && this.until == that.until;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.golemId, this.until); }

        @Override
        public String toString() {
            return "Reservation[" + "golemId=" + this.golemId + ", " + "until=" + this.until + "]";
        }

    }
}
