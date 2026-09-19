package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.SpiderSurfaceNavigator;
import fr.vanillainstincts.ai.SpiderSurfaceNode;
import fr.vanillainstincts.ai.SpiderSurfacePath;
import fr.vanillainstincts.ai.SpiderSurfacePathfinder;
import fr.vanillainstincts.ai.SpeciesRuntimeState;
import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import fr.vanillainstincts.core.rules.SpiderRules;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

/** Integration coverage for fixed45 Spider Navigation 2.0. */
@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpiderNavigationGameTests {
    private SpiderNavigationGameTests() {
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePathClimbsWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        BlockPos wallBase = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(wallBase, Blocks.AIR.defaultBlockState(), 3);
        helper.assertFalse(SpiderSurfacePathfinder.isClimbableSupport(
                        level, wallBase, level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le cache doit observer l'air avant la construction du mur"));
        verticalColumn(level, helper, new BlockPos(3, 1, 3), 3,
                Blocks.STONE.defaultBlockState());
        helper.assertTrue(SpiderSurfacePathfinder.isClimbableSupport(
                        level, wallBase, level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le cache doit invalider immédiatement l'air devenu mur"));
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        SpiderSurfaceNode goal = node(helper, new BlockPos(2, 3, 3),
                Direction.EAST);

        Optional<SpiderSurfacePath> path = SpiderSurfacePathfinder.findPath(
                spider, level, SpiderSurfacePathfinder.targetFeet(spider, level, goal),
                level.getGameTime());
        helper.assertTrue(path.isPresent(),
                net.minecraft.network.chat.Component.literal("Spider Navigation 2.0 doit trouver la montée du mur"));
        helper.assertTrue(path.get().usesWall(),
                net.minecraft.network.chat.Component.literal("Le chemin doit réellement utiliser une surface verticale"));
        helper.assertTrue(path.get().nodes().stream().anyMatch(candidate ->
                        candidate.feet().getY() >= helper.absolutePos(
                                new BlockPos(0, 2, 0)).getY()),
                net.minecraft.network.chat.Component.literal("Le chemin doit gagner de l'altitude sur le mur"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePathReachesCeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        verticalColumn(level, helper, new BlockPos(3, 1, 3), 3,
                Blocks.STONE.defaultBlockState());
        for (int x = 1; x <= 5; x++) {
            for (int z = 2; z <= 5; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 4, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        SpiderSurfaceNode goal = node(helper, new BlockPos(2, 3, 5),
                Direction.UP);

        Optional<SpiderSurfacePath> path = SpiderSurfacePathfinder.findPath(
                spider, level, SpiderSurfacePathfinder.targetFeet(spider, level, goal),
                level.getGameTime());
        helper.assertTrue(path.isPresent(),
                net.minecraft.network.chat.Component.literal("L'araignée doit trouver un itinéraire vers le plafond"));
        helper.assertTrue(path.get().usesCeiling(),
                net.minecraft.network.chat.Component.literal("L'itinéraire doit contenir au moins un noeud plafond"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePathCrossesOuterCorner(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        BlockPos support = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
        Spider spider = spawnSpider(helper, new BlockPos(2, 2, 3));
        SpiderSurfaceNode westFace = new SpiderSurfaceNode(
                helper.absolutePos(new BlockPos(2, 2, 3)), Direction.EAST);
        SpiderSurfaceNode northFace = new SpiderSurfaceNode(
                helper.absolutePos(new BlockPos(3, 2, 2)), Direction.SOUTH);

        helper.assertTrue(SpiderSurfacePathfinder.isOuterCornerTransition(
                        westFace, northFace),
                net.minecraft.network.chat.Component.literal("Deux faces convexes du même bloc doivent former une transition"));
        Vec3 waypoint = SpiderSurfacePathfinder.outerCornerWaypoint(
                spider, westFace, northFace);
        helper.assertTrue(waypoint.x < support.getX()
                        && waypoint.z < support.getZ(),
                net.minecraft.network.chat.Component.literal("Le waypoint doit contourner le coin par l'extérieur"));

        Optional<SpiderSurfacePath> path = SpiderSurfacePathfinder.findPath(
                spider, level,
                SpiderSurfacePathfinder.targetFeet(spider, level, northFace),
                level.getGameTime());
        helper.assertTrue(path.isPresent(),
                net.minecraft.network.chat.Component.literal("Le graphe de surfaces doit pouvoir contourner un coin convexe"));
        helper.assertTrue(path.get().nodes().stream().anyMatch(node ->
                        node.support() == Direction.SOUTH),
                net.minecraft.network.chat.Component.literal("Le chemin doit atteindre la seconde face du coin"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceNodesMapSupportModes(
            GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.assertValueEqual(new SpiderSurfaceNode(feet, Direction.DOWN).mode(),
                SpiderSurfaceMode.GROUND,
                net.minecraft.network.chat.Component.literal("Un support sous l'araignée doit représenter le sol"));
        helper.assertValueEqual(new SpiderSurfaceNode(feet, Direction.EAST).mode(),
                SpiderSurfaceMode.WALL,
                net.minecraft.network.chat.Component.literal("Un support horizontal doit représenter un mur"));
        helper.assertValueEqual(new SpiderSurfaceNode(feet, Direction.UP).mode(),
                SpiderSurfaceMode.CEILING,
                net.minecraft.network.chat.Component.literal("Un support au-dessus doit représenter le plafond"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceRejectsUnclimbableTag(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        BlockPos magma = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(magma, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
        helper.assertFalse(SpiderSurfacePathfinder.isClimbableSupport(
                        level, magma, level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le tag spider_unclimbable doit bloquer la surface"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceRejectsWaterCell(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 3));
        level.setBlock(feet.east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.WATER.defaultBlockState(), 3);
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        helper.assertFalse(SpiderSurfacePathfinder.isNodeValid(spider, level,
                        new SpiderSurfaceNode(feet, Direction.EAST),
                        level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le chemin de surface ne doit pas planifier dans l'eau"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceSearchIsBounded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        verticalColumn(level, helper, new BlockPos(3, 1, 3), 3,
                Blocks.STONE.defaultBlockState());
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        SpiderSurfaceNode goal = node(helper, new BlockPos(2, 3, 3),
                Direction.EAST);
        Optional<SpiderSurfacePath> path = SpiderSurfacePathfinder.findPath(
                spider, level, SpiderSurfacePathfinder.targetFeet(spider, level, goal),
                level.getGameTime());
        helper.assertTrue(path.isPresent(),
                net.minecraft.network.chat.Component.literal("Le fixture doit produire un chemin pour mesurer le budget"));
        helper.assertTrue(path.get().expandedNodes() > 0
                        && path.get().expandedNodes()
                        <= SpiderRules.SPIDER_SURFACE_MAX_EXPANDED_NODES,
                net.minecraft.network.chat.Component.literal("La recherche A* doit rester strictement bornée"));
        helper.assertTrue(path.get().nodes().size()
                        <= SpiderRules.SPIDER_SURFACE_MAX_PATH_LENGTH,
                net.minecraft.network.chat.Component.literal("La reconstruction de chemin doit elle aussi être bornée"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceNavigatorActivatesAndCancels(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        BlockPos wall = helper.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        SpiderSurfacePath path = new SpiderSurfacePath(java.util.List.of(
                node(helper, new BlockPos(1, 1, 3), Direction.DOWN),
                node(helper, new BlockPos(2, 1, 3), Direction.EAST)),
                false, 2, 1.0D);

        SpiderSurfaceNavigator.activate(spider, path,
                SpiderSurfacePathfinder.targetFeet(spider, level,
                        path.nodes().get(1)), level.getGameTime());
        helper.assertValueEqual(SpiderSurfaceNavigator.activePathLength(spider),
                2, net.minecraft.network.chat.Component.literal("Le follower doit conserver le chemin accepté"));
        SpiderSurfaceNavigator.cancel(spider);
        helper.assertFalse(SpiderSurfaceNavigator.isActive(spider),
                net.minecraft.network.chat.Component.literal("L'annulation doit libérer immédiatement l'état transitoire"));
        helper.assertFalse(spider.isNoGravity(),
                net.minecraft.network.chat.Component.literal("L'annulation doit toujours restaurer la gravité"));
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfaceNavigatorMovesEveryTick(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        level.setBlock(helper.absolutePos(new BlockPos(3, 1, 3)),
                Blocks.STONE.defaultBlockState(), 3);
        Spider spider = spawnSpider(helper, new BlockPos(1, 1, 3));
        SpiderSurfacePath path = new SpiderSurfacePath(java.util.List.of(
                node(helper, new BlockPos(1, 1, 3), Direction.DOWN),
                node(helper, new BlockPos(2, 1, 3), Direction.EAST)),
                false, 2, 1.0D);
        helper.assertTrue(SpiderSurfacePathfinder.isNodeValid(
                        spider, level, path.nodes().get(1), level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le nœud mur du follower doit être physiquement valide"));
        SpeciesRuntimeState species = new SpeciesRuntimeState();
        SpiderSurfaceNavigator.activate(spider, path,
                SpiderSurfacePathfinder.targetFeet(spider, level,
                        path.nodes().get(1)), level.getGameTime());

        helper.assertTrue(SpiderSurfaceNavigator.tick(
                        spider, species, level, level.getGameTime()),
                net.minecraft.network.chat.Component.literal("Le follower doit produire un mouvement entre deux décisions"));
        helper.assertTrue(spider.getDeltaMovement().lengthSqr() > 1.0E-6D,
                net.minecraft.network.chat.Component.literal("Le déplacement de surface doit modifier la vitesse réelle"));
        helper.assertValueEqual(species.spiderSurfaceMode(),
                SpiderSurfaceMode.WALL,
                net.minecraft.network.chat.Component.literal("Le follower doit publier le mode de surface réellement suivi"));
        SpiderSurfaceNavigator.cancel(spider);
        helper.succeed();
    }

    @GameTest(batch = "spider_navigation",
            templateNamespace = "vanillainstincts", template = "empty")
    public static void spiderSurfacePathFallsBackWithoutRoute(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clearFixture(level, helper);
        floor(level, helper, 0);
        Spider spider = spawnSpider(helper, new BlockPos(3, 1, 3));
        Vec3 unreachableAbove = spider.position().add(0.0D, 4.0D, 0.0D);
        Optional<SpiderSurfacePath> path = SpiderSurfacePathfinder.findPath(
                spider, level, unreachableAbove, level.getGameTime());
        helper.assertTrue(path.isEmpty(),
                net.minecraft.network.chat.Component.literal("Sans mur/plafond utile, la navigation 3D doit céder au fallback vanilla"));
        helper.succeed();
    }

    private static Spider spawnSpider(GameTestHelper helper, BlockPos relative) {
        Spider spider = helper.spawn(EntityType.SPIDER, relative);
        spider.setNoAi(true);
        spider.setDeltaMovement(Vec3.ZERO);
        return spider;
    }

    private static SpiderSurfaceNode node(GameTestHelper helper,
                                          BlockPos relativeFeet,
                                          Direction support) {
        return new SpiderSurfaceNode(helper.absolutePos(relativeFeet), support);
    }

    private static void clearFixture(ServerLevel level,
                                     GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 0; z <= 6; z++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void floor(ServerLevel level, GameTestHelper helper, int y) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static void verticalColumn(ServerLevel level,
                                       GameTestHelper helper,
                                       BlockPos relativeBase,
                                       int height,
                                       net.minecraft.world.level.block.state.BlockState state) {
        for (int index = 0; index < height; index++) {
            level.setBlock(helper.absolutePos(relativeBase.above(index)),
                    state, 3);
        }
    }
}
