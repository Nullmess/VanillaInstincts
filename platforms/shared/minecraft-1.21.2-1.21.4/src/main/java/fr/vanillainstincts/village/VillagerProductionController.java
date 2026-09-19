package fr.vanillainstincts.village;

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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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

    public static void maintain(Villager villager, ServerLevel level,
                                long gameTime) {
        if (villager == null) return;
        if (!active(villager, gameTime)) {
            clearExpired(villager, gameTime);
            return;
        }
        ProfessionRecipe recipe = storedRecipe(villager);
        if (recipe == null || recipe.profession()
                != villager.getVillagerData().getProfession()) {
            cancel(villager, gameTime, true);
            return;
        }
        BlockPos job = readPos(villager, JOB_POS);
        if (job != null) {
            villager.getLookControl().setLookAt(job.getX() + 0.5D,
                    job.getY() + 0.7D, job.getZ() + 0.5D,
                    30.0F, 30.0F);
        }
        if (VanillaInstinctsScheduler.isScheduled(villager,
                configuredTicks(
                        ProfessionRules.PROFESSION_WORK_ANIMATION_TICKS))) {
            animate(villager, level, job);
        }
        if (!villager.getPersistentData().getBoolean(COMPLETED)
                && gameTime >= villager.getPersistentData()
                .getLong(RESULT_AT)) {
            complete(villager, level, recipe);
        }
    }

    public static boolean contribute(Villager villager,
                                     VillagerRuntimeState state,
                                     MobDecisionPlan plan,
                                     ServerLevel level,
                                     long gameTime) {
        if (active(villager, gameTime)) {
            plan.offerSpecial(VanillaInstinctsState.PROFESSION_PRODUCE,
                    ActionOwner.VILLAGER_PROFESSION,
                    ProfessionRules.PRIORITY_PROFESSION_PRODUCTION,
                    ProfessionRules.STATE_HOLD_PROFESSION_PRODUCTION_TICKS,
                    () -> villager.getNavigation().stop());
            return true;
        }
        if (!eligible(villager, state, level, gameTime)) return false;
        int scanTicks = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_SCAN_TICKS);
        if (Math.floorMod(gameTime + villager.getId() * 13L,
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
        if (job != null && !villager.blockPosition().closerThan(job,
                ProfessionRules.PROFESSION_WORK_REACHED_DISTANCE)) {
            Optional<Vec3> destination =
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
                ? villager.blockPosition() : job.immutable();
        plan.offerSpecial(VanillaInstinctsState.PROFESSION_PRODUCE,
                ActionOwner.VILLAGER_PROFESSION,
                ProfessionRules.PRIORITY_PROFESSION_PRODUCTION + 2,
                ProfessionRules.STATE_HOLD_PROFESSION_PRODUCTION_TICKS,
                () -> start(villager, level, acceptedJob,
                        recipe, gameTime));
        return true;
    }

    public static ItemStack workItem(VillagerProfession profession) {
        List<ProfessionRecipe> recipes =
                ProfessionRecipeCatalog.recipes(profession);
        if (!recipes.isEmpty()) {
            return recipes.get(0).ingredients().get(0).stack();
        }
        if (profession == VillagerProfession.CLERIC) {
            return new ItemStack(Items.POTION);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return new ItemStack(Items.FISHING_ROD);
        }
        if (profession == VillagerProfession.FARMER) {
            return new ItemStack(Items.WHEAT);
        }
        return ItemStack.EMPTY;
    }

    private static void start(Villager villager, ServerLevel level,
                              BlockPos job, ProfessionRecipe recipe,
                              long gameTime) {
        int levelValue = villager.getVillagerData().getLevel();
        if (!ProfessionRecipeCatalog.consume(villager.getInventory(),
                recipe, levelValue)) {
            setReady(villager, gameTime, configuredTicks(
                    ProfessionRules.PROFESSION_PRODUCTION_IDLE_TICKS));
            return;
        }
        long sequence = villager.getPersistentData().getLong(SEQUENCE) + 1L;
        villager.getPersistentData().putLong(SEQUENCE, sequence);
        villager.getPersistentData().putString(RECIPE_ID, recipe.id());
        villager.getPersistentData().putLong(JOB_POS, job.asLong());
        villager.getPersistentData().putLong(RESULT_AT,
                gameTime + configuredTicks(
                        ProfessionRules.PROFESSION_WORK_RESULT_TICKS));
        villager.getPersistentData().putLong(ACTIVE_UNTIL,
                gameTime + configuredTicks(
                        ProfessionRules.PROFESSION_WORK_DURATION_TICKS));
        villager.getPersistentData().putBoolean(COMPLETED, false);
        villager.setItemInHand(InteractionHand.MAIN_HAND,
                recipe.ingredients().get(0).stack());
        animate(villager, level, job);
    }

    private static void complete(Villager villager, ServerLevel level,
                                 ProfessionRecipe recipe) {
        ItemStack product = recipe.result();
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        villager.setItemInHand(InteractionHand.MAIN_HAND, product.copy());
        villager.swing(InteractionHand.MAIN_HAND);
        VillageMarketController.recordProduction(level,
                villager.blockPosition(), product);

        int villageStock = ProfessionStockController.countExact(
                villager.getInventory(), product);
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
            ProfessionStockController.insert(villager.getInventory(),
                    inventoryProduct);
            if (!inventoryProduct.isEmpty()) {
                villager.spawnAtLocation(level, inventoryProduct);
            }
            villager.getInventory().setChanged();
        }
        villager.getPersistentData().putBoolean(COMPLETED, true);
    }

    private static void animate(Villager villager, ServerLevel level,
                                BlockPos job) {
        villager.swing(InteractionHand.MAIN_HAND);
        villager.playWorkSound();
        BlockPos source = job == null ? villager.blockPosition() : job;
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            level.sendParticles(ParticleTypes.CRIT,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 4,
                    0.2D, 0.2D, 0.2D, 0.04D);
            return;
        }
        if (profession == VillagerProfession.BUTCHER) {
            level.sendParticles(ParticleTypes.SMOKE,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.01D);
            return;
        }
        if (profession == VillagerProfession.LIBRARIAN
                || profession == VillagerProfession.CARTOGRAPHER) {
            level.sendParticles(ParticleTypes.ENCHANT,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 4,
                    0.2D, 0.2D, 0.2D, 0.1D);
            return;
        }
        if (profession == VillagerProfession.FLETCHER) {
            level.sendParticles(ParticleTypes.CRIT,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.03D);
            return;
        }
        if (profession == VillagerProfession.SHEPHERD) {
            level.sendParticles(ParticleTypes.POOF,
                    source.getX() + 0.5D, source.getY() + 1.0D,
                    source.getZ() + 0.5D, 3,
                    0.2D, 0.2D, 0.2D, 0.01D);
            return;
        }
        if (profession == VillagerProfession.MASON) {
            level.sendParticles(ParticleTypes.CLOUD,
                    source.getX() + 0.5D, source.getY() + 0.8D,
                    source.getZ() + 0.5D, 2,
                    0.2D, 0.1D, 0.2D, 0.01D);
            return;
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                source.getX() + 0.5D, source.getY() + 1.0D,
                source.getZ() + 0.5D, 2,
                0.2D, 0.2D, 0.2D, 0.0D);
    }

    private static boolean eligible(Villager villager,
                                    VillagerRuntimeState state,
                                    ServerLevel level,
                                    long gameTime) {
        if (villager == null || villager.isBaby() || villager.isTrading()
                || state.danger(gameTime) != null
                || VillagerRoutineController.phaseFor(level.getDayTime())
                != VillagerSchedulePhase.WORK
                || gameTime < villager.getPersistentData()
                .getLong(READY_AT)) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        return !ProfessionRecipeCatalog.recipes(profession).isEmpty();
    }

    private static ProfessionRecipe storedRecipe(Villager villager) {
        return ProfessionRecipeCatalog.byId(villager.getPersistentData()
                .getString(RECIPE_ID));
    }

    private static void clearExpired(Villager villager, long gameTime) {
        long activeUntil = villager.getPersistentData().getLong(ACTIVE_UNTIL);
        if (activeUntil <= 0L || gameTime < activeUntil) return;
        boolean completed = villager.getPersistentData()
                .getBoolean(COMPLETED);
        cancel(villager, gameTime, !completed);
    }

    private static void cancel(Villager villager, long gameTime,
                               boolean refund) {
        ProfessionRecipe recipe = storedRecipe(villager);
        if (refund && recipe != null) {
            for (ItemStack overflow : ProfessionRecipeCatalog.refund(
                    villager.getInventory(), recipe)) {
                if (villager.level() instanceof ServerLevel level) {
                    villager.spawnAtLocation(level, overflow);
                }
            }
        }
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        villager.getPersistentData().remove(ACTIVE_UNTIL);
        villager.getPersistentData().remove(RESULT_AT);
        villager.getPersistentData().remove(RECIPE_ID);
        villager.getPersistentData().remove(JOB_POS);
        villager.getPersistentData().remove(COMPLETED);
        setReady(villager, gameTime, productionCooldown(villager, sequence));
    }

    private static boolean active(Villager villager, long gameTime) {
        return villager != null && gameTime < villager.getPersistentData()
                .getLong(ACTIVE_UNTIL);
    }

    private static void setReady(Villager villager, long gameTime,
                                 int delay) {
        villager.getPersistentData().putLong(READY_AT,
                gameTime + Math.max(1, delay));
    }

    private static BlockPos readPos(Villager villager, String key) {
        return villager.getPersistentData().contains(key)
                ? BlockPos.of(villager.getPersistentData().getLong(key))
                : null;
    }

    public static ProfessionRecipe nextRecipe(Villager villager) {
        if (villager == null) return null;
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        List<ProfessionRecipe> recipes =
                ProfessionRecipeCatalog.recipes(profession);
        if (recipes.isEmpty()) return null;
        long sequence = villager.getPersistentData().getLong(SEQUENCE);
        int start = Math.floorMod((int) sequence + villager.getId(),
                recipes.size());
        int level = villager.getVillagerData().getLevel();
        for (int offset = 0; offset < recipes.size(); offset++) {
            ProfessionRecipe recipe = recipes.get(
                    (start + offset) % recipes.size());
            if (ProfessionRecipeCatalog.canCraft(villager.getInventory(),
                    recipe, level)) {
                return recipe;
            }
        }
        return null;
    }

    public static List<ItemStack> catalog(VillagerProfession profession) {
        return ProfessionRecipeCatalog.products(profession);
    }

    public static boolean produces(VillagerProfession profession,
                                   ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack product : catalog(profession)) {
            if (product.is(stack.getItem())) return true;
        }
        return false;
    }

    public static int productionUses(ItemStack product, long sequence) {
        return product == null || product.isEmpty() ? 0 : 1;
    }

    /** A completed professional village sale accelerates the next batches. */
    public static void recordSocialSale(Villager seller, ServerLevel level,
                                        ItemStack sold, long gameTime) {
        if (seller == null || level == null || sold == null || sold.isEmpty()) {
            return;
        }
        long currentBoost = seller.getPersistentData().getLong(
                SOCIAL_BOOST_UNTIL);
        seller.getPersistentData().putLong(SOCIAL_BOOST_UNTIL,
                Math.max(currentBoost, gameTime + SOCIAL_BOOST_TICKS));
        int sales = Math.max(0, seller.getPersistentData().getInt(
                SOCIAL_SALES));
        seller.getPersistentData().putInt(SOCIAL_SALES,
                Math.min(4_096, sales + 1));
        long readyAt = seller.getPersistentData().getLong(READY_AT);
        if (readyAt > gameTime + 20L) {
            seller.getPersistentData().putLong(READY_AT, gameTime + 20L);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                seller.getX(), seller.getY() + 1.4D, seller.getZ(),
                5, 0.25D, 0.35D, 0.25D, 0.02D);
    }

    public static boolean socialBoostActive(Villager villager,
                                            long gameTime) {
        return villager != null && gameTime < villager.getPersistentData()
                .getLong(SOCIAL_BOOST_UNTIL);
    }

    public static int socialSaleCount(Villager villager) {
        return villager == null ? 0 : Math.max(0,
                villager.getPersistentData().getInt(SOCIAL_SALES));
    }

    public static int productionCooldown(Villager villager,
                                         long sequence) {
        int maximum = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_VARIATION_TICKS);
        int variation = Math.floorMod(villager.getUUID().hashCode()
                + Long.hashCode(sequence), maximum + 1);
        int cooldown = configuredTicks(
                ProfessionRules.PROFESSION_PRODUCTION_MIN_COOLDOWN_TICKS)
                + variation;
        if (socialBoostActive(villager, villager.level().getGameTime())) {
            cooldown = Math.max(20, cooldown * 3 / 4);
        }
        return cooldown;
    }

    private static int configuredTicks(int baseTicks) {
        return RuntimeConfig.interval(FeatureFlag.VILLAGER_PRODUCTION,
                baseTicks);
    }
}
