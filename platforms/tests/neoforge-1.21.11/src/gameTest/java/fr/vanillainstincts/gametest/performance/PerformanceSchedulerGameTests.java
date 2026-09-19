package fr.vanillainstincts.gametest.performance;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.TargetAcquisitionController;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.performance.SchedulerPriority;
import fr.vanillainstincts.core.rules.AcquisitionRules;
import fr.vanillainstincts.core.rules.PerformanceRules;
import fr.vanillainstincts.diagnostic.AiPerformanceTracker;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

/** Final integration and performance-regression coverage for fixed49. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceSchedulerGameTests {
    private PerformanceSchedulerGameTests() {
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void performanceTrackerSamplesOncePerTick(GameTestHelper helper) {
        var level = helper.getLevel();
        AiPerformanceTracker.clearLevel(level);
        long now = level.getGameTime();
        AiPerformanceTracker.sample(level, now);
        AiPerformanceTracker.sample(level, now);
        helper.assertValueEqual(AiPerformanceTracker.summary(level).samples(), 1,
                net.minecraft.network.chat.Component.literal("La télémétrie performance ne doit échantillonner qu'une fois par tick"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void performanceTrackerAcceptsNextTick(GameTestHelper helper) {
        var level = helper.getLevel();
        AiPerformanceTracker.clearLevel(level);
        long now = level.getGameTime();
        AiPerformanceTracker.sample(level, now);
        AiPerformanceTracker.sample(level, now + 1L);
        helper.assertValueEqual(AiPerformanceTracker.summary(level).samples(), 2,
                net.minecraft.network.chat.Component.literal("Deux ticks logiques distincts doivent produire deux échantillons"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void performanceTrackerClearDropsHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        AiPerformanceTracker.sample(level, level.getGameTime());
        AiPerformanceTracker.clearLevel(level);
        helper.assertValueEqual(AiPerformanceTracker.summary(level).samples(), 0,
                net.minecraft.network.chat.Component.literal("Le unload d'un niveau doit pouvoir supprimer toute son historique perf"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void performanceHistoryCapacityIsBounded(GameTestHelper helper) {
        helper.assertValueEqual(AiPerformanceTracker.summary(helper.getLevel()).capacity(),
                PerformanceRules.PERFORMANCE_HISTORY_TICKS,
                net.minecraft.network.chat.Component.literal("La fenêtre performance doit rester strictement bornée"));
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void schedulerPriorityUsesNearPlayerBand(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 3));
        var player = helper.makeMockServerPlayerInLevel();
        player.snapTo(zombie.getX() + 3.0D, zombie.getY(), zombie.getZ());
        VanillaInstinctsScheduler.clearLevel(helper.getLevel());
        helper.assertValueEqual(VanillaInstinctsScheduler.priority(
                        helper.getLevel(), zombie), SchedulerPriority.NEAR_PLAYER,
                net.minecraft.network.chat.Component.literal("Un mob proche d'un joueur doit conserver la priorité NEAR_PLAYER"));
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void schedulerPriorityKeepsPlayerCombatCritical(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 3));
        var player = helper.makeMockServerPlayerInLevel();
        player.snapTo(zombie.getX() + 12.0D, zombie.getY(), zombie.getZ());
        zombie.setTarget(player);
        VanillaInstinctsScheduler.clearLevel(helper.getLevel());
        helper.assertValueEqual(VanillaInstinctsScheduler.priority(
                        helper.getLevel(), zombie), SchedulerPriority.CRITICAL,
                net.minecraft.network.chat.Component.literal("Le combat actif contre un joueur doit rester CRITICAL sous load shedding"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void schedulerClearLevelResetsBudget(GameTestHelper helper) {
        var level = helper.getLevel();
        VanillaInstinctsScheduler.clearLevel(level);
        helper.assertTrue(VanillaInstinctsScheduler.claim(level, 3),
                net.minecraft.network.chat.Component.literal("Le budget initial doit accepter une petite réservation"));
        helper.assertTrue(VanillaInstinctsScheduler.usedCost(level) >= 3,
                net.minecraft.network.chat.Component.literal("La réservation doit être visible dans la télémétrie"));
        VanillaInstinctsScheduler.clearLevel(level);
        helper.assertValueEqual(VanillaInstinctsScheduler.usedCost(level), 0,
                net.minecraft.network.chat.Component.literal("Un unload/reload logique doit repartir sans coût résiduel"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void schedulerTelemetryKeepsPositiveBudgets(GameTestHelper helper) {
        var telemetry = VanillaInstinctsScheduler.telemetry(helper.getLevel());
        helper.assertTrue(telemetry.effectiveCostBudget() > 0,
                net.minecraft.network.chat.Component.literal("Le budget coût ne doit jamais devenir nul"));
        helper.assertTrue(telemetry.effectiveTimeBudgetNanos() > 0L,
                net.minecraft.network.chat.Component.literal("Le budget temps ne doit jamais devenir nul"));
        helper.succeed();
    }

    @GameTest(batch = "performance_scheduler",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void acquisitionCandidateInspectionRemainsBounded(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 3));
        for (int index = 0; index < AcquisitionRules.MAX_CANDIDATES + 8; index++) {
            Villager villager = helper.spawn(EntityType.VILLAGER,
                    new BlockPos(2 + index % 4, 1, 2 + (index / 4) % 4));
            villager.setNoAi(true);
        }
        var result = TargetAcquisitionController.findBestTarget(zombie,
                helper.getLevel());
        helper.assertTrue(result.inspectedCandidates()
                        <= AcquisitionRules.MAX_CANDIDATES,
                net.minecraft.network.chat.Component.literal("L'acquisition ne doit jamais inspecter plus que la limite configurée"));
        helper.assertTrue(result.inspectedCandidates() > 0,
                net.minecraft.network.chat.Component.literal("Le fixture dense doit réellement exercer le chemin d'acquisition"));
        helper.succeed();
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos local) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, local);
        zombie.setNoAi(true);
        return zombie;
    }

    private static void clearAndFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 10; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = 0; z <= 10; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 0, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }
}
