package fr.vanillainstincts.gametest.possession;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.possession.VanillaMobPossessionCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Mob Possession 1.1 catalog and movement-contract coverage. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PossessionCatalogGameTests {
    private PossessionCatalogGameTests() {
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void possessionCatalogAuditsAllVanillaMobs(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.vanillaMobIds().size(), 82,
                "Le catalogue de possession doit rester explicite et complet");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void possessionCatalogRecognizesRealVanillaMob(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setNoAi(true);
        helper.assertTrue(VanillaMobPossessionCatalog.coversVanillaMob(zombie),
                "Un zombie vanilla réel doit être reconnu par le catalogue");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombieUsesGroundMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("zombie"),
                VanillaMobPossessionCatalog.Movement.GROUND,
                "Le zombie doit conserver une locomotion terrestre");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderUsesClimbingMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("spider"),
                VanillaMobPossessionCatalog.Movement.CLIMBING,
                "L'araignée doit conserver sa locomotion grimpante");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void codUsesAquaticMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("cod"),
                VanillaMobPossessionCatalog.Movement.AQUATIC,
                "Le cabillaud ne doit pas obtenir une marche terrestre");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void axolotlUsesAmphibiousMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("axolotl"),
                VanillaMobPossessionCatalog.Movement.AMPHIBIOUS,
                "L'axolotl doit rester mobile dans l'eau et sur terre");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void allayUsesFlyingMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("allay"),
                VanillaMobPossessionCatalog.Movement.FLYING,
                "L'allay doit conserver le vol 3D");
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("parrot"),
                VanillaMobPossessionCatalog.Movement.FLYING,
                "Le perroquet doit conserver le vol 3D");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void dragonUsesNativePhaseMovement(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("ender_dragon"),
                VanillaMobPossessionCatalog.Movement.DRAGON,
                "Le Dragon doit passer par ses phases de vol vanilla");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void shulkerRemainsStationary(GameTestHelper helper) {
        helper.assertValueEqual(VanillaMobPossessionCatalog.movement("shulker"),
                VanillaMobPossessionCatalog.Movement.STATIONARY,
                "La possession ne doit pas donner de jambes au Shulker");
        helper.succeed();
    }

    @GameTest(batch = "possession_catalog",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void catalogIncludesBossesAndUnusedVanillaMobs(GameTestHelper helper) {
        helper.assertTrue(VanillaMobPossessionCatalog.coversVanillaId("ender_dragon"),
                "Le Dragon doit être explicitement audité");
        helper.assertTrue(VanillaMobPossessionCatalog.coversVanillaId("wither"),
                "Le Wither doit être explicitement audité");
        helper.assertTrue(VanillaMobPossessionCatalog.coversVanillaId("giant"),
                "Le Giant vanilla doit être explicitement audité");
        helper.assertTrue(VanillaMobPossessionCatalog.coversVanillaId("illusioner"),
                "L'Illusioner vanilla doit être explicitement audité");
        helper.succeed();
    }
}
