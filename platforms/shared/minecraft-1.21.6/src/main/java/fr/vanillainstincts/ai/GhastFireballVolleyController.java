package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.NetherRules;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.phys.Vec3;
/**
 * Transforme certains renvois de boule de feu en duel de volley lisible.
 *
 * <p>Le même projectile reste utilisé pendant son échange, mais les tirs du
 * ghast restent entièrement vanilla : plusieurs grosses boules peuvent être
 * actives simultanément et chacune est suivie indépendamment.</p>
 */
public final class GhastFireballVolleyController {
    private static final String PREFIX = "VanillaInstinctsGhastVolley";
    private static final String TRACKED = PREFIX + "Tracked";
    private static final String SOURCE_GHAST = PREFIX + "SourceGhast";
    private static final String TARGET_PLAYER = PREFIX + "TargetPlayer";
    private static final String RETURNS = PREFIX + "Returns";
    private static final String MAX_RETURNS = PREFIX + "MaxReturns";
    private static final String LAST_RETURN_AT = PREFIX + "LastReturnAt";

    private static final String PERSONALITY_ROLLED = PREFIX + "PersonalityRolled";
    private static final String VOLLEY_PLAYER = PREFIX + "VolleyPlayer";
    private static final String PERSONALITY_DIFFICULTY =
            PREFIX + "PersonalityDifficulty";
    private GhastFireballVolleyController() {
    }

    /**
     * Enregistre chaque boule sans jamais annuler un tir vanilla.
     *
     * @return toujours {@code false} : VanillaInstincts ne limite plus le nombre de
     *         projectiles actifs d'un ghast.
     */
    public static boolean onFireballJoin(LargeFireball fireball,
                                         ServerLevel level,
                                         boolean loadedFromDisk) {
        if (fireball == null || level == null) return false;
        CompoundTag ballData = fireball.getPersistentData();
        if (fr.vanillainstincts.persistence.NbtCompat.getBoolean(ballData, TRACKED)) return false;

        Entity owner = fireball.getOwner();
        if (!(owner instanceof Ghast ghast)) return false;

        ballData.putBoolean(TRACKED, true);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(ballData, SOURCE_GHAST, ghast.getUUID());
        if (ghast.getTarget() instanceof Player player) {
            fr.vanillainstincts.persistence.NbtCompat.putUuid(ballData, TARGET_PLAYER, player.getUUID());
        }
        ballData.putInt(RETURNS, 0);
        ballData.putInt(MAX_RETURNS, maxReturns(level.getDifficulty(),
                ghast.getRandom().nextInt()));
        rollPersonality(ghast, level.getDifficulty());
        return false;
    }

    /** Maintient le duel et renvoie la balle lorsqu'elle arrive sur le ghast. */
    public static void tickFireball(LargeFireball fireball,
                                    ServerLevel level,
                                    long gameTime) {
        if (fireball == null || level == null || !fireball.isAlive()) return;
        CompoundTag ballData = fireball.getPersistentData();
        if (!fr.vanillainstincts.persistence.NbtCompat.getBoolean(ballData, TRACKED)
                || !fr.vanillainstincts.persistence.NbtCompat.hasUuid(ballData, SOURCE_GHAST)) {
            return;
        }

        Entity source = level.getEntity(fr.vanillainstincts.persistence.NbtCompat.getUuid(ballData, SOURCE_GHAST));
        if (!(source instanceof Ghast ghast) || !ghast.isAlive()) return;
        Entity owner = fireball.getOwner();
        if (!(owner instanceof Player player)
                || player.isCreative() || player.isSpectator()) {
            return;
        }

        int returns = fr.vanillainstincts.persistence.NbtCompat.getInt(ballData, RETURNS);
        int maximum = fr.vanillainstincts.persistence.NbtCompat.getInt(ballData, MAX_RETURNS);
        if (!canReturn(level.getDifficulty(), isVolleyPlayer(ghast),
                returns, maximum)) {
            return;
        }
        if (gameTime - fr.vanillainstincts.persistence.NbtCompat.getLong(ballData, LAST_RETURN_AT)
                < NetherRules.GHAST_VOLLEY_RETURN_GRACE_TICKS) {
            return;
        }

        Vec3 toGhast = ghast.getEyePosition().subtract(fireball.position());
        double distanceSqr = toGhast.lengthSqr();
        double trigger = returnTriggerRadius(level.getDifficulty(), returns);
        if (distanceSqr > trigger * trigger
                || !isApproaching(fireball.getDeltaMovement(), toGhast,
                NetherRules.GHAST_VOLLEY_MIN_APPROACH_DOT)) {
            return;
        }

        ghast.getLookControl().setLookAt(fireball, 40.0F, 40.0F);
        returnToPlayer(fireball, ghast, player, level, gameTime,
                returns + 1);
    }

    /** Aucun verrou de tir n'est conservé lors d'un impact. */
    public static void onFireballImpact(LargeFireball fireball,
                                        ServerLevel level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    /** Aucun verrou de tir n'est conservé lors d'un déchargement. */
    public static void onFireballLeave(LargeFireball fireball,
                                       ServerLevel level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    private static void returnToPlayer(LargeFireball fireball, Ghast ghast,
                                       Player player, ServerLevel level,
                                       long gameTime, int returnNumber) {
        double speed = returnSpeed(returnNumber);
        Vec3 destination = predictedTarget(player, fireball.position(), speed);
        Vec3 direction = destination.subtract(fireball.position());
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = player.getEyePosition().subtract(fireball.position());
        }

        double spread = aimSpread(level.getDifficulty(), returnNumber);
        if (spread > 0.0D) {
            direction = direction.add(
                    ghast.getRandom().nextGaussian() * spread,
                    ghast.getRandom().nextGaussian() * spread * 0.55D,
                    ghast.getRandom().nextGaussian() * spread);
        }

        fireball.setOwner(ghast);
        fireball.setDeltaMovement(direction.normalize().scale(speed));
        fireball.hasImpulse = true;

        CompoundTag ballData = fireball.getPersistentData();
        ballData.putInt(RETURNS, returnNumber);
        ballData.putLong(LAST_RETURN_AT, gameTime);
        fr.vanillainstincts.persistence.NbtCompat.putUuid(ballData, TARGET_PLAYER, player.getUUID());
        float pitch = (float) Math.min(1.65D, 0.82D + returnNumber * 0.11D);
        level.playSound(null, ghast.blockPosition(), SoundEvents.GHAST_WARN,
                SoundSource.HOSTILE, 1.35F, pitch);
        level.playSound(null, fireball.blockPosition(), SoundEvents.GHAST_SHOOT,
                SoundSource.HOSTILE, 1.0F, pitch);
    }

    private static Vec3 predictedTarget(Player player, Vec3 origin,
                                        double speed) {
        double distance = Math.sqrt(origin.distanceToSqr(player.getEyePosition()));
        double leadTicks = Math.max(2.0D, Math.min(14.0D,
                distance / Math.max(0.1D, speed)));
        return player.getEyePosition().add(
                player.getDeltaMovement().scale(leadTicks));
    }

    private static void rollPersonality(Ghast ghast, Difficulty difficulty) {
        CompoundTag data = ghast.getPersistentData();
        int difficultyId = difficulty.ordinal();
        if (fr.vanillainstincts.persistence.NbtCompat.getBoolean(data, PERSONALITY_ROLLED)
                && fr.vanillainstincts.persistence.NbtCompat.getInt(data, PERSONALITY_DIFFICULTY) == difficultyId) {
            return;
        }
        data.putBoolean(PERSONALITY_ROLLED, true);
        data.putInt(PERSONALITY_DIFFICULTY, difficultyId);
        data.putBoolean(VOLLEY_PLAYER,
                ghast.getRandom().nextDouble() < returnerChance(difficulty));
    }

    private static boolean isVolleyPlayer(Ghast ghast) {
        return fr.vanillainstincts.persistence.NbtCompat.getBoolean(ghast.getPersistentData(), VOLLEY_PLAYER);
    }

    public static double returnerChance(Difficulty difficulty) {
        if (difficulty == null) return 0.0D;
        return switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 0.25D;
            case NORMAL -> 0.55D;
            case HARD -> 0.85D;
        };
    }

    public static int maxReturns(Difficulty difficulty, int personalityRoll) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case PEACEFUL -> 0;
            case EASY -> 1;
            case NORMAL -> 2 + Math.floorMod(personalityRoll, 3);
            case HARD -> 4 + Math.floorMod(personalityRoll, 5);
        };
    }

    public static boolean canReturn(Difficulty difficulty, boolean capable,
                                    int completedReturns, int maximumReturns) {
        return difficulty != null && difficulty != Difficulty.PEACEFUL
                && capable && completedReturns >= 0
                && completedReturns < maximumReturns;
    }

    public static double returnSpeed(int returnNumber) {
        int rally = Math.max(1, returnNumber);
        return Math.min(NetherRules.GHAST_VOLLEY_MAX_SPEED,
                NetherRules.GHAST_VOLLEY_BASE_SPEED
                        + (rally - 1) * NetherRules.GHAST_VOLLEY_SPEED_STEP);
    }

    public static double returnTriggerRadius(Difficulty difficulty,
                                             int completedReturns) {
        double base = switch (difficulty == null
                ? Difficulty.PEACEFUL : difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 10.0D;
            case NORMAL -> 8.0D;
            case HARD -> 6.0D;
        };
        return Math.max(4.0D, base - Math.max(0, completedReturns) * 0.25D);
    }

    public static double aimSpread(Difficulty difficulty, int returnNumber) {
        if (difficulty == null || difficulty == Difficulty.PEACEFUL) {
            return 0.0D;
        }
        double base = switch (difficulty) {
            case PEACEFUL -> 0.0D;
            case EASY -> 0.32D;
            case NORMAL -> 0.16D;
            case HARD -> 0.07D;
        };
        return Math.max(0.02D, base - Math.max(0, returnNumber - 1) * 0.01D);
    }

    public static boolean isApproaching(Vec3 velocity, Vec3 toGhast,
                                        double minimumDot) {
        if (velocity == null || toGhast == null
                || velocity.lengthSqr() < 1.0E-8D
                || toGhast.lengthSqr() < 1.0E-8D) {
            return false;
        }
        return velocity.normalize().dot(toGhast.normalize()) >= minimumDot;
    }

    public static boolean allowsOnlyOneActiveFireball() {
        return false;
    }
}
