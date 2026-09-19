package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.LegacyRegistry;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;

/** Persistent sixth villager rank layered above vanilla master level five. */
public final class VillagerGrandMasterController {
    public static final int REQUIRED_MASTER_TRADES = 24;

    private static final String RANK =
            "vanillainstincts_profession_rank";
    private static final String GRAND_MASTER = "grand_master";
    private static final String MASTER_TRADES =
            "vanillainstincts_master_trade_count";
    private static final String MASTER_SINCE_DAY =
            "vanillainstincts_master_since_day";
    private static final String MASTER_PROFESSION =
            "vanillainstincts_master_profession";
    private static final String PROMOTED_AT =
            "vanillainstincts_grand_master_promoted_at";

    private VillagerGrandMasterController() {
    }

    public static void maintain(EntityVillager villager, WorldServer level) {
        maintain(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static boolean maintain(EntityVillager villager, WorldServer level,
                                   long currentDay) {
        if (villager == null || level == null) return false;
        if (!isEligibleMaster(villager)) {
            clear(villager);
            return false;
        }

        NBTTagCompound data = villager.getEntityData();
        String profession = professionKey(villager);
        if (!data.hasKey(MASTER_PROFESSION)
                || !profession.equals(data.getString(MASTER_PROFESSION))) {
            resetProgress(villager, profession, currentDay);
            return false;
        }

        if (isGrandMaster(villager)) {
            ensureMarker(villager);
            return false;
        }

        if (masterTradeCount(villager) < REQUIRED_MASTER_TRADES) {
            removeMarker(villager);
            return false;
        }

        data.setString(RANK, GRAND_MASTER);
        data.setLong(PROMOTED_AT, level.getTotalWorldTime());
        ensureMarker(villager);
        celebrate(villager, level);
        return true;
    }

    public static void recordMasterTrade(EntityVillager villager,
                                         WorldServer level) {
        recordMasterTrade(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static void recordMasterTrade(EntityVillager villager,
                                         WorldServer level,
                                         long currentDay) {
        if (villager == null || level == null || !isEligibleMaster(villager)) {
            return;
        }
        maintain(villager, level, currentDay);
        if (isGrandMaster(villager)) return;

        NBTTagCompound data = villager.getEntityData();
        int next = Math.min(REQUIRED_MASTER_TRADES,
                masterTradeCount(villager) + 1);
        data.setInteger(MASTER_TRADES, next);
        maintain(villager, level, currentDay);
    }

    public static boolean isGrandMaster(EntityVillager villager) {
        return villager != null && GRAND_MASTER.equals(
                villager.getEntityData().getString(RANK));
    }

    public static int masterTradeCount(EntityVillager villager) {
        if (villager == null) return 0;
        return Math.max(0, villager.getEntityData().getInteger(MASTER_TRADES));
    }

    public static long masterSinceDay(EntityVillager villager) {
        if (villager == null) return 0L;
        return villager.getEntityData().getLong(MASTER_SINCE_DAY);
    }

    public static long masteredDays(EntityVillager villager, long currentDay) {
        if (villager == null) return 0L;
        return Math.max(0L, currentDay - masterSinceDay(villager));
    }

    /** Visible Master -> Grand Master progress, driven by Master trades. */
    public static float progress(EntityVillager villager, long currentDay) {
        if (isGrandMaster(villager)) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, masterTradeCount(villager)
                / (float) REQUIRED_MASTER_TRADES));
    }

    public static boolean hasNetheriteMarker(EntityVillager villager) {
        // 1.12 has no dedicated synchronized Grand Master effect layer yet.
        // The authoritative server-side marker is the persistent rank NBT.
        return isGrandMaster(villager);
    }

    /** Preserves professional progression through villager zombification. */
    public static void copyProgress(Entity source, Entity outcome) {
        if (source == null || outcome == null) return;
        NBTTagCompound from = source.getEntityData();
        NBTTagCompound to = outcome.getEntityData();
        copyString(from, to, RANK);
        copyInt(from, to, MASTER_TRADES);
        copyLong(from, to, MASTER_SINCE_DAY);
        copyString(from, to, MASTER_PROFESSION);
        copyLong(from, to, PROMOTED_AT);
    }

    private static boolean isEligibleMaster(EntityVillager villager) {
        if (villager.isChild() || LegacyVillagerProfession.level(villager) < 5) {
            return false;
        }
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        return profession != LegacyVillagerProfession.NONE
                && profession != LegacyVillagerProfession.NITWIT;
    }

    private static String professionKey(EntityVillager villager) {
        net.minecraft.util.ResourceLocation key = LegacyRegistry.VILLAGER_PROFESSION.getKey(
                LegacyVillagerProfession.of(villager));
        return key == null ? "minecraft:none" : key.toString();
    }

    private static void resetProgress(EntityVillager villager, String profession,
                                      long currentDay) {
        NBTTagCompound data = villager.getEntityData();
        data.removeTag(RANK);
        data.removeTag(PROMOTED_AT);
        data.setString(MASTER_PROFESSION, profession);
        data.setInteger(MASTER_TRADES, 0);
        data.setLong(MASTER_SINCE_DAY, currentDay);
        removeMarker(villager);
    }

    private static void clear(EntityVillager villager) {
        NBTTagCompound data = villager.getEntityData();
        data.removeTag(RANK);
        data.removeTag(MASTER_TRADES);
        data.removeTag(MASTER_SINCE_DAY);
        data.removeTag(MASTER_PROFESSION);
        data.removeTag(PROMOTED_AT);
        removeMarker(villager);
    }

    private static void ensureMarker(EntityVillager villager) {
        // Server-side 1.12 compatibility: rank NBT is authoritative.
    }

    private static void removeMarker(EntityVillager villager) {
        // No client marker is registered during the server-first 1.12 port.
    }

    private static void celebrate(EntityVillager villager, WorldServer level) {
        level.playSound(null, entityBlockPos(villager),
                SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL,
                1.0F, 0.85F + level.rand.nextFloat() * 0.2F);
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                villager.posX, villager.posY + 1.5D, villager.posZ,
                24, 0.55D, 0.65D, 0.55D, 0.03D);
        level.spawnParticle(EnumParticleTypes.FLAME,
                villager.posX, villager.posY + 1.25D, villager.posZ,
                8, 0.35D, 0.45D, 0.35D, 0.01D);
    }

    private static void copyString(NBTTagCompound from, NBTTagCompound to,
                                   String key) {
        if (from.hasKey(key)) to.setString(key, from.getString(key));
    }

    private static void copyInt(NBTTagCompound from, NBTTagCompound to,
                                String key) {
        if (from.hasKey(key)) to.setInteger(key, from.getInteger(key));
    }

    private static void copyLong(NBTTagCompound from, NBTTagCompound to,
                                 String key) {
        if (from.hasKey(key)) to.setLong(key, from.getLong(key));
    }

    private static long currentDay(WorldServer level) {
        return Math.floorDiv(level.getWorldTime(), 24_000L);
    }
}
