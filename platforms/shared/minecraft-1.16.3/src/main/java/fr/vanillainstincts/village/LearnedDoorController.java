package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.ZombieVillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BlockState;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.state.properties.DoubleBlockHalf;
import net.minecraft.util.math.vector.Vector3d;
/**
 * Utilise les portes sans jamais les casser. Les portes en bois sont un savoir
 * naturel du zombie-villageois ; une porte en fer exige une mémoire apprise
 * avant ou après une guérison.
 */
public final class LearnedDoorController {
    private static final String NEXT_USE_AT = "vanillainstincts_learned_door_next_use_at";
    private static final String PENDING_IRON_DOOR =
            "vanillainstincts_pending_learned_iron_door";
    private static final String PENDING_IRON_MECHANISM =
            "vanillainstincts_pending_learned_iron_mechanism";

    private LearnedDoorController() {
    }

    public static boolean maintainVillager(VillagerEntity villager,
                                             ServerWorld level,
                                             long gameTime) {
        if (villager == null || level == null || villager.isBaby()
                || villager.isTrading()) {
            return false;
        }
        if (continuePendingIronDoor(villager, level, gameTime)) {
            return true;
        }
        BlockPos objective = villager.getNavigation().getTargetPos();
        if (objective == null) return false;
        return maintain(villager, level, objective, false, gameTime);
    }

    public static boolean maintainZombieVillager(ZombieVillagerEntity zombie,
                                                   ServerWorld level,
                                                   long gameTime) {
        if (zombie == null || level == null) return false;

        // Cette règle est absolue : VanillaInstincts ne donne jamais de bris de porte
        // au zombie-villageois, même en difficulté difficile.
        zombie.setCanBreakDoors(false);
        if (zombie.isBaby()) return false;

        if (continuePendingIronDoor(zombie, level, gameTime)) {
            return true;
        }
        LivingEntity target = zombie.getTarget();
        BlockPos objective = target != null && target.isAlive()
                ? target.blockPosition() : zombie.getNavigation().getTargetPos();
        if (objective == null) return false;
        return maintain(zombie, level, objective, true, gameTime);
    }

    private static boolean maintain(MobEntity actor, ServerWorld level,
                                    BlockPos objective,
                                    boolean knowsWoodNaturally,
                                    long gameTime) {
        Optional<DoorTarget> found = findBlockingDoor(actor, level, objective,
                knowsWoodNaturally);
        if (!found.isPresent()) return false;

        DoorTarget target = found.get();
        if (target.iron()) {
            IronDoorMemory.LearnedDoor learned = IronDoorMemory.entries(actor,
                    level).stream()
                    .filter(entry -> entry.door().equals(target.position()))
                    .findFirst().orElse(null);
            if (learned == null) return false;
            rememberPendingIronDoor(actor, learned);
            return useLearnedIronDoor(actor, level, target.position(), learned,
                    gameTime);
        }
        return openWoodenDoor(actor, level, target.position(), gameTime);
    }

    private static boolean continuePendingIronDoor(MobEntity actor,
                                                    ServerWorld level,
                                                    long gameTime) {
        if (!actor.getPersistentData().contains(PENDING_IRON_DOOR)
                || !actor.getPersistentData().contains(
                PENDING_IRON_MECHANISM)) {
            return false;
        }
        BlockPos door = BlockPos.of(actor.getPersistentData()
                .getLong(PENDING_IRON_DOOR));
        BlockPos mechanism = BlockPos.of(actor.getPersistentData()
                .getLong(PENDING_IRON_MECHANISM));
        BlockState state = level.getBlockState(door);
        if (!(state.getBlock() instanceof DoorBlock)
                || state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN)
                || !IronDoorMemory.knows(actor, level, door)) {
            clearPendingIronDoor(actor);
            return false;
        }
        return useLearnedIronDoor(actor, level, door,
                new IronDoorMemory.LearnedDoor(door, mechanism, 0L),
                gameTime);
    }

    private static void rememberPendingIronDoor(
            MobEntity actor, IronDoorMemory.LearnedDoor learned) {
        actor.getPersistentData().putLong(PENDING_IRON_DOOR,
                learned.door().asLong());
        actor.getPersistentData().putLong(PENDING_IRON_MECHANISM,
                learned.mechanism().asLong());
    }

    private static void clearPendingIronDoor(MobEntity actor) {
        actor.getPersistentData().remove(PENDING_IRON_DOOR);
        actor.getPersistentData().remove(PENDING_IRON_MECHANISM);
    }

    private static boolean openWoodenDoor(MobEntity actor, ServerWorld level,
                                           BlockPos door, long gameTime) {
        double reach = VillageSocialRules.LEARNED_DOOR_REACH_SQR;
        if (actor.distanceToSqr(Vector3d.atCenterOf(door)) > reach) {
            actor.getNavigation().moveTo(door.getX() + 0.5D, door.getY(),
                    door.getZ() + 0.5D,
                    VillageSocialRules.ZOMBIE_VILLAGER_DOOR_SPEED);
            return true;
        }
        return open(actor, level, door, null, gameTime);
    }

    private static boolean useLearnedIronDoor(MobEntity actor, ServerWorld level,
                                               BlockPos door,
                                               IronDoorMemory.LearnedDoor learned,
                                               long gameTime) {
        BlockPos mechanism = learned.mechanism();
        if (!level.hasChunkAt(mechanism)
                || !IronDoorLearningController.isLearnableMechanism(
                level.getBlockState(mechanism))) {
            clearPendingIronDoor(actor);
            return false;
        }
        if (actor.distanceToSqr(Vector3d.atCenterOf(mechanism))
                > VillageSocialRules.LEARNED_DOOR_MECHANISM_REACH_SQR) {
            double speed = actor instanceof VillagerEntity
                    ? VillageSocialRules.VILLAGER_IRON_DOOR_SPEED
                    : VillageSocialRules.ZOMBIE_VILLAGER_DOOR_SPEED;
            actor.getNavigation().moveTo(mechanism.getX() + 0.5D,
                    mechanism.getY(), mechanism.getZ() + 0.5D, speed);
            return true;
        }
        return open(actor, level, door, mechanism, gameTime);
    }

    private static boolean open(MobEntity actor, ServerWorld level, BlockPos door,
                                BlockPos mechanism, long gameTime) {
        if (gameTime < actor.getPersistentData().getLong(NEXT_USE_AT)) {
            return true;
        }
        BlockState doorState = level.getBlockState(door);
        if (!(doorState.getBlock() instanceof DoorBlock)) {
            return false;
        } DoorBlock doorBlock = (DoorBlock) (doorState.getBlock());

        if (mechanism != null) {
            BlockState mechanismState = level.getBlockState(mechanism);
            if (mechanismState.hasProperty(BlockStateProperties.POWERED)
                    && !mechanismState.getValue(BlockStateProperties.POWERED)) {
                Block block = mechanismState.getBlock();
                if (!WorldPermissionService.setBlock(level, actor, mechanism,
                        mechanismState.setValue(BlockStateProperties.POWERED,
                                true), 3,
                        WorldActionType.REDSTONE_INTERACTION)) {
                    return false;
                }
                level.updateNeighborsAt(mechanism, block);
                // Seul le bouton doit revenir automatiquement à son état
                // normal. Le levier reste enclenché comme après un vrai usage.
                if (block instanceof AbstractButtonBlock) {
                    level.getBlockTicks().scheduleTick(mechanism, block,
                            VillageSocialRules.LEARNED_BUTTON_RESET_TICKS);
                }
            }
        }

        actor.getLookControl().setLookAt(door.getX() + 0.5D,
                door.getY() + 0.8D, door.getZ() + 0.5D, 30.0F, 30.0F);
        actor.swing(Hand.MAIN_HAND);
        doorBlock.setOpen(level, doorState, door, true);
        if (doorState.is(Blocks.IRON_DOOR)) {
            clearPendingIronDoor(actor);
        }
        actor.getPersistentData().putLong(NEXT_USE_AT,
                gameTime + VillageSocialRules.LEARNED_DOOR_USE_COOLDOWN_TICKS);
        return true;
    }

    public static Optional<DoorTarget> findBlockingDoor(MobEntity actor,
                                                         ServerWorld level,
                                                         BlockPos objective,
                                                         boolean woodKnown) {
        if (actor == null || level == null || objective == null) {
            return Optional.empty();
        }
        int radius = VillageSocialRules.LEARNED_DOOR_SCAN_RADIUS;
        BlockPos center = actor.blockPosition();
        Vector3d objectiveCenter = Vector3d.atCenterOf(objective);
        double actorToObjective = actor.position().distanceToSqr(objectiveCenter);

        return BlockPos.betweenClosedStream(
                        center.offset(-radius, -1, -radius),
                        center.offset(radius, 2, radius))
                .filter(level::hasChunkAt)
                .map(position -> lowerHalf(position,
                        level.getBlockState(position)))
                .distinct()
                .map(position -> targetFor(actor, level, position, woodKnown))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(target -> Vector3d.atCenterOf(target.position())
                        .distanceToSqr(objectiveCenter) < actorToObjective)
                .min(Comparator.comparingDouble(target ->
                        actor.distanceToSqr(Vector3d.atCenterOf(target.position()))));
    }

    private static Optional<DoorTarget> targetFor(MobEntity actor,
                                                   ServerWorld level,
                                                   BlockPos position,
                                                   boolean woodKnown) {
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof DoorBlock)
                || state.hasProperty(BlockStateProperties.OPEN)
                && state.getValue(BlockStateProperties.OPEN)) {
            return Optional.empty();
        }
        boolean iron = state.is(Blocks.IRON_DOOR);
        boolean wooden = state.is(BlockTags.WOODEN_DOORS);
        if (!canUseDoor(wooden, iron,
                iron && IronDoorMemory.knows(actor, level, position),
                woodKnown)) {
            return Optional.empty();
        }
        return Optional.of(new DoorTarget(position.immutable(), iron));
    }

    public static boolean canUseDoor(boolean wooden, boolean iron,
                                     boolean learnedIron,
                                     boolean knowsWoodNaturally) {
        return wooden && knowsWoodNaturally || iron && learnedIron;
    }

    private static BlockPos lowerHalf(BlockPos position, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF)
                == DoubleBlockHalf.UPPER) {
            return position.below();
        }
        return position;
    }

    public static class DoorTarget {
        private final BlockPos position;
        private final boolean iron;

        public DoorTarget(BlockPos position, boolean iron) {
            this.position = position;
            this.iron = iron;
        }

        public BlockPos position() { return this.position; }

        public boolean iron() { return this.iron; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof DoorTarget)) return false;
            DoorTarget that = (DoorTarget) other;
            return java.util.Objects.equals(this.position, that.position) && this.iron == that.iron;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.iron); }

        @Override
        public String toString() {
            return "DoorTarget[" + "position=" + this.position + ", " + "iron=" + this.iron + "]";
        }

    }
}
