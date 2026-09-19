package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.village.VillagerProfessionCompat;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.village.DynamicItemValueController;
import fr.vanillainstincts.village.VanillaVillageStructureCatalog;
import fr.vanillainstincts.village.VillageEvolutionController;
import fr.vanillainstincts.village.VillageEvolutionSavedData;
import fr.vanillainstincts.village.VillagerProductionController;
import fr.vanillainstincts.village.VillagerRoutineController;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import fr.vanillainstincts.testcompat.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import fr.vanillainstincts.testcompat.GameTestHolder;
import fr.vanillainstincts.testcompat.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageProductionEvolutionGameTests {
    private VillageProductionEvolutionGameTests() {
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void morningIsProductionTime(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController.phaseFor(3_000L)
                        == VillagerSchedulePhase.WORK,
                net.minecraft.network.chat.Component.literal("La première moitié de journée doit être réservée au travail"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void afternoonIsCommercialTime(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController.phaseFor(8_000L)
                        == VillagerSchedulePhase.SOCIAL,
                net.minecraft.network.chat.Component.literal("La seconde moitié de journée doit être réservée aux échanges"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void fiveCarrotsTeachCommodity(GameTestHelper helper) {
        helper.assertValueEqual(DynamicItemValueController.learnedTradeMinimum(
                        new ItemStack(Items.CARROT)), 5,
                net.minecraft.network.chat.Component.literal("Une marchandise commune doit être apprise après cinq exemplaires"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void uniqueEquipmentNeedsOneSample(GameTestHelper helper) {
        helper.assertValueEqual(DynamicItemValueController.learnedTradeMinimum(
                        new ItemStack(Items.IRON_SWORD)), 1,
                net.minecraft.network.chat.Component.literal("Un équipement unique doit pouvoir être appris dès sa récupération"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void diamondArmorCostsMoreThanIron(GameTestHelper helper) {
        UUID merchant = UUID.fromString("11111111-2222-3333-4444-555555555555");
        int iron = DynamicItemValueController.emeraldPrice(merchant,
                new ItemStack(Items.IRON_CHESTPLATE), 3L, 0);
        int diamond = DynamicItemValueController.emeraldPrice(merchant,
                new ItemStack(Items.DIAMOND_CHESTPLATE), 3L, 0);
        helper.assertTrue(diamond > iron,
                net.minecraft.network.chat.Component.literal("La matière et la rareté doivent modifier le prix réel"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void damagedToolIsCheaper(GameTestHelper helper) {
        ItemStack intact = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = intact.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        helper.assertTrue(DynamicItemValueController.unitValue(intact)
                        > DynamicItemValueController.unitValue(damaged),
                net.minecraft.network.chat.Component.literal("La durabilité restante doit influencer la valeur"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void everyCraftProfessionHasProducts(GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfessionCompat.value(VillagerProfession.ARMORER), VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
                VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH), VillagerProfessionCompat.value(VillagerProfession.BUTCHER),
                VillagerProfessionCompat.value(VillagerProfession.FLETCHER), VillagerProfessionCompat.value(VillagerProfession.SHEPHERD),
                VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
                VillagerProfessionCompat.value(VillagerProfession.MASON), VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER));
        helper.assertTrue(professions.stream().allMatch(profession ->
                        !VillagerProductionController.catalog(profession).isEmpty()),
                net.minecraft.network.chat.Component.literal("Chaque métier artisanal doit avoir un catalogue varié"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void armorerProducesSeveralItemFamilies(GameTestHelper helper) {
        List<ItemStack> catalog = VillagerProductionController.catalog(
                VillagerProfessionCompat.value(VillagerProfession.ARMORER));
        helper.assertTrue(catalog.size() >= 10
                        && catalog.stream().anyMatch(stack -> stack.is(Items.SHIELD))
                        && catalog.stream().anyMatch(stack -> stack.is(Items.DIAMOND_CHESTPLATE)),
                net.minecraft.network.chat.Component.literal("L'armurier ne doit pas produire un objet fixe unique"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void villageBlocksCanBeRemembered(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.isVillageArchitecture(
                        Blocks.OAK_PLANKS.defaultBlockState())
                        && VillageEvolutionController.isVillageArchitecture(
                        Blocks.BREWING_STAND.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Maisons et postes de métier doivent entrer dans la mémoire de réparation"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void naturalGroundIsNotRebuiltAsHouse(GameTestHelper helper) {
        helper.assertFalse(VillageEvolutionController.isVillageArchitecture(
                        Blocks.GRASS_BLOCK.defaultBlockState()),
                net.minecraft.network.chat.Component.literal("Le terrain naturel ne doit pas être copié par la reconstruction"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void activeProjectSurvivesSerialization(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        BlockPos center = new BlockPos(10, 70, 10);
        BlockPos origin = new BlockPos(20, 69, 20);
        UUID builder = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String template = "minecraft:village/plains/houses/plains_temple_3";
        data.begin(center, origin, "WORKSHOP", "cleric", template,
                "CLOCKWISE_90", 47, builder);
        CompoundTag tag = data.saveTag(new CompoundTag(), null);
        VillageEvolutionSavedData restored = VillageEvolutionSavedData.load(tag, null);
        helper.assertTrue(restored.project() != null
                        && restored.project().origin().equals(origin)
                        && restored.project().index() == 47
                        && restored.project().builderId().equals(builder)
                        && restored.project().templateId().equals(template)
                        && restored.project().rotation().equals("CLOCKWISE_90")
                        && VanillaVillageStructureCatalog.isVanillaHouseId(
                        ResourceLocation.fromNamespaceAndPath("minecraft",
                                "village/plains/houses/plains_temple_3")),
                net.minecraft.network.chat.Component.literal("Le modèle vanilla exact d'un chantier incomplet doit reprendre après redémarrage"));
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", templateNamespace = "vanillainstincts", template = "empty")
    public static void clearingProjectIsPersistent(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        data.begin(BlockPos.ZERO, BlockPos.ZERO, "HOUSE", "none", 1, null);
        data.clear();
        CompoundTag tag = data.saveTag(new CompoundTag(), null);
        helper.assertFalse(fr.vanillainstincts.persistence.NbtCompat.getBoolean(tag, "active"),
                net.minecraft.network.chat.Component.literal("Un chantier terminé ne doit pas réapparaître au chargement"));
        helper.succeed();
    }
}
