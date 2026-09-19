package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft115TagCompat;

import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.Comparator;
import java.util.Optional;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.block.BlockDoor;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockDoor;
import fr.vanillainstincts.compat.LegacyBlockState;
import fr.vanillainstincts.compat.Vec3;
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

    public static boolean maintainVillager(EntityVillager villager,
                                             WorldServer level,
                                             long gameTime) {
        if (villager == null || level == null || villager.isChild()
                || villager.isTrading()) {
            return false;
        }
        if (continuePendingIronDoor(villager, level, gameTime)) {
            return true;
        }
        BlockPos objective = fr.vanillainstincts.compat.Minecraft112Compat.navigationTarget(villager);
        if (objective == null) return false;
        return maintain(villager, level, objective, false, gameTime);
    }

    public static boolean maintainZombieVillager(EntityZombie zombie,
                                                   WorldServer level,
                                                   long gameTime) {
        if (zombie == null || level == null
                || !fr.vanillainstincts.compat.Minecraft110Compat.isZombieVillager(zombie)) return false;

        // Cette règle est absolue : VanillaInstincts ne donne jamais de bris de porte
        // au zombie-villageois, même en difficulté difficile.
        fr.vanillainstincts.compat.Minecraft112Compat.setBreakDoors(zombie, false);
        if (zombie.isChild()) return false;

        if (continuePendingIronDoor(zombie, level, gameTime)) {
            return true;
        }
        EntityLivingBase target = zombie.getAttackTarget();
        BlockPos objective = target != null && target.isEntityAlive()
                ? entityBlockPos(target) : fr.vanillainstincts.compat.Minecraft112Compat.navigationTarget(zombie);
        if (objective == null) return false;
        return maintain(zombie, level, objective, true, gameTime);
    }

    private static boolean maintain(EntityLiving actor, WorldServer level,
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

    private static boolean continuePendingIronDoor(EntityLiving actor,
                                                    WorldServer level,
                                                    long gameTime) {
        if (!actor.getEntityData().hasKey(PENDING_IRON_DOOR)
                || !actor.getEntityData().hasKey(
                PENDING_IRON_MECHANISM)) {
            return false;
        }
        BlockPos door = BlockPos.fromLong(actor.getEntityData()
                .getLong(PENDING_IRON_DOOR));
        BlockPos mechanism = BlockPos.fromLong(actor.getEntityData()
                .getLong(PENDING_IRON_MECHANISM));
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, door);
        if (!(state.getBlock() instanceof BlockDoor)
                || state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorOpen(state)
                || !IronDoorMemory.knows(actor, level, door)) {
            clearPendingIronDoor(actor);
            return false;
        }
        return useLearnedIronDoor(actor, level, door,
                new IronDoorMemory.LearnedDoor(door, mechanism, 0L),
                gameTime);
    }

    private static void rememberPendingIronDoor(
            EntityLiving actor, IronDoorMemory.LearnedDoor learned) {
        actor.getEntityData().setLong(PENDING_IRON_DOOR,
                learned.door().toLong());
        actor.getEntityData().setLong(PENDING_IRON_MECHANISM,
                learned.mechanism().toLong());
    }

    private static void clearPendingIronDoor(EntityLiving actor) {
        actor.getEntityData().removeTag(PENDING_IRON_DOOR);
        actor.getEntityData().removeTag(PENDING_IRON_MECHANISM);
    }

    private static boolean openWoodenDoor(EntityLiving actor, WorldServer level,
                                           BlockPos door, long gameTime) {
        double reach = VillageSocialRules.LEARNED_DOOR_REACH_SQR;
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(actor, Minecraft115VectorCompat.atCenterOf(door)) > reach) {
            actor.getNavigator().tryMoveToXYZ(door.getX() + 0.5D, door.getY(),
                    door.getZ() + 0.5D,
                    VillageSocialRules.ZOMBIE_VILLAGER_DOOR_SPEED);
            return true;
        }
        return open(actor, level, door, null, gameTime);
    }

    private static boolean useLearnedIronDoor(EntityLiving actor, WorldServer level,
                                               BlockPos door,
                                               IronDoorMemory.LearnedDoor learned,
                                               long gameTime) {
        BlockPos mechanism = learned.mechanism();
        if (!fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, mechanism)
                || !IronDoorLearningController.isLearnableMechanism(
                fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, mechanism))) {
            clearPendingIronDoor(actor);
            return false;
        }
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(actor, Minecraft115VectorCompat.atCenterOf(mechanism))
                > VillageSocialRules.LEARNED_DOOR_MECHANISM_REACH_SQR) {
            double speed = actor instanceof EntityVillager
                    ? VillageSocialRules.VILLAGER_IRON_DOOR_SPEED
                    : VillageSocialRules.ZOMBIE_VILLAGER_DOOR_SPEED;
            actor.getNavigator().tryMoveToXYZ(mechanism.getX() + 0.5D,
                    mechanism.getY(), mechanism.getZ() + 0.5D, speed);
            return true;
        }
        return open(actor, level, door, mechanism, gameTime);
    }

    private static boolean open(EntityLiving actor, WorldServer level, BlockPos door,
                                BlockPos mechanism, long gameTime) {
        if (gameTime < actor.getEntityData().getLong(NEXT_USE_AT)) {
            return true;
        }
        LegacyBlockState doorState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, door);
        if (!(doorState.getBlock() instanceof BlockDoor)) {
            return false;
        } BlockDoor doorBlock = (BlockDoor) (doorState.getBlock());

        if (mechanism != null) {
            LegacyBlockState mechanismState = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, mechanism);
            if (isMechanismPoweredProperty(mechanismState)
                    && !isMechanismPowered(mechanismState)) {
                Block block = mechanismState.getBlock();
                if (!WorldPermissionService.setBlock(level, actor, mechanism,
                        poweredMechanismState(mechanismState, true), 3,
                        WorldActionType.REDSTONE_INTERACTION)) {
                    return false;
                }
                fr.vanillainstincts.compat.Minecraft17Compat.notifyNeighbors(level, mechanism, block);
                // Seul le bouton doit revenir automatiquement à son état
                // normal. Le levier reste enclenché comme après un vrai usage.
                if (block instanceof BlockButton) {
                    fr.vanillainstincts.compat.Minecraft17Compat.scheduleUpdate(level, mechanism, block,
                            VillageSocialRules.LEARNED_BUTTON_RESET_TICKS);
                }
            }
        }

        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(actor,
                door.getX() + 0.5D, door.getY() + 0.8D, door.getZ() + 0.5D,
                30.0F, 30.0F);
        actor.swingItem();
        fr.vanillainstincts.compat.Minecraft17Compat.toggleDoor(doorBlock, level, door, true);
        if (doorState.getBlock().equals(Blocks.iron_door)) {
            clearPendingIronDoor(actor);
        }
        actor.getEntityData().setLong(NEXT_USE_AT,
                gameTime + VillageSocialRules.LEARNED_DOOR_USE_COOLDOWN_TICKS);
        return true;
    }

    public static Optional<DoorTarget> findBlockingDoor(EntityLiving actor,
                                                         WorldServer level,
                                                         BlockPos objective,
                                                         boolean woodKnown) {
        if (actor == null || level == null || objective == null) {
            return Optional.empty();
        }
        int radius = VillageSocialRules.LEARNED_DOOR_SCAN_RADIUS;
        BlockPos center = entityBlockPos(actor);
        Vec3 objectiveCenter = Minecraft115VectorCompat.atCenterOf(objective);
        double actorToObjective = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(actor), objectiveCenter);

        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, -radius, -1, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(center, radius, 2, radius))
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos))
                .map(position -> lowerHalf(position,
                        fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position)))
                .distinct()
                .map(position -> targetFor(actor, level, position, woodKnown))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(target -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(Minecraft115VectorCompat.atCenterOf(target.position()), objectiveCenter) < actorToObjective)
                .min(Comparator.comparingDouble(target ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(actor, Minecraft115VectorCompat.atCenterOf(target.position()))));
    }

    private static Optional<DoorTarget> targetFor(EntityLiving actor,
                                                   WorldServer level,
                                                   BlockPos position,
                                                   boolean woodKnown) {
        LegacyBlockState state = fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, position);
        if (!(state.getBlock() instanceof BlockDoor)
                || state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorOpen(state)) {
            return Optional.empty();
        }
        boolean iron = state.getBlock().equals(Blocks.iron_door);
        boolean wooden = state.getBlock() instanceof BlockDoor;
        if (!canUseDoor(wooden, iron,
                iron && IronDoorMemory.knows(actor, level, position),
                woodKnown)) {
            return Optional.empty();
        }
        return Optional.of(new DoorTarget(immutableBlockPos(position), iron));
    }

    public static boolean canUseDoor(boolean wooden, boolean iron,
                                     boolean learnedIron,
                                     boolean knowsWoodNaturally) {
        return wooden && knowsWoodNaturally || iron && learnedIron;
    }

    private static BlockPos lowerHalf(BlockPos position, LegacyBlockState state) {
        if (state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorUpper(state)) {
            return position.down();
        }
        return position;
    }

    private static boolean isMechanismPoweredProperty(LegacyBlockState state) {
        return state != null && (state.getBlock() instanceof BlockButton
                || state.getBlock() instanceof BlockLever);
    }

    private static boolean isMechanismPowered(LegacyBlockState state) {
        if (state.getBlock() instanceof BlockButton) {
            return fr.vanillainstincts.compat.Minecraft17Compat.mechanismPowered(state);
        }
        if (state.getBlock() instanceof BlockLever) {
            return fr.vanillainstincts.compat.Minecraft17Compat.mechanismPowered(state);
        }
        return false;
    }

    private static LegacyBlockState poweredMechanismState(LegacyBlockState state, boolean powered) {
        if (state.getBlock() instanceof BlockButton) {
            return fr.vanillainstincts.compat.Minecraft17Compat.withMechanismPowered(state, powered);
        }
        if (state.getBlock() instanceof BlockLever) {
            return fr.vanillainstincts.compat.Minecraft17Compat.withMechanismPowered(state, powered);
        }
        return state;
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
