package fr.vanillainstincts.gametest.world;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.village.GolemRepairController;
import fr.vanillainstincts.village.RecoveredTradeController;
import fr.vanillainstincts.village.VillageGolemCeremonyController;
import fr.vanillainstincts.world.CryingObsidianPortalController;
import fr.vanillainstincts.world.CryingPortalLinkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CryingPortalAndRecoveryGameTests {
    private CryingPortalAndRecoveryGameTests() {
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void repairUsesArtificialIron(GameTestHelper helper) {
        helper.assertTrue(GolemRepairController.usesArtificialServiceIron(),
                "La réparation ne doit plus dépendre de l'inventaire");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void ceremonyUsesArtificialStock(GameTestHelper helper) {
        helper.assertTrue(VillageGolemCeremonyController
                        .usesArtificialCeremonyStock()
                        && VillageConstructionRules.GOLEM_CEREMONY_ASSIST_TICKS <= 600,
                "La cérémonie doit disposer du stock et sortir d'un trajet bloqué");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void playerRecoveredOfferIsSingleUse(GameTestHelper helper) {
        helper.assertValueEqual(RecoveredTradeController
                        .recoveredOfferMaxUses(true), 1,
                "Toute offre issue d'un objet du joueur doit disparaître après achat");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void naturalRecoveredOfferKeepsOldStock(GameTestHelper helper) {
        helper.assertValueEqual(RecoveredTradeController
                        .recoveredOfferMaxUses(false), Integer.MAX_VALUE,
                "Seuls les objets jetés par un joueur sont à usage unique");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void itemSearchIsLongRange(GameTestHelper helper) {
        helper.assertTrue(ProfessionRules.RECOVERED_TRADE_SCAN_RADIUS >= 48.0D,
                "Les objets professionnels doivent être détectés loin");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void itemRecoveryBeatsCeremony(GameTestHelper helper) {
        helper.assertTrue(ProfessionRules.PRIORITY_RECOVERED_TRADE
                        > VillageConstructionRules.PRIORITY_GOLEM_CEREMONY,
                "Un objet intéressant au sol doit interrompre le travail ordinaire");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingFrameXAxisIsRecognized(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        helper.assertTrue(CryingObsidianPortalController.findFrame(
                        helper.getLevel(), interior, Direction.Axis.X) != null,
                "Le cadre pur en obsidienne pleureuse doit être reconnu");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingPortalIgnitesWithFlintAndSteel(
            GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos clicked = interior.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clicked).add(0.0D, 0.5D, 0.0D),
                Direction.UP, clicked, false);
        InteractionResult result = player.gameMode.useItemOn(player,
                helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction(),
                "Le vrai pipeline serveur du briquet doit consommer l'action");
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, localInterior);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void largeFullFrameIgnitesWithRealFlintPipeline(
            GameTestHelper helper) {
        // The portal batch uses a 7x7x7 template. Keep the complete frame
        // inside those bounds so parallel GameTests cannot overwrite it.
        // Interior 4x3 => outer frame 6x5, fitting x=0..5 / y=1..5.
        BlockPos localInterior = new BlockPos(1, 2, 3);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrameSized(helper, interior, Direction.Axis.X,
                4, 3, true, false, false);
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos clicked = interior.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clicked).add(0.0D, 0.5D, 0.0D),
                Direction.UP, clicked, false);
        InteractionResult result = player.gameMode.useItemOn(player,
                helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result.consumesAction(),
                "Le briquet doit être utilisé sur un grand cadre pleureur complet");
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, localInterior);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL,
                localInterior.relative(Direction.EAST, 3).above(2));
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void placedFireIgnitesCryingPortal(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        helper.getLevel().setBlock(interior, Blocks.FIRE.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertBlockPresent(Blocks.NETHER_PORTAL, localInterior);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void breakingPortalInteriorCollapsesWholeCryingPortal(
            GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        helper.getLevel().setBlock(interior, Blocks.FIRE.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertTrue(helper.getLevel().getBlockState(interior)
                        .is(Blocks.NETHER_PORTAL),
                "Le portail pleureur doit d'abord être actif");

        BlockPos survivor = interior.relative(Direction.EAST).above();
        helper.getLevel().setBlockAndUpdate(interior,
                Blocks.AIR.defaultBlockState());
        helper.assertTrue(helper.getLevel().getBlockState(survivor).isAir(),
                "Casser un bloc intérieur doit faire disparaître tout le portail");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void breakingCryingFrameCollapsesWholePortal(
            GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        helper.getLevel().setBlock(interior, Blocks.FIRE.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertTrue(helper.getLevel().getBlockState(interior)
                        .is(Blocks.NETHER_PORTAL),
                "Le portail pleureur doit d'abord être actif");

        BlockPos requiredSide = interior.relative(Direction.WEST).above();
        BlockPos survivor = interior.relative(Direction.EAST).above();
        helper.getLevel().setBlockAndUpdate(requiredSide,
                Blocks.AIR.defaultBlockState());
        helper.assertTrue(helper.getLevel().getBlockState(survivor).isAir(),
                "Casser un bloc du cadre doit faire disparaître tout le portail");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingFrameZAxisIsRecognized(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.Z, false, false);
        helper.assertTrue(CryingObsidianPortalController.findFrame(
                        helper.getLevel(), interior, Direction.Axis.Z) != null,
                "Le cadre doit fonctionner sur les deux axes");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void mixedObsidianFrameIsRejected(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, true, false);
        helper.assertTrue(CryingObsidianPortalController.findFrame(
                        helper.getLevel(), interior, Direction.Axis.X) == null,
                "Le portail spécial doit être entièrement pleureur");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void brokenCryingFrameIsRejected(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, true);
        helper.assertTrue(CryingObsidianPortalController.findFrame(
                        helper.getLevel(), interior, Direction.Axis.X) == null,
                "Un cadre incomplet ne doit pas rester actif");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void minimumPortalInteriorIsAccepted(GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);
        var frame = CryingObsidianPortalController.findFrame(
                helper.getLevel(), interior, Direction.Axis.X);
        helper.assertTrue(frame != null && frame.width() == 2
                        && frame.height() == 3
                        && CryingObsidianPortalController
                        .requiredFrameBlocks(2, 3) == 10
                        && CryingObsidianPortalController.roofFrameY() >= 128
                        && CryingObsidianPortalController
                        .netherCoordinate(-1) == -1,
                "Le format vanilla minimal doit utiliser dix blocs et viser le toit");
        helper.succeed();
    }


    @SuppressWarnings("removal")
    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void cryingPortalCreatesStableNetherRoofDestination(
            GameTestHelper helper) {
        BlockPos localInterior = new BlockPos(2, 2, 2);
        BlockPos interior = helper.absolutePos(localInterior);
        buildFrame(helper, interior, Direction.Axis.X, false, false);

        // Use the same server-side flint-and-steel pipeline as a real player.
        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.FLINT_AND_STEEL));
        BlockPos clicked = interior.below();
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(clicked).add(0.0D, 0.5D, 0.0D),
                Direction.UP, clicked, false);
        InteractionResult ignition = player.gameMode.useItemOn(player,
                helper.getLevel(), player.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(ignition.consumesAction()
                        && helper.getLevel().getBlockState(interior)
                        .is(Blocks.NETHER_PORTAL),
                "Le vrai briquet doit créer le portail source avant le voyage");

        // Call the actual NetherPortalBlock API. The registered mixin must
        // replace vanilla's destination with the crying-portal roof route.
        var transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                .getPortalDestination(helper.getLevel(), player, interior);
        helper.assertTrue(transition != null,
                "Le bloc portail réel doit produire une transition vers le Nether");

        var frame = CryingObsidianPortalController.findFrame(
                helper.getLevel(), interior, Direction.Axis.X);
        helper.assertTrue(frame != null,
                "Le cadre source doit rester détectable après activation");
        var server = helper.getLevel().getServer();
        var nether = server.getLevel(Level.NETHER);
        var link = CryingPortalLinkSavedData.get(server)
                .linkForSource(frame.bottomLeft());
        helper.assertTrue(nether != null && link != null,
                "Le hook de destination doit enregistrer une sortie pleureuse dans le Nether");
        helper.assertTrue(link.netherFrameBase().getY()
                        >= CryingObsidianPortalController.roofFrameY(),
                "La sortie doit être construite au-dessus de la couche de bedrock");
        helper.assertTrue(CryingObsidianPortalController.isReusablePortal(
                        nether, link.netherFrameBase(), link.netherAxis()),
                "Le portail de destination doit encore être actif après sa construction");

        BlockPos netherInterior = CryingObsidianPortalController
                .arrivalPosition(link.netherFrameBase(), link.netherAxis());
        var returnTransition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                .getPortalDestination(nether, player, netherInterior);
        helper.assertTrue(returnTransition != null,
                "Le portail du toit doit également produire un retour vers l'Overworld");
        helper.succeed();
    }

    @GameTest(batch = "crying_portal_recovery", templateNamespace = "vanillainstincts", template = "empty")
    public static void scanIntervalIsResponsive(GameTestHelper helper) {
        helper.assertTrue(ProfessionRules.RECOVERED_TRADE_SCAN_INTERVAL_TICKS <= 5,
                "La recherche d'objets doit réagir rapidement");
        helper.succeed();
    }

    private static void buildFrame(GameTestHelper helper, BlockPos interior,
                                   Direction.Axis axis, boolean mixed,
                                   boolean broken) {
        buildFrameSized(helper, interior, axis, 2, 3, false, mixed, broken);
    }

    private static void buildFrameSized(GameTestHelper helper,
                                        BlockPos interior,
                                        Direction.Axis axis, int width,
                                        int height, boolean corners,
                                        boolean mixed, boolean broken) {
        Direction right = axis == Direction.Axis.X
                ? Direction.EAST : Direction.SOUTH;
        BlockPos leftSide = interior.relative(right.getOpposite());
        BlockPos rightSide = interior.relative(right, width);
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();

        for (int x = 0; x < width; x++) {
            helper.getLevel().setBlockAndUpdate(
                    interior.relative(right, x).below(), crying);
            helper.getLevel().setBlockAndUpdate(
                    interior.relative(right, x).above(height), crying);
        }
        for (int y = 0; y < height; y++) {
            helper.getLevel().setBlockAndUpdate(leftSide.above(y), crying);
            helper.getLevel().setBlockAndUpdate(rightSide.above(y), crying);
        }
        if (corners) {
            helper.getLevel().setBlockAndUpdate(leftSide.below(), crying);
            helper.getLevel().setBlockAndUpdate(rightSide.below(), crying);
            helper.getLevel().setBlockAndUpdate(leftSide.above(height), crying);
            helper.getLevel().setBlockAndUpdate(rightSide.above(height), crying);
        }
        if (mixed) {
            helper.getLevel().setBlockAndUpdate(leftSide.above(),
                    Blocks.OBSIDIAN.defaultBlockState());
        }
        if (broken) {
            helper.getLevel().setBlockAndUpdate(leftSide.above(),
                    Blocks.AIR.defaultBlockState());
        }
    }
}
