package fr.vanillainstincts.world;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import java.util.Random;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;

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

    private static final Map<WorldServer, LevelState> STATES =
            new WeakHashMap<>();

    private TrappedChestPrankController() {
    }

    public static boolean trySchedule(WorldServer level, BlockPos chestPos,
                                      EntityPlayerMP player, long gameTime) {
        if (level == null || chestPos == null || player == null
                || player.isSpectator()
                || !isEligible(level.getBlockState(chestPos))) {
            return false;
        }

        LevelState state = STATES.computeIfAbsent(level,
                ignored -> new LevelState());
        long key = chestPos.toLong();
        if (!canRoll(gameTime, state.nextRollAt.getOrDefault(key, 0L))) {
            return false;
        }

        state.nextRollAt.put(key, gameTime + ROLL_COOLDOWN_TICKS);
        Random random =fr.vanillainstincts.compat.Minecraft112Compat.random(level);
        if (!rollTriggers(random.nextDouble())) {
            return false;
        }

        PrankKind kind = prankFromUnit(random.nextDouble());
        int delay = delayFromUnit(random.nextDouble());
        state.pending.put(key, new PendingPrank(immutableBlockPos(chestPos),
                gameTime + delay, kind));
        state.nextRollAt.put(key, gameTime + SUCCESS_COOLDOWN_TICKS);
        return true;
    }

    public static void tickLevel(WorldServer level, long gameTime) {
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
            if (!level.isBlockLoaded(pending.position())
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

    public static void clearLevel(WorldServer level) {
        STATES.remove(level);
    }

    private static void play(WorldServer level, PendingPrank pending) {
        PrankKind kind = pending.kind();
        float pitch = kind == PrankKind.TNT_FUSE
                ? 0.92F +fr.vanillainstincts.compat.Minecraft112Compat.random(level).nextFloat() * 0.12F
                : 0.96F +fr.vanillainstincts.compat.Minecraft112Compat.random(level).nextFloat() * 0.08F;
        fr.vanillainstincts.compat.Minecraft18SoundCompat.play(level, pending.position(), kind.sound(),
                0.9F, pitch);
    }

    public static boolean isEligible(IBlockState state) {
        return state != null && state.getBlock().equals(Blocks.trapped_chest);
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
        TNT_FUSE("game.tnt.primed"),
        CREEPER_HISS("creeper.primed");

        private final String sound;

        PrankKind(String sound) {
            this.sound = sound;
        }

        public String sound() {
            return sound;
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
