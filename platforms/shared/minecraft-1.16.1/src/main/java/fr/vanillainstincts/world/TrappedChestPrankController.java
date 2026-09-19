package fr.vanillainstincts.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;

/**
 * Ajoute de rares fausses alertes sonores aux coffres piégés.
 *
 * <p>Aucune entité explosive n'est créée : le contrôleur ne fait que jouer
 * un son après un petit délai. Un verrou par coffre empêche le joueur de
 * provoquer le troll en ouvrant et fermant rapidement le même coffre.</p>
 */
public final class TrappedChestPrankController {
    public static final double TRIGGER_CHANCE = 0.025D;
    public static final int MIN_DELAY_TICKS = 5;
    public static final int MAX_DELAY_TICKS = 22;
    public static final int ROLL_COOLDOWN_TICKS = 200;
    public static final int SUCCESS_COOLDOWN_TICKS = 12_000;

    private static final Map<ServerWorld, LevelState> STATES =
            new WeakHashMap<>();

    private TrappedChestPrankController() {
    }

    public static boolean trySchedule(ServerWorld level, BlockPos chestPos,
                                      ServerPlayerEntity player, long gameTime) {
        if (level == null || chestPos == null || player == null
                || player.isSpectator()
                || !isEligible(level.getBlockState(chestPos))) {
            return false;
        }

        LevelState state = STATES.computeIfAbsent(level,
                ignored -> new LevelState());
        long key = chestPos.asLong();
        if (!canRoll(gameTime, state.nextRollAt.getOrDefault(key, 0L))) {
            return false;
        }

        state.nextRollAt.put(key, gameTime + ROLL_COOLDOWN_TICKS);
        Random random = level.getRandom();
        if (!rollTriggers(random.nextDouble())) {
            return false;
        }

        PrankKind kind = prankFromUnit(random.nextDouble());
        int delay = delayFromUnit(random.nextDouble());
        state.pending.put(key, new PendingPrank(chestPos.immutable(),
                gameTime + delay, kind));
        state.nextRollAt.put(key, gameTime + SUCCESS_COOLDOWN_TICKS);
        return true;
    }

    public static void tickLevel(ServerWorld level, long gameTime) {
        LevelState state = STATES.get(level);
        if (state == null || state.pending.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Long, PendingPrank>> iterator =
                state.pending.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingPrank pending = iterator.next().getValue();
            if (gameTime < pending.playAt()) {
                continue;
            }
            iterator.remove();
            if (!level.hasChunkAt(pending.position())
                    || !isEligible(level.getBlockState(pending.position()))) {
                continue;
            }
            play(level, pending);
        }

        if ((gameTime & 1023L) == 0L) {
            state.nextRollAt.entrySet().removeIf(
                    entry -> entry.getValue() + SUCCESS_COOLDOWN_TICKS
                            < gameTime);
        }
    }

    public static void clearLevel(ServerWorld level) {
        STATES.remove(level);
    }

    private static void play(ServerWorld level, PendingPrank pending) {
        PrankKind kind = pending.kind();
        float pitch = kind == PrankKind.TNT_FUSE
                ? 0.92F + level.getRandom().nextFloat() * 0.12F
                : 0.96F + level.getRandom().nextFloat() * 0.08F;
        level.playSound(null, pending.position(), kind.sound(), kind.source(),
                0.9F, pitch);
    }

    public static boolean isEligible(BlockState state) {
        return state != null && state.is(Blocks.TRAPPED_CHEST);
    }

    public static boolean rollTriggers(double unitRoll) {
        return unitRoll >= 0.0D && unitRoll < TRIGGER_CHANCE;
    }

    public static boolean canRoll(long gameTime, long nextRollAt) {
        return gameTime >= nextRollAt;
    }

    public static int delayFromUnit(double unitRoll) {
        double bounded = Math.max(0.0D, Math.min(0.999999D, unitRoll));
        int span = MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1;
        return MIN_DELAY_TICKS + (int) Math.floor(bounded * span);
    }

    public static PrankKind prankFromUnit(double unitRoll) {
        return unitRoll < 0.5D
                ? PrankKind.TNT_FUSE : PrankKind.CREEPER_HISS;
    }

    public enum PrankKind {
        TNT_FUSE(SoundEvents.TNT_PRIMED, SoundCategory.BLOCKS),
        CREEPER_HISS(SoundEvents.CREEPER_PRIMED, SoundCategory.HOSTILE);

        private final SoundEvent sound;
        private final SoundCategory source;

        PrankKind(SoundEvent sound, SoundCategory source) {
            this.sound = sound;
            this.source = source;
        }

        public SoundEvent sound() {
            return sound;
        }

        public SoundCategory source() {
            return source;
        }
    }

    public static class PendingPrank {
        private final BlockPos position;
        private final long playAt;
        private final PrankKind kind;

        public PendingPrank(BlockPos position, long playAt, PrankKind kind) {
            this.position = position;
            this.playAt = playAt;
            this.kind = kind;
        }

        public BlockPos position() { return this.position; }

        public long playAt() { return this.playAt; }

        public PrankKind kind() { return this.kind; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingPrank)) return false;
            PendingPrank that = (PendingPrank) other;
            return java.util.Objects.equals(this.position, that.position) && this.playAt == that.playAt && java.util.Objects.equals(this.kind, that.kind);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.position, this.playAt, this.kind); }

        @Override
        public String toString() {
            return "PendingPrank[" + "position=" + this.position + ", " + "playAt=" + this.playAt + ", " + "kind=" + this.kind + "]";
        }

    }

    private static final class LevelState {
        private final Map<Long, Long> nextRollAt = new HashMap<>();
        private final Map<Long, PendingPrank> pending = new HashMap<>();
    }
}
