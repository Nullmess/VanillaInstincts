package fr.vanillainstincts.gametest.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import fr.vanillainstincts.world.CryingPortalLinkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CryingPortalNetworkGameTests {
    private CryingPortalNetworkGameTests() {
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingNetworkIsSeparatedFromVanilla(GameTestHelper helper) {
        helper.assertTrue(CryingObsidianPortalController
                        .isSeparatedFromVanillaNetwork(),
                "Le réseau pleureur doit rester hors du rayon vanilla");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingTargetUsesDedicatedLane(GameTestHelper helper) {
        int vanilla = CryingObsidianPortalController.netherCoordinate(800);
        int crying = CryingObsidianPortalController.cryingNetherX(800);
        helper.assertValueEqual(crying - vanilla,
                CryingObsidianPortalController.cryingNetworkOffset(),
                "La sortie pleureuse doit utiliser la voie dédiée");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void nearbySourcesMayReuseOneDestination(GameTestHelper helper) {
        helper.assertTrue(CryingObsidianPortalController
                        .sourcePortalsCanShareDestination(
                                new BlockPos(0, 64, 0),
                                new BlockPos(512, 70, 0)),
                "Deux portails proches doivent pouvoir partager une sortie");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void distantSourcesKeepSeparateDestinations(GameTestHelper helper) {
        helper.assertTrue(!CryingObsidianPortalController
                        .sourcePortalsCanShareDestination(
                                new BlockPos(0, 64, 0),
                                new BlockPos(2_048, 70, 0)),
                "Deux portails très éloignés ne doivent pas être fusionnés");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingXRoundTripKeepsCoordinate(GameTestHelper helper) {
        int source = 8_000;
        int nether = CryingObsidianPortalController.cryingNetherX(source);
        helper.assertValueEqual(CryingObsidianPortalController
                        .cryingOverworldX(nether), source,
                "Le décalage réservé doit être réversible");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingZKeepsVanillaRatio(GameTestHelper helper) {
        int source = -8_000;
        int nether = CryingObsidianPortalController.cryingNetherZ(source);
        helper.assertValueEqual(CryingObsidianPortalController
                        .cryingOverworldZ(nether), source,
                "L'axe Z doit conserver le rapport vanilla 8:1");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void xFrameHasCanonicalOuterBase(GameTestHelper helper) {
        var frame = new CryingObsidianPortalController.PortalFrame(
                new BlockPos(2, 2, 2), Direction.Axis.X, 2, 3);
        helper.assertValueEqual(CryingObsidianPortalController
                        .outerFrameBase(frame), new BlockPos(1, 1, 2),
                "Le cadre X doit posséder une base canonique stable");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void zFrameHasCanonicalOuterBase(GameTestHelper helper) {
        var frame = new CryingObsidianPortalController.PortalFrame(
                new BlockPos(2, 2, 2), Direction.Axis.Z, 2, 3);
        helper.assertValueEqual(CryingObsidianPortalController
                        .outerFrameBase(frame), new BlockPos(2, 1, 1),
                "Le cadre Z doit posséder une base canonique stable");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void activeDestinationIsReused(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 2);
        buildActiveMinimumPortal(helper, base, Direction.Axis.X);
        helper.assertTrue(CryingObsidianPortalController.isReusablePortal(
                        helper.getLevel(), base, Direction.Axis.X)
                        && CryingObsidianPortalController.arrivalPosition(
                        base, Direction.Axis.X).equals(new BlockPos(2, 2, 2)),
                "Une sortie active doit être réutilisée sans replacer ses blocs");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void occupiedDestinationCannotBeOverwritten(GameTestHelper helper) {
        BlockPos base = new BlockPos(1, 1, 2);
        buildActiveMinimumPortal(helper, base, Direction.Axis.X);
        helper.assertTrue(!CryingObsidianPortalController.portalSiteClear(
                        helper.getLevel(), base, Direction.Axis.X),
                "Un cadre existant ne doit jamais être considéré comme libre");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void severalSourcesCanShareStoredDestination(GameTestHelper helper) {
        CryingPortalLinkSavedData data = new CryingPortalLinkSavedData();
        BlockPos destination = new BlockPos(1_024, 128, 0);
        data.putLink(new BlockPos(0, 64, 0), Direction.Axis.X,
                destination, Direction.Axis.X);
        data.putLink(new BlockPos(16, 64, 0), Direction.Axis.X,
                destination, Direction.Axis.X);
        helper.assertTrue(data.linkCount() == 2
                        && data.destinationUserCount(destination) == 2,
                "Le partage d'une sortie ne doit pas dupliquer le portail");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void relinkingSourceDoesNotDuplicateRecord(GameTestHelper helper) {
        CryingPortalLinkSavedData data = new CryingPortalLinkSavedData();
        BlockPos source = new BlockPos(0, 64, 0);
        data.putLink(source, Direction.Axis.X,
                new BlockPos(1_024, 128, 0), Direction.Axis.X);
        data.putLink(source, Direction.Axis.X,
                new BlockPos(1_040, 128, 0), Direction.Axis.X);
        helper.assertTrue(data.linkCount() == 1
                        && data.linkForSource(source).netherFrameBase()
                        .equals(new BlockPos(1_040, 128, 0)),
                "Mettre à jour un lien ne doit jamais créer un doublon");
        helper.succeed();
    }
    private static void buildActiveMinimumPortal(GameTestHelper helper,
                                                  BlockPos base,
                                                  Direction.Axis axis) {
        Direction right = axis == Direction.Axis.X
                ? Direction.EAST : Direction.SOUTH;
        for (int x = 1; x <= 2; x++) {
            helper.getLevel().setBlock(base.relative(right, x),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    Block.UPDATE_ALL);
            helper.getLevel().setBlock(base.relative(right, x).above(4),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        for (int y = 1; y <= 3; y++) {
            helper.getLevel().setBlock(base.above(y),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    Block.UPDATE_ALL);
            helper.getLevel().setBlock(base.relative(right, 3).above(y),
                    Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        var portal = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, axis);
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                helper.getLevel().setBlock(
                        base.relative(right, x).above(y), portal,
                        Block.UPDATE_ALL);
            }
        }
    }

}
