package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.village.FarmerController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.village.VillagerEconomyController;
import fr.vanillainstincts.village.VillagerFoodExchangeController;
import fr.vanillainstincts.village.VillagerRoutineController;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageScheduleEconomyGameTests {
    private VillageScheduleEconomyGameTests() {
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void transitionBreakStartsAfterProduction(GameTestHelper helper) {
        helper.assertValueEqual(VillagerRoutineController.phaseFor(6_500L),
                VillagerSchedulePhase.MIDDAY_BREAK,
                net.minecraft.network.chat.Component.literal("La transition après la production doit libérer les villageois de leur poste"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void morningRemainsWorkTime(GameTestHelper helper) {
        helper.assertValueEqual(VillagerRoutineController.phaseFor(4_000L),
                VillagerSchedulePhase.WORK,
                net.minecraft.network.chat.Component.literal("Le matin reste une période de travail"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void afternoonBecomesCommercialTime(GameTestHelper helper) {
        helper.assertValueEqual(VillagerRoutineController.phaseFor(8_000L),
                VillagerSchedulePhase.SOCIAL,
                net.minecraft.network.chat.Component.literal("La seconde moitié de journée doit rester consacrée aux échanges"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonyCanStartAtNoon(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .isCeremonyWindow(6_000L),
                net.minecraft.network.chat.Component.literal("La cérémonie doit pouvoir commencer autour de midi"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonyDoesNotStartAtDawn(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController
                        .isCeremonyWindow(2_000L),
                net.minecraft.network.chat.Component.literal("Une cérémonie ne doit pas interrompre le début du travail"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void pricePenaltyIsCapped(GameTestHelper helper) {
        helper.assertValueEqual(VillagerEconomyController.cappedPenalty(22, 8),
                ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                net.minecraft.network.chat.Component.literal("La colère commerciale doit rester bornée"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void repeatedCropDamageAlertsVillage(GameTestHelper helper) {
        helper.assertTrue(VillagerEconomyController
                        .shouldAlertForCropSabotage(3),
                net.minecraft.network.chat.Component.literal("Trois cultures cassées rapidement doivent déclencher l'alerte"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void singleCropDamageDoesNotAlertGolem(GameTestHelper helper) {
        helper.assertFalse(VillagerEconomyController
                        .shouldAlertForCropSabotage(1),
                net.minecraft.network.chat.Component.literal("Une seule casse ne doit pas provoquer une chasse immédiate"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void largerFoodGiftPaysMore(GameTestHelper helper) {
        helper.assertValueEqual(VillagerFoodExchangeController
                        .paymentEmeralds(6), 2,
                net.minecraft.network.chat.Component.literal("Un gros don alimentaire doit produire deux émeraudes"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void villageGiftCannotBecomeRecoveredTrade(GameTestHelper helper) {
        helper.assertTrue(VillagerFoodExchangeController
                        .isVillageOriginData(true, false, false),
                net.minecraft.network.chat.Component.literal("Un objet jeté par le village doit garder son origine"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void finishedFarmerMayShareAtNoon(GameTestHelper helper) {
        helper.assertTrue(FarmerController.shouldShareAfterWork(0, false,
                        VillagerSchedulePhase.MIDDAY_BREAK),
                net.minecraft.network.chat.Component.literal("Un fermier libre doit pouvoir partager son surplus à midi"));
        helper.succeed();
    }

    @GameTest(batch = "village_schedule_economy", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerFinishesRepairQueueBeforeSharing(GameTestHelper helper) {
        helper.assertFalse(FarmerController.shouldShareAfterWork(4, false,
                        VillagerSchedulePhase.MIDDAY_BREAK),
                net.minecraft.network.chat.Component.literal("Les cultures sabotées doivent être replantées avant le partage"));
        helper.succeed();
    }
}
