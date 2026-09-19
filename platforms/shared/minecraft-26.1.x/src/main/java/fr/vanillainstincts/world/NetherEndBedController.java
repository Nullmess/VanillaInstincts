package fr.vanillainstincts.world;

import com.mojang.datafixers.util.Either;
import fr.vanillainstincts.VanillaInstincts;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Autorise un vrai sommeil vanilla dans le Nether et l'End sans laisser
 * BedBlock déclencher son explosion dimensionnelle.
 */
public final class NetherEndBedController {
    private NetherEndBedController() {
    }

    public static boolean isSupportedDimension(Level level) {
        return level != null && isSupportedDimension(level.dimension());
    }

    public static boolean isSupportedDimension(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) || Level.END.equals(dimension);
    }

    /**
     * Retire uniquement les refus propres à la dimension et à l'heure fixe.
     * Les refus de distance, d'obstruction, d'occupation et de sécurité restent
     * gérés par le chemin de sommeil vanilla.
     */
    public static boolean shouldClearSleepProblem(
            ResourceKey<Level> dimension,
            @Nullable Player.BedSleepingProblem problem) {
        if (!isSupportedDimension(dimension) || problem == null) {
            return false;
        }
        return problem.equals(BedRule.EXPLODES.asProblem())
                || problem.equals(BedRule.CAN_SLEEP_WHEN_DARK.asProblem());
    }

    /**
     * Pendant que le joueur dort, seul le refus lié au temps est ignoré.
     * Un lit retiré, détruit ou perdu continue donc à réveiller le joueur.
     */
    public static boolean shouldContinueSleeping(
            ResourceKey<Level> dimension,
            @Nullable Player.BedSleepingProblem problem) {
        return isSupportedDimension(dimension)
                && problem != null
                && problem.equals(BedRule.CAN_SLEEP_WHEN_DARK.asProblem());
    }

    public static BlockPos resolveHeadPosition(
            BlockPos clickedPos, Direction facing, BedPart part) {
        return part == BedPart.HEAD
                ? clickedPos
                : clickedPos.relative(facing);
    }

    /**
     * Remplace seulement l'utilisation explosive de BedBlock. L'appel à
     * startSleepInBed garde l'état de sommeil, l'animation et la synchronisation
     * serveur vanilla.
     */
    public static InteractionResult useBed(
            BlockState clickedState, Level level, BlockPos clickedPos,
            Player player) {
        if (!isSupportedDimension(level)
                || !(clickedState.getBlock() instanceof BedBlock)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        Direction facing = clickedState.getValue(BedBlock.FACING);
        BedPart part = clickedState.getValue(BedBlock.PART);
        BlockPos headPos = resolveHeadPosition(clickedPos, facing, part);
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return InteractionResult.PASS;
        }

        if (headState.getValue(BedBlock.OCCUPIED)) {
            List<Villager> sleepers = level.getEntitiesOfClass(
                    Villager.class, new AABB(headPos), Villager::isSleeping);
            if (sleepers.isEmpty()) {
                serverPlayer.sendOverlayMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "block.minecraft.bed.occupied"));
                return InteractionResult.SUCCESS_SERVER;
            }
            sleepers.getFirst().stopSleeping();
        }

        Either<Player.BedSleepingProblem, net.minecraft.util.Unit> result =
                serverPlayer.startSleepInBed(headPos);
        result.ifLeft(problem -> {
            if (problem.message() != null) {
                serverPlayer.sendOverlayMessage(problem.message());
            } else {
                VanillaInstincts.LOGGER.debug(
                        "Sommeil refusé sans message à {} dans {}",
                        headPos, level.dimension().identifier());
            }
        });
        return InteractionResult.SUCCESS_SERVER;
    }
}
