package fr.vanillainstincts.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Random;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

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

    private static final Map<ServerLevel, LevelState> STATES =
            new WeakHashMap<>();

    private TrappedChestPrankController() {
    }

    public static boolean trySchedule(ServerLevel level, BlockPos chestPos,
                                      ServerPlayer player, long gameTime) {
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

    public static void tickLevel(ServerLevel level, long gameTime) {
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

    public static void clearLevel(ServerLevel level) {
        STATES.remove(level);
    }

    private static void play(ServerLevel level, PendingPrank pending) {
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
        TNT_FUSE(SoundEvents.TNT_PRIMED, SoundSource.BLOCKS),
        CREEPER_HISS(SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE);

        private final SoundEvent sound;
        private final SoundSource source;

        PrankKind(SoundEvent sound, SoundSource source) {
            this.sound = sound;
            this.source = source;
        }

        public SoundEvent sound() {
            return sound;
        }

        public SoundSource source() {
            return source;
        }
    }

    public record PendingPrank(BlockPos position, long playAt,
                               PrankKind kind) {
    }

    private static final class LevelState {
        private final Map<Long, Long> nextRollAt = new HashMap<>();
        private final Map<Long, PendingPrank> pending = new HashMap<>();
    }
}
