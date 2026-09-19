package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.ChildVillageAlertController;
import fr.vanillainstincts.village.GolemPatrolController;
import fr.vanillainstincts.village.VillageAssemblyController;
import fr.vanillainstincts.village.VillageChestIntrusionController;
import fr.vanillainstincts.village.VillagerProfessionController;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageAlertPatrolGameTests {
    private VillageAlertPatrolGameTests() {
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void babySeeksAvailableAdult(GameTestHelper helper) {
        helper.assertTrue(ChildVillageAlertController.shouldSeekAdult(
                true, true, 40L),
                net.minecraft.network.chat.Component.literal("Un enfant doit d'abord chercher un adulte disponible"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void babyStopsAdultSearchAtDeadline(GameTestHelper helper) {
        helper.assertFalse(ChildVillageAlertController.shouldSeekAdult(
                true, true, 200L),
                net.minecraft.network.chat.Component.literal("Après le délai, l'enfant doit chercher directement un golem"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void adultNeverUsesChildSearch(GameTestHelper helper) {
        helper.assertFalse(ChildVillageAlertController.shouldSeekAdult(
                false, true, 0L),
                net.minecraft.network.chat.Component.literal("Un adulte ne doit pas entrer dans la chaîne d'alerte enfant"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void missingAdultSkipsAdultSearch(GameTestHelper helper) {
        helper.assertFalse(ChildVillageAlertController.shouldSeekAdult(
                true, false, 20L),
                net.minecraft.network.chat.Component.literal("Sans adulte disponible, l'enfant doit pouvoir chercher le golem"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void patrolSlotStaysInsideEightSectors(GameTestHelper helper) {
        int slot = GolemPatrolController.preferredSlot(12345);
        helper.assertTrue(slot >= 0 && slot < 8,
                net.minecraft.network.chat.Component.literal("Une patrouille doit rester dans les huit secteurs"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void patrolSlotIsDeterministic(GameTestHelper helper) {
        helper.assertValueEqual(GolemPatrolController.preferredSlot(41),
                GolemPatrolController.preferredSlot(41),
                net.minecraft.network.chat.Component.literal("Le même golem doit conserver son secteur préféré"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void neighboringGolemsPreferDifferentSectors(GameTestHelper helper) {
        helper.assertTrue(GolemPatrolController.preferredSlot(41)
                        != GolemPatrolController.preferredSlot(42),
                net.minecraft.network.chat.Component.literal("Deux identifiants voisins doivent préférer des secteurs distincts"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithFindsRealIronIngot(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
        helper.assertValueEqual(VillagerProfessionController.findIronSlot(
                inventory), 1,
                net.minecraft.network.chat.Component.literal("Le forgeron doit trouver un lingot réellement présent"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithCannotCreateMissingIron(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(0, new ItemStack(Items.COAL));
        helper.assertValueEqual(VillagerProfessionController.findIronSlot(
                inventory), -1,
                net.minecraft.network.chat.Component.literal("Sans lingot, aucune réparation ne doit être inventée"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void assemblyRequestIsActiveBeforeDeadline(GameTestHelper helper) {
        helper.assertTrue(VillageAssemblyController.isRequestActive(
                120L, 80L),
                net.minecraft.network.chat.Component.literal("La demande de golem doit rester active avant sa fin"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void assemblyRequestExpiresCleanly(GameTestHelper helper) {
        helper.assertFalse(VillageAssemblyController.isRequestActive(
                120L, 120L),
                net.minecraft.network.chat.Component.literal("La demande doit expirer à son échéance"));
        helper.succeed();
    }

    @GameTest(batch = "village_alert_patrol", templateNamespace = "vanillainstincts", template = "empty")
    public static void chestAlertRequiresHouseContext(GameTestHelper helper) {
        helper.assertTrue(VillageChestIntrusionController.isHouseContext(
                        true, false)
                        && VillageChestIntrusionController.isHouseContext(
                        false, true)
                        && !VillageChestIntrusionController.isHouseContext(
                        false, false),
                net.minecraft.network.chat.Component.literal("Un coffre ne doit alerter que près d'une maison ou à l'intérieur"));
        helper.succeed();
    }
}
