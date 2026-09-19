package fr.vanillainstincts.village;

import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

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

    public static void maintain(Villager villager, ServerLevel level) {
        maintain(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static boolean maintain(Villager villager, ServerLevel level,
                                   long currentDay) {
        if (villager == null || level == null) return false;
        if (!isEligibleMaster(villager)) {
            clear(villager);
            return false;
        }

        CompoundTag data = villager.getPersistentData();
        String profession = professionKey(villager);
        if (!fr.vanillainstincts.persistence.NbtCompat.contains(data, MASTER_PROFESSION)
                || !profession.equals(fr.vanillainstincts.persistence.NbtCompat.getString(data, MASTER_PROFESSION))) {
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

    public static void recordMasterTrade(Villager villager,
                                         ServerLevel level) {
        recordMasterTrade(villager, level, currentDay(level));
    }

    /** Deterministic overload used by validation and administrative tooling. */
    public static void recordMasterTrade(Villager villager,
                                         ServerLevel level,
                                         long currentDay) {
        if (villager == null || level == null || !isEligibleMaster(villager)) {
            return;
        }
        maintain(villager, level, currentDay);
        if (isGrandMaster(villager)) return;

        CompoundTag data = villager.getPersistentData();
        int next = Math.min(REQUIRED_MASTER_TRADES,
                masterTradeCount(villager) + 1);
        data.putInt(MASTER_TRADES, next);
        maintain(villager, level, currentDay);
    }

    public static boolean isGrandMaster(Villager villager) {
        return villager != null && GRAND_MASTER.equals(
                fr.vanillainstincts.persistence.NbtCompat.getString(villager.getPersistentData(), RANK));
    }

    public static int masterTradeCount(Villager villager) {
        if (villager == null) return 0;
        return Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(villager.getPersistentData(), MASTER_TRADES));
    }

    public static long masterSinceDay(Villager villager) {
        if (villager == null) return 0L;
        return fr.vanillainstincts.persistence.NbtCompat.getLong(villager.getPersistentData(), MASTER_SINCE_DAY);
    }

    public static long masteredDays(Villager villager, long currentDay) {
        if (villager == null) return 0L;
        return Math.max(0L, currentDay - masterSinceDay(villager));
    }

    /** Visible Master -> Grand Master progress, driven by Master trades. */
    public static float progress(Villager villager, long currentDay) {
        if (isGrandMaster(villager)) return 1.0F;
        return Math.max(0.0F, Math.min(1.0F, masterTradeCount(villager)
                / (float) REQUIRED_MASTER_TRADES));
    }

    public static boolean hasNetheriteMarker(Villager villager) {
        return villager != null
                && villager.hasEffect(VanillaInstinctsMobEffects.GRAND_MASTER);
    }

    /** Preserves professional progression through villager zombification. */
    public static void copyProgress(Entity source, Entity outcome) {
        if (source == null || outcome == null) return;
        CompoundTag from = source.getPersistentData();
        CompoundTag to = outcome.getPersistentData();
        copyString(from, to, RANK);
        copyInt(from, to, MASTER_TRADES);
        copyLong(from, to, MASTER_SINCE_DAY);
        copyString(from, to, MASTER_PROFESSION);
        copyLong(from, to, PROMOTED_AT);
    }

    private static boolean isEligibleMaster(Villager villager) {
        if (villager.isBaby() || villager.getVillagerData().level() < 5) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData().profession().value();
        return profession != VillagerProfessionCompat.value(VillagerProfession.NONE)
                && profession != VillagerProfessionCompat.value(VillagerProfession.NITWIT);
    }

    private static String professionKey(Villager villager) {
        var key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().profession().value());
        return key == null ? "minecraft:none" : key.toString();
    }

    private static void resetProgress(Villager villager, String profession,
                                      long currentDay) {
        CompoundTag data = villager.getPersistentData();
        data.remove(RANK);
        data.remove(PROMOTED_AT);
        data.putString(MASTER_PROFESSION, profession);
        data.putInt(MASTER_TRADES, 0);
        data.putLong(MASTER_SINCE_DAY, currentDay);
        removeMarker(villager);
    }

    private static void clear(Villager villager) {
        CompoundTag data = villager.getPersistentData();
        data.remove(RANK);
        data.remove(MASTER_TRADES);
        data.remove(MASTER_SINCE_DAY);
        data.remove(MASTER_PROFESSION);
        data.remove(PROMOTED_AT);
        removeMarker(villager);
    }

    private static void ensureMarker(Villager villager) {
        boolean newlyMarked = !villager.hasEffect(
                VanillaInstinctsMobEffects.GRAND_MASTER);
        ((GrandMasterSyncedData) villager)
                .vanillainstincts$setGrandMasterSynced(true);
        if (newlyMarked) {
            villager.addEffect(new MobEffectInstance(
                    VanillaInstinctsMobEffects.GRAND_MASTER,
                    MobEffectInstance.INFINITE_DURATION, 0,
                    true, false, false));
        }
        // NeoForge does not allow defining a new SynchedEntityData accessor on
        // vanilla villagers. The hidden effect is still authoritative, while a
        // lightweight vanilla entity event is repeated as a client-rendering
        // fallback so Grand Masters never keep the diamond badge.
        if (newlyMarked || Math.floorMod(villager.tickCount + villager.getId(),
                20) == 0) {
            broadcastGrandMasterMarker(villager, true);
        }
    }

    private static void removeMarker(Villager villager) {
        boolean wasMarked = villager.hasEffect(
                VanillaInstinctsMobEffects.GRAND_MASTER)
                || ((GrandMasterSyncedData) villager)
                .vanillainstincts$isGrandMasterSynced();
        ((GrandMasterSyncedData) villager)
                .vanillainstincts$setGrandMasterSynced(false);
        if (villager.hasEffect(VanillaInstinctsMobEffects.GRAND_MASTER)) {
            villager.removeEffect(VanillaInstinctsMobEffects.GRAND_MASTER);
        }
        if (wasMarked) {
            broadcastGrandMasterMarker(villager, false);
        }
    }

    private static void broadcastGrandMasterMarker(Villager villager,
                                                    boolean grandMaster) {
        if (villager.level() instanceof ServerLevel serverLevel) {
            serverLevel.broadcastEntityEvent(villager, grandMaster
                    ? GrandMasterSyncedData.GRAND_MASTER_ON_EVENT
                    : GrandMasterSyncedData.GRAND_MASTER_OFF_EVENT);
        }
    }

    private static void celebrate(Villager villager, ServerLevel level) {
        level.playSound(null, villager.blockPosition(),
                SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL,
                1.0F, 0.85F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                villager.getX(), villager.getY() + 1.5D, villager.getZ(),
                24, 0.55D, 0.65D, 0.55D, 0.03D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                villager.getX(), villager.getY() + 1.25D, villager.getZ(),
                8, 0.35D, 0.45D, 0.35D, 0.01D);
    }

    private static void copyString(CompoundTag from, CompoundTag to,
                                   String key) {
        if (fr.vanillainstincts.persistence.NbtCompat.contains(from, key)) to.putString(key, fr.vanillainstincts.persistence.NbtCompat.getString(from, key));
    }

    private static void copyInt(CompoundTag from, CompoundTag to,
                                String key) {
        if (fr.vanillainstincts.persistence.NbtCompat.contains(from, key)) to.putInt(key, fr.vanillainstincts.persistence.NbtCompat.getInt(from, key));
    }

    private static void copyLong(CompoundTag from, CompoundTag to,
                                 String key) {
        if (fr.vanillainstincts.persistence.NbtCompat.contains(from, key)) to.putLong(key, fr.vanillainstincts.persistence.NbtCompat.getLong(from, key));
    }

    private static long currentDay(ServerLevel level) {
        return Math.floorDiv(level.getDayTime(), 24_000L);
    }
}
