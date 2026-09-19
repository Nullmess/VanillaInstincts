package fr.vanillainstincts.ai;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.core.rules.NetherRules;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import fr.vanillainstincts.compat.Vec3;
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
    public static boolean onFireballJoin(EntityFireball fireball,
                                         WorldServer level,
                                         boolean loadedFromDisk) {
        if (fireball == null || level == null) return false;
        NBTTagCompound ballData = fireball.getEntityData();
        if (ballData.getBoolean(TRACKED)) return false;

        EntityGhast ghast = nearestSourceGhast(fireball, level);
        if (ghast == null) return false;

        ballData.setBoolean(TRACKED, true);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(ballData, SOURCE_GHAST, ghast.getUniqueID());
        if (ghast.getAttackTarget() instanceof EntityPlayer) { EntityPlayer player = (EntityPlayer) (ghast.getAttackTarget()); 
            fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(ballData, TARGET_PLAYER, player.getUniqueID());
        }
        ballData.setInteger(RETURNS, 0);
        ballData.setInteger(MAX_RETURNS, maxReturns(level.difficultySetting,fr.vanillainstincts.compat.Minecraft112Compat.random(ghast).nextInt()));
        rollPersonality(ghast, level.difficultySetting);
        return false;
    }

    /** Maintient le duel et renvoie la balle lorsqu'elle arrive sur le ghast. */
    public static void tickFireball(EntityFireball fireball,
                                    WorldServer level,
                                    long gameTime) {
        if (fireball == null || level == null || !fireball.isEntityAlive()) return;
        NBTTagCompound ballData = fireball.getEntityData();
        if (!ballData.getBoolean(TRACKED)
                || !fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(ballData, SOURCE_GHAST)) {
            return;
        }

        Entity source = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(ballData, SOURCE_GHAST));
        if (!(source instanceof EntityGhast) || !((EntityGhast) (source)).isEntityAlive()) return; EntityGhast ghast = (EntityGhast) (source);
        EntityPlayer player = null;
        if (fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(ballData, TARGET_PLAYER)) {
            Entity target = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(ballData, TARGET_PLAYER));
            if (target instanceof EntityPlayer) {
                player = (EntityPlayer) target;
            }
        }
        if (player == null && ghast.getAttackTarget() instanceof EntityPlayer) {
            player = (EntityPlayer) ghast.getAttackTarget();
        }
        if (player == null || !player.isEntityAlive() || player.capabilities.isCreativeMode
                || fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)) {
            return;
        }

        int returns = ballData.getInteger(RETURNS);
        int maximum = ballData.getInteger(MAX_RETURNS);
        if (!canReturn(level.difficultySetting, isVolleyPlayer(ghast),
                returns, maximum)) {
            return;
        }
        if (gameTime - ballData.getLong(LAST_RETURN_AT)
                < NetherRules.GHAST_VOLLEY_RETURN_GRACE_TICKS) {
            return;
        }

        Vec3 toGhast = fr.vanillainstincts.compat.Minecraft17Compat.eyes(ghast, 1.0F).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(fireball));
        double distanceSqr = fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(toGhast);
        double trigger = returnTriggerRadius(level.difficultySetting, returns);
        if (distanceSqr > trigger * trigger
                || !isApproaching(fr.vanillainstincts.compat.Minecraft112Compat.motion(fireball), toGhast,
                NetherRules.GHAST_VOLLEY_MIN_APPROACH_DOT)) {
            return;
        }

        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(ghast, fireball, 40.0F, 40.0F);
        returnToPlayer(fireball, ghast, player, level, gameTime,
                returns + 1);
    }

    /** Aucun verrou de tir n'est conservé lors d'un impact. */
    public static void onFireballImpact(EntityFireball fireball,
                                        WorldServer level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    /** Aucun verrou de tir n'est conservé lors d'un déchargement. */
    public static void onFireballLeave(EntityFireball fireball,
                                       WorldServer level) {
        // Chaque projectile est indépendant ; rien à libérer.
    }

    private static void returnToPlayer(EntityFireball fireball, EntityGhast ghast,
                                       EntityPlayer player, WorldServer level,
                                       long gameTime, int returnNumber) {
        double speed = returnSpeed(returnNumber);
        Vec3 destination = predictedTarget(player, fr.vanillainstincts.compat.Minecraft17Compat.position(fireball), speed);
        Vec3 direction = destination.subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(fireball));
        if (fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(direction) < 1.0E-6D) {
            direction = fr.vanillainstincts.compat.Minecraft17Compat.eyes(player, 1.0F).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(fireball));
        }

        double spread = aimSpread(level.difficultySetting, returnNumber);
        if (spread > 0.0D) {
            direction =fr.vanillainstincts.compat.Minecraft112Compat.add(direction, fr.vanillainstincts.compat.Minecraft112Compat.random(ghast).nextGaussian() * spread,fr.vanillainstincts.compat.Minecraft112Compat.random(ghast).nextGaussian() * spread * 0.55D,fr.vanillainstincts.compat.Minecraft112Compat.random(ghast).nextGaussian() * spread);
        }

        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(fireball, fr.vanillainstincts.compat.Minecraft112Compat.scale(direction.normalize(), speed));
        fireball.velocityChanged = true;

        NBTTagCompound ballData = fireball.getEntityData();
        ballData.setInteger(RETURNS, returnNumber);
        ballData.setLong(LAST_RETURN_AT, gameTime);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(ballData, TARGET_PLAYER, player.getUniqueID());
        float pitch = (float) Math.min(1.65D, 0.82D + returnNumber * 0.11D);
        fr.vanillainstincts.compat.Minecraft18SoundCompat.play(level, entityBlockPos(ghast), "mob.ghast.charge", 1.35F, pitch);
        fr.vanillainstincts.compat.Minecraft18SoundCompat.play(level, entityBlockPos(fireball), "mob.ghast.fireball", 1.0F, pitch);
    }

    private static EntityGhast nearestSourceGhast(EntityFireball fireball,
                                                   WorldServer level) {
        EntityGhast nearest = null;
        double bestDistance = 16.0D * 16.0D;
        for (EntityGhast candidate : fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, 
                EntityGhast.class, fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(fireball).expand(16.0D, 16.0D, 16.0D),
                EntityGhast::isEntityAlive)) {
            if (!(candidate.getAttackTarget() instanceof EntityPlayer)) continue;
            double distance = fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(fr.vanillainstincts.compat.Minecraft17Compat.position(candidate), fr.vanillainstincts.compat.Minecraft17Compat.position(fireball));
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static Vec3 predictedTarget(EntityPlayer player, Vec3 origin,
                                        double speed) {
        double distance = Math.sqrt(fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(origin, fr.vanillainstincts.compat.Minecraft17Compat.eyes(player, 1.0F)));
        double leadTicks = Math.max(2.0D, Math.min(14.0D,
                distance / Math.max(0.1D, speed)));
        return fr.vanillainstincts.compat.Minecraft17Compat.eyes(player, 1.0F).add(
                fr.vanillainstincts.compat.Minecraft112Compat.scale(fr.vanillainstincts.compat.Minecraft112Compat.motion(player), leadTicks));
    }

    private static void rollPersonality(EntityGhast ghast, EnumDifficulty difficulty) {
        NBTTagCompound data = ghast.getEntityData();
        int difficultyId = difficulty.ordinal();
        if (data.getBoolean(PERSONALITY_ROLLED)
                && data.getInteger(PERSONALITY_DIFFICULTY) == difficultyId) {
            return;
        }
        data.setBoolean(PERSONALITY_ROLLED, true);
        data.setInteger(PERSONALITY_DIFFICULTY, difficultyId);
        data.setBoolean(VOLLEY_PLAYER,fr.vanillainstincts.compat.Minecraft112Compat.random(ghast).nextDouble() < returnerChance(difficulty));
    }

    private static boolean isVolleyPlayer(EntityGhast ghast) {
        return ghast.getEntityData().getBoolean(VOLLEY_PLAYER);
    }

    public static double returnerChance(EnumDifficulty difficulty) {
        if (difficulty == null) return 0.0D;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 0.25D; case NORMAL:  return 0.55D; case HARD:  return 0.85D;  default: throw new AssertionError("Unexpected switch value"); } });
    }

    public static int maxReturns(EnumDifficulty difficulty, int personalityRoll) {
        if (difficulty == null) return 0;
        return fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0; case EASY:  return 1; case NORMAL:  return 2 + Math.floorMod(personalityRoll, 3); case HARD:  return 4 + Math.floorMod(personalityRoll, 5);  default: throw new AssertionError("Unexpected switch value"); } });
    }

    public static boolean canReturn(EnumDifficulty difficulty, boolean capable,
                                    int completedReturns, int maximumReturns) {
        return difficulty != null && difficulty != EnumDifficulty.PEACEFUL
                && capable && completedReturns >= 0
                && completedReturns < maximumReturns;
    }

    public static double returnSpeed(int returnNumber) {
        int rally = Math.max(1, returnNumber);
        return Math.min(NetherRules.GHAST_VOLLEY_MAX_SPEED,
                NetherRules.GHAST_VOLLEY_BASE_SPEED
                        + (rally - 1) * NetherRules.GHAST_VOLLEY_SPEED_STEP);
    }

    public static double returnTriggerRadius(EnumDifficulty difficulty,
                                             int completedReturns) {
        double base = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty == null
                ? EnumDifficulty.PEACEFUL : difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 10.0D; case NORMAL:  return 8.0D; case HARD:  return 6.0D;  default: throw new AssertionError("Unexpected switch value"); } });
        return Math.max(4.0D, base - Math.max(0, completedReturns) * 0.25D);
    }

    public static double aimSpread(EnumDifficulty difficulty, int returnNumber) {
        if (difficulty == null || difficulty == EnumDifficulty.PEACEFUL) {
            return 0.0D;
        }
        double base = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((difficulty)) { case PEACEFUL:  return 0.0D; case EASY:  return 0.32D; case NORMAL:  return 0.16D; case HARD:  return 0.07D;  default: throw new AssertionError("Unexpected switch value"); } });
        return Math.max(0.02D, base - Math.max(0, returnNumber - 1) * 0.01D);
    }

    public static boolean isApproaching(Vec3 velocity, Vec3 toGhast,
                                        double minimumDot) {
        if (velocity == null || toGhast == null
                || fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(velocity) < 1.0E-8D
                || fr.vanillainstincts.compat.Minecraft112Compat.lengthSq(toGhast) < 1.0E-8D) {
            return false;
        }
        return fr.vanillainstincts.compat.Minecraft112Compat.dot(velocity.normalize(), toGhast.normalize()) >= minimumDot;
    }

    public static boolean allowsOnlyOneActiveFireball() {
        return false;
    }
}
