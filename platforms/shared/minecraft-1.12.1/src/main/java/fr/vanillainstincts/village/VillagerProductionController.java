package fr.vanillainstincts.village;



import static fr.vanillainstincts.compat.Minecraft115PositionCompat.immutableBlockPos;
import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.config.FeatureFlag;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.economy.ProductionBalancePolicy;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.ProfessionRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.math.Vec3d;

/** Material-conserving production for every generic villager profession. */
public final class VillagerProductionController {
    private static final String READY_AT =
            "vanillainstincts_production_ready_at";
    private static final String SEQUENCE =
            "vanillainstincts_production_sequence";
    private static final String ACTIVE_UNTIL =
            "vanillainstincts_production_active_until";
    private static final String RESULT_AT =
            "vanillainstincts_production_result_at";
    private static final String RECIPE_ID =
            "vanillainstincts_production_recipe_id";
    private static final String JOB_POS =
            "vanillainstincts_production_job_pos";
    private static final String COMPLETED =
            "vanillainstincts_production_completed";
    private static final String SOCIAL_BOOST_UNTIL =
            "vanillainstincts_social_production_boost_until";
    private static final String SOCIAL_SALES =
            "vanillainstincts_social_profession_sales";
    private static final long SOCIAL_BOOST_TICKS = 24_000L;

    private VillagerProductionController() {
    }

    public static void maintain(EntityVillager villager, WorldServer level,
                                long gameTime) {
        if (villager == null) return;
        if (!active(villager, gameTime)) {
            clearExpired(villager, gameTime);
            return;
        }
        ProfessionRecipe recipe = storedRecipe(villager);
        if (recipe == null || recipe.profession()
                != LegacyVillagerProfession.of(villager)) {
            cancel(villager, gameTime, true);
            return;
        }
        BlockPos job = readPos(villager, JOB_POS);
        if (job != null) {
            fr.vanillainstincts.compat.Minecraft112Compat.lookAt(villager,
                    job.getX() + 0.5D, job.getY() + 0.7D, job.getZ() + 0.5D,
                    30.0F, 30.0F);
        }
        if (VanillaInstinctsScheduler.isScheduled(villager,
                configuredTicks(
                        ProfessionRules.PROFESSION_WORK_ANIMATION_TICKS))) {
            animate(villager, level, job);
        }
        if (!villager.getEntityData().getBoolean(COMPLETED)
                && gameTime >= villager.getEntityData()
                .getLong(RESULT_AT)) {
            complete(villager, level, recipe);
        }
    }

    public static boolean contribute(EntityVillager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     WorldServer level,
                                     long gameTime) {
        if (active(villager, gameTime)) {
            plan.offerSpecial(VanillaInstinctsState.PROFESSION_PRODUCE,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_PROFESSION_PRODUCTION,
                    ProfessionRules.STATE_HOLD_PROFESSION_PRODUCTION_TICKS,
                    () -> villager.getNavigator().clearPath());
            return true;
        }
        if (!eligible(villager, state, level, gameTime)) return false;
        int scanTicks = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_SCAN_TICKS);
        if (Math.floorMod(gameTime + villager.getEntityId() * 13L,
                scanTicks) != 0L) {
            return false;
        }

        ProfessionRecipe recipe = nextRecipe(villager);
        if (recipe == null) {
            setReady(villager, gameTime, configuredTicks(
                    ProfessionRules.PROFESSION_PRODUCTION_IDLE_TICKS));
            return false;
        }

        VillagePoiScanner.refresh(villager, state, level, gameTime);
        BlockPos job = state.jobSite(gameTime);
        if (job != null && !fr.vanillainstincts.compat.Minecraft112Compat.closerThan(entityBlockPos(villager), job,
                ProfessionRules.PROFESSION_WORK_REACHED_DISTANCE)) {
            Optional<Vec3d> destination =
                    VillagerRoutineController.adjacentDestination(
                            villager, job);
            if (destination.isPresent()) {
                plan.offerNavigation(
                        VanillaInstinctsState.PROFESSION_PRODUCE,
                        ActionOwner.VILLAGER_PROFESSION,
                        ProfessionRules.PRIORITY_PROFESSION_PRODUCTION,
                        destination.get(),
                        ProfessionRules.PROFESSION_WORK_SPEED,
                        ProfessionRules
                                .STATE_HOLD_PROFESSION_PRODUCTION_TICKS,
                        null);
                return true;
            }
        }

        BlockPos acceptedJob = job == null
                ? entityBlockPos(villager) : immutableBlockPos(job);
        plan.offerSpecial(VanillaInstinctsState.PROFESSION_PRODUCE,
                ActionOwner.VILLAGER_PROFESSION,
                ProfessionRules.PRIORITY_PROFESSION_PRODUCTION + 2,
                ProfessionRules.STATE_HOLD_PROFESSION_PRODUCTION_TICKS,
                () -> start(villager, level, acceptedJob,
                        recipe, gameTime));
        return true;
    }

    public static ItemStack workItem(LegacyVillagerProfession profession) {
        List<ProfessionRecipe> recipes =
                ProfessionRecipeCatalog.recipes(profession);
        if (!recipes.isEmpty()) {
            return recipes.get(0).ingredients().get(0).stack();
        }
        if (profession == LegacyVillagerProfession.CLERIC) {
            return new ItemStack(Items.POTIONITEM);
        }
        if (profession == LegacyVillagerProfession.FISHERMAN) {
            return new ItemStack(Items.FISHING_ROD);
        }
        if (profession == LegacyVillagerProfession.FARMER) {
            return new ItemStack(Items.WHEAT);
        }
        return ItemStack.EMPTY;
    }

    private static void start(EntityVillager villager, WorldServer level,
                              BlockPos job, ProfessionRecipe recipe,
                              long gameTime) {
        int levelValue = LegacyVillagerProfession.level(villager);
        if (!ProfessionRecipeCatalog.consume(villager.getVillagerInventory(),
                recipe, levelValue)) {
            setReady(villager, gameTime, configuredTicks(
                    ProfessionRules.PROFESSION_PRODUCTION_IDLE_TICKS));
            return;
        }
        long sequence = villager.getEntityData().getLong(SEQUENCE) + 1L;
        villager.getEntityData().setLong(SEQUENCE, sequence);
        villager.getEntityData().setString(RECIPE_ID, recipe.id());
        villager.getEntityData().setLong(JOB_POS, job.toLong());
        villager.getEntityData().setLong(RESULT_AT,
                gameTime + configuredTicks(
                        ProfessionRules.PROFESSION_WORK_RESULT_TICKS));
        villager.getEntityData().setLong(ACTIVE_UNTIL,
                gameTime + configuredTicks(
                        ProfessionRules.PROFESSION_WORK_DURATION_TICKS));
        villager.getEntityData().setBoolean(COMPLETED, false);
        villager.setHeldItem(EnumHand.MAIN_HAND,
                recipe.ingredients().get(0).stack());
        animate(villager, level, job);
    }

    private static void complete(EntityVillager villager, WorldServer level,
                                 ProfessionRecipe recipe) {
        ItemStack product = recipe.result();
        long sequence = villager.getEntityData().getLong(SEQUENCE);
        villager.setHeldItem(EnumHand.MAIN_HAND, product.copy());
        villager.swingArm(EnumHand.MAIN_HAND);
        VillageMarketController.recordProduction(level,
                entityBlockPos(villager), product);

        int villageStock = ProfessionStockController.countExact(
                villager.getVillagerInventory(), product);
        boolean villageRoute = ProductionBalancePolicy.routeToVillageStock(
                sequence, villageStock);
        boolean stored = false;
        int uses = ProductionBalancePolicy.merchantUsesForBatch(villageRoute);
        if (uses > 0) {
            stored = RecoveredTradeController.addProducedOffer(
                    villager, product, uses, recipe.workmanship());
        }
        if (!stored) {
            ItemStack inventoryProduct = product.copy();
            ProfessionStockController.insert(villager.getVillagerInventory(),
                    inventoryProduct);
            if (!inventoryProduct.isEmpty()) {
                fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(villager, inventoryProduct);
            }
            villager.getVillagerInventory().markDirty();
        }
        villager.getEntityData().setBoolean(COMPLETED, true);
    }

    private static void animate(EntityVillager villager, WorldServer level,
                                BlockPos job) {
        villager.swingArm(EnumHand.MAIN_HAND);
        // 1.12 villagers have no profession work-sound hook.
        BlockPos source = job == null ? entityBlockPos(villager) : job;
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            level.spawnParticle(EnumParticleTypes.CRIT,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 4,
                    0.2D, 0.2D, 0.2D, 0.04D);
            return;
        }
        if (profession == LegacyVillagerProfession.BUTCHER) {
            level.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.01D);
            return;
        }
        if (profession == LegacyVillagerProfession.LIBRARIAN
                || profession == LegacyVillagerProfession.CARTOGRAPHER) {
            level.spawnParticle(EnumParticleTypes.ENCHANTMENT_TABLE,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 4,
                    0.2D, 0.2D, 0.2D, 0.1D);
            return;
        }
        if (profession == LegacyVillagerProfession.FLETCHER) {
            level.spawnParticle(EnumParticleTypes.CRIT,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.03D);
            return;
        }
        if (profession == LegacyVillagerProfession.SHEPHERD) {
            level.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.01D);
            return;
        }
        if (profession == LegacyVillagerProfession.MASON) {
            level.spawnParticle(EnumParticleTypes.CLOUD,
                    source.getX() + 0.5D, source.getY() + 0.8D,
                    source.getZ() + 0.5D, 2,
                    0.2D, 0.1D, 0.2D, 0.01D);
            return;
        }
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                source.getX() + 0.5D, source.getY() + 1.0D,
                source.getZ() + 0.5D, 2,
                0.2D, 0.2D, 0.2D, 0.0D);
    }

    private static boolean eligible(EntityVillager villager,
                                    VillagerRuntimeState state,
                                    WorldServer level,
                                    long gameTime) {
        if (villager == null || villager.isChild() || villager.isTrading()
                || state.danger(gameTime) != null
                || VillagerRoutineController.phaseFor(level.getWorldTime())
                != VillagerSchedulePhase.WORK
                || gameTime < villager.getEntityData()
                .getLong(READY_AT)) {
            return false;
        }
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        return !ProfessionRecipeCatalog.recipes(profession).isEmpty();
    }

    private static ProfessionRecipe storedRecipe(EntityVillager villager) {
        return ProfessionRecipeCatalog.byId(villager.getEntityData()
                .getString(RECIPE_ID));
    }

    private static void clearExpired(EntityVillager villager, long gameTime) {
        long activeUntil = villager.getEntityData().getLong(ACTIVE_UNTIL);
        if (activeUntil <= 0L || gameTime < activeUntil) return;
        boolean completed = villager.getEntityData()
                .getBoolean(COMPLETED);
        cancel(villager, gameTime, !completed);
    }

    private static void cancel(EntityVillager villager, long gameTime,
                               boolean refund) {
        ProfessionRecipe recipe = storedRecipe(villager);
        if (refund && recipe != null) {
            for (ItemStack overflow : ProfessionRecipeCatalog.refund(
                    villager.getVillagerInventory(), recipe)) {
                fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(villager, overflow);
            }
        }
        long sequence = villager.getEntityData().getLong(SEQUENCE);
        villager.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        villager.getEntityData().removeTag(ACTIVE_UNTIL);
        villager.getEntityData().removeTag(RESULT_AT);
        villager.getEntityData().removeTag(RECIPE_ID);
        villager.getEntityData().removeTag(JOB_POS);
        villager.getEntityData().removeTag(COMPLETED);
        setReady(villager, gameTime, productionCooldown(villager, sequence));
    }

    private static boolean active(EntityVillager villager, long gameTime) {
        return villager != null && gameTime < villager.getEntityData()
                .getLong(ACTIVE_UNTIL);
    }

    private static void setReady(EntityVillager villager, long gameTime,
                                 int delay) {
        villager.getEntityData().setLong(READY_AT,
                gameTime + Math.max(1, delay));
    }

    private static BlockPos readPos(EntityVillager villager, String key) {
        return villager.getEntityData().hasKey(key)
                ? BlockPos.fromLong(villager.getEntityData().getLong(key))
                : null;
    }

    public static ProfessionRecipe nextRecipe(EntityVillager villager) {
        if (villager == null) return null;
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        List<ProfessionRecipe> recipes =
                ProfessionRecipeCatalog.recipes(profession);
        if (recipes.isEmpty()) return null;
        long sequence = villager.getEntityData().getLong(SEQUENCE);
        int start = Math.floorMod((int) sequence + villager.getEntityId(),
                recipes.size());
        int level = LegacyVillagerProfession.level(villager);
        for (int offset = 0; offset < recipes.size(); offset++) {
            ProfessionRecipe recipe = recipes.get(
                    (start + offset) % recipes.size());
            if (ProfessionRecipeCatalog.canCraft(villager.getVillagerInventory(),
                    recipe, level)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<ItemStack> catalog(LegacyVillagerProfession profession) {
        return ProfessionRecipeCatalog.products(profession);
    }

    public static boolean produces(LegacyVillagerProfession profession,
                                   ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack product : catalog(profession)) {
            if (fr.vanillainstincts.compat.Minecraft116Compat.stackIs(product, stack.getItem())) return true;
        }
        return false;
    }

    public static int productionUses(ItemStack product, long sequence) {
        return product == null || product.isEmpty() ? 0 : 1;
    }

    /** A completed professional village sale accelerates the next batches. */
    public static void recordSocialSale(EntityVillager seller, WorldServer level,
                                        ItemStack sold, long gameTime) {
        if (seller == null || level == null || sold == null || sold.isEmpty()) {
            return;
        }
        long currentBoost = seller.getEntityData().getLong(
                SOCIAL_BOOST_UNTIL);
        seller.getEntityData().setLong(SOCIAL_BOOST_UNTIL,
                Math.max(currentBoost, gameTime + SOCIAL_BOOST_TICKS));
        int sales = Math.max(0, seller.getEntityData().getInteger(
                SOCIAL_SALES));
        seller.getEntityData().setInteger(SOCIAL_SALES,
                Math.min(4_096, sales + 1));
        long readyAt = seller.getEntityData().getLong(READY_AT);
        if (readyAt > gameTime + 20L) {
            seller.getEntityData().setLong(READY_AT, gameTime + 20L);
        }
        level.spawnParticle(EnumParticleTypes.VILLAGER_HAPPY,
                seller.posX, seller.posY + 1.4D, seller.posZ,
                5, 0.25D, 0.35D, 0.25D, 0.02D);
    }

    public static boolean socialBoostActive(EntityVillager villager,
                                            long gameTime) {
        return villager != null && gameTime < villager.getEntityData()
                .getLong(SOCIAL_BOOST_UNTIL);
    }

    public static int socialSaleCount(EntityVillager villager) {
        return villager == null ? 0 : Math.max(0,
                villager.getEntityData().getInteger(SOCIAL_SALES));
    }

    public static int productionCooldown(EntityVillager villager,
                                         long sequence) {
        int maximum = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_VARIATION_TICKS);
        int variation = Math.floorMod(villager.getUniqueID().hashCode()
                + Long.hashCode(sequence), maximum + 1);
        int cooldown = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_MIN_COOLDOWN_TICKS)
                + variation;
        if (socialBoostActive(villager, villager.world.getTotalWorldTime())) {
            cooldown = Math.max(20, cooldown * 3 / 4);
        }
        return cooldown;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.VILLAGER_PRODUCTION,
                baseTicks);
    }
}
