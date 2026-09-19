package fr.vanillainstincts.testcompat;

import fr.vanillainstincts.VanillaInstincts;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/** 1.21.5 registry bridge for the legacy Vanilla Instincts GameTest methods. */
public final class VanillaInstinctsGameTestBootstrap {
    private static final Class<?>[] TEST_CLASSES = new Class<?>[] {
            fr.vanillainstincts.gametest.ai.AnimalZombieSkeletonTacticsGameTests.class,
            fr.vanillainstincts.gametest.ai.CreeperCoordinationAndSpiderRetreatGameTests.class,
            fr.vanillainstincts.gametest.ai.CreeperSpiderTacticsGameTests.class,
            fr.vanillainstincts.gametest.ai.EcologyAcquisitionMobilityGameTests.class,
            fr.vanillainstincts.gametest.ai.EndermanCargoGameTests.class,
            fr.vanillainstincts.gametest.ai.GhastTacticsGameTests.class,
            fr.vanillainstincts.gametest.ai.HerdPackFarmerGameTests.class,
            fr.vanillainstincts.gametest.ai.MovementPolicyGameTests.class,
            fr.vanillainstincts.gametest.ai.NetherMissionCombatGameTests.class,
            fr.vanillainstincts.gametest.ai.NetherMissionPersistenceGameTests.class,
            fr.vanillainstincts.gametest.ai.NetherReinforcementGameTests.class,
            fr.vanillainstincts.gametest.ai.PillagerLootGameTests.class,
            fr.vanillainstincts.gametest.ai.SpiderNavigationGameTests.class,
            fr.vanillainstincts.gametest.data.DataDrivenAiGameTests.class,
            fr.vanillainstincts.gametest.data.WelfareDataDrivenBehaviorGameTests.class,
            fr.vanillainstincts.gametest.diagnostic.RuntimeDiagnosticsGameTests.class,
            fr.vanillainstincts.gametest.performance.AdaptiveLoadGameTests.class,
            fr.vanillainstincts.gametest.performance.PerformanceSchedulerGameTests.class,
            fr.vanillainstincts.gametest.performance.SchedulerVillageGrowthGameTests.class,
            fr.vanillainstincts.gametest.persistence.PersistenceMigrationGameTests.class,
            fr.vanillainstincts.gametest.possession.PossessionCatalogGameTests.class,
            fr.vanillainstincts.gametest.progression.AdaptiveSkeletonProgressionGameTests.class,
            fr.vanillainstincts.gametest.village.ClericServiceDemandGameTests.class,
            fr.vanillainstincts.gametest.village.ConstructionSafetyMissionGameTests.class,
            fr.vanillainstincts.gametest.village.DoorLearningGameTests.class,
            fr.vanillainstincts.gametest.village.GolemCeremonyNetherGameTests.class,
            fr.vanillainstincts.gametest.village.GolemConstructionRepairGameTests.class,
            fr.vanillainstincts.gametest.village.ProfessionExpeditionGameTests.class,
            fr.vanillainstincts.gametest.village.ProfessionStockGameTests.class,
            fr.vanillainstincts.gametest.village.RecoveredTradeGameTests.class,
            fr.vanillainstincts.gametest.village.StorageNetworkGameTests.class,
            fr.vanillainstincts.gametest.village.VillageAlertPatrolGameTests.class,
            fr.vanillainstincts.gametest.village.VillageBuildingGameTests.class,
            fr.vanillainstincts.gametest.village.VillageCommunityConstructionGameTests.class,
            fr.vanillainstincts.gametest.village.VillageConstructionCapabilityGameTests.class,
            fr.vanillainstincts.gametest.village.VillageConstructionRulesGameTests.class,
            fr.vanillainstincts.gametest.village.VillageCraftingMarketGameTests.class,
            fr.vanillainstincts.gametest.village.VillageProductionEvolutionGameTests.class,
            fr.vanillainstincts.gametest.village.VillageScheduleEconomyGameTests.class,
            fr.vanillainstincts.gametest.village.VillageSocialTradeGameTests.class,
            fr.vanillainstincts.gametest.village.VillageTemplateSelectionGameTests.class,
            fr.vanillainstincts.gametest.world.CryingPortalAndRecoveryGameTests.class,
            fr.vanillainstincts.gametest.world.CryingPortalNetworkGameTests.class,
            fr.vanillainstincts.gametest.world.DimensionBedGameTests.class,
            fr.vanillainstincts.gametest.world.TrappedChestPrankGameTests.class,
            fr.vanillainstincts.gametest.world.WorldPermissionGameTests.class
    };

    private VanillaInstinctsGameTestBootstrap() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(VanillaInstinctsGameTestBootstrap::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> {
            int[] count = new int[] {0};
            for (Class<?> testClass : TEST_CLASSES) {
                Method[] methods = testClass.getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));
                for (Method method : methods) {
                    GameTest test = method.getAnnotation(GameTest.class);
                    if (test == null) {
                        continue;
                    }
                    if (!Modifier.isStatic(method.getModifiers())
                            || method.getParameterCount() != 1
                            || method.getParameterTypes()[0] != GameTestHelper.class) {
                        throw new IllegalStateException(
                                "Invalid GameTest method: " + testClass.getName() + "#" + method.getName());
                    }
                    String path = test.batch() + "/" + snakeCase(method.getName());
                    Consumer<GameTestHelper> function = helper -> invoke(method, helper);
                    registry.register(VanillaInstincts.id(path), function);
                    count[0]++;
                }
            }
            if (count[0] != 600) {
                throw new IllegalStateException(
                        "Expected 600 Vanilla Instincts GameTest functions, registered " + count[0]);
            }
            VanillaInstincts.LOGGER.info(
                    "Registered {} Vanilla Instincts 1.21.5 GameTest functions", count[0]);
        });
    }

    private static void invoke(Method method, GameTestHelper helper) {
        try {
            method.invoke(null, helper);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot invoke GameTest " + method, exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        }
    }

    private static String snakeCase(String value) {
        return value
                .replaceAll("(.)([A-Z][a-z]+)", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(java.util.Locale.ROOT);
    }
}
