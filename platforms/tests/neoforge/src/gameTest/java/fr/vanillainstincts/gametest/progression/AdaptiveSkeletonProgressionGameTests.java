package fr.vanillainstincts.gametest.progression;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.SniperSkeletonController;
import fr.vanillainstincts.core.rules.SniperSkeletonRules;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdaptiveSkeletonProgressionGameTests {
    private AdaptiveSkeletonProgressionGameTests() {
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionRequiresAdvancement(GameTestHelper helper) {
        helper.assertFalse(SniperSkeletonController.shouldPromote(
                        false, true, 0, 0.0D),
                "L'avancement doit être obligatoire");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionRequiresNaturalSpawn(GameTestHelper helper) {
        helper.assertFalse(SniperSkeletonController.shouldPromote(
                        true, false, 0, 0.0D),
                "Un squelette artificiel ne doit pas être promu");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionHonorsLocalLimit(GameTestHelper helper) {
        helper.assertFalse(SniperSkeletonController.shouldPromote(
                        true, true,
                        SniperSkeletonRules.SNIPER_SKELETON_LOCAL_LIMIT, 0.0D),
                "Le plafond local doit être respecté");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionAcceptsLowRoll(GameTestHelper helper) {
        helper.assertTrue(SniperSkeletonController.shouldPromote(
                        true, true, 0, 0.0D),
                "Un tirage valide doit promouvoir le squelette");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionRejectsBoundaryRoll(GameTestHelper helper) {
        helper.assertFalse(SniperSkeletonController.shouldPromote(
                        true, true, 0,
                        SniperSkeletonRules.SNIPER_SKELETON_SPAWN_CHANCE),
                "La borne supérieure doit être exclusive");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void customShotStartsAtFiftyBlocks(
            GameTestHelper helper) {
        double range = SniperSkeletonRules.SNIPER_SKELETON_CUSTOM_MIN_RANGE;
        helper.assertTrue(SniperSkeletonController.canCustomShoot(
                        range * range, true),
                "Le tir longue portée doit commencer à cinquante blocs");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void customShotAcceptsMaximumRange(GameTestHelper helper) {
        double range = SniperSkeletonRules.SNIPER_SKELETON_MAX_RANGE;
        helper.assertTrue(SniperSkeletonController.canCustomShoot(
                        range * range, true),
                "Le tir doit rester possible à la portée maximale");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void customShotRejectsBeyondMaximumRange(
            GameTestHelper helper) {
        double range = SniperSkeletonRules.SNIPER_SKELETON_MAX_RANGE + 0.1D;
        helper.assertFalse(SniperSkeletonController.canCustomShoot(
                        range * range, true),
                "Le tir doit s'arrêter après la portée maximale");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void customShotRequiresLineOfSight(GameTestHelper helper) {
        helper.assertFalse(SniperSkeletonController.canCustomShoot(
                        900.0D, false),
                "Le squelette ne doit pas tirer à travers les murs");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void leadRemainsBounded(GameTestHelper helper) {
        helper.assertTrue(SniperSkeletonController.leadTicks(500.0D)
                        <= SniperSkeletonRules.SNIPER_SKELETON_MAX_LEAD_TICKS,
                "L'anticipation doit rester bornée");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void promotionPersistsOwner(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, 1, 2, 1);
        UUID owner = UUID.randomUUID();
        SniperSkeletonController.promote(skeleton, owner);
        helper.assertTrue(SniperSkeletonController.isSniper(skeleton),
                "Le marqueur tireur doit être persistant");
        helper.assertTrue(owner.equals(
                        SniperSkeletonController.targetId(skeleton)),
                "Le joueur déclencheur doit être conservé");
        helper.succeed();
    }

    @GameTest(batch = "adaptive_skeleton_progression", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void advancementIdUsesVanillaSniperDuel(
            GameTestHelper helper) {
        helper.assertValueEqual(
                SniperSkeletonController.advancementId().toString(),
                "minecraft:adventure/sniper_duel",
                "Le bon avancement vanilla doit être utilisé");
        helper.succeed();
    }
}
