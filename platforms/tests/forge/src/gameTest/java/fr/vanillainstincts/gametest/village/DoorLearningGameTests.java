package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.IronDoorLearningController;
import fr.vanillainstincts.village.IronDoorMemory;
import fr.vanillainstincts.village.LearnedDoorController;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class DoorLearningGameTests {
    private DoorLearningGameTests() {
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void leverCanTeachIronDoor(GameTestHelper helper) {
        helper.assertTrue(IronDoorLearningController.isLearnableMechanism(
                Blocks.LEVER.defaultBlockState()),
                "Un levier doit être un mécanisme observable");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void buttonCanTeachIronDoor(GameTestHelper helper) {
        helper.assertTrue(IronDoorLearningController.isLearnableMechanism(
                Blocks.STONE_BUTTON.defaultBlockState()),
                "Un bouton doit être un mécanisme observable");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void ordinaryBlockCannotTeachDoor(GameTestHelper helper) {
        helper.assertFalse(IronDoorLearningController.isLearnableMechanism(
                Blocks.STONE.defaultBlockState()),
                "Un bloc ordinaire ne doit pas devenir un mécanisme appris");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void zombieVillagerKnowsWoodDoor(GameTestHelper helper) {
        helper.assertTrue(LearnedDoorController.canUseDoor(true, false,
                        false, true),
                "Le zombie-villageois doit savoir ouvrir le bois");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void ironDoorNeedsMemory(GameTestHelper helper) {
        helper.assertFalse(LearnedDoorController.canUseDoor(false, true,
                        false, true),
                "Une porte en fer inconnue doit rester inaccessible");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void learnedIronDoorCanBeUsed(GameTestHelper helper) {
        helper.assertTrue(LearnedDoorController.canUseDoor(false, true,
                        true, true),
                "Une porte en fer apprise doit pouvoir être utilisée");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void memoryStoresDoorAndMechanism(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        BlockPos door = villager.blockPosition().east();
        BlockPos mechanism = door.west();
        IronDoorMemory.remember(villager, helper.getLevel(), door,
                mechanism, helper.getLevel().getGameTime());
        helper.assertTrue(IronDoorMemory.knows(villager, helper.getLevel(), door),
                "La porte observée doit être mémorisée");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void repeatedObservationDoesNotDuplicate(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        BlockPos door = villager.blockPosition().east();
        IronDoorMemory.remember(villager, helper.getLevel(), door,
                door.west(), 10L);
        IronDoorMemory.remember(villager, helper.getLevel(), door,
                door.north(), 20L);
        helper.assertValueEqual(IronDoorMemory.size(villager,
                helper.getLevel()), 1,
                "Réapprendre la même porte doit remplacer son mécanisme");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void zombificationCopiesDoorKnowledge(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        ZombieVillager zombie = helper.spawn(EntityType.ZOMBIE_VILLAGER,
                new BlockPos(4, 1, 2));
        BlockPos door = villager.blockPosition().east();
        IronDoorMemory.remember(villager, helper.getLevel(), door,
                door.west(), 10L);
        IronDoorMemory.copy(villager, zombie);
        helper.assertTrue(IronDoorMemory.knows(zombie, helper.getLevel(), door),
                "Le zombie-villageois doit conserver le savoir de sa vie passée");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void healingCanReturnDoorKnowledge(GameTestHelper helper) {
        ZombieVillager zombie = helper.spawn(EntityType.ZOMBIE_VILLAGER,
                new BlockPos(2, 1, 2));
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(4, 1, 2));
        BlockPos door = zombie.blockPosition().east();
        IronDoorMemory.remember(zombie, helper.getLevel(), door,
                door.west(), 10L);
        IronDoorMemory.copy(zombie, villager);
        helper.assertTrue(IronDoorMemory.knows(villager, helper.getLevel(), door),
                "La guérison doit restituer la mémoire de porte");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void nearestIronDoorIsAssociatedWithMechanism(GameTestHelper helper) {
        BlockPos mechanism = new BlockPos(3, 1, 3);
        BlockPos door = mechanism.east();
        helper.getLevel().setBlockAndUpdate(mechanism,
                Blocks.LEVER.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(door,
                Blocks.IRON_DOOR.defaultBlockState().setValue(
                        BlockStateProperties.DOUBLE_BLOCK_HALF,
                        DoubleBlockHalf.LOWER));
        helper.getLevel().setBlockAndUpdate(door.above(),
                Blocks.IRON_DOOR.defaultBlockState().setValue(
                        BlockStateProperties.DOUBLE_BLOCK_HALF,
                        DoubleBlockHalf.UPPER));
        helper.assertValueEqual(IronDoorLearningController.findNearestIronDoor(
                helper.getLevel(), mechanism, 4), door,
                "Le mécanisme doit être associé à la porte en fer proche");
        helper.succeed();
    }

    @GameTest(batch = "door_learning", template = "empty")
    public static void memoryIsBounded(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER,
                new BlockPos(2, 1, 2));
        for (int index = 0; index < 30; index++) {
            BlockPos door = villager.blockPosition().offset(index, 0, 0);
            IronDoorMemory.remember(villager, helper.getLevel(), door,
                    door.north(), index);
        }
        helper.assertTrue(IronDoorMemory.size(villager, helper.getLevel()) <= 24,
                "La mémoire doit rester bornée pour éviter une croissance infinie");
        helper.succeed();
    }
}
