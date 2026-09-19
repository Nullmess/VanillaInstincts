package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.GenericMobilityController;
import fr.vanillainstincts.ai.MobEcologyController;
import fr.vanillainstincts.ai.MobPerceptionMemory;
import fr.vanillainstincts.ai.MobStimulusSystem;
import fr.vanillainstincts.ai.TargetAcquisitionController;
import fr.vanillainstincts.core.model.MobRelationType;
import fr.vanillainstincts.core.model.StimulusType;
import fr.vanillainstincts.data.MobRelationManager;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

/** Integration coverage for fixed47 Intelligence Core 3.0. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EcologyAcquisitionMobilityGameTests {
    private EcologyAcquisitionMobilityGameTests() {
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void mobRelationsLoadFromDatapack(GameTestHelper helper) {
        helper.assertTrue(MobRelationManager.ruleCount() >= 6,
                net.minecraft.network.chat.Component.literal("Intelligence Core 3.0 doit charger les relations mob data-driven"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombieVillagerRelationIsHunt(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager villager = spawnVillager(helper, new BlockPos(6, 1, 4));
        var relation = MobRelationManager.relation(zombie, villager);
        helper.assertTrue(relation.isPresent(),
                net.minecraft.network.chat.Component.literal("Une relation zombie -> villageois doit exister"));
        helper.assertValueEqual(relation.get().relation(), MobRelationType.HUNT,
                net.minecraft.network.chat.Component.literal("Le zombie doit considérer le villageois comme une cible écologique"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void acquisitionFindsVisibleUntargetedEntity(
            GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager villager = spawnVillager(helper, new BlockPos(7, 1, 4));
        helper.assertTrue(zombie.getTarget() == null,
                net.minecraft.network.chat.Component.literal("Le fixture doit commencer sans cible vanilla"));
        var result = TargetAcquisitionController.findBestTarget(zombie,
                helper.getLevel());
        helper.assertTrue(result.target().orElse(null) == villager,
                net.minecraft.network.chat.Component.literal("Acquisition 3.0 doit pouvoir remarquer une cible visible sans mob.getTarget préalable"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void acquisitionNeverTargetsThroughWall(GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        spawnVillager(helper, new BlockPos(7, 1, 4));
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 7; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(4, y, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(TargetAcquisitionController.findBestTarget(zombie,
                        level).target().isEmpty(),
                net.minecraft.network.chat.Component.literal("Acquisition 3.0 ne doit jamais créer une vision x-ray"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void acquisitionDoesNotReplaceLiveVanillaTarget(
            GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager current = spawnVillager(helper, new BlockPos(8, 1, 4));
        spawnVillager(helper, new BlockPos(4, 1, 4));
        zombie.setTarget(current);
        TargetAcquisitionController.maintain(zombie, helper.getLevel(),
                helper.getLevel().getGameTime());
        helper.assertTrue(zombie.getTarget() == current,
                net.minecraft.network.chat.Component.literal("Le nouveau socle ne doit pas écraser une cible vanilla encore valide"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void stimulusStoresContextWithoutHiddenIdentity(
            GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        Sheep sheep = spawnSheep(helper, new BlockPos(3, 1, 6));
        long now = helper.getLevel().getGameTime();
        BlockPos source = helper.absolutePos(new BlockPos(5, 1, 4));
        helper.assertTrue(MobPerceptionMemory.perceiveProfiledStimulus(
                        zombie, helper.getLevel(), source, 12.0D, 80, null,
                        StimulusType.BLOCK_BREAK,
                        MobStimulusSystem.defaultConfidence(
                                StimulusType.BLOCK_BREAK)),
                net.minecraft.network.chat.Component.literal("Le fixture hostile doit entendre le stimulus"));
        helper.assertTrue(MobPerceptionMemory.perceiveProfiledStimulus(
                        sheep, helper.getLevel(), source, 12.0D, 80, null,
                        StimulusType.BLOCK_BREAK,
                        MobStimulusSystem.defaultConfidence(
                                StimulusType.BLOCK_BREAK)),
                net.minecraft.network.chat.Component.literal("Le fixture passif doit utiliser le même calcul sensoriel"));
        helper.assertValueEqual(MobPerceptionMemory.lastStimulusType(zombie,
                        now).orElse(null), StimulusType.BLOCK_BREAK,
                net.minecraft.network.chat.Component.literal("Le mob hostile doit mémoriser la nature du stimulus"));
        helper.assertValueEqual(MobPerceptionMemory.lastStimulusType(sheep,
                        now).orElse(null), StimulusType.BLOCK_BREAK,
                net.minecraft.network.chat.Component.literal("Le bus de stimuli doit être partagé par les mobs non hostiles"));
        helper.assertTrue(MobPerceptionMemory.lastSeenTarget(zombie, now)
                        .isEmpty(),
                net.minecraft.network.chat.Component.literal("Un bruit derrière soi ne doit pas révéler magiquement une identité"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void interestMemoryExpires(GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        long now = helper.getLevel().getGameTime();
        MobPerceptionMemory.rememberInterest(zombie, zombie.blockPosition(),
                StimulusType.SOUND, now, 20, 0.5D);
        helper.assertTrue(MobPerceptionMemory.hasFreshInterest(zombie,
                        now + 19),
                net.minecraft.network.chat.Component.literal("Une zone d'intérêt doit vivre pendant sa durée annoncée"));
        helper.assertFalse(MobPerceptionMemory.hasFreshInterest(zombie,
                        now + 21),
                net.minecraft.network.chat.Component.literal("Une zone d'intérêt ne doit pas devenir une connaissance permanente"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void sheepAvoidsWolfRelation(GameTestHelper helper) {
        clearAndFloor(helper);
        Sheep sheep = spawnSheep(helper, new BlockPos(3, 1, 4));
        Wolf wolf = spawnWolf(helper, new BlockPos(6, 1, 4));
        var relation = MobRelationManager.relation(sheep, wolf);
        helper.assertValueEqual(relation.map(r -> r.relation()).orElse(null),
                MobRelationType.AVOID,
                net.minecraft.network.chat.Component.literal("L'écologie doit pouvoir décrire une relation proie -> prédateur"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void ecologyFindsOnlyPerceivedThreat(GameTestHelper helper) {
        clearAndFloor(helper);
        Sheep sheep = spawnSheep(helper, new BlockPos(3, 1, 4));
        Wolf wolf = spawnWolf(helper, new BlockPos(6, 1, 4));
        var threat = MobEcologyController.nearestAvoidedThreat(sheep,
                helper.getLevel());
        helper.assertTrue(threat.isPresent() && threat.get().entity() == wolf,
                net.minecraft.network.chat.Component.literal("Une proie doit reconnaître le prédateur réellement visible"));
        Vec3 requested = MobEcologyController.fleeDestination(sheep, wolf);
        helper.assertTrue(requested != null
                        && fr.vanillainstincts.ai.SafePositionFinder
                        .resolveGroundDestination(sheep, requested).isPresent(),
                net.minecraft.network.chat.Component.literal("La fuite écologique doit pouvoir être résolue vers une position sûre"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void ecologyDoesNotSensePredatorThroughWall(
            GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        Sheep sheep = spawnSheep(helper, new BlockPos(2, 1, 4));
        spawnWolf(helper, new BlockPos(7, 1, 4));
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 7; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(4, y, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(MobEcologyController.nearestAvoidedThreat(sheep,
                        level).isEmpty(),
                net.minecraft.network.chat.Component.literal("Mob Ecology ne doit pas donner de radar à travers les murs"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void wildWolfCanAcquireVisiblePrey(GameTestHelper helper) {
        clearAndFloor(helper);
        Wolf wolf = spawnWolf(helper, new BlockPos(2, 1, 4));
        Sheep sheep = spawnSheep(helper, new BlockPos(6, 1, 4));
        var result = TargetAcquisitionController.findBestTarget(wolf,
                helper.getLevel());
        helper.assertTrue(result.target().orElse(null) == sheep,
                net.minecraft.network.chat.Component.literal("Une relation HUNT data-driven doit alimenter l'acquisition du prédateur"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void genericMobilityFindsShortSafeGap(GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos gapFloor = helper.absolutePos(new BlockPos(3, 0, 4));
        level.setBlock(gapFloor, Blocks.AIR.defaultBlockState(), 3);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager target = spawnVillager(helper, new BlockPos(6, 1, 4));
        Optional<Vec3> landing = GenericMobilityController.safeGapLanding(
                zombie, target, level);
        helper.assertTrue(landing.isPresent(),
                net.minecraft.network.chat.Component.literal("Generic Mobility doit accepter un petit gap avec vraie zone d'atterrissage"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void genericMobilityDoesNotJumpNormalGround(
            GameTestHelper helper) {
        clearAndFloor(helper);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager target = spawnVillager(helper, new BlockPos(6, 1, 4));
        helper.assertTrue(GenericMobilityController.safeGapLanding(zombie,
                        target, helper.getLevel()).isEmpty(),
                net.minecraft.network.chat.Component.literal("Le système de gap ne doit pas transformer la marche normale en parkour"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void genericMobilityRejectsMissingLanding(
            GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        level.setBlock(helper.absolutePos(new BlockPos(3, 0, 4)),
                Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(4, 0, 4)),
                Blocks.AIR.defaultBlockState(), 3);
        Zombie zombie = spawnZombie(helper, new BlockPos(2, 1, 4));
        Villager target = spawnVillager(helper, new BlockPos(6, 1, 4));
        helper.assertTrue(GenericMobilityController.safeGapLanding(zombie,
                        target, level).isEmpty(),
                net.minecraft.network.chat.Component.literal("Un mob ne doit pas sauter quand aucune réception sûre n'existe"));
        helper.succeed();
    }

    @GameTest(batch = "ecology_acquisition_mobility",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void genericMobilityFindsNearbyShore(GameTestHelper helper) {
        clearAndFloor(helper);
        ServerLevel level = helper.getLevel();
        Zombie zombie = spawnZombie(helper, new BlockPos(4, 1, 4));
        level.setBlock(zombie.blockPosition(), Blocks.WATER.defaultBlockState(),
                3);
        Optional<Vec3> shore = GenericMobilityController.nearestShore(zombie,
                level);
        helper.assertTrue(shore.isPresent(),
                net.minecraft.network.chat.Component.literal("Un mob terrestre dans l'eau doit pouvoir repérer une rive locale sûre"));
        helper.assertTrue(level.getFluidState(BlockPos.containing(shore.get()))
                        .isEmpty(),
                net.minecraft.network.chat.Component.literal("La destination de rive doit être sèche"));
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

    private static Sheep spawnSheep(GameTestHelper helper, BlockPos pos) {
        Sheep sheep = helper.spawn(EntityType.SHEEP, pos);
        sheep.setNoAi(true);
        sheep.setDeltaMovement(Vec3.ZERO);
        return sheep;
    }

    private static Wolf spawnWolf(GameTestHelper helper, BlockPos pos) {
        Wolf wolf = helper.spawn(EntityType.WOLF, pos);
        wolf.setNoAi(true);
        wolf.setDeltaMovement(Vec3.ZERO);
        return wolf;
    }
}
