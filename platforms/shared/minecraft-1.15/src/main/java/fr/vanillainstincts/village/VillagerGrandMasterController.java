package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import net.minecraft.util.registry.Registry;
import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.potion.EffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;

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

    public static void maintain(VillagerEntity villager, ServerWorld level) {
        maintain(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static boolean maintain(VillagerEntity villager, ServerWorld level,
                                   long currentDay) {
        if (villager == null || level == null) return false;
        if (!isEligibleMaster(villager)) {
            clear(villager);
            return false;
        }

        CompoundNBT data = villager.getPersistentData();
        String profession = professionKey(villager);
        if (!data.contains(MASTER_PROFESSION)
                || !profession.equals(data.getString(MASTER_PROFESSION))) {
            resetProgress(villager, profession, currentDay);
            VillagerGrandMasterProgressDisplay.update(villager, level,
                    currentDay);
            return false;
        }

        if (isGrandMaster(villager)) {
            ensureMarker(villager);
            VillagerGrandMasterProgressDisplay.update(villager, level,
                    currentDay);
            return false;
        }

        if (masterTradeCount(villager) < REQUIRED_MASTER_TRADES) {
            removeMarker(villager);
            VillagerGrandMasterProgressDisplay.update(villager, level,
                    currentDay);
            return false;
        }

        data.putString(RANK, GRAND_MASTER);
        data.putLong(PROMOTED_AT, level.getGameTime());
        ensureMarker(villager);
        VillagerGrandMasterProgressDisplay.update(villager, level,
                currentDay);
        celebrate(villager, level);
        return true;
    }

    public static void recordMasterTrade(VillagerEntity villager,
                                         ServerWorld level) {
        recordMasterTrade(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static void recordMasterTrade(VillagerEntity villager,
                                         ServerWorld level,
                                         long currentDay) {
        if (villager == null || level == null || !isEligibleMaster(villager)) {
            return;
        }
        maintain(villager, level, currentDay);
        if (isGrandMaster(villager)) return;

        CompoundNBT data = villager.getPersistentData();
        int next = Math.min(REQUIRED_MASTER_TRADES,
                masterTradeCount(villager) + 1);
        data.putInt(MASTER_TRADES, next);
        maintain(villager, level, currentDay);
    }

    public static boolean isGrandMaster(VillagerEntity villager) {
        return villager != null && GRAND_MASTER.equals(
                villager.getPersistentData().getString(RANK));
    }

    public static int masterTradeCount(VillagerEntity villager) {
        if (villager == null) return 0;
        return Math.max(0, villager.getPersistentData().getInt(MASTER_TRADES));
    }

    public static long masterSinceDay(VillagerEntity villager) {
        if (villager == null) return 0L;
        return villager.getPersistentData().getLong(MASTER_SINCE_DAY);
    }

    public static long masteredDays(VillagerEntity villager, long currentDay) {
        if (villager == null) return 0L;
        return Math.max(0L, currentDay - masterSinceDay(villager));
    }

    /** Visible Master -> Grand Master progress, driven by Master trades. */
    public static float progress(VillagerEntity villager, long currentDay) {
        if (isGrandMaster(villager)) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, masterTradeCount(villager)
                / (float) REQUIRED_MASTER_TRADES));
    }

    public static boolean hasNetheriteMarker(VillagerEntity villager) {
        return villager != null
                && villager.hasEffect(VanillaInstinctsMobEffects.grandMasterEffect());
    }

    /** Preserves professional progression through villager zombification. */
    public static void copyProgress(Entity source, Entity outcome) {
        if (source == null || outcome == null) return;
        CompoundNBT from = source.getPersistentData();
        CompoundNBT to = outcome.getPersistentData();
        copyString(from, to, RANK);
        copyInt(from, to, MASTER_TRADES);
        copyLong(from, to, MASTER_SINCE_DAY);
        copyString(from, to, MASTER_PROFESSION);
        copyLong(from, to, PROMOTED_AT);
    }

    private static boolean isEligibleMaster(VillagerEntity villager) {
        if (villager.isBaby() || villager.getVillagerData().getLevel() < 5) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        return profession != VillagerProfession.NONE
                && profession != VillagerProfession.NITWIT;
    }

    private static String professionKey(VillagerEntity villager) {
        net.minecraft.util.ResourceLocation key = Registry.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        return key == null ? "minecraft:none" : key.toString();
    }

    private static void resetProgress(VillagerEntity villager, String profession,
                                      long currentDay) {
        CompoundNBT data = villager.getPersistentData();
        data.remove(RANK);
        data.remove(PROMOTED_AT);
        data.putString(MASTER_PROFESSION, profession);
        data.putInt(MASTER_TRADES, 0);
        data.putLong(MASTER_SINCE_DAY, currentDay);
        removeMarker(villager);
    }

    private static void clear(VillagerEntity villager) {
        CompoundNBT data = villager.getPersistentData();
        data.remove(RANK);
        data.remove(MASTER_TRADES);
        data.remove(MASTER_SINCE_DAY);
        data.remove(MASTER_PROFESSION);
        data.remove(PROMOTED_AT);
        removeMarker(villager);
    }

    private static void ensureMarker(VillagerEntity villager) {
        ((GrandMasterSyncedData) villager)
                .vanillainstincts$setGrandMasterSynced(true);
        if (!villager.hasEffect(VanillaInstinctsMobEffects.grandMasterEffect())) {
            villager.addEffect(new EffectInstance(
                    VanillaInstinctsMobEffects.grandMasterEffect(),
                    Integer.MAX_VALUE, 0,
                    true, false, false));
        }
    }

    private static void removeMarker(VillagerEntity villager) {
        ((GrandMasterSyncedData) villager)
                .vanillainstincts$setGrandMasterSynced(false);
        if (villager.hasEffect(VanillaInstinctsMobEffects.grandMasterEffect())) {
            villager.removeEffect(VanillaInstinctsMobEffects.grandMasterEffect());
        }
    }

    private static void celebrate(VillagerEntity villager, ServerWorld level) {
        level.playSound(null, entityBlockPos(villager),
                SoundEvents.VILLAGER_CELEBRATE, SoundCategory.NEUTRAL,
                1.0F, 0.85F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                villager.getX(), villager.getY() + 1.5D, villager.getZ(),
                24, 0.55D, 0.65D, 0.55D, 0.03D);
        level.sendParticles(ParticleTypes.FLAME,
                villager.getX(), villager.getY() + 1.25D, villager.getZ(),
                8, 0.35D, 0.45D, 0.35D, 0.01D);
    }

    private static void copyString(CompoundNBT from, CompoundNBT to,
                                   String key) {
        if (from.contains(key)) to.putString(key, from.getString(key));
    }

    private static void copyInt(CompoundNBT from, CompoundNBT to,
                                String key) {
        if (from.contains(key)) to.putInt(key, from.getInt(key));
    }

    private static void copyLong(CompoundNBT from, CompoundNBT to,
                                 String key) {
        if (from.contains(key)) to.putLong(key, from.getLong(key));
    }

    private static long currentDay(ServerWorld level) {
        return Math.floorDiv(level.getDayTime(), 24_000L);
    }
}
