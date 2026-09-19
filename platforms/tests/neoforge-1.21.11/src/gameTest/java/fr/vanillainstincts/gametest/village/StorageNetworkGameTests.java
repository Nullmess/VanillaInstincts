package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.PillagerOutpostLootController;
import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.PillagerRules;
import fr.vanillainstincts.permission.WorldPermissionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import java.util.function.Consumer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorageNetworkGameTests {
    private StorageNetworkGameTests() {
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void firstStorageGroupStartsTwoBlocksEast(GameTestHelper helper) {
        BlockPos root = new BlockPos(10, 10, 10);
        helper.assertValueEqual(PillagerOutpostLootController
                .storageAnchor(root, 0), root.east(2),
                net.minecraft.network.chat.Component.literal("Le premier coffre supplémentaire doit commencer à deux blocs"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void secondStorageGroupUsesOppositeSide(GameTestHelper helper) {
        BlockPos root = new BlockPos(10, 10, 10);
        helper.assertValueEqual(PillagerOutpostLootController
                .storageAnchor(root, 1), root.west(2),
                net.minecraft.network.chat.Component.literal("Le second emplacement doit rester proche mais opposé"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void secondRingLeavesAFreeGap(GameTestHelper helper) {
        BlockPos root = new BlockPos(10, 10, 10);
        helper.assertValueEqual(PillagerOutpostLootController
                .storageAnchor(root, 8), root.east(5),
                net.minecraft.network.chat.Component.literal("La seconde couronne doit laisser un bloc libre entre les paires"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void diagonalStorageExpandsOutward(GameTestHelper helper) {
        BlockPos root = new BlockPos(0, 0, 0);
        helper.assertValueEqual(PillagerOutpostLootController
                        .storageOutwardDirection(root, root.offset(-5, 0, 5)),
                Direction.WEST,
                net.minecraft.network.chat.Component.literal("Une paire diagonale doit s'agrandir loin du coffre principal"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void emptyContainerAcceptsLoot(GameTestHelper helper) {
        helper.assertTrue(PillagerOutpostLootController.canAccept(
                        new SimpleContainer(1), new ItemStack(Items.DIAMOND)),
                net.minecraft.network.chat.Component.literal("Un coffre vide doit accepter le butin"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void fullContainerRejectsLoot(GameTestHelper helper) {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        helper.assertFalse(PillagerOutpostLootController.canAccept(container,
                        new ItemStack(Items.DIAMOND)),
                net.minecraft.network.chat.Component.literal("Un coffre entièrement plein doit demander une extension"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void partialMatchingStackAcceptsLoot(GameTestHelper helper) {
        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.DIAMOND, 63));
        helper.assertTrue(PillagerOutpostLootController.canAccept(container,
                        new ItemStack(Items.DIAMOND)),
                net.minecraft.network.chat.Component.literal("Une pile compatible incomplète doit être remplie avant l'extension"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void storageNetworkHasLargeSafetyCapacity(GameTestHelper helper) {
        helper.assertTrue(PillagerRules.OUTPOST_STORAGE_MAX_GROUPS >= 32,
                net.minecraft.network.chat.Component.literal("Le réseau doit pouvoir créer de nombreuses paires sans boucle infinie"));
        helper.succeed();
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void fullRootChestBecomesDouble(GameTestHelper helper) {
        runStorageMutationTest(helper, test -> {
            BlockPos root = prepareRoot(test);
            fill(chest(test, root));
            ItemStack remainder = PillagerOutpostLootController
                    .insertIntoOutpostStorage(test.getLevel(), root,
                            new ItemStack(Items.DIAMOND));
            test.assertTrue(remainder.isEmpty()
                            && test.getLevel().getBlockState(root.east())
                            .is(Blocks.CHEST)
                            && test.getLevel().getBlockState(root)
                            .getValue(ChestBlock.TYPE) != ChestType.SINGLE,
                    net.minecraft.network.chat.Component.literal("Le coffre principal plein doit devenir un double coffre"));
        });
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void fullDoubleChestCreatesNearbySingle(GameTestHelper helper) {
        runStorageMutationTest(helper, test -> {
            BlockPos root = prepareFullDoubleRoot(test);
            ItemStack remainder = PillagerOutpostLootController
                    .insertIntoOutpostStorage(test.getLevel(), root,
                            new ItemStack(Items.EMERALD));
            BlockPos expected = PillagerOutpostLootController.storageAnchor(root, 1);
            test.assertTrue(remainder.isEmpty()
                            && test.getLevel().getBlockState(expected)
                            .is(Blocks.CHEST),
                    net.minecraft.network.chat.Component.literal("Une nouvelle réserve simple doit apparaître près de la paire pleine"));
        });
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void fullSecondaryChestBecomesDouble(GameTestHelper helper) {
        runStorageMutationTest(helper, test -> {
            BlockPos root = prepareFullDoubleRoot(test);
            BlockPos secondary = PillagerOutpostLootController.storageAnchor(root, 1);
            test.getLevel().setBlock(secondary,
                    Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            fill(chest(test, secondary));
            ItemStack remainder = PillagerOutpostLootController
                    .insertIntoOutpostStorage(test.getLevel(), root,
                            new ItemStack(Items.EMERALD));
            test.assertTrue(remainder.isEmpty()
                            && test.getLevel().getBlockState(secondary.west())
                            .is(Blocks.CHEST),
                    net.minecraft.network.chat.Component.literal("Le coffre secondaire plein doit être agrandi vers l'extérieur"));
        });
    }

    @GameTest(batch = "storage_network", templateNamespace = "vanillainstincts", template = "empty")
    public static void blockedAnchorUsesAnotherNearbyPosition(GameTestHelper helper) {
        runStorageMutationTest(helper, test -> {
            BlockPos root = prepareFullDoubleRoot(test);
            BlockPos west = PillagerOutpostLootController.storageAnchor(root, 1);
            test.getLevel().setBlock(west, Blocks.STONE.defaultBlockState(),
                    Block.UPDATE_ALL);
            BlockPos target = PillagerOutpostLootController.nextStorageTarget(
                    test.getLevel(), root, new ItemStack(Items.EMERALD));
            test.assertValueEqual(target,
                    PillagerOutpostLootController.storageAnchor(root, 2),
                    net.minecraft.network.chat.Component.literal("Un emplacement bloqué doit déplacer le nouveau coffre sans écraser de bloc"));
        });
    }


    private static void runStorageMutationTest(GameTestHelper helper,
                                               Consumer<GameTestHelper> test) {
        try {
            RuntimeConfig.install(ConfigSnapshot.builder()
                    .protectSpawnArea(false).build());
            WorldPermissionService.clearLevel(helper.getLevel());
            test.accept(helper);
            helper.succeed();
        } finally {
            RuntimeConfig.reset();
            WorldPermissionService.clearLevel(helper.getLevel());
        }
    }

    private static BlockPos prepareRoot(GameTestHelper helper) {
        // Chaque GameTest possède une origine différente. Utiliser une
        // position absolue fixe faisait travailler les quatre tests de
        // stockage sur le même coffre lorsqu'ils s'exécutaient en parallèle.
        BlockPos root = helper.absolutePos(new BlockPos(8, 4, 8));
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                helper.getLevel().setBlock(root.offset(x, -1, z),
                        Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                helper.getLevel().setBlock(root.offset(x, 0, z),
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                helper.getLevel().setBlock(root.offset(x, 1, z),
                        Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        helper.getLevel().setBlock(root, Blocks.CHEST.defaultBlockState(),
                Block.UPDATE_ALL);
        return root;
    }

    private static BlockPos prepareFullDoubleRoot(GameTestHelper helper) {
        BlockPos root = prepareRoot(helper);
        fill(chest(helper, root));
        PillagerOutpostLootController.insertIntoOutpostStorage(
                helper.getLevel(), root, new ItemStack(Items.DIAMOND));
        fill(chest(helper, root));
        fill(chest(helper, root.east()));
        return root;
    }

    private static Container chest(GameTestHelper helper, BlockPos position) {
        BlockEntity entity = helper.getLevel().getBlockEntity(position);
        if (!(entity instanceof Container container)) {
            throw new IllegalStateException("Coffre GameTest absent à " + position);
        }
        return container;
    }

    private static void fill(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            container.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        container.setChanged();
    }
}
