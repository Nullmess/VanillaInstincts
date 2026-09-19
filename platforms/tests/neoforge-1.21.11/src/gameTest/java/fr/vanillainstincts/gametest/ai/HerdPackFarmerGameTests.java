package fr.vanillainstincts.gametest.ai;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.AnimalHerdController;
import fr.vanillainstincts.ai.WolfPackController;
import fr.vanillainstincts.village.FarmerController;
import java.util.List;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HerdPackFarmerGameTests {
    private HerdPackFarmerGameTests() {
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void herdSharesOneLeaderAndSettlesCrowds(
            GameTestHelper helper) {
        Cow self = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Cow adult = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        Cow follower = helper.spawn(EntityType.COW, new BlockPos(2, 1, 3));
        Cow fourth = helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        Cow baby = helper.spawn(EntityType.COW, new BlockPos(4, 1, 2));
        baby.setAge(-24000);
        List<net.minecraft.world.entity.animal.Animal> herd = List.of(
                adult, follower, fourth, baby);
        Cow leader = (Cow) AnimalHerdController.stableLeader(self, herd,
                helper.getLevel(), 100L);
        Cow inherited = (Cow) AnimalHerdController.stableLeader(adult,
                List.of(self, follower, fourth, baby), helper.getLevel(),
                101L);
        helper.assertTrue(leader != null && !leader.isBaby()
                        && leader == inherited,
                net.minecraft.network.chat.Component.literal("Tout le groupe doit conserver le même meneur adulte"));
        List<Cow> nonLeaders = List.of(self, adult, follower, fourth)
                .stream()
                .filter(cow -> cow != leader)
                .toList();
        Cow firstFollower = nonLeaders.get(0);
        Cow secondFollower = nonLeaders.get(1);
        List<net.minecraft.world.entity.animal.Animal> allAnimals = List.of(
                self, adult, follower, fourth, baby);
        Vec3 firstSlot = AnimalHerdController.formationTarget(firstFollower,
                leader, allAnimals);
        Vec3 secondSlot = AnimalHerdController.formationTarget(secondFollower,
                leader, allAnimals);
        helper.assertFalse(firstSlot.equals(secondSlot),
                net.minecraft.network.chat.Component.literal("Deux suiveurs ne doivent pas viser le même emplacement"));
        helper.assertTrue(AnimalHerdController.settledInCrowd(firstFollower,
                        allAnimals, leader),
                net.minecraft.network.chat.Component.literal("Un groupe déjà compact doit cesser de marcher en boucle"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void herdWithoutAdultHasNoLeader(GameTestHelper helper) {
        Cow self = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Cow other = helper.spawn(EntityType.COW, new BlockPos(3, 1, 2));
        self.setAge(-24000);
        other.setAge(-24000);
        helper.assertTrue(AnimalHerdController.temporaryLeader(self,
                        List.of(other)) == null,
                net.minecraft.network.chat.Component.literal("Deux petits ne doivent pas inventer un meneur adulte"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void commonFleeMovesAwayFromDanger(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        Vec3 danger = cow.position().add(5.0D, 0.0D, 0.0D);
        Vec3 destination = AnimalHerdController.commonFleeDestination(cow,
                List.of(), danger);
        helper.assertTrue(destination.x < cow.getX(),
                net.minecraft.network.chat.Component.literal("La direction commune doit s'éloigner du danger"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void wildWolfUsesPackController(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 2));
        helper.assertTrue(WolfPackController.supports(wolf),
                net.minecraft.network.chat.Component.literal("Un loup sauvage doit utiliser la logique de meute"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void missingWolfIsRejected(GameTestHelper helper) {
        helper.assertFalse(WolfPackController.supports(null),
                net.minecraft.network.chat.Component.literal("Un loup absent doit être rejeté proprement"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void injuredPackAbandonsDangerousPrey(GameTestHelper helper) {
        helper.assertTrue(WolfPackController.shouldAbandon(0.30D, true),
                net.minecraft.network.chat.Component.literal("Une meute blessée doit abandonner une proie dangereuse"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void healthyPackKeepsHunt(GameTestHelper helper) {
        helper.assertFalse(WolfPackController.shouldAbandon(0.90D, true),
                net.minecraft.network.chat.Component.literal("Une meute saine ne doit pas abandonner automatiquement"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void harmlessPreyDoesNotForceRetreat(GameTestHelper helper) {
        helper.assertFalse(WolfPackController.shouldAbandon(0.20D, false),
                net.minecraft.network.chat.Component.literal("La santé seule ne doit pas annuler toute chasse"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void wolfFlankKeepsRingDistance(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 2));
        Cow target = helper.spawn(EntityType.COW, new BlockPos(6, 1, 6));
        Vec3 flank = WolfPackController.flankPosition(target, wolf, 4);
        double horizontal = Math.hypot(flank.x - target.getX(),
                flank.z - target.getZ());
        helper.assertTrue(horizontal > 3.0D && horizontal < 8.0D,
                net.minecraft.network.chat.Component.literal("Les loups doivent encercler sans occuper le même bloc"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerCountsRealSeedReserve(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(4);
        inventory.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
        inventory.setItem(1, new ItemStack(Items.CARROT, 3));
        helper.assertValueEqual(FarmerController.totalPlantableCount(inventory),
                8, net.minecraft.network.chat.Component.literal("La réserve doit compter uniquement les objets réellement plantables"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerIgnoresNonPlantableItems(GameTestHelper helper) {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(0, new ItemStack(Items.DIAMOND, 4));
        helper.assertValueEqual(FarmerController.totalPlantableCount(inventory),
                0, net.minecraft.network.chat.Component.literal("Les objets non agricoles ne doivent pas devenir des graines"));
        helper.succeed();
    }

    @GameTest(batch = "herd_pack_farmer", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerMapsSeedToVanillaCrop(GameTestHelper helper) {
        helper.assertValueEqual(FarmerController.cropBlockForItem(
                        Items.WHEAT_SEEDS), Blocks.WHEAT,
                net.minecraft.network.chat.Component.literal("Les graines de blé doivent toujours replanter du blé vanilla"));
        helper.succeed();
    }
}
