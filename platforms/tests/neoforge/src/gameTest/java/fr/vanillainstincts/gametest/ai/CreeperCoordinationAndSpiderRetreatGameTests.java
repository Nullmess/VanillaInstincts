package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.CreeperCoordinationController;
import fr.vanillainstincts.ai.CreeperPassageController;
import fr.vanillainstincts.ai.SpiderRetreatController;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreeperCoordinationAndSpiderRetreatGameTests {
    private CreeperCoordinationAndSpiderRetreatGameTests() {
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void badlyInjuredSpiderMayRetreat(GameTestHelper helper) {
        helper.assertTrue(SpiderRetreatController.shouldRetreat(
                3.0D, 16.0D, 0.1D),
                "Une araignée très blessée peut décider de fuir");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void healthySpiderDoesNotRetreat(GameTestHelper helper) {
        helper.assertFalse(SpiderRetreatController.shouldRetreat(
                12.0D, 16.0D, 0.0D),
                "Une araignée en bonne santé ne doit pas utiliser cette fuite");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void retreatRemainsOccasional(GameTestHelper helper) {
        helper.assertFalse(SpiderRetreatController.shouldRetreat(
                3.0D, 16.0D, 1.0D),
                "La fuite blessée ne doit pas se déclencher à chaque combat");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void invalidHealthCannotTriggerRetreat(GameTestHelper helper) {
        helper.assertFalse(SpiderRetreatController.shouldRetreat(
                0.0D, 0.0D, 0.0D),
                "Une valeur de santé invalide doit être refusée proprement");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void ignitedNeighborDelaysFuse(GameTestHelper helper) {
        helper.assertTrue(CreeperCoordinationController.shouldDelayFuse(true),
                "Un creeper doit attendre si un voisin est déjà allumé");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void isolatedCreeperMayFuse(GameTestHelper helper) {
        helper.assertFalse(CreeperCoordinationController.shouldDelayFuse(false),
                "Un creeper isolé ne doit pas être bloqué par la coordination");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void realIgnitedNeighborIsDetected(GameTestHelper helper) {
        Creeper first = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        Creeper second = helper.spawn(EntityType.CREEPER,
                new BlockPos(3, 1, 2));
        second.ignite();
        helper.assertTrue(CreeperCoordinationController.hasIgnitedNeighbor(
                        first, helper.getLevel()),
                "La détection doit utiliser la vraie mèche du voisin");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void distantUnlitCreeperIsIgnored(GameTestHelper helper) {
        Creeper first = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        helper.spawn(EntityType.CREEPER, new BlockPos(8, 1, 2));
        helper.assertFalse(CreeperCoordinationController.hasIgnitedNeighbor(
                        first, helper.getLevel()),
                "Un creeper éloigné et éteint ne doit pas retarder la mèche");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void openDoorIsNeverExplosionObstacle(GameTestHelper helper) {
        Creeper creeper = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        BlockPos door = creeper.blockPosition().east();
        helper.getLevel().setBlockAndUpdate(door,
                Blocks.OAK_DOOR.defaultBlockState().setValue(
                        BlockStateProperties.OPEN, true));
        helper.assertFalse(CreeperPassageController.canOpenWithExplosion(
                        helper.getLevel(), creeper, door),
                "Une porte déjà ouverte ne doit jamais justifier une explosion");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void openingMustLeadTowardTarget(GameTestHelper helper) {
        Creeper creeper = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        BlockPos obstacle = creeper.blockPosition().east();
        BlockPos beyond = obstacle.east();
        helper.getLevel().getChunkAt(beyond);
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(beyond.above(), Blocks.AIR.defaultBlockState());
        helper.assertTrue(CreeperPassageController.opensTowardDestination(
                        helper.getLevel(), creeper, obstacle,
                        creeper.position().add(6.0D, 0.0D, 0.0D)),
                "L'espace derrière l'obstacle doit réellement rapprocher de la cible");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void blockedSpaceDoesNotOpenPassage(GameTestHelper helper) {
        Creeper creeper = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        BlockPos obstacle = creeper.blockPosition().east();
        helper.getLevel().setBlockAndUpdate(obstacle, Blocks.DIRT.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(obstacle.east(), Blocks.STONE.defaultBlockState());
        helper.assertFalse(CreeperPassageController.opensTowardDestination(
                        helper.getLevel(), creeper, obstacle,
                        creeper.position().add(6.0D, 0.0D, 0.0D)),
                "Un mur restant derrière l'explosion ne doit pas valider le passage");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_coordination", templateNamespace = "vanillainstincts", template = "empty")
    public static void zeroDirectionCannotOpenPassage(GameTestHelper helper) {
        Creeper creeper = helper.spawn(EntityType.CREEPER,
                new BlockPos(2, 1, 2));
        helper.assertFalse(CreeperPassageController.opensTowardDestination(
                        helper.getLevel(), creeper, creeper.blockPosition(),
                        creeper.position()),
                "Une destination sans direction ne doit jamais déclencher l'explosion");
        helper.succeed();
    }
}
