package fr.vanillainstincts.world;

import net.minecraft.world.dimension.DimensionType;
import com.mojang.datafixers.util.Either;
import fr.vanillainstincts.VanillaInstincts;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Direction;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BedPart;
import net.minecraft.util.math.AxisAlignedBB;
import javax.annotation.Nullable;

/**
 * Autorise un vrai sommeil vanilla dans le Nether et l'End sans laisser
 * BedBlock déclencher son explosion dimensionnelle.
 */
public final class NetherEndBedController {
    private NetherEndBedController() {
    }

    public static boolean isSupportedDimension(World level) {
        return level != null && isSupportedDimension(level.dimension.getType());
    }

    public static boolean isSupportedDimension(DimensionType dimension) {
        return DimensionType.NETHER.equals(dimension) || DimensionType.THE_END.equals(dimension);
    }

    /**
     * Retire uniquement les refus propres à la dimension et à l'heure fixe.
     * Les refus de distance, d'obstruction, d'occupation et de sécurité restent
     * gérés par le chemin de sommeil vanilla.
     */
    public static boolean shouldClearSleepProblem(
            DimensionType dimension,
            @Nullable PlayerEntity.SleepResult problem) {
        if (!isSupportedDimension(dimension) || problem == null) {
            return false;
        }
        return problem == PlayerEntity.SleepResult.NOT_POSSIBLE_HERE
                || problem == PlayerEntity.SleepResult.NOT_POSSIBLE_NOW;
    }

    /**
     * Pendant que le joueur dort, seul le refus lié au temps est ignoré.
     * Un lit retiré, détruit ou perdu continue donc à réveiller le joueur.
     */
    public static boolean shouldContinueSleeping(
            DimensionType dimension,
            @Nullable PlayerEntity.SleepResult problem) {
        return isSupportedDimension(dimension)
                && problem == PlayerEntity.SleepResult.NOT_POSSIBLE_NOW;
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
    public static ActionResultType useBed(
            BlockState clickedState, World level, BlockPos clickedPos,
            PlayerEntity player) {
        if (!isSupportedDimension(level)
                || !(clickedState.getBlock() instanceof BedBlock)) {
            return ActionResultType.PASS;
        }

        if (level.isClientSide()) {
            return ActionResultType.SUCCESS;
        }
        if (!(player instanceof ServerPlayerEntity)) {
            return ActionResultType.PASS;
        } ServerPlayerEntity serverPlayer = (ServerPlayerEntity) (player);

        Direction facing = clickedState.getValue(BedBlock.FACING);
        BedPart part = clickedState.getValue(BedBlock.PART);
        BlockPos headPos = resolveHeadPosition(clickedPos, facing, part);
        BlockState headState = level.getBlockState(headPos);
        if (!(headState.getBlock() instanceof BedBlock)) {
            return ActionResultType.PASS;
        }

        if (headState.getValue(BedBlock.OCCUPIED)) {
            List<VillagerEntity> sleepers = level.getEntitiesOfClass(
                    VillagerEntity.class, new AxisAlignedBB(headPos), VillagerEntity::isSleeping);
            if (sleepers.isEmpty()) {
                serverPlayer.displayClientMessage(
                        new net.minecraft.util.text.TranslationTextComponent(
                                "block.minecraft.bed.occupied"), true);
                return ActionResultType.SUCCESS;
            }
            sleepers.get(0).stopSleeping();
        }

        Either<PlayerEntity.SleepResult, net.minecraft.util.Unit> result =
                serverPlayer.startSleepInBed(headPos);
        result.ifLeft(problem -> {
            if (problem.getMessage() != null) {
                serverPlayer.displayClientMessage(problem.getMessage(), true);
            } else {
                VanillaInstincts.LOGGER.debug(
                        "Sommeil refusé sans message à {} dans {}",
                        headPos, String.valueOf(level.dimension.getType()));
            }
        });
        return ActionResultType.SUCCESS;
    }
}
