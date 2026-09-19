package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
/** Apprentissage visuel d'un mécanisme ouvrant réellement une porte en fer. */
public final class IronDoorLearningController {
    private static final Map<ServerLevel, List<PendingObservation>> PENDING =
            new WeakHashMap<>();

    private IronDoorLearningController() {
    }

    /**
     * L'événement de clic arrive avant la réaction complète du bloc. La scène
     * est donc vérifiée un tick plus tard : aucune connaissance n'est accordée
     * si le mécanisme n'a finalement ni été alimenté ni ouvert la porte.
     */
    public static void observePlayerUse(ServerLevel level,
                                        ServerPlayer player,
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

    public static void tickPending(ServerLevel level, long gameTime) {
        List<PendingObservation> observations = PENDING.get(level);
        if (observations == null || observations.isEmpty()) return;
        observations.removeIf(observation -> {
            if (gameTime < observation.verifyAt()) return false;
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(observation.playerId());
            if (player != null && player.serverLevel() == level) {
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

    public static void clearLevel(ServerLevel level) {
        PENDING.remove(level);
    }

    private static void teachWitnesses(ServerLevel level,
                                       ServerPlayer player,
                                       BlockPos mechanism,
                                       BlockPos door,
                                       long gameTime) {
        double radius = VillageSocialRules.IRON_DOOR_LEARNING_RADIUS;
        for (Villager villager : level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(radius),
                candidate -> candidate.isAlive() && !candidate.isBaby())) {
            if (villager.distanceToSqr(player) > radius * radius
                    || villager.distanceToSqr(Vec3.atCenterOf(mechanism))
                    > radius * radius
                    || !villager.hasLineOfSight(player)) {
                continue;
            }
            IronDoorMemory.remember(villager, level, door, mechanism,
                    gameTime);
        }
    }

    public static boolean isLearnableMechanism(BlockState state) {
        return state != null && (state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof ButtonBlock);
    }

    public static BlockPos findNearestIronDoor(ServerLevel level,
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
                        Vec3.atCenterOf(pos).distanceToSqr(
                                Vec3.atCenterOf(mechanism))))
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

    private record PendingObservation(UUID playerId, BlockPos mechanism,
                                      BlockPos door, boolean poweredBefore,
                                      boolean openBefore, long verifyAt) {
    }
}
