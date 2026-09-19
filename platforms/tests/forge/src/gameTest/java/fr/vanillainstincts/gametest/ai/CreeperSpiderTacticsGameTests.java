package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.CreeperBlastEvaluator;
import fr.vanillainstincts.ai.CreeperDiversionController;
import fr.vanillainstincts.ai.CreeperTacticsController;
import fr.vanillainstincts.ai.SpiderDropAssaultController;
import fr.vanillainstincts.ai.SpiderSurfacePlanner;
import fr.vanillainstincts.ai.SpiderWebController;
import fr.vanillainstincts.core.model.CreeperEnvironment;
import fr.vanillainstincts.ai.CreeperEnvironmentEvaluator;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.CreeperRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class CreeperSpiderTacticsGameTests {
    private CreeperSpiderTacticsGameTests() {
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperShelterImprovesHiddenAmbush(
            GameTestHelper helper) {
        double sheltered = CreeperEnvironmentEvaluator.ambushScore(
                CreeperEnvironment.SHELTERED, false);
        double exposed = CreeperEnvironmentEvaluator.ambushScore(
                CreeperEnvironment.OPEN, true);
        helper.assertTrue(sheltered > exposed,
                "Un angle abrité et caché doit être mieux noté");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperStalkOffsetContractsIndoors(
            GameTestHelper helper) {
        helper.assertTrue(CreeperEnvironmentEvaluator.stalkOffset(
                        CreeperEnvironment.OPEN)
                        > CreeperEnvironmentEvaluator.stalkOffset(
                        CreeperEnvironment.CONFINED),
                "Le creeper doit rester plus proche dans un espace confiné");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperBlastDamageFallsWithDistance(
            GameTestHelper helper) {
        double close = CreeperBlastEvaluator.estimatedDamage(
                1.0D, 3.0F, 1.0D);
        double far = CreeperBlastEvaluator.estimatedDamage(
                5.5D, 3.0F, 1.0D);
        helper.assertTrue(close > far && far > 0.0D,
                "Les dégâts prédits doivent diminuer avec la distance");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperBlastExposureControlsDamage(
            GameTestHelper helper) {
        double open = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 1.0D);
        double partial = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 0.25D);
        double blocked = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 0.0D);
        helper.assertTrue(open > partial && partial > blocked,
                "Les blocs doivent réduire la décision d'explosion");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperTacticalFuseContinuesInsideDamageRange(
            GameTestHelper helper) {
        double predictedDamage = CreeperBlastEvaluator.estimatedDamage(
                2.0D, 3.0F, 1.0D);
        helper.assertTrue(CreeperTacticsController.shouldMaintainTacticalFuse(
                        predictedDamage, true, false),
                "La mèche doit continuer lorsque l'explosion peut toucher");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperOrbitDestinationMovesBehindPlayer(
            GameTestHelper helper) {
        Vec3 destination = CreeperTacticsController.blindSideOrbitDestination(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), true);
        helper.assertTrue(destination.z < 0.0D,
                "La destination doit se trouver derrière le regard");
        helper.assertTrue(destination.x < 0.0D,
                "La destination doit aussi être décalée latéralement");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperFeintDistanceIsBounded(
            GameTestHelper helper) {
        helper.assertFalse(CreeperDiversionController.distanceAllowsFeint(9.0D),
                "Une feinte trop proche serait dangereuse");
        helper.assertTrue(CreeperDiversionController.distanceAllowsFeint(49.0D),
                "Une distance moyenne doit permettre une diversion");
        helper.assertFalse(CreeperDiversionController.distanceAllowsFeint(196.0D),
                "Une feinte trop éloignée serait inutile");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void creeperFuseMovementIsWalkingSpeed(
            GameTestHelper helper) {
        Vec3 velocity = CreeperTacticsController.fuseAdvanceVelocity(
                new Vec3(0.30D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertTrue(velocity.x > 0.0D,
                "Le creeper doit continuer à avancer pendant sa mèche");
        helper.assertTrue(velocity.horizontalDistance()
                        <= CreeperRules.CREEPER_FUSE_MAX_HORIZONTAL_SPEED + 0.001D,
                "Le creeper doit marcher et non courir pendant sa mèche");
        helper.assertValueEqual(CreeperRules.CREEPER_FUSE_ADVANCE_SPEED, 1.0D,
                "Le multiplicateur de marche pendant la mèche doit être 1.0");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void spiderSurfacePlannerSelectsCeiling(
            GameTestHelper helper) {
        helper.assertValueEqual(SpiderSurfacePlanner.chooseMode(
                        true, true, 1.0D), SpiderSurfaceMode.CEILING,
                "Le plafond doit être choisi pour une cible en hauteur");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void spiderSurfacePlannerSelectsDescent(
            GameTestHelper helper) {
        helper.assertValueEqual(SpiderSurfacePlanner.chooseMode(
                        true, false, -3.0D), SpiderSurfaceMode.DESCENDING,
                "Une cible basse doit déclencher une descente contrôlée");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void spiderAimLeadsMovingTarget(GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER,
                new BlockPos(2, 1, 2));
        Zombie target = helper.spawn(EntityType.ZOMBIE,
                new BlockPos(7, 1, 2));
        target.setDeltaMovement(0.20D, 0.0D, 0.0D);
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 aim = SpiderWebController.predictedAimPoint(spider, target);
        helper.assertTrue(aim.x > center.x,
                "Le projectile de toile doit anticiper le mouvement");
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", template = "empty")
    public static void spiderDropRequiresSkeletonPassenger(
            GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER,
                new BlockPos(2, 1, 2));
        helper.assertFalse(SpiderDropAssaultController
                        .hasSkeletonPassenger(spider),
                "Une araignée vide ne doit pas simuler une dépose");
        Skeleton skeleton = helper.spawn(EntityType.SKELETON,
                new BlockPos(3, 1, 2));
        skeleton.startRiding(spider, true);
        helper.assertTrue(SpiderDropAssaultController
                        .hasSkeletonPassenger(spider),
                "Un squelette monté doit être reconnu");
        helper.succeed();
    }
}
