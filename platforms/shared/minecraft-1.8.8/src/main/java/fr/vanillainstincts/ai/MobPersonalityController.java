package fr.vanillainstincts.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.decision.MobPersonality;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.entity.monster.EntityEnderman;
/** Attribution déterministe des quatre profils de transport Enderman. */
public final class MobPersonalityController {
    private static final String ROOT_KEY = VanillaInstincts.MOD_ID
            + "_enderman_personality";
    private static final long SALT = 0x504552534F4E4131L;

    private MobPersonalityController() {
    }

    public static MobPersonality profileFor(EntityEnderman enderman) {
        if (enderman == null) return MobPersonality.GENERIC;
        NBTTagCompound persistent = enderman.getEntityData();
        if (persistent.hasKey(ROOT_KEY)) {
            MobPersonality stored = read(persistent.getString(ROOT_KEY));
            if (stored != MobPersonality.GENERIC) return stored;
        }
        int roll = stableBucket(enderman, 100, 0L);
        MobPersonality assigned = roll < 32
                ? MobPersonality.ENDERMAN_CARRIER
                : roll < 59 ? MobPersonality.ENDERMAN_COURIER
                : roll < 78 ? MobPersonality.ENDERMAN_RESCUER
                : MobPersonality.ENDERMAN_TRICKSTER;
        persistent.setString(ROOT_KEY, assigned.name());
        return assigned;
    }

    public static int stableBucket(EntityEnderman enderman, int bound) {
        return stableBucket(enderman, bound, 0L);
    }

    public static int stableBucket(EntityEnderman enderman, int bound,
                                   long channelSalt) {
        if (enderman == null || bound <= 1) return 0;
        long mixed = enderman.getUniqueID().getMostSignificantBits()
                ^ Long.rotateLeft(
                enderman.getUniqueID().getLeastSignificantBits(), 23)
                ^ SALT ^ channelSalt;
        return (int) Long.remainderUnsigned(mix64(mixed), bound);
    }

    private static MobPersonality read(String name) {
        try {
            return MobPersonality.valueOf(name);
        } catch (RuntimeException ignored) {
            return MobPersonality.GENERIC;
        }
    }

    private static long mix64(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }
}
