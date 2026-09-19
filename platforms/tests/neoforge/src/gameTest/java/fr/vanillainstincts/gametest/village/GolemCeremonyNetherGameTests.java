package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.NetherReinforcementController;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.rules.NetherRules;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.village.VillageGolemCeremonySavedData;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GolemCeremonyNetherGameTests {
    private GolemCeremonyNetherGameTests() {
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonyStartsDuringDay(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController.isDaytime(1_000L),
                "La cérémonie doit pouvoir démarrer pendant la journée");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonyDoesNotStartAtNight(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController.isDaytime(14_000L),
                "La cérémonie ne doit pas démarrer pendant la nuit");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void existingGolemsBlockConstruction(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController.mayBuildForCount(2),
                "Le plafond de golems doit bloquer la cérémonie");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void secondDailyBuildIsBlocked(GameTestHelper helper) {
        helper.assertFalse(VillageGolemCeremonyController.mayBuildToday(1),
                "Une seule construction quotidienne doit être autorisée");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void ironBlocksCountAsNineUnits(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(8);
        inventory.setItem(0, new ItemStack(Items.IRON_BLOCK, 4));
        helper.assertValueEqual(VillageGolemCeremonyController.ironUnits(
                        inventory), 36,
                "Quatre blocs doivent fournir les trente-six lingots requis");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerPumpkinIsRequired(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(8);
        helper.assertFalse(VillageGolemCeremonyController.hasPumpkin(inventory),
                "Un inventaire vide ne doit pas terminer le golem");
        inventory.setItem(0, new ItemStack(Items.PUMPKIN));
        helper.assertTrue(VillageGolemCeremonyController.hasPumpkin(inventory),
                "Une citrouille réelle doit permettre la finition");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void ironShapeUsesFourDistinctBlocks(GameTestHelper helper) {
        var positions = VillageGolemCeremonyController.ironPositions(
                new BlockPos(4, 1, 4));
        helper.assertValueEqual(positions.size(), 4,
                "Le forgeron doit poser exactement quatre blocs");
        helper.assertValueEqual(new HashSet<>(positions).size(), 4,
                "Les quatre positions doivent être distinctes");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericRemainsOptional(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .requiredParticipantsPresent(true, true, false),
                "Le forgeron et le fermier doivent suffire sans clerc");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void villageBuildsOncePerDay(GameTestHelper helper) {
        VillageGolemCeremonySavedData data =
                VillageGolemCeremonySavedData.get(helper.getLevel());
        long key = 14_000_014L;
        long day = 14L;
        helper.assertTrue(data.canBuild(key, day),
                "Le premier golem du jour doit être autorisé");
        data.markBuilt(key, day);
        helper.assertFalse(data.canBuild(key, day),
                "Le second golem du jour doit être refusé");
        helper.assertTrue(data.canBuild(key, day + 1L),
                "Le compteur quotidien doit être réinitialisé");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void pigDetectsPortalFarther(GameTestHelper helper) {
        helper.assertValueEqual(NetherReinforcementController
                        .portalDetectionRadius(
                                NetherReinforcementKind.ZOMBIFIED_PIGLIN),
                NetherRules.NETHER_MESSENGER_PORTAL_RADIUS,
                "Le cochon messager doit utiliser le rayon étendu");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void cowDetectsPortalFarther(GameTestHelper helper) {
        helper.assertValueEqual(NetherReinforcementController
                        .portalDetectionRadius(NetherReinforcementKind.ZOGLIN),
                NetherRules.NETHER_MESSENGER_PORTAL_RADIUS,
                "La vache messagère doit utiliser le rayon étendu");
        helper.succeed();
    }

    @GameTest(batch = "golem_ceremony_nether", templateNamespace = "vanillainstincts", template = "empty")
    public static void unrelatedAnimalsHaveNoPortalMission(GameTestHelper helper) {
        helper.assertValueEqual(NetherReinforcementController
                        .portalDetectionRadius(null), 0,
                "Une espèce sans mission ne doit pas scanner de portail");
        helper.succeed();
    }
}
