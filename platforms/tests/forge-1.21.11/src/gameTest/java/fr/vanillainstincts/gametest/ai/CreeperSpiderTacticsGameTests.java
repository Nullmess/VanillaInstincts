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
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CreeperSpiderTacticsGameTests {
    private CreeperSpiderTacticsGameTests() {
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperShelterImprovesHiddenAmbush(
            GameTestHelper helper) {
        double sheltered = CreeperEnvironmentEvaluator.ambushScore(
                CreeperEnvironment.SHELTERED, false);
        double exposed = CreeperEnvironmentEvaluator.ambushScore(
                CreeperEnvironment.OPEN, true);
        helper.assertTrue(sheltered > exposed,
                net.minecraft.network.chat.Component.literal("Un angle abrité et caché doit être mieux noté"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperStalkOffsetContractsIndoors(
            GameTestHelper helper) {
        helper.assertTrue(CreeperEnvironmentEvaluator.stalkOffset(
                        CreeperEnvironment.OPEN)
                        > CreeperEnvironmentEvaluator.stalkOffset(
                        CreeperEnvironment.CONFINED),
                net.minecraft.network.chat.Component.literal("Le creeper doit rester plus proche dans un espace confiné"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperBlastDamageFallsWithDistance(
            GameTestHelper helper) {
        double close = CreeperBlastEvaluator.estimatedDamage(
                1.0D, 3.0F, 1.0D);
        double far = CreeperBlastEvaluator.estimatedDamage(
                5.5D, 3.0F, 1.0D);
        helper.assertTrue(close > far && far > 0.0D,
                net.minecraft.network.chat.Component.literal("Les dégâts prédits doivent diminuer avec la distance"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperBlastExposureControlsDamage(
            GameTestHelper helper) {
        double open = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 1.0D);
        double partial = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 0.25D);
        double blocked = CreeperBlastEvaluator.estimatedDamage(
                3.0D, 3.0F, 0.0D);
        helper.assertTrue(open > partial && partial > blocked,
                net.minecraft.network.chat.Component.literal("Les blocs doivent réduire la décision d'explosion"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperTacticalFuseContinuesInsideDamageRange(
            GameTestHelper helper) {
        double predictedDamage = CreeperBlastEvaluator.estimatedDamage(
                2.0D, 3.0F, 1.0D);
        helper.assertTrue(CreeperTacticsController.shouldMaintainTacticalFuse(
                        predictedDamage, true, false),
                net.minecraft.network.chat.Component.literal("La mèche doit continuer lorsque l'explosion peut toucher"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperOrbitDestinationMovesBehindPlayer(
            GameTestHelper helper) {
        Vec3 destination = CreeperTacticsController.blindSideOrbitDestination(
                Vec3.ZERO, new Vec3(0.0D, 0.0D, 1.0D), true);
        helper.assertTrue(destination.z < 0.0D,
                net.minecraft.network.chat.Component.literal("La destination doit se trouver derrière le regard"));
        helper.assertTrue(destination.x < 0.0D,
                net.minecraft.network.chat.Component.literal("La destination doit aussi être décalée latéralement"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperFeintDistanceIsBounded(
            GameTestHelper helper) {
        helper.assertFalse(CreeperDiversionController.distanceAllowsFeint(9.0D),
                net.minecraft.network.chat.Component.literal("Une feinte trop proche serait dangereuse"));
        helper.assertTrue(CreeperDiversionController.distanceAllowsFeint(49.0D),
                net.minecraft.network.chat.Component.literal("Une distance moyenne doit permettre une diversion"));
        helper.assertFalse(CreeperDiversionController.distanceAllowsFeint(196.0D),
                net.minecraft.network.chat.Component.literal("Une feinte trop éloignée serait inutile"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void creeperFuseMovementIsWalkingSpeed(
            GameTestHelper helper) {
        Vec3 velocity = CreeperTacticsController.fuseAdvanceVelocity(
                new Vec3(0.30D, 0.0D, 0.0D),
                new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertTrue(velocity.x > 0.0D,
                net.minecraft.network.chat.Component.literal("Le creeper doit continuer à avancer pendant sa mèche"));
        helper.assertTrue(velocity.horizontalDistance()
                        <= CreeperRules.CREEPER_FUSE_MAX_HORIZONTAL_SPEED + 0.001D,
                net.minecraft.network.chat.Component.literal("Le creeper doit marcher et non courir pendant sa mèche"));
        helper.assertValueEqual(CreeperRules.CREEPER_FUSE_ADVANCE_SPEED, 1.0D,
                net.minecraft.network.chat.Component.literal("Le multiplicateur de marche pendant la mèche doit être 1.0"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePlannerSelectsCeiling(
            GameTestHelper helper) {
        helper.assertValueEqual(SpiderSurfacePlanner.chooseMode(
                        true, true, 1.0D), SpiderSurfaceMode.CEILING,
                net.minecraft.network.chat.Component.literal("Le plafond doit être choisi pour une cible en hauteur"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePlannerSelectsDescent(
            GameTestHelper helper) {
        helper.assertValueEqual(SpiderSurfacePlanner.chooseMode(
                        true, false, -3.0D), SpiderSurfaceMode.DESCENDING,
                net.minecraft.network.chat.Component.literal("Une cible basse doit déclencher une descente contrôlée"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderAimLeadsMovingTarget(GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER,
                new BlockPos(2, 1, 2));
        Zombie target = helper.spawn(EntityType.ZOMBIE,
                new BlockPos(7, 1, 2));
        target.setDeltaMovement(0.20D, 0.0D, 0.0D);
        Vec3 center = target.getBoundingBox().getCenter();
        Vec3 aim = SpiderWebController.predictedAimPoint(spider, target);
        helper.assertTrue(aim.x > center.x,
                net.minecraft.network.chat.Component.literal("Le projectile de toile doit anticiper le mouvement"));
        helper.succeed();
    }

    @GameTest(batch = "creeper_spider_tactics", templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderDropRequiresSkeletonPassenger(
            GameTestHelper helper) {
        Spider spider = helper.spawn(EntityType.SPIDER,
                new BlockPos(2, 1, 2));
        helper.assertFalse(SpiderDropAssaultController
                        .hasSkeletonPassenger(spider),
                net.minecraft.network.chat.Component.literal("Une araignée vide ne doit pas simuler une dépose"));
        Skeleton skeleton = helper.spawn(EntityType.SKELETON,
                new BlockPos(3, 1, 2));
        skeleton.startRiding(spider, true, true);
        helper.assertTrue(SpiderDropAssaultController
                        .hasSkeletonPassenger(spider),
                net.minecraft.network.chat.Component.literal("Un squelette monté doit être reconnu"));
        helper.succeed();
    }
}
