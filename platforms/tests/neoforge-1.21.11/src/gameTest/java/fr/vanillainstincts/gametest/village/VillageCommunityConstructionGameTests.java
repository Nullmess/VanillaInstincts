package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.village.FarmerVillageServiceController;
import fr.vanillainstincts.village.VillageConstructionCapability;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageCommunityConstructionGameTests {
    private VillageCommunityConstructionGameTests() {
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void farmerCanBuildClericWorkshop(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.FARMER), VillagerProfessionCompat.value(VillagerProfession.CLERIC)),
                net.minecraft.network.chat.Component.literal("Un fermier disponible doit pouvoir aider au temple du clerc"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void unemployedCanBuildWeaponsmithWorkshop(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildWorkshop(
                        VillagerProfessionCompat.value(VillagerProfession.NONE),
                        VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH)),
                net.minecraft.network.chat.Component.literal("Un villageois ordinaire doit pouvoir aider à la forge"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void librarianCanBuildRoad(GameTestHelper helper) {
        helper.assertTrue(VillageConstructionCapability.canBuildRoad(
                        VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN)),
                net.minecraft.network.chat.Component.literal("La construction des chemins doit être communautaire"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void ownerProfessionRemainsPreferred(GameTestHelper helper) {
        int owner = VillageConstructionCapability.workPreference(
                VillagerProfessionCompat.value(VillagerProfession.CLERIC), VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                false, false);
        int helperScore = VillageConstructionCapability.workPreference(
                VillagerProfessionCompat.value(VillagerProfession.FARMER), VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                false, false);
        helper.assertTrue(owner < helperScore,
                net.minecraft.network.chat.Component.literal("Le propriétaire du poste doit rester prioritaire sans être obligatoire"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void masonIsPreferredCommunityHelper(GameTestHelper helper) {
        int mason = VillageConstructionCapability.workPreference(
                VillagerProfessionCompat.value(VillagerProfession.MASON), VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                false, false);
        int farmer = VillageConstructionCapability.workPreference(
                VillagerProfessionCompat.value(VillagerProfession.FARMER), VillagerProfessionCompat.value(VillagerProfession.CLERIC),
                false, false);
        helper.assertTrue(mason < farmer,
                net.minecraft.network.chat.Component.literal("Le maçon doit être choisi avant un autre métier non propriétaire"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void hayPatternKeepsRequestedSize(GameTestHelper helper) {
        helper.assertValueEqual(FarmerVillageServiceController
                        .hayPattern(11, 0).size(), 11,
                net.minecraft.network.chat.Component.literal("Un lot de onze foins doit produire onze emplacements"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void hayPatternContainsNoDuplicate(GameTestHelper helper) {
        List<BlockPos> pattern = FarmerVillageServiceController
                .hayPattern(13, 2);
        helper.assertValueEqual(new HashSet<>(pattern).size(), pattern.size(),
                net.minecraft.network.chat.Component.literal("Un amas de foin ne doit jamais viser deux fois le même bloc"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void hayPatternIsConnected(GameTestHelper helper) {
        List<BlockPos> pattern = FarmerVillageServiceController
                .hayPattern(13, 1);
        Set<BlockPos> accepted = new HashSet<>();
        accepted.add(pattern.getFirst());
        for (int index = 1; index < pattern.size(); index++) {
            BlockPos position = pattern.get(index);
            boolean touches = false;
            for (Direction direction : Direction.values()) {
                if (accepted.contains(position.relative(direction))) {
                    touches = true;
                    break;
                }
            }
            helper.assertTrue(touches,
                    net.minecraft.network.chat.Component.literal("Chaque nouveau foin doit toucher l'amas déjà posé"));
            accepted.add(position);
        }
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void hayPileMayLoseOneBlock(GameTestHelper helper) {
        helper.assertValueEqual(FarmerVillageServiceController
                        .adjustedHayCount(8, 0L), 7,
                net.minecraft.network.chat.Component.literal("Un amas peut être légèrement réduit pour représenter le pain produit"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void hayPileMayGainOneBlock(GameTestHelper helper) {
        helper.assertValueEqual(FarmerVillageServiceController
                        .adjustedHayCount(8, 2L), 9,
                net.minecraft.network.chat.Component.literal("Un amas peut être légèrement agrandi par la production du fermier"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void recentHayPileIsProtected(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 2, 5);
        helper.assertTrue(FarmerVillageServiceController.isProtectedPosition(
                        center.offset(1, 0, 0),
                        new long[]{center.asLong()}, 1_000L, 7_000L),
                net.minecraft.network.chat.Component.literal("Le fermier ne doit pas recasser immédiatement le nouvel amas"));
        helper.succeed();
    }

    @GameTest(batch = "village_community_construction", templateNamespace = "vanillainstincts", template = "empty")
    public static void oldHayPileProtectionExpires(GameTestHelper helper) {
        BlockPos center = new BlockPos(5, 2, 5);
        helper.assertFalse(FarmerVillageServiceController.isProtectedPosition(
                        center,
                        new long[]{center.asLong()}, 7_000L, 7_000L),
                net.minecraft.network.chat.Component.literal("Un ancien amas doit redevenir disponible après la protection"));
        helper.succeed();
    }
}
