package fr.vanillainstincts.gametest.data;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.AnimalComfortController;
import fr.vanillainstincts.ai.AnimalWelfareController;
import fr.vanillainstincts.ai.GenericMobilityController;
import fr.vanillainstincts.ai.SpiderTacticsController;
import fr.vanillainstincts.ai.ZombieTacticsController;
import fr.vanillainstincts.config.VanillaInstinctsServerConfig;
import fr.vanillainstincts.core.model.AnimalWelfareState;
import fr.vanillainstincts.data.AnimalWelfareProfileManager;
import fr.vanillainstincts.data.FarmerCropRegistry;
import fr.vanillainstincts.data.VillagePathPreferenceManager;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import fr.vanillainstincts.village.FarmerController;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration coverage for fixed48 Complete Mob Behaviour 3.0. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WelfareDataDrivenBehaviorGameTests {
    private WelfareDataDrivenBehaviorGameTests() {
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalWelfareProfilesLoad(GameTestHelper helper) {
        helper.assertTrue(AnimalWelfareProfileManager.profileCount() >= 2,
                "Animal Welfare 3.0 doit charger les profils par espèce");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void livestockUsesConfiguredWelfareProfile(GameTestHelper helper) {
        clearAndFloor(helper);
        Cow cow = spawnCow(helper, new BlockPos(3, 1, 3));
        var profile = AnimalWelfareProfileManager.profileFor(cow);
        helper.assertValueEqual(profile.id().getPath(), "livestock",
                "La vache doit utiliser le profil Animal Welfare livestock");
        helper.assertTrue(profile.needsHerd() && profile.needsShelter(),
                "Le profil livestock doit conserver ses besoins configurés");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void chickenWelfareCanDisableWaterNeed(GameTestHelper helper) {
        clearAndFloor(helper);
        Chicken chicken = helper.spawn(EntityType.CHICKEN,
                new BlockPos(3, 1, 3));
        chicken.setNoAi(true);
        var profile = AnimalWelfareProfileManager.profileFor(chicken);
        helper.assertValueEqual(profile.id().getPath(), "chickens",
                "La poule doit utiliser son profil dédié");
        helper.assertFalse(profile.needsNearbyWater(),
                "Les besoins d'une espèce doivent être réellement configurables");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalWelfareEntersStressState(GameTestHelper helper) {
        clearAndFloor(helper);
        Cow cow = spawnCow(helper, new BlockPos(3, 1, 3));
        long now = helper.getLevel().getGameTime();
        cow.setHealth(Math.max(1.0F, cow.getMaxHealth() * 0.35F));
        AnimalComfortController.recordFear(cow,
                cow.position().add(3.0D, 0.0D, 0.0D), now);
        var snapshot = AnimalWelfareController.maintain(cow,
                helper.getLevel(), now);
        helper.assertValueEqual(snapshot.state(), AnimalWelfareState.STRESSED,
                "Une peur persistante et une mauvaise santé doivent créer du stress");
        helper.assertTrue(snapshot.score() < snapshot.profile().stressThreshold(),
                "L'état STRESSED doit être justifié par le score de bien-être");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalWelfareUsesRecoveryState(GameTestHelper helper) {
        clearAndFloor(helper);
        Cow cow = spawnCow(helper, new BlockPos(3, 1, 3));
        long now = helper.getLevel().getGameTime();
        cow.setHealth(Math.max(1.0F, cow.getMaxHealth() * 0.35F));
        AnimalComfortController.recordFear(cow,
                cow.position().add(3.0D, 0.0D, 0.0D), now);
        AnimalWelfareController.maintain(cow, helper.getLevel(), now);

        cow.getPersistentData().remove("vanillainstincts_comfort_fear_until");
        cow.getPersistentData().remove("vanillainstincts_comfort_fear_pos");
        cow.setHealth(cow.getMaxHealth());
        var recovery = AnimalWelfareController.maintain(cow,
                helper.getLevel(), now + 1L);
        helper.assertValueEqual(recovery.state(), AnimalWelfareState.RECOVERING,
                "Le bien-être doit récupérer progressivement au lieu de passer instantanément à HEALTHY");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void stressedAnimalCannotBreed(GameTestHelper helper) {
        clearAndFloor(helper);
        Cow cow = spawnCow(helper, new BlockPos(3, 1, 3));
        long now = helper.getLevel().getGameTime();
        cow.setHealth(Math.max(1.0F, cow.getMaxHealth() * 0.35F));
        AnimalComfortController.recordFear(cow,
                cow.position().add(3.0D, 0.0D, 0.0D), now);
        AnimalWelfareController.maintain(cow, helper.getLevel(), now);
        helper.assertFalse(AnimalWelfareController.allowsBreeding(cow),
                "Un animal réellement stressé ne doit pas être considéré prêt à se reproduire");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerRegistryStillLoadsCrops(GameTestHelper helper) {
        helper.assertTrue(FarmerCropRegistry.definitionCount() >= 4,
                "Farmer 3.0 doit conserver le registre de cultures data-driven");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerSoilUsesExtensibleTag(GameTestHelper helper) {
        helper.assertTrue(Blocks.FARMLAND.defaultBlockState()
                        .is(VanillaInstinctsTags.FARMER_PLANTABLE_ON),
                "Le sol vanilla doit provenir du tag farmer_plantable_on extensible");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerHoeImprovesWorkRecovery(GameTestHelper helper) {
        clearAndFloor(helper);
        Villager farmer = spawnVillager(helper, new BlockPos(3, 1, 3));
        int withoutTool = FarmerController.farmerActionCooldown(farmer);
        farmer.getInventory().setItem(0, new ItemStack(Items.WOODEN_HOE));
        int withTool = FarmerController.farmerActionCooldown(farmer);
        helper.assertTrue(withTool < withoutTool,
                "Une houe reconnue par tag doit améliorer le rythme du fermier");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void villagerShareableFoodIsDataDriven(GameTestHelper helper) {
        helper.assertTrue(new ItemStack(Items.BREAD)
                        .is(VanillaInstinctsTags.VILLAGER_SHAREABLE_FOOD),
                "La nourriture partageable vanilla doit provenir du tag extensible");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void villagerAvoidNearPenalizesLava(GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos candidate = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(candidate.east(2), Blocks.LAVA.defaultBlockState(), 3);
        double penalty = VillagePathPreferenceManager.proximityPreference(
                VillagerProfession.FARMER, level, candidate);
        helper.assertTrue(penalty < 0.0D,
                "avoid_near doit pénaliser une destination proche de lave");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombieUsesContextualPursuitSprint(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager target = spawnVillager(helper, new BlockPos(7, 1, 4));
        zombie.setTarget(target);
        zombie.setOnGround(true);
        ZombieTacticsController.maintain(zombie, helper.getLevel(),
                helper.getLevel().getGameTime());
        helper.assertTrue(zombie.isSprinting(),
                "Un zombie sain avec cible visible à moyenne distance doit sprinter contextuellement");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderRainReducesAdhesion(GameTestHelper helper) {
        helper.assertTrue(SpiderTacticsController.rainAdhesionFactor(true)
                        < SpiderTacticsController.rainAdhesionFactor(false),
                "La pluie doit réduire l'adhérence tactique des araignées");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderCanFindNearbyShore(GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        Spider spider = helper.spawn(EntityType.SPIDER,
                new BlockPos(4, 1, 4));
        spider.setNoAi(true);
        level.setBlock(spider.blockPosition(), Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(spider.blockPosition().east(),
                Blocks.WATER.defaultBlockState(), 3);
        Optional<Vec3> shore = GenericMobilityController.nearestShore(spider,
                level);
        helper.assertTrue(shore.isPresent(),
                "Generic Mobility doit donner une vraie recherche de rive aux araignées");
        helper.succeed();
    }

    @GameTest(batch = "welfare_data_driven_behavior",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperTacticsAcceptConfiguredNormalHeight(GameTestHelper helper) {
        helper.assertTrue(VanillaInstinctsServerConfig.creeperTacticsAllowedAtY(64),
                "Les limites Y configurables ne doivent pas désactiver le creeper aux hauteurs normales par défaut");
        helper.succeed();
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

    private static Cow spawnCow(GameTestHelper helper, BlockPos pos) {
        Cow cow = helper.spawn(EntityType.COW, pos);
        cow.setNoAi(true);
        cow.setDeltaMovement(Vec3.ZERO);
        return cow;
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        zombie.setNoAi(true);
        zombie.setDeltaMovement(Vec3.ZERO);
        return zombie;
    }

    private static Villager spawnVillager(GameTestHelper helper, BlockPos pos) {
        Villager villager = helper.spawn(EntityType.VILLAGER, pos);
        villager.setNoAi(true);
        villager.setDeltaMovement(Vec3.ZERO);
        return villager;
    }
}
