package fr.vanillainstincts.gametest.diagnostic;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.AnimalComfortController;
import fr.vanillainstincts.ai.MobPerceptionMemory;
import fr.vanillainstincts.ai.PillagerTacticsController;
import fr.vanillainstincts.ai.SkeletonTacticsController;
import fr.vanillainstincts.ai.ZombieTacticsController;
import fr.vanillainstincts.village.VillagePathEvaluator;
import fr.vanillainstincts.core.diagnostic.BoundedDiagnosticLog;
import fr.vanillainstincts.core.diagnostic.DiagnosticReport;
import fr.vanillainstincts.core.diagnostic.DiagnosticSeverity;
import fr.vanillainstincts.core.diagnostic.DiagnosticViolation;
import fr.vanillainstincts.core.diagnostic.HealthLevel;
import fr.vanillainstincts.core.diagnostic.RuntimeHealthSnapshot;
import fr.vanillainstincts.core.diagnostic.RuntimeInvariantPolicy;
import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import fr.vanillainstincts.diagnostic.RuntimeDiagnosticsService;
import fr.vanillainstincts.village.ClericNetherExpeditionController;
import fr.vanillainstincts.village.FarmerLivestockController;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import fr.vanillainstincts.village.VillagerGrandMasterIntelligenceController;
import fr.vanillainstincts.village.VillagerRuntimeState;
import fr.vanillainstincts.village.VillagerStateStore;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RuntimeDiagnosticsGameTests {
    private static final long TARGET = 50_000_000L;
    private static final long OVERLOAD = 80_000_000L;

    private RuntimeDiagnosticsGameTests() {
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void healthySnapshotIsHealthy(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        helper.assertTrue(report.healthy(),
                "Un instantané nominal doit être sain");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void missingSnapshotIsUnhealthy(GameTestHelper helper) {
        DiagnosticReport report = RuntimeInvariantPolicy.evaluate(null,
                TARGET, OVERLOAD, 1_000, 1_000);
        helper.assertValueEqual(report.health(), HealthLevel.UNHEALTHY,
                "Un diagnostic sans instantané doit échouer clairement");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void slowTickProducesWarning(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(60_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        helper.assertTrue(hasCode(report, "scheduler.tick_slow"),
                "Un tick lent doit être visible dans le diagnostic");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void overloadTickProducesWarning(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(90_000_000L,
                1_000_000L, 100, 80, 100, 0, 0));
        helper.assertTrue(hasCode(report, "scheduler.tick_overload"),
                "Une surcharge doit utiliser un code stable");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void costBudgetOverflowIsError(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 101, 100, 0, 0));
        helper.assertValueEqual(report.health(), HealthLevel.UNHEALTHY,
                "Le dépassement du budget logique doit être une erreur");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void timeBudgetOverflowIsWarning(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                2_000_001L, 100, 80, 100, 0, 0));
        helper.assertTrue(hasCode(report, "scheduler.time_budget_exceeded"),
                "Un dépassement temporel simple doit être signalé");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void severeTimeOverflowIsError(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                8_000_001L, 100, 80, 100, 0, 0));
        helper.assertTrue(hasCode(report, "scheduler.time_budget_severe"),
                "Un dépassement temporel extrême doit être une erreur");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void rejectionPressureIsWarning(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 80, 75, 25, 0));
        helper.assertTrue(hasCode(report, "scheduler.rejection_pressure"),
                "Un quart de refus doit être diagnostiqué");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void severeRejectionIsError(GameTestHelper helper) {
        DiagnosticReport report = evaluate(snapshot(40_000_000L,
                1_000_000L, 100, 20, 25, 75, 0));
        helper.assertTrue(hasCode(report, "scheduler.rejection_severe"),
                "Trois quarts de refus doivent être une erreur");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void orphanReservationIsError(GameTestHelper helper) {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 0, 1, 0, 0L);
        helper.assertTrue(hasCode(evaluate(snapshot),
                        "economy.orphaned_reservations"),
                "Une réservation orpheline doit être une erreur");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void staleOperationIsWarning(GameTestHelper helper) {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 0, 0, 1, 0L);
        helper.assertTrue(hasCode(evaluate(snapshot),
                        "persistence.stale_operations"),
                "Une opération ancienne doit être diagnostiquée");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void trackedEntityCeilingIsWarning(GameTestHelper helper) {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 1_001, 0, 0, 0, 0, 0L);
        helper.assertTrue(hasCode(evaluate(snapshot),
                        "state.tracked_entities_high"),
                "Un cache d'entités trop grand doit être signalé");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void surfaceCacheCeilingIsWarning(GameTestHelper helper) {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 2_001, 0, 0, 0, 0L);
        helper.assertTrue(hasCode(evaluate(snapshot),
                        "state.cached_surfaces_high"),
                "Un cache de surfaces trop grand doit être signalé");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void temporaryBlockCeilingIsWarning(
            GameTestHelper helper) {
        RuntimeHealthSnapshot snapshot = new RuntimeHealthSnapshot(
                telemetry(40_000_000L, 1_000_000L, 100, 80,
                        100, 0, 0), 0, 0, 1_001, 0, 0, 0L);
        helper.assertTrue(hasCode(evaluate(snapshot),
                        "state.temporary_blocks_high"),
                "Un registre temporaire trop grand doit être signalé");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void boundedLogKeepsNewestEntries(GameTestHelper helper) {
        BoundedDiagnosticLog log = new BoundedDiagnosticLog(2);
        log.add(violation("one", DiagnosticSeverity.WARNING));
        log.add(violation("two", DiagnosticSeverity.WARNING));
        log.add(violation("three", DiagnosticSeverity.ERROR));
        helper.assertValueEqual(log.size(), 2,
                "Le journal doit rester borné");
        helper.assertValueEqual(log.snapshot().getFirst().code(), "two",
                "Le plus ancien diagnostic doit être retiré");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void masterStartsGrandMasterProgress(
            GameTestHelper helper) {
        Villager villager = masterVillager(helper, new BlockPos(2, 1, 2));
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 100L);
        helper.assertFalse(VillagerGrandMasterController
                        .isGrandMaster(villager),
                "Un maître récent ne doit pas être promu immédiatement");
        helper.assertValueEqual(VillagerGrandMasterController
                        .masterTradeCount(villager), 0,
                "Le compteur doit commencer à zéro");
        helper.assertValueEqual(VillagerGrandMasterController
                        .masterSinceDay(villager), 100L,
                "L'ancienneté de maître doit être mémorisée");
        helper.assertValueEqual(VillagerGrandMasterController
                        .progress(villager, 100L), 0.0F,
                "La barre visible doit commencer vide");
        helper.assertTrue(FarmerLivestockController.shouldBreed(7),
                "Un troupeau sous la cible doit encore pouvoir se reproduire");
        helper.assertFalse(FarmerLivestockController.shouldBreed(8),
                "La reproduction doit s'arrêter à la population cible");
        helper.assertFalse(FarmerLivestockController.shouldCull(12),
                "Le plafond autorisé ne doit pas déclencher d'abattage");
        helper.assertTrue(FarmerLivestockController.shouldCull(13),
                "L'excédent au-dessus du plafond doit être régulé");
        helper.assertValueEqual(ClericNetherExpeditionController
                        .requiredPortalObsidian(), 10,
                "Un cadre minimal sans coins doit consommer dix obsidiennes");
        int duration = ClericNetherExpeditionController.virtualDuration(42L);
        helper.assertTrue(duration >= 6_000 && duration <= 12_000,
                "Une expédition simulée doit durer entre cinq et dix minutes");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void twentyThreeTradesDoNotPromote(
            GameTestHelper helper) {
        Villager villager = masterVillager(helper, new BlockPos(4, 1, 2));
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 100L);
        for (int trade = 0; trade < 23; trade++) {
            VillagerGrandMasterController.recordMasterTrade(villager,
                    helper.getLevel(), 100L);
        }
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 103L);
        helper.assertFalse(VillagerGrandMasterController
                        .isGrandMaster(villager),
                "Vingt-trois échanges ne doivent pas suffire");
        helper.assertValueEqual(VillagerGrandMasterController
                        .masterTradeCount(villager), 23,
                "La progression doit conserver chaque échange de maître");
        helper.assertTrue(VillagerGrandMasterController
                        .progress(villager, 103L) > 0.95F,
                "La barre doit montrer une progression presque complète");
        helper.assertTrue(VillagerGrandMasterController
                        .progress(villager, 103L) < 1.0F,
                "La barre ne doit être pleine qu'après la promotion");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void grandMasterUsesNetheriteMarkerAndKeepsLevelFive(
            GameTestHelper helper) {
        Villager villager = masterVillager(helper, new BlockPos(6, 1, 2));
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 100L);
        for (int trade = 0;
             trade < VillagerGrandMasterController.REQUIRED_MASTER_TRADES;
             trade++) {
            VillagerGrandMasterController.recordMasterTrade(villager,
                    helper.getLevel(), 100L);
        }
        helper.assertTrue(VillagerGrandMasterController
                        .isGrandMaster(villager),
                "Le vingt-quatrième échange de maître doit promouvoir immédiatement");
        helper.assertFalse(VillagerGrandMasterController
                        .maintain(villager, helper.getLevel(), 103L),
                "Un Grand Maître déjà promu ne doit pas être promu une seconde fois");
        helper.assertTrue(VillagerGrandMasterController
                        .isGrandMaster(villager),
                "Le rang Grand Maître doit être persistant");
        helper.assertTrue(VillagerGrandMasterController
                        .hasNetheriteMarker(villager),
                "Le marqueur synchronisé doit afficher l'insigne netherite");
        helper.assertTrue(((GrandMasterSyncedData) villager)
                        .vanillainstincts$isGrandMasterSynced(),
                "Le bit client Grand Maître doit être synchronisé");
        helper.assertValueEqual(villager.getVillagerData().getLevel(), 5,
                "Le rang moddé ne doit jamais créer un niveau vanilla 6");
        helper.assertValueEqual(VillagerGrandMasterController
                        .progress(villager, 103L), 1.0F,
                "La barre doit être pleine pour un Grand Maître");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void grandMasterKeepsProfessionalMemoryLonger(
            GameTestHelper helper) {
        Villager grandMaster = masterVillager(helper, new BlockPos(2, 1, 2));
        promoteGrandMaster(grandMaster, helper.getLevel());
        Villager apprentice = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        apprentice.setVillagerData(apprentice.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN).setLevel(2));

        helper.assertTrue(VillagerGrandMasterIntelligenceController
                        .jobMemoryTicks(grandMaster)
                        > VillageSocialRules.VILLAGER_POI_CACHE_TICKS,
                "Un Grand Maître doit retenir son poste plus longtemps");
        helper.assertValueEqual(VillagerGrandMasterIntelligenceController
                        .jobMemoryTicks(apprentice),
                VillageSocialRules.VILLAGER_POI_CACHE_TICKS,
                "La mémoire standard des autres villageois ne doit pas changer");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void grandMasterMentorTransfersJobKnowledge(
            GameTestHelper helper) {
        Villager grandMaster = masterVillager(helper, new BlockPos(2, 1, 2),
                VillagerProfession.BUTCHER);
        promoteGrandMaster(grandMaster, helper.getLevel());
        Villager apprentice = helper.spawn(EntityType.VILLAGER,
                new BlockPos(3, 1, 2));
        apprentice.setVillagerData(apprentice.getVillagerData()
                .setProfession(VillagerProfession.BUTCHER).setLevel(2));
        VillagerRuntimeState masterState =
                VillagerStateStore.stateFor(grandMaster);
        VillagerRuntimeState apprenticeState =
                VillagerStateStore.stateFor(apprentice);
        BlockPos learnedJob = helper.absolutePos(new BlockPos(6, 1, 6));
        masterState.cacheJobSite(learnedJob, 10_000L);

        long assignedAt = -1L;
        for (long tick = 0;
             tick < VillageSocialRules.GRAND_MASTER_MENTOR_SCAN_TICKS;
             tick++) {
            VillagerGrandMasterIntelligenceController.maintain(grandMaster,
                    masterState, helper.getLevel(), tick);
            if (grandMaster.getUUID().equals(
                    VillagerGrandMasterIntelligenceController.mentorId(
                            apprentice, tick))) {
                assignedAt = tick;
                break;
            }
        }
        helper.assertTrue(assignedAt >= 0L,
                "Le Grand Maître doit sélectionner son apprenti proche "
                        + "sans interférence entre GameTests");
        helper.assertValueEqual(
                VillagerGrandMasterIntelligenceController.mentorId(
                        apprentice, assignedAt), grandMaster.getUUID(),
                "L'apprenti doit conserver le Grand Maître attendu comme mentor");
        VillagerGrandMasterIntelligenceController.maintain(apprentice,
                apprenticeState, helper.getLevel(), assignedAt);
        helper.assertTrue(learnedJob.equals(
                        apprenticeState.jobSite(assignedAt)),
                "Un apprenti proche doit apprendre le poste connu du mentor");
        helper.assertValueEqual(VillagerGrandMasterIntelligenceController
                        .lessonCount(apprentice), 1,
                "Une session ne doit transmettre la connaissance qu'une fois");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void grandMasterMentorsOnlySameProfession(
            GameTestHelper helper) {
        Villager grandMaster = masterVillager(helper, new BlockPos(2, 1, 2));
        promoteGrandMaster(grandMaster, helper.getLevel());
        Villager farmer = helper.spawn(EntityType.VILLAGER,
                new BlockPos(3, 1, 2));
        farmer.setVillagerData(farmer.getVillagerData()
                .setProfession(VillagerProfession.FARMER).setLevel(2));
        helper.assertFalse(VillagerGrandMasterIntelligenceController
                        .isEligibleApprentice(grandMaster, farmer),
                "Un Grand Maître ne doit former que son propre métier");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void invalidGrandMasterRankIsRemoved(
            GameTestHelper helper) {
        Villager villager = masterVillager(helper, new BlockPos(5, 1, 2));
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 100L);
        for (int trade = 0;
             trade < VillagerGrandMasterController.REQUIRED_MASTER_TRADES;
             trade++) {
            VillagerGrandMasterController.recordMasterTrade(villager,
                    helper.getLevel(), 100L);
        }
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 103L);
        villager.setVillagerData(villager.getVillagerData().setLevel(4));
        VillagerGrandMasterController.maintain(villager,
                helper.getLevel(), 104L);
        helper.assertFalse(VillagerGrandMasterController
                        .isGrandMaster(villager),
                "Un villageois qui n'est plus maître doit perdre le rang");
        helper.assertFalse(VillagerGrandMasterController
                        .hasNetheriteMarker(villager),
                "L'insigne netherite doit disparaître avec le rang invalide");
        helper.assertValueEqual(VillagerGrandMasterController
                        .masterTradeCount(villager), 0,
                "Une progression incohérente doit être supprimée");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceCreatesLevelSnapshot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RuntimeDiagnosticsService.clearLevel(level);
        RuntimeDiagnosticsService.tick(level, 0L);
        helper.assertTrue(RuntimeDiagnosticsService.latestSnapshot(level)
                        != null,
                "Le service doit produire un instantané sans scanner le monde");
        RuntimeDiagnosticsService.clearLevel(level);
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceKeepsBoundedHistory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RuntimeDiagnosticsService.clearLevel(level);
        for (int index = 0; index < 100; index++) {
            RuntimeDiagnosticsService.tick(level, index * 200L);
        }
        helper.assertTrue(RuntimeDiagnosticsService.recent(level).size()
                        <= 64,
                "L'historique de production doit rester borné");
        RuntimeDiagnosticsService.clearLevel(level);
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void serviceClearsOnLevelUnload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RuntimeDiagnosticsService.tick(level, 0L);
        RuntimeDiagnosticsService.clearLevel(level);
        helper.assertTrue(RuntimeDiagnosticsService.latestSnapshot(level)
                        == null,
                "Le diagnostic ne doit pas retenir un niveau déchargé");
        helper.assertTrue(RuntimeDiagnosticsService.recent(level).isEmpty(),
                "L'historique doit être libéré avec le niveau");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void perceptionNoiseCreatesAndExpiresMemory(
            GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockPos noise = helper.absolutePos(new BlockPos(4, 1, 4));
        MobPerceptionMemory.broadcastNoise(level, noise, 12.0D, 40);
        helper.assertTrue(MobPerceptionMemory.bestKnownPosition(zombie,
                        level.getGameTime()).isPresent(),
                "Un bruit proche doit être mémorisé");
        helper.assertTrue(MobPerceptionMemory.bestKnownPosition(zombie,
                        level.getGameTime() + 41L).isEmpty(),
                "La mémoire sonore doit expirer");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombieToolUseRequiresSuitableHeldTool(
            GameTestHelper helper) {
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        ServerLevel level = helper.getLevel();
        BlockPos stone = helper.absolutePos(new BlockPos(3, 1, 2));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(ZombieTacticsController.canBreakWithHeldTool(
                        zombie, level, stone),
                "Un zombie à mains nues ne doit pas miner");
        zombie.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.WOODEN_PICKAXE));
        helper.assertTrue(ZombieTacticsController.canBreakWithHeldTool(
                        zombie, level, stone),
                "Un outil réellement adapté doit autoriser le passage");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonRetreatIncreasesDistance(
            GameTestHelper helper) {
        var skeleton = helper.spawn(EntityType.SKELETON,
                new BlockPos(2, 1, 2));
        Villager target = helper.spawn(EntityType.VILLAGER,
                new BlockPos(3, 1, 2));
        double before = skeleton.position().distanceToSqr(target.position());
        Vec3 retreat = SkeletonTacticsController.retreatDestination(
                skeleton, target, 9.0D);
        helper.assertTrue(retreat.distanceToSqr(target.position()) > before,
                "Le repositionnement proche doit augmenter la distance");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void pillagerSquadUsesDifferentFlankSlots(
            GameTestHelper helper) {
        var first = helper.spawn(EntityType.PILLAGER, new BlockPos(1, 1, 2));
        var second = helper.spawn(EntityType.PILLAGER, new BlockPos(2, 1, 2));
        Villager target = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        Vec3 a = PillagerTacticsController.flankDestination(first, target, 3);
        Vec3 b = PillagerTacticsController.flankDestination(second, target, 3);
        helper.assertTrue(a.distanceToSqr(b) > 0.25D,
                "Deux pillards doivent pouvoir choisir des slots distincts");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortFindsRoofedPosition(
            GameTestHelper helper) {
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(AnimalComfortController.findShelter(cow, level)
                        .isPresent(),
                "Un animal doit repérer un abri proche sous un toit");
        helper.succeed();
    }

    @GameTest(batch = "runtime_diagnostics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void villagerProfessionPathBiasesFarmland(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos farmerFeet = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos librarianFeet = helper.absolutePos(new BlockPos(4, 1, 2));
        level.setBlock(farmerFeet.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(librarianFeet.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        Villager farmer = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        farmer.setVillagerData(farmer.getVillagerData()
                .setProfession(VillagerProfession.FARMER));
        Villager librarian = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        librarian.setVillagerData(librarian.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN));
        double farmerBias = VillagePathEvaluator.professionPreference(
                farmer, level, farmerFeet);
        double librarianBias = VillagePathEvaluator.professionPreference(
                librarian, level, librarianFeet);
        helper.assertTrue(farmerBias > librarianBias,
                "Le fermier doit préférer les rangées agricoles aux autres métiers");
        helper.succeed();
    }

    private static void promoteGrandMaster(Villager villager,
                                           ServerLevel level) {
        VillagerGrandMasterController.maintain(villager, level, 100L);
        for (int trade = 0;
             trade < VillagerGrandMasterController.REQUIRED_MASTER_TRADES;
             trade++) {
            VillagerGrandMasterController.recordMasterTrade(villager, level,
                    100L);
        }
    }

    private static Villager masterVillager(GameTestHelper helper,
                                             BlockPos position) {
        return masterVillager(helper, position, VillagerProfession.LIBRARIAN);
    }

    private static Villager masterVillager(GameTestHelper helper,
                                             BlockPos position,
                                             VillagerProfession profession) {
        Villager villager = helper.spawn(EntityType.VILLAGER, position);
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(profession)
                .setLevel(5));
        return villager;
    }

    private static RuntimeHealthSnapshot snapshot(long averageTick,
                                                  long spentAi,
                                                  int costBudget,
                                                  int usedCost,
                                                  int accepted,
                                                  int rejected,
                                                  int rejectedCost) {
        return new RuntimeHealthSnapshot(telemetry(averageTick, spentAi,
                costBudget, usedCost, accepted, rejected, rejectedCost),
                0, 0, 0, 0, 0, 0L);
    }

    private static SchedulerTelemetry telemetry(long averageTick,
                                                long spentAi,
                                                int costBudget,
                                                int usedCost,
                                                int accepted,
                                                int rejected,
                                                int rejectedCost) {
        return new SchedulerTelemetry(LoadTier.NORMAL, averageTick, spentAi,
                costBudget, 2_000_000L, usedCost, accepted, rejected,
                rejectedCost);
    }

    private static DiagnosticReport evaluate(RuntimeHealthSnapshot snapshot) {
        return RuntimeInvariantPolicy.evaluate(snapshot, TARGET, OVERLOAD,
                1_000, 1_000);
    }

    private static boolean hasCode(DiagnosticReport report, String code) {
        return report.violations().stream()
                .anyMatch(violation -> violation.code().equals(code));
    }

    private static DiagnosticViolation violation(
            String code, DiagnosticSeverity severity) {
        return new DiagnosticViolation(code, severity, "", 0L, 0L);
    }
}
