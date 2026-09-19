package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.rules.NetherRules;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.Difficulty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.math.vector.Vector3d;
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
    public static boolean onFireballJoin(FireballEntity fireball,
                                         ServerWorld level,
                                         boolean loadedFromDisk) {
        if (fireball == null || level == null) return false;
        CompoundNBT ballData = fireball.getPersistentData();
        if (ballData.getBoolean(TRACKED)) return false;

        Entity owner = fireball.getOwner();
        if (!(owner instanceof GhastEntity)) return false; GhastEntity ghast = (GhastEntity) (owner);

        ballData.putBoolean(TRACKED, true);
        ballData.putUUID(SOURCE_GHAST, ghast.getUUID());
        if (ghast.getTarget() instanceof PlayerEntity) { PlayerEntity player = (PlayerEntity) (ghast.getTarget()); 
            ballData.putUUID(TARGET_PLAYER, player.getUUID());
        }
        ballData.putInt(RETURNS, 0);
        ballData.putInt(MAX_RETURNS, maxReturns(level.getDifficulty(),
                ghast.getRandom().nextInt()));
        rollPersonality(ghast, level.getDifficulty());
        return false;
    }

    /** Maintient le duel et renvoie la balle lorsqu'elle arrive sur le ghast. */
    public static void tickFireball(FireballEntity fireball,
                                    ServerWorld level,
                                    long gameTime) {
        if (fireball == null || level == null || !fireball.isAlive()) return;
        CompoundNBT ballData = fireball.getPersistentData();
        if (!ballData.getBoolean(TRACKED)
                || !ballData.hasUUID(SOURCE_GHAST)) {
            return;
        }

        Entity source = level.getEntity(ballData.getUUID(SOURCE_GHAST));
        if (!(source instanceof GhastEntity) || !((GhastEntity) (source)).isAlive()) return; GhastEntity ghast = (GhastEntity) (source);
        Entity owner = fireball.getOwner();
        if (!(owner instanceof PlayerEntity)
                || ((PlayerEntity) (owner)).isCreative() || ((PlayerEntity) (owner)).isSpectator()) {
            return;
        } PlayerEntity player = (PlayerEntity) (owner);

        int returns = ballData.getInt(RETURNS);
        int maximum = ballData.getInt(MAX_RETURNS);
        if (!canReturn(level.getDifficulty(), isVolleyPlayer(ghast),
                returns, maximum)) {
            return;
        }
        if (gameTime - ballData.getLong(LAST_RETURN_AT)
                < NetherRules.GHAST_VOLLEY_RETURN_GRACE_TICKS) {
            return;
        }

        Vector3d toGhast = ghast.getEyePosition(1.0F).subtract(fireball.position());
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
    public static void onFireballImpact(FireballEntity fireball,
                                        ServerWorld level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    /** Aucun verrou de tir n'est conservé lors d'un déchargement. */
    public static void onFireballLeave(FireballEntity fireball,
                                       ServerWorld level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    private static void returnToPlayer(FireballEntity fireball, GhastEntity ghast,
                                       PlayerEntity player, ServerWorld level,
                                       long gameTime, int returnNumber) {
        double speed = returnSpeed(returnNumber);
        Vector3d destination = predictedTarget(player, fireball.position(), speed);
        Vector3d direction = destination.subtract(fireball.position());
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = player.getEyePosition(1.0F).subtract(fireball.position());
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

        CompoundNBT ballData = fireball.getPersistentData();
        ballData.putInt(RETURNS, returnNumber);
        ballData.putLong(LAST_RETURN_AT, gameTime);
        ballData.putUUID(TARGET_PLAYER, player.getUUID());
        float pitch = (float) Math.min(1.65D, 0.82D + returnNumber * 0.11D);
        level.playSound(null, ghast.blockPosition(), SoundEvents.GHAST_WARN,
                SoundCategory.HOSTILE, 1.35F, pitch);
        level.playSound(null, fireball.blockPosition(), SoundEvents.GHAST_SHOOT,
                SoundCategory.HOSTILE, 1.0F, pitch);
    }

    private static Vector3d predictedTarget(PlayerEntity player, Vector3d origin,
                                        double speed) {
        double distance = Math.sqrt(origin.distanceToSqr(player.getEyePosition(1.0F)));
        double leadTicks = Math.max(2.0D, Math.min(14.0D,
                distance / Math.max(0.1D, speed)));
        return player.getEyePosition(1.0F).add(
                player.getDeltaMovement().scale(leadTicks));
    }

    private static void rollPersonality(GhastEntity ghast, Difficulty difficulty) {
        CompoundNBT data = ghast.getPersistentData();
        int difficultyId = difficulty.ordinal();
        if (data.getBoolean(PERSONALITY_ROLLED)
                && data.getInt(PERSONALITY_DIFFICULTY) == difficultyId) {
            return;
        }
        data.putBoolean(PERSONALITY_ROLLED, true);
        data.putInt(PERSONALITY_DIFFICULTY, difficultyId);
        data.putBoolean(VOLLEY_PLAYER,
                ghast.getRandom().nextDouble() < returnerChance(difficulty));
    }

    private static boolean isVolleyPlayer(GhastEntity ghast) {
        return ghast.getPersistentData().getBoolean(VOLLEY_PLAYER);
    }

    public static double returnerChance(Difficulty difficulty) {
        if (difficulty == null) return 0.0D;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 0.25D; case NORMAL:  return 0.55D; case HARD:  return 0.85D;  default: throw new AssertionError("Unexpected switch value"); } });
    }

    public static int maxReturns(Difficulty difficulty, int personalityRoll) {
        if (difficulty == null) return 0;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0; case EASY:  return 1; case NORMAL:  return 2 + Math.floorMod(personalityRoll, 3); case HARD:  return 4 + Math.floorMod(personalityRoll, 5);  default: throw new AssertionError("Unexpected switch value"); } });
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
        double base = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty == null
                ? Difficulty.PEACEFUL : difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 10.0D; case NORMAL:  return 8.0D; case HARD:  return 6.0D;  default: throw new AssertionError("Unexpected switch value"); } });
        return Math.max(4.0D, base - Math.max(0, completedReturns) * 0.25D);
    }

    public static double aimSpread(Difficulty difficulty, int returnNumber) {
        if (difficulty == null || difficulty == Difficulty.PEACEFUL) {
            return 0.0D;
        }
        double base = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 0.32D; case NORMAL:  return 0.16D; case HARD:  return 0.07D;  default: throw new AssertionError("Unexpected switch value"); } });
        return Math.max(0.02D, base - Math.max(0, returnNumber - 1) * 0.01D);
    }

    public static boolean isApproaching(Vector3d velocity, Vector3d toGhast,
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
