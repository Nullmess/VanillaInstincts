package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.VillageConstructionAccess;
import fr.vanillainstincts.village.VillageConstructionCapability;
import fr.vanillainstincts.village.VillageEvolutionController;
import fr.vanillainstincts.village.VillageRoadPlanner;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageBuildingGameTests {
    private VillageBuildingGameTests() {
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void unemployedBuildsSimpleHouse(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildSimpleHouse(
                        VillagerProfessionCompat.value(VillagerProfession.NONE)),
                net.minecraft.network.chat.Component.literal("Le villageois sans profession doit bâtir les maisons simples"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericCanHelpBuildSimpleHouse(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildSimpleHouse(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC)),
                net.minecraft.network.chat.Component.literal("Un clerc disponible doit pouvoir aider sur une maison simple"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void unemployedBuildsRoad(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildRoad(
                        VillagerProfessionCompat.value(VillagerProfession.NONE)),
                net.minecraft.network.chat.Component.literal("Le villageois ordinaire doit prolonger les chemins"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void professionalCanHelpBuildRoad(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildRoad(
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH)),
                net.minecraft.network.chat.Component.literal("Un professionnel disponible doit pouvoir aider sur un chemin"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericRepairsClericBuilding(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC), VillagerProfessionCompat.value(VillagerProfession.CLERIC)),
                net.minecraft.network.chat.Component.literal("Le clerc doit construire et réparer le temple vanilla"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void clericCanHelpRepairForge(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH)),
                net.minecraft.network.chat.Component.literal("Le clerc doit pouvoir aider à réparer une forge si nécessaire"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void weaponsmithRepairsOwnForge(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH)),
                net.minecraft.network.chat.Component.literal("Le fabricant d'armes doit réparer sa forge vanilla"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void smithSpecialtiesCanCooperate(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.ARMORER),
                        VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH)),
                net.minecraft.network.chat.Component.literal("Les métiers de forge doivent pouvoir coopérer sur un atelier"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void nearbyWorkPositionCanReachBlock(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionAccess.canWorkFrom(
                        new BlockPos(0, 0, 0), new BlockPos(3, 0, 0)),
                net.minecraft.network.chat.Component.literal("Un poste proche doit permettre de poser le bloc"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void distantWorkPositionCannotReachBlock(GameTestHelper helper) {
        helper.assertFalse(VillageConstructionAccess.canWorkFrom(
                        new BlockPos(0, 0, 0), new BlockPos(7, 0, 0)),
                net.minecraft.network.chat.Component.literal("Un poste trop éloigné ne doit pas être accepté"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void dirtPathIsRecognizedAsRoad(GameTestHelper helper) {
        helper.assertTrue(VillageRoadPlanner.isPath(
                        Blocks.DIRT_PATH.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Le planificateur doit favoriser les chemins existants"));
        BlockPos first = helper.absolutePos(new BlockPos(2, 2, 2));
        for (int x = 0; x < 3; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos feet = first.offset(x, 0, z);
                helper.getLevel().setBlockAndUpdate(feet.below(),
                        Blocks.GRASS_BLOCK.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(feet,
                        Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(feet.above(),
                        Blocks.AIR.defaultBlockState());
            }
        }
        List<BlockPos> centerline = List.of(first, first.east(),
                first.east(2));
        List<BlockPos> widened = VillageRoadPlanner.widen(helper.getLevel(),
                centerline, VillageConstructionRules.VILLAGE_ROAD_WIDTH);
        helper.assertValueEqual(VillageConstructionRules.VILLAGE_ROAD_WIDTH, 3,
                net.minecraft.network.chat.Component.literal("Les chemins construits doivent mesurer trois blocs de large"));
        helper.assertValueEqual(widened.size(), 9,
                net.minecraft.network.chat.Component.literal("Une route droite de trois blocs doit produire neuf surfaces"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void houseWallIsNotRoad(GameTestHelper helper) {
        helper.assertFalse(VillageRoadPlanner.isPath(
                        Blocks.OAK_PLANKS.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Le planificateur ne doit pas tracer une route sur un mur"));
        helper.succeed();
    }
    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void vanillaTempleBelongsToCleric(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.professionFromTemplateName(
                        Identifier.fromNamespaceAndPath("minecraft",
                                "village/plains/houses/plains_temple_3"))
                        == VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                net.minecraft.network.chat.Component.literal("Un temple vanilla doit appartenir au clerc"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void vanillaForgeBelongsToWeaponsmith(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.professionFromTemplateName(
                        Identifier.fromNamespaceAndPath("minecraft",
                                "village/plains/houses/plains_weaponsmith_1"))
                        == VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH),
                net.minecraft.network.chat.Component.literal("La forge vanilla d'armes doit appartenir au fabricant d'armes"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void vanillaFarmBelongsToFarmer(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.professionFromTemplateName(
                        Identifier.fromNamespaceAndPath("minecraft",
                                "village/desert/houses/desert_farm_1"))
                        == VillagerProfessionCompat.value(VillagerProfession.FARMER),
                net.minecraft.network.chat.Component.literal("Une ferme vanilla doit appartenir au fermier"));
        helper.succeed();
    }

    @GameTest(batch = "village_building", templateNamespace = "vanillainstincts", template = "empty")
    public static void vanillaSmallHouseBelongsToUnemployed(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.professionFromTemplateName(
                        Identifier.fromNamespaceAndPath("minecraft",
                                "village/taiga/houses/taiga_small_house_2"))
                        == VillagerProfessionCompat.value(VillagerProfession.NONE),
                net.minecraft.network.chat.Component.literal("Une maison vanilla simple doit appartenir aux sans-profession"));
        helper.succeed();
    }

}
