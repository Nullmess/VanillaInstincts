package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.MobMovementPolicy;
import fr.vanillainstincts.ai.NetherReinforcementState;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.NetherReinforcementKind;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementPolicyGameTests {
    private MovementPolicyGameTests() {
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void babyLifecycleIsRemembered(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setBaby(true);
        MobMovementPolicy.observeLifecycle(pig);
        helper.assertTrue(MobMovementPolicy.wasObservedAsBaby(pig),
                "Le passage par l'état bébé doit être mémorisé");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void grownMobBecomesRunEligible(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setBaby(true);
        MobMovementPolicy.observeLifecycle(pig);
        pig.setBaby(false);
        MobMovementPolicy.observeLifecycle(pig);
        helper.assertTrue(MobMovementPolicy.hasGrownFromBaby(pig),
                "Un adulte observé bébé doit devenir éligible à la course");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void ordinaryAndFrightenedPigSpeedPolicy(
            GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        double speed = MobMovementPolicy.navigationSpeed(pig,
                VanillaInstinctsState.PURSUE, 1.35D, helper.getLevel().getGameTime());
        helper.assertValueEqual(speed, 1.0D,
                "Un cochon sans mission doit rester à la vitesse de base");
        MobMovementPolicy.markFrightened(pig,
                helper.getLevel().getGameTime(), 100L);
        double frightenedSpeed = MobMovementPolicy.navigationSpeed(pig,
                VanillaInstinctsState.FLEE, 1.25D,
                helper.getLevel().getGameTime());
        helper.assertValueEqual(frightenedSpeed, 1.25D,
                "Un animal apeuré doit conserver sa course de panique");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void grownPigMayRun(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        pig.setBaby(true);
        MobMovementPolicy.observeLifecycle(pig);
        pig.setBaby(false);
        MobMovementPolicy.observeLifecycle(pig);
        double speed = MobMovementPolicy.navigationSpeed(pig,
                VanillaInstinctsState.PURSUE, 1.25D, helper.getLevel().getGameTime());
        helper.assertValueEqual(speed, 1.25D,
                "Un cochon devenu adulte peut utiliser la course");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void ironGolemMayRun(GameTestHelper helper) {
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM,
                new BlockPos(2, 1, 2));
        double speed = MobMovementPolicy.navigationSpeed(golem,
                VanillaInstinctsState.GOLEM_PURSUIT, 1.18D,
                helper.getLevel().getGameTime());
        helper.assertValueEqual(speed, 1.18D,
                "Le golem doit conserver sa course défensive");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void frightenedVillagerMayRun(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        double speed = MobMovementPolicy.navigationSpeed(villager,
                VanillaInstinctsState.VILLAGE_FLEE, 1.24D,
                helper.getLevel().getGameTime());
        helper.assertValueEqual(speed, 1.24D,
                "Un villageois apeuré doit pouvoir courir");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void ordinaryZombifiedPiglinCannotUseVanillaInstinctsRun(GameTestHelper helper) {
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 2));
        helper.assertFalse(MobMovementPolicy.hasActiveNetherRun(piglin,
                        helper.getLevel().getGameTime()),
                "Un cochon zombifié ordinaire ne doit pas recevoir la course VanillaInstincts");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void messengerPigMayRun(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginMessenger(UUID.randomUUID(), UUID.randomUUID(),
                NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 3), now, 600L, 200L);
        state.save(pig);
        helper.assertTrue(MobMovementPolicy.hasActiveNetherRun(pig, now),
                "Le cochon messager doit pouvoir courir vers le portail");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void messengerCowMayRun(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginMessenger(UUID.randomUUID(), UUID.randomUUID(),
                NetherReinforcementKind.ZOGLIN,
                new BlockPos(2, 1, 3), now, 600L, 200L);
        state.save(cow);
        helper.assertTrue(MobMovementPolicy.hasActiveNetherRun(cow, now),
                "La vache messagère doit pouvoir courir vers le portail");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void recruitedPiglinMayRun(GameTestHelper helper) {
        ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        beginReinforcement(piglin, NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                now);
        helper.assertTrue(MobMovementPolicy.hasActiveNetherRun(piglin, now),
                "Un cochon zombifié recruté doit pouvoir courir");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void recruitedZoglinMayRun(GameTestHelper helper) {
        Zoglin zoglin = helper.spawn(EntityType.ZOGLIN,
                new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        beginReinforcement(zoglin, NetherReinforcementKind.ZOGLIN, now);
        helper.assertTrue(MobMovementPolicy.hasActiveNetherRun(zoglin, now),
                "Un zoglin recruté doit pouvoir courir");
        helper.succeed();
    }

    @GameTest(batch = "movement_policy", templateNamespace = "vanillainstincts", template = "empty")
    public static void expiredMissionRemovesRun(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginMessenger(UUID.randomUUID(), UUID.randomUUID(),
                NetherReinforcementKind.ZOMBIFIED_PIGLIN,
                new BlockPos(2, 1, 3), now, 1L, 1L);
        state.save(pig);
        helper.assertFalse(MobMovementPolicy.hasActiveNetherRun(pig,
                        now + 2L),
                "Une mission expirée ne doit plus autoriser la course");
        helper.succeed();
    }

    private static void beginReinforcement(
            net.minecraft.world.entity.Mob mob,
            NetherReinforcementKind kind, long now) {
        NetherReinforcementState state = new NetherReinforcementState();
        state.beginReinforcement(UUID.randomUUID(), UUID.randomUUID(), kind,
                new BlockPos(2, 1, 3), new BlockPos(2, 1, 3),
                now, now + 600L, 0);
        state.save(mob);
    }
}
