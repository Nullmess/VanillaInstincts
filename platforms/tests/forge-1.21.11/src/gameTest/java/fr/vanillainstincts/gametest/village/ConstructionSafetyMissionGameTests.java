package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.ai.PillagerOutpostLootController;
import fr.vanillainstincts.village.VanillaVillageStructureCatalog;
import fr.vanillainstincts.village.VillageConstructionSafety;
import fr.vanillainstincts.village.VillageConstructionSiteValidator;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import fr.vanillainstincts.village.VillageHouseLootController;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionSafetyMissionGameTests {
    private ConstructionSafetyMissionGameTests() {
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void solidConstructionIsNeverOverwritten(GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        helper.getLevel().setBlock(position,
                Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertFalse(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.OAK_PLANKS.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), false, false),
                net.minecraft.network.chat.Component.literal("Un mur existant différent ne doit jamais être écrasé"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void missingBlockCanBeRestored(GameTestHelper helper) {
        BlockPos position = new BlockPos(3, 2, 2);
        helper.getLevel().setBlock(position, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertTrue(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.AIR.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState(), false, false),
                net.minecraft.network.chat.Component.literal("Un bloc réellement absent doit pouvoir être reconstruit"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void sameBlockTypeCanBeCorrected(GameTestHelper helper) {
        BlockPos position = new BlockPos(4, 2, 2);
        helper.assertTrue(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.OAK_STAIRS.defaultBlockState().setValue(
                                BlockStateProperties.HORIZONTAL_FACING,
                                Direction.NORTH),
                        Blocks.OAK_STAIRS.defaultBlockState().setValue(
                                BlockStateProperties.HORIZONTAL_FACING,
                                Direction.SOUTH), false, false),
                net.minecraft.network.chat.Component.literal("Le même type de bloc mal orienté doit rester corrigeable"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void roadCannotCutThroughHouse(GameTestHelper helper) {
        BlockPos position = new BlockPos(5, 2, 2);
        helper.getLevel().setBlock(position,
                Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertFalse(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.SPRUCE_PLANKS.defaultBlockState(),
                        Blocks.DIRT_PATH.defaultBlockState(), true, true),
                net.minecraft.network.chat.Component.literal("Un chemin ne doit pas traverser une construction"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void roadMayReplaceNaturalGround(GameTestHelper helper) {
        BlockPos position = new BlockPos(6, 2, 2);
        helper.assertTrue(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.DIRT_PATH.defaultBlockState(), true, true),
                net.minecraft.network.chat.Component.literal("Le routier doit pouvoir travailler le terrain naturel"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void foundationCannotReplaceSolidTerrain(GameTestHelper helper) {
        BlockPos position = new BlockPos(7, 2, 3);
        helper.getLevel().setBlock(position, Blocks.STONE.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertFalse(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.OAK_PLANKS.defaultBlockState(), true, false),
                net.minecraft.network.chat.Component.literal("Une fondation ne doit pas assimiler un bloc solide à du terrain libre"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void roadCannotReplaceStoneFoundation(GameTestHelper helper) {
        BlockPos position = new BlockPos(7, 2, 4);
        helper.getLevel().setBlock(position, Blocks.STONE.defaultBlockState(),
                Block.UPDATE_ALL);
        helper.assertFalse(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.DIRT_PATH.defaultBlockState(), true, true),
                net.minecraft.network.chat.Component.literal("Un chemin ne doit pas confondre une fondation en pierre avec du sol meuble"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void chestIsNeverClearedByTemplateAir(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(new BlockPos(7, 2, 2));
        helper.getLevel().setBlock(position, Blocks.CHEST.defaultBlockState(),
                Block.UPDATE_ALL);
        CompoundTag blockTag = new CompoundTag();
        CompoundTag blockEntityTag = new CompoundTag();
        blockEntityTag.putString("LootTable",
                "minecraft:chests/village/village_plains_house");
        blockEntityTag.putLong("LootTableSeed", 1234L);
        blockTag.put("nbt", blockEntityTag);
        Identifier lootTable = VanillaVillageStructureCatalog
                .lootTableFromBlockTag(blockTag);
        helper.assertTrue(lootTable != null
                        && VillageHouseLootController.ensure(helper.getLevel(),
                        position, lootTable, 1234L),
                net.minecraft.network.chat.Component.literal("Le coffre construit doit reprendre la loot table du village vanilla"));
        var container = (RandomizableContainerBlockEntity) helper.getLevel()
                .getBlockEntity(position);
        helper.assertTrue(container != null
                        && container.getLootTable() != null
                        && container.getLootTable().identifier().equals(lootTable)
                        && container.getLootTableSeed() == 1234L,
                net.minecraft.network.chat.Component.literal("La table et sa graine doivent être conservées sans générer le loot en avance"));
        helper.assertFalse(VillageConstructionSafety.canApply(
                        helper.getLevel(), position,
                        helper.getLevel().getBlockState(position),
                        Blocks.AIR.defaultBlockState(), false, false),
                net.minecraft.network.chat.Component.literal("L'air d'un template ne doit jamais supprimer un coffre"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void roofCannotBecomeConstructionGround(GameTestHelper helper) {
        BlockPos roof = new BlockPos(9, 3, 3);
        helper.getLevel().setBlock(roof,
                Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
        VanillaVillageStructureCatalog.RotatedPlan plan =
                new VanillaVillageStructureCatalog.RotatedPlan(
                        Identifier.fromNamespaceAndPath("minecraft",
                                "village/plains/houses/plains_small_house_1"),
                        Rotation.NONE,
                        List.of(new VanillaVillageStructureCatalog.TemplateBlock(
                                BlockPos.ZERO,
                                Blocks.OAK_PLANKS.defaultBlockState())),
                        1, 1, 1);
        BlockPos origin = VillageConstructionSiteValidator.origin(
                helper.getLevel(), roof.getX(), roof.getZ(), plan);
        helper.assertTrue(origin == null,
                net.minecraft.network.chat.Component.literal("Le toit d'une structure ne doit jamais servir de terrain"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void vanillaIdentitySurvivesSerialization(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        BlockPos center = new BlockPos(10, 70, 10);
        BlockPos origin = new BlockPos(16, 69, 18);
        String template = "minecraft:village/plains/houses/plains_weaponsmith_1";
        data.rememberStructure(center, origin, "WORKSHOP", "weaponsmith",
                template, "CLOCKWISE_90");
        CompoundTag saved = data.saveTag(new CompoundTag());
        VillageEvolutionSavedData restored = VillageEvolutionSavedData.load(saved);
        helper.assertTrue(restored.knownStructures().size() == 1
                        && restored.knownStructures().get(0).origin().equals(origin)
                        && restored.knownStructures().get(0).templateId()
                        .equals(template)
                        && restored.knownStructures().get(0).rotation()
                        .equals("CLOCKWISE_90"),
                net.minecraft.network.chat.Component.literal("La maison vanilla exacte doit rester connue après redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void sameOriginKeepsOnlyBestIdentity(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        BlockPos origin = new BlockPos(20, 70, 20);
        data.rememberStructure(BlockPos.ZERO, origin, "HOUSE", "none",
                "minecraft:village/plains/houses/plains_small_house_1",
                "NONE");
        data.rememberStructure(BlockPos.ZERO, origin, "HOUSE", "none",
                "minecraft:village/plains/houses/plains_small_house_2",
                "CLOCKWISE_90");
        helper.assertTrue(data.knownStructures().size() == 1
                        && data.knownStructures().get(0).templateId()
                        .endsWith("plains_small_house_2"),
                net.minecraft.network.chat.Component.literal("Une origine ne doit pas mémoriser deux maisons contradictoires"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void freshPillagerCanOwnWholeMission(GameTestHelper helper) {
        Pillager pillager = helper.spawn(EntityType.PILLAGER,
                new BlockPos(2, 1, 6));
        helper.assertTrue(PillagerOutpostLootController.availableForMission(
                        pillager),
                net.minecraft.network.chat.Component.literal("Un pillard libre doit pouvoir prendre toute la mission"));
        helper.succeed();
    }

    @GameTest(batch = "construction_safety_mission", templateNamespace = "vanillainstincts", template = "empty")
    public static void busyPillagerCannotShareSecondMission(GameTestHelper helper) {
        Pillager pillager = helper.spawn(EntityType.PILLAGER,
                new BlockPos(4, 1, 6));
        fr.vanillainstincts.persistence.NbtCompat.putUuid(pillager.getPersistentData(), "vanillainstincts_outpost_mission",
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        helper.assertFalse(PillagerOutpostLootController.availableForMission(
                        pillager),
                net.minecraft.network.chat.Component.literal("Un pillard déjà porteur ne doit pas partager une autre mort"));
        helper.succeed();
    }
}
