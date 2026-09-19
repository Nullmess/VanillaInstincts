package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.util.math.vector.Vector3d;
/** Apprentissage visuel d'un mécanisme ouvrant réellement une porte en fer. */
public final class IronDoorLearningController {
    private static final Map<ServerWorld, List<PendingObservation>> PENDING =
            new WeakHashMap<>();

    private IronDoorLearningController() {
    }

    /**
     * L'événement de clic arrive avant la réaction complète du bloc. La scène
     * est donc vérifiée un tick plus tard : aucune connaissance n'est accordée
     * si le mécanisme n'a finalement ni été alimenté ni ouvert la porte.
     */
    public static void observePlayerUse(ServerWorld level,
                                        ServerPlayerEntity player,
                                        BlockPos mechanism,
                                        long gameTime) {
        if (level == null || player == null || mechanism == null
                || !isLearnableMechanism(level.getBlockState(mechanism))) {
            return;
        }
        BlockPos door = findNearestIronDoor(level, mechanism,
                VillageSocialRules.IRON_DOOR_MECHANISM_SEARCH_RADIUS);
        if (door == null) return;
        boolean poweredBefore = isPowered(level.getBlockState(mechanism));
        boolean openBefore = isOpen(level.getBlockState(door));
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingObservation(player.getUUID(),
                        mechanism.immutable(), door.immutable(),
                        poweredBefore, openBefore, gameTime + 1L));
    }

    public static void tickPending(ServerWorld level, long gameTime) {
        List<PendingObservation> observations = PENDING.get(level);
        if (observations == null || observations.isEmpty()) return;
        observations.removeIf(observation -> {
            if (gameTime < observation.verifyAt()) return false;
            ServerPlayerEntity player = level.getServer().getPlayerList()
                    .getPlayer(observation.playerId());
            if (player != null && player.getLevel() == level) {
                boolean poweredAfter = isPowered(level.getBlockState(
                        observation.mechanism()));
                boolean openAfter = isOpen(level.getBlockState(
                        observation.door()));
                // On n'apprend que l'ouverture réellement observée : une porte
                // déjà ouverte à côté d'un levier sans rapport ne suffit pas.
                if (!observation.openBefore() && openAfter
                        && poweredAfter != observation.poweredBefore()) {
                    teachWitnesses(level, player, observation.mechanism(),
                            observation.door(), gameTime);
                }
            }
            return true;
        });
        if (observations.isEmpty()) PENDING.remove(level);
    }

    public static void clearLevel(ServerWorld level) {
        PENDING.remove(level);
    }

    private static void teachWitnesses(ServerWorld level,
                                       ServerPlayerEntity player,
                                       BlockPos mechanism,
                                       BlockPos door,
                                       long gameTime) {
        double radius = VillageSocialRules.IRON_DOOR_LEARNING_RADIUS;
        for (VillagerEntity villager : level.getEntitiesOfClass(VillagerEntity.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && !candidate.isBaby())) {
            if (villager.distanceToSqr(player) > radius * radius
                    || villager.distanceToSqr(Vector3d.atCenterOf(mechanism))
                    > radius * radius
                    || !villager.canSee(player)) {
                continue;
            }
            IronDoorMemory.remember(villager, level, door, mechanism,
                    gameTime);
        }
    }

    public static boolean isLearnableMechanism(BlockState state) {
        return state != null && (state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof AbstractButtonBlock);
    }

    public static BlockPos findNearestIronDoor(ServerWorld level,
                                                BlockPos mechanism,
                                                int radius) {
        if (level == null || mechanism == null) return null;
        return BlockPos.betweenClosedStream(
                        mechanism.offset(-radius, -radius, -radius),
                        mechanism.offset(radius, radius, radius))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.IRON_DOOR))
                .map(pos -> lowerHalf(pos, level.getBlockState(pos)))
                .distinct()
                .min(Comparator.comparingDouble(pos ->
                        Vector3d.atCenterOf(pos).distanceToSqr(
                                Vector3d.atCenterOf(mechanism))))
                .map(BlockPos::immutable)
                .orElse(null);
    }

    private static boolean isPowered(BlockState state) {
        return state != null && state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
    }

    private static boolean isOpen(BlockState state) {
        return state != null && state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN);
    }

    private static BlockPos lowerHalf(BlockPos position, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == DoubleBlockHalf.UPPER) {
            return position.below();
        }
        return position;
    }

    private static class PendingObservation {
        private final UUID playerId;
        private final BlockPos mechanism;
        private final BlockPos door;
        private final boolean poweredBefore;
        private final boolean openBefore;
        private final long verifyAt;

        public PendingObservation(UUID playerId, BlockPos mechanism, BlockPos door, boolean poweredBefore, boolean openBefore, long verifyAt) {
            this.playerId = playerId;
            this.mechanism = mechanism;
            this.door = door;
            this.poweredBefore = poweredBefore;
            this.openBefore = openBefore;
            this.verifyAt = verifyAt;
        }

        public UUID playerId() { return this.playerId; }

        public BlockPos mechanism() { return this.mechanism; }

        public BlockPos door() { return this.door; }

        public boolean poweredBefore() { return this.poweredBefore; }

        public boolean openBefore() { return this.openBefore; }

        public long verifyAt() { return this.verifyAt; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingObservation)) return false;
            PendingObservation that = (PendingObservation) other;
            return java.util.Objects.equals(this.playerId, that.playerId) && java.util.Objects.equals(this.mechanism, that.mechanism) && java.util.Objects.equals(this.door, that.door) && this.poweredBefore == that.poweredBefore && this.openBefore == that.openBefore && this.verifyAt == that.verifyAt;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.playerId, this.mechanism, this.door, this.poweredBefore, this.openBefore, this.verifyAt); }

        @Override
        public String toString() {
            return "PendingObservation[" + "playerId=" + this.playerId + ", " + "mechanism=" + this.mechanism + ", " + "door=" + this.door + ", " + "poweredBefore=" + this.poweredBefore + ", " + "openBefore=" + this.openBefore + ", " + "verifyAt=" + this.verifyAt + "]";
        }

    }
}
