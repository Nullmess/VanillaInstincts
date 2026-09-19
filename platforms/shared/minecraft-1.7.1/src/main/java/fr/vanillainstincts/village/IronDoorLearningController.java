package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import fr.vanillainstincts.compat.Minecraft115VectorCompat;

import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import fr.vanillainstincts.compat.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLever;
import fr.vanillainstincts.compat.LegacyBlockState;
import fr.vanillainstincts.compat.Vec3;
/** Apprentissage visuel d'un mécanisme ouvrant réellement une porte en fer. */
public final class IronDoorLearningController {
    private static final Map<WorldServer, List<PendingObservation>> PENDING =
            new WeakHashMap<>();

    private IronDoorLearningController() {
    }

    /**
     * L'événement de clic arrive avant la réaction complète du bloc. La scène
     * est donc vérifiée un tick plus tard : aucune connaissance n'est accordée
     * si le mécanisme n'a finalement ni été alimenté ni ouvert la porte.
     */
    public static void observePlayerUse(WorldServer level,
                                        EntityPlayerMP player,
                                        BlockPos mechanism,
                                        long gameTime) {
        if (level == null || player == null || mechanism == null
                || !isLearnableMechanism(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, mechanism))) {
            return;
        }
        BlockPos door = findNearestIronDoor(level, mechanism,
                VillageSocialRules.IRON_DOOR_MECHANISM_SEARCH_RADIUS);
        if (door == null) return;
        boolean poweredBefore = isPowered(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, mechanism));
        boolean openBefore = isOpen(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, door));
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>())
                .add(new PendingObservation(player.getUniqueID(),
                        immutableBlockPos(mechanism), immutableBlockPos(door),
                        poweredBefore, openBefore, gameTime + 1L));
    }

    public static void tickPending(WorldServer level, long gameTime) {
        List<PendingObservation> observations = PENDING.get(level);
        if (observations == null || observations.isEmpty()) return;
        observations.removeIf(observation -> {
            if (gameTime < observation.verifyAt()) return false;
            EntityPlayerMP player = fr.vanillainstincts.compat.Minecraft17Compat.player(fr.vanillainstincts.compat.Minecraft17Compat.server(level), observation.playerId());
            if (player != null && player.worldObj == level) {
                boolean poweredAfter = isPowered(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, 
                        observation.mechanism()));
                boolean openAfter = isOpen(fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, 
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

    public static void clearLevel(WorldServer level) {
        PENDING.remove(level);
    }

    private static void teachWitnesses(WorldServer level,
                                       EntityPlayerMP player,
                                       BlockPos mechanism,
                                       BlockPos door,
                                       long gameTime) {
        double radius = VillageSocialRules.IRON_DOOR_LEARNING_RADIUS;
        for (EntityVillager villager : fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class,
                fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(player).expand(radius, radius, radius),
                candidate -> candidate.isEntityAlive() && !candidate.isChild())) {
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, player) > radius * radius
                    || fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, Minecraft115VectorCompat.atCenterOf(mechanism))
                    > radius * radius
                    || !fr.vanillainstincts.compat.Minecraft112Compat.canSee(villager, player)) {
                continue;
            }
            IronDoorMemory.remember(villager, level, door, mechanism,
                    gameTime);
        }
    }

    public static boolean isLearnableMechanism(LegacyBlockState state) {
        return state != null && (state.getBlock() instanceof BlockLever
                || state.getBlock() instanceof BlockButton);
    }

    public static BlockPos findNearestIronDoor(WorldServer level,
                                                BlockPos mechanism,
                                                int radius) {
        if (level == null || mechanism == null) return null;
        return fr.vanillainstincts.compat.Minecraft112BlockPosCompat.betweenClosedStream(
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(mechanism, -radius, -radius, -radius),
                        fr.vanillainstincts.compat.Minecraft112Compat.offset(mechanism, radius, radius, radius))
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.isBlockLoaded(level, pos))
                .filter(pos -> fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos).getBlock().equals(Blocks.iron_door))
                .map(pos -> lowerHalf(pos, fr.vanillainstincts.compat.Minecraft17Compat.getBlockState(level, pos)))
                .distinct()
                .min(Comparator.comparingDouble(pos ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(Minecraft115VectorCompat.atCenterOf(pos), 
                                Minecraft115VectorCompat.atCenterOf(mechanism))))
                .map(fr.vanillainstincts.compat.Minecraft112Compat::immutable)
                .orElse(null);
    }

    private static boolean isPowered(LegacyBlockState state) {
        if (state == null) return false;
        if (state.getBlock() instanceof BlockButton) return fr.vanillainstincts.compat.Minecraft17Compat.mechanismPowered(state);
        if (state.getBlock() instanceof BlockLever) return fr.vanillainstincts.compat.Minecraft17Compat.mechanismPowered(state);
        return false;
    }

    private static boolean isOpen(LegacyBlockState state) {
        return state != null && state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorOpen(state);
    }

    private static BlockPos lowerHalf(BlockPos position, LegacyBlockState state) {
        if (state.getBlock() instanceof BlockDoor
                && fr.vanillainstincts.compat.Minecraft17Compat.doorUpper(state)) {
            return position.down();
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
