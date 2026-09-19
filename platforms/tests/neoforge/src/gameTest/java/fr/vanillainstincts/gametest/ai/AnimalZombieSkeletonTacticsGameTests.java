package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.AnimalComfortController;
import fr.vanillainstincts.ai.SkeletonTacticsController;
import fr.vanillainstincts.ai.ZombieHordePressureController;
import fr.vanillainstincts.core.rules.AnimalRules;
import fr.vanillainstincts.core.rules.ZombieRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Integration coverage for fixed46 combat and comfort systems. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnimalZombieSkeletonTacticsGameTests {
    private AnimalZombieSkeletonTacticsGameTests() {
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortFearPersistsAndExpires(GameTestHelper helper) {
        Cow cow = spawnCow(helper, new BlockPos(2, 1, 2));
        long now = helper.getLevel().getGameTime();
        Vec3 danger = cow.position().add(4.0D, 0.0D, 0.0D);
        AnimalComfortController.recordFear(cow, danger, now);
        helper.assertTrue(AnimalComfortController.fearSource(cow, now + 1).isPresent(),
                "La peur Animal Comfort 2.0 doit persister dans la mémoire de l'animal");
        helper.assertTrue(AnimalComfortController.fearSource(cow,
                        now + AnimalRules.ANIMAL_FEAR_MEMORY_TICKS + 1).isEmpty(),
                "La peur doit expirer sans devenir permanente");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortRemembersGoodShelter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 6, 0, 6);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(feet.above(2), Blocks.STONE.defaultBlockState(), 3);
        Cow cow = spawnCow(helper, new BlockPos(2, 1, 2));
        long now = level.getGameTime();
        AnimalComfortController.rememberShelter(cow, feet, now);
        helper.assertTrue(AnimalComfortController.rememberedShelter(cow,
                        level, now + 1).isPresent(),
                "Un bon abri doit être mémorisé entre deux décisions");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortStormPrefersRoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 7, 0, 7);
        Cow cow = spawnCow(helper, new BlockPos(1, 1, 3));
        BlockPos open = helper.absolutePos(new BlockPos(3, 1, 2));
        BlockPos roofed = helper.absolutePos(new BlockPos(3, 1, 4));
        level.setBlock(roofed.above(2), Blocks.STONE.defaultBlockState(), 3);
        double openScore = AnimalComfortController.comfortScore(cow, level,
                open, AnimalComfortController.ComfortNeed.STORM,
                null, null, null);
        double roofScore = AnimalComfortController.comfortScore(cow, level,
                roofed, AnimalComfortController.ComfortNeed.STORM,
                null, null, null);
        helper.assertTrue(roofScore > openScore + 10.0D,
                "Sous la pluie, une zone couverte doit dominer une zone ouverte");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortHeatPrefersShadeAndWater(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Cow cow = spawnCow(helper, new BlockPos(1, 1, 4));
        BlockPos hot = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos cool = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(cool.above(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cool.east(), Blocks.WATER.defaultBlockState(), 3);
        double hotScore = AnimalComfortController.comfortScore(cow, level,
                hot, AnimalComfortController.ComfortNeed.HEAT,
                null, null, null);
        double coolScore = AnimalComfortController.comfortScore(cow, level,
                cool, AnimalComfortController.ComfortNeed.HEAT,
                null, null, null);
        helper.assertTrue(coolScore > hotScore,
                "En chaleur, l'ombre proche de l'eau doit être préférée");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortPrefersAvailableSpace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Cow cow = spawnCow(helper, new BlockPos(1, 1, 4));
        BlockPos cramped = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos open = helper.absolutePos(new BlockPos(4, 1, 6));
        level.setBlock(cramped.north(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cramped.south(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(cramped.east(), Blocks.STONE.defaultBlockState(), 3);
        double crampedScore = AnimalComfortController.comfortScore(cow, level,
                cramped, AnimalComfortController.ComfortNeed.REST,
                null, null, null);
        double openScore = AnimalComfortController.comfortScore(cow, level,
                open, AnimalComfortController.ComfortNeed.REST,
                null, null, null);
        helper.assertTrue(openScore > crampedScore,
                "Le repos doit favoriser une zone où l'animal dispose d'espace");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void animalComfortUsesHerdAnchorWhenChoosingZone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Cow cow = spawnCow(helper, new BlockPos(4, 1, 4));
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 4));
        BlockPos east = helper.absolutePos(new BlockPos(6, 1, 4));
        Vec3 herd = Vec3.atBottomCenterOf(east);
        double westScore = AnimalComfortController.comfortScore(cow, level,
                west, AnimalComfortController.ComfortNeed.REST,
                null, herd, null);
        double eastScore = AnimalComfortController.comfortScore(cow, level,
                east, AnimalComfortController.ComfortNeed.REST,
                null, herd, null);
        helper.assertTrue(eastScore > westScore,
                "À confort égal, la proximité du troupeau doit départager les zones");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressureCountsOnlyRearAllies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Zombie front = spawnZombie(helper, new BlockPos(4, 1, 4));
        spawnZombie(helper, new BlockPos(3, 1, 4));
        spawnZombie(helper, new BlockPos(2, 1, 4));
        spawnZombie(helper, new BlockPos(5, 1, 4));
        List<Zombie> rear = ZombieHordePressureController.rearAllies(front,
                level, new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertValueEqual(rear.size(), 2,
                "La pression doit compter les zombies derrière, pas ceux devant");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressureMeasuresTwoBlockObstacle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        BlockPos front = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(front, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(front.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertValueEqual(ZombieHordePressureController.obstacleHeight(
                        zombie, level, new Vec3(1.0D, 0.0D, 0.0D)), 2,
                "Une barrière de deux blocs doit être mesurée comme telle");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressureAllowsBoundedBodyStackBoost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        BlockPos front = helper.absolutePos(new BlockPos(4, 1, 4));
        level.setBlock(front, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(front.above(), Blocks.STONE.defaultBlockState(), 3);
        Optional<Vec3> impulse = ZombieHordePressureController.pressureImpulse(
                zombie, level, new Vec3(1.0D, 0.0D, 0.0D), 2,
                ZombieRules.PRESSURE_MIN_STACKERS);
        helper.assertTrue(impulse.isPresent() && impulse.get().y > 0.0D,
                "Trois zombies derrière doivent permettre un boost physique borné");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressurePushesAtOneBlockObstacle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        Optional<Vec3> impulse = ZombieHordePressureController.pressureImpulse(
                zombie, level, new Vec3(1.0D, 0.0D, 0.0D), 1,
                ZombieRules.PRESSURE_MIN_PUSHERS);
        helper.assertTrue(impulse.isPresent()
                        && impulse.get().x > 0.0D
                        && Math.abs(impulse.get().y) < 1.0E-8D,
                "La pression simple doit pousser sans créer un saut magique");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressureRejectsTooHighObstacle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        Zombie zombie = spawnZombie(helper, new BlockPos(3, 1, 4));
        Optional<Vec3> impulse = ZombieHordePressureController.pressureImpulse(
                zombie, level, new Vec3(1.0D, 0.0D, 0.0D),
                ZombieRules.PRESSURE_MAX_OBSTACLE_HEIGHT + 1,
                ZombieRules.PRESSURE_MAX_REAR_ALLIES);
        helper.assertTrue(impulse.isEmpty(),
                "La horde ne doit jamais former une tour absurde au-delà de la limite");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void zombiePressureRearScanIsBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 10, 0, 10);
        Zombie front = spawnZombie(helper, new BlockPos(8, 1, 5));
        for (int x = 2; x <= 7; x++) {
            spawnZombie(helper, new BlockPos(x, 1, 5));
            spawnZombie(helper, new BlockPos(x, 1, 6));
        }
        List<Zombie> rear = ZombieHordePressureController.rearAllies(front,
                level, new Vec3(1.0D, 0.0D, 0.0D));
        helper.assertTrue(rear.size() <= ZombieRules.PRESSURE_MAX_REAR_ALLIES,
                "Le calcul de pression doit borner le nombre d'alliés retenus");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonFireRateDependsOnDistance(GameTestHelper helper) {
        int close = SkeletonTacticsController.attackIntervalTicks(16.0D,
                Difficulty.NORMAL);
        int ideal = SkeletonTacticsController.attackIntervalTicks(81.0D,
                Difficulty.NORMAL);
        int far = SkeletonTacticsController.attackIntervalTicks(256.0D,
                Difficulty.NORMAL);
        helper.assertTrue(ideal < close && ideal < far,
                "Le squelette doit tirer plus volontiers à sa distance préférée");

        // Exercise the real Mixin accessor as well, not only the pure formula.
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(2, 1, 4));
        Zombie target = spawnZombie(helper, new BlockPos(7, 1, 4));
        skeleton.setTarget(target);
        SkeletonTacticsController.maintain(skeleton, level, level.getGameTime());
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonDetectsCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 8, 0, 8);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(4, 1, 4));
        BlockPos feet = skeleton.blockPosition();
        level.setBlock(feet.east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet.north(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(SkeletonTacticsController.isCornered(skeleton, level),
                "Deux faces bloquées doivent déclencher la sortie de coin");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonRetreatRefusesVoidLedge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        // A single island: all meaningful retreat samples leave solid ground.
        floor(level, helper, 0, 3, 5, 3, 5);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(4, 1, 4));
        Zombie target = spawnZombie(helper, new BlockPos(3, 1, 4));
        Optional<Vec3> retreat = SkeletonTacticsController.safeRetreatDestination(
                skeleton, target, level, 9.0D);
        helper.assertTrue(retreat.isEmpty(),
                "Un squelette ne doit pas reculer volontairement dans le vide");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonRetreatAvoidsLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 10, 0, 10);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(4, 1, 5));
        Zombie target = spawnZombie(helper, new BlockPos(3, 1, 5));
        for (int x = 7; x <= 10; x++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 0, 5)),
                    Blocks.LAVA.defaultBlockState(), 3);
        }
        Optional<Vec3> retreat = SkeletonTacticsController.safeRetreatDestination(
                skeleton, target, level, 9.0D);
        helper.assertTrue(retreat.isPresent(),
                "Le squelette doit chercher une autre voie lorsque le recul direct est dangereux");
        BlockPos chosenFloor = BlockPos.containing(retreat.get()).below();
        helper.assertFalse(level.getFluidState(chosenFloor).is(FluidTags.LAVA),
                "La destination de recul ne doit pas être dans la lave");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonStrafeKeepsSafeLineOfFire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(level, helper);
        floor(level, helper, 0, 0, 14, 0, 14);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(3, 1, 7));
        Zombie target = spawnZombie(helper, new BlockPos(10, 1, 7));
        Optional<Vec3> strafe = SkeletonTacticsController.bestStrafeDestination(
                skeleton, target, level);
        helper.assertTrue(strafe.isPresent(),
                "Un terrain ouvert doit offrir une position de strafe sûre");
        helper.assertTrue(SkeletonTacticsController.hasLineOfFire(skeleton,
                        target, level, strafe.get()),
                "La position choisie doit conserver une vraie ligne de tir");
        helper.succeed();
    }

    @GameTest(batch = "animal_zombie_skeleton_tactics",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void skeletonLaneBiasIsStableAndLimited(GameTestHelper helper) {
        clear(helper.getLevel(), helper);
        floor(helper.getLevel(), helper, 0, 0, 6, 0, 6);
        AbstractSkeleton skeleton = spawnSkeleton(helper, new BlockPos(3, 1, 3));
        double first = SkeletonTacticsController.laneOffset(skeleton);
        double second = SkeletonTacticsController.laneOffset(skeleton);
        helper.assertValueEqual(first, second,
                "Le couloir tactique individuel doit rester stable");
        helper.assertTrue(Math.abs(first) <= 1.6D,
                "La coopération doit rester limitée et ne pas former une conscience collective");
        helper.succeed();
    }

    private static Cow spawnCow(GameTestHelper helper, BlockPos relative) {
        Cow cow = helper.spawn(EntityType.COW, relative);
        cow.setNoAi(true);
        cow.setDeltaMovement(Vec3.ZERO);
        return cow;
    }

    private static Zombie spawnZombie(GameTestHelper helper, BlockPos relative) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, relative);
        zombie.setNoAi(true);
        zombie.setDeltaMovement(Vec3.ZERO);
        return zombie;
    }

    private static AbstractSkeleton spawnSkeleton(GameTestHelper helper,
                                                   BlockPos relative) {
        AbstractSkeleton skeleton = helper.spawn(EntityType.SKELETON, relative);
        skeleton.setNoAi(true);
        skeleton.setDeltaMovement(Vec3.ZERO);
        return skeleton;
    }

    private static void clear(ServerLevel level, GameTestHelper helper) {
        for (int x = 0; x <= 15; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = 0; z <= 15; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void floor(ServerLevel level, GameTestHelper helper, int y,
                              int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }
}
