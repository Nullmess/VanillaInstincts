package fr.vanillainstincts.gametest.data;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.permission.PermissionDecision;
import fr.vanillainstincts.core.permission.WorldActionType;
import fr.vanillainstincts.ai.MobPerceptionMemory;
import fr.vanillainstincts.data.FarmerCropRegistry;
import fr.vanillainstincts.data.PerceptionProfileManager;
import fr.vanillainstincts.data.VillagePathPreferenceManager;
import fr.vanillainstincts.village.FarmerController;
import fr.vanillainstincts.village.VillagePathEvaluator;
import fr.vanillainstincts.permission.WorldPermissionService;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration coverage for the fixed44 AI Foundations 2.0 datapack layer. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataDrivenAiGameTests {
    private DataDrivenAiGameTests() {
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void villagerPathRulesLoadFromDatapack(GameTestHelper helper) {
        helper.assertTrue(VillagePathPreferenceManager.ruleCount() >= 5,
                "Les préférences de chemin intégrées doivent être chargées");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void dataDrivenFarmerPrefersFarmland(GameTestHelper helper) {
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

        double farmerScore = VillagePathEvaluator.professionPreference(
                farmer, level, farmerFeet);
        double librarianScore = VillagePathEvaluator.professionPreference(
                librarian, level, librarianFeet);
        helper.assertTrue(farmerScore > 0.0D,
                "Le datapack doit favoriser la farmland pour le fermier");
        helper.assertTrue(librarianScore < 0.0D,
                "Le datapack doit décourager la farmland hors métier agricole");
        helper.assertTrue(farmerScore > librarianScore,
                "Les coûts de métier doivent produire un choix distinct");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void fishermanUsesDynamicWaterBonus(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(feet.east(), Blocks.WATER.defaultBlockState(), 3);
        Villager fisherman = helper.spawn(EntityType.VILLAGER,
                new BlockPos(3, 1, 3));
        fisherman.setVillagerData(fisherman.getVillagerData()
                .setProfession(VillagerProfession.FISHERMAN));

        helper.assertTrue(VillagePathEvaluator.professionPreference(
                        fisherman, level, feet) > 0.0D,
                "Le bonus d'eau dynamique du pêcheur doit rester actif");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void perceptionProfilesLoadAndDifferentiateMobs(
            GameTestHelper helper) {
        helper.assertTrue(PerceptionProfileManager.profileRuleCount() >= 7,
                "Les profils de perception intégrés doivent être chargés");
        var zombie = PerceptionProfileManager.profileFor(EntityType.ZOMBIE);
        var creeper = PerceptionProfileManager.profileFor(EntityType.CREEPER);
        helper.assertTrue(zombie.visualRange() > creeper.visualRange(),
                "Les espèces doivent pouvoir avoir des portées distinctes");
        helper.assertTrue(zombie.hardDifficultyMultiplier()
                        > zombie.easyDifficultyMultiplier(),
                "La difficulté doit pouvoir moduler la perception");
        helper.assertTrue(zombie.sneakingVisualMultiplier() < 1.0D
                        && zombie.sneakingHearingMultiplier() < 1.0D,
                "La furtivité doit réduire vision et audition");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void perceptionNeverSeesThroughSolidWall(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        Villager target = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        level.setBlock(helper.absolutePos(new BlockPos(3, 1, 2)),
                Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(3, 2, 2)),
                Blocks.STONE.defaultBlockState(), 3);

        helper.assertFalse(MobPerceptionMemory.canSee(zombie, target),
                "Perception 2.0 ne doit jamais donner de vision à travers un mur");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerCropDefinitionsLoadFromDatapack(
            GameTestHelper helper) {
        helper.assertTrue(FarmerCropRegistry.definitionCount() >= 4,
                "Les quatre cultures vanilla doivent être enregistrées");
        helper.assertValueEqual(FarmerCropRegistry.cropBlockForItem(
                        Items.WHEAT_SEEDS), Blocks.WHEAT,
                "Le registre doit associer les graines de blé au blé");
        helper.assertValueEqual(FarmerCropRegistry.cropBlockForItem(
                        Items.CARROT), Blocks.CARROTS,
                "Le registre doit associer la carotte à sa culture");
        helper.succeed();
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerPlantsRegisteredCrop(GameTestHelper helper) {
        runWorldMutationTest(helper, level -> {
            BlockPos cropPos = helper.absolutePos(new BlockPos(3, 1, 3));
            // Do not rely on whatever state existed in the generated test world:
            // Farmer Compatibility specifically requires an empty crop cell.
            level.setBlock(cropPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockState(cropPos).isAir(),
                    "La case de plantation du fixture doit être vide");
            Villager farmer = helper.spawn(EntityType.VILLAGER,
                    new BlockPos(1, 1, 1));
            farmer.setVillagerData(farmer.getVillagerData()
                    .setProfession(VillagerProfession.FARMER));
            // setItem is deterministic for this fixture: the test validates
            // Farmer Compatibility, not SimpleContainer insertion heuristics.
            farmer.getInventory().setItem(0,
                    new ItemStack(Items.WHEAT_SEEDS, 32));

            helper.assertValueEqual(FarmerController.totalPlantableCount(
                            farmer.getInventory()), 32,
                    "Le stock de graines du fixture doit être visible");
            helper.assertTrue(FarmerCropRegistry.canPlant(
                            Items.WHEAT_SEEDS, level, cropPos),
                    "Le registre doit accepter la plantation sur le sol taggé");
            helper.assertTrue(FarmerController.findPlantingSlot(
                            farmer.getInventory(), level, cropPos, 2) >= 0,
                    "Le fermier doit trouver un emplacement de graines utilisable");
            helper.assertValueEqual(WorldPermissionService.check(level, farmer,
                            WorldActionType.PLACE_BLOCK, cropPos,
                            FarmerCropRegistry.plantingState(Items.WHEAT_SEEDS)),
                    PermissionDecision.ALLOWED,
                    "La passerelle de permission doit autoriser cette plantation");
            helper.assertTrue(FarmerController.performAction(
                            level, farmer, cropPos, true),
                    "Le fermier doit planter une culture enregistrée");
            helper.assertTrue(level.getBlockState(cropPos).is(Blocks.WHEAT),
                    "La plantation doit créer le bloc de culture enregistré");
        });
    }

    @GameTest(batch = "data_driven_ai",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerHarvestsAndReplantsRegisteredCrop(
            GameTestHelper helper) {
        runWorldMutationTest(helper, level -> {
            BlockPos cropPos = helper.absolutePos(new BlockPos(3, 1, 3));
            level.setBlock(cropPos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
            CropBlock wheat = (CropBlock) Blocks.WHEAT;
            level.setBlock(cropPos, wheat.getStateForAge(7), 3);
            Villager farmer = helper.spawn(EntityType.VILLAGER,
                    new BlockPos(1, 1, 1));
            farmer.setVillagerData(farmer.getVillagerData()
                    .setProfession(VillagerProfession.FARMER));

            helper.assertTrue(FarmerController.performAction(
                            level, farmer, cropPos, true),
                    "Le fermier doit récolter une culture mûre enregistrée");
            helper.assertTrue(level.getBlockState(cropPos).is(Blocks.WHEAT),
                    "Une culture replantable doit rester plantée après récolte");
            helper.assertTrue(level.getBlockState(cropPos)
                            .equals(wheat.getStateForAge(0)),
                    "La culture replantée doit revenir à son âge minimal");
        });
    }

    /**
     * World-mutating GameTests must not depend on where Mojang's GameTest
     * server placed the suite relative to the world's shared spawn. Production
     * keeps spawn protection enabled; only this fixture disables it, exactly as
     * the dedicated permission tests already do.
     */
    private static void runWorldMutationTest(
            GameTestHelper helper, Consumer<ServerLevel> assertions) {
        ServerLevel level = helper.getLevel();
        boolean originalMobGriefing = level.getGameRules()
                .getRule(GameRules.RULE_MOBGRIEFING).get();
        try {
            RuntimeConfig.install(ConfigSnapshot.builder()
                    .allowWorldChanges(true)
                    .allowItemChanges(true)
                    .respectMobGriefing(true)
                    .protectSpawnArea(false)
                    .spawnProtectionRadius(0)
                    .build());
            WorldPermissionService.clearLevel(level);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(true, level.getServer());
            assertions.accept(level);
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(level);
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING)
                    .set(originalMobGriefing, level.getServer());
        }
    }
}
