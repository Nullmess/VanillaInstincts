package fr.vanillainstincts.gametest.village;

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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(VanillaInstincts.MOD_ID)
public final class VillageProductionEvolutionGameTests {
    private VillageProductionEvolutionGameTests() {
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void morningIsProductionTime(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController.phaseFor(3_000L)
                        == VillagerSchedulePhase.WORK,
                "La première moitié de journée doit être réservée au travail");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void afternoonIsCommercialTime(GameTestHelper helper) {
        helper.assertTrue(VillagerRoutineController.phaseFor(8_000L)
                        == VillagerSchedulePhase.SOCIAL,
                "La seconde moitié de journée doit être réservée aux échanges");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void fiveCarrotsTeachCommodity(GameTestHelper helper) {
        helper.assertValueEqual(DynamicItemValueController.learnedTradeMinimum(
                        new ItemStack(Items.CARROT)), 5,
                "Une marchandise commune doit être apprise après cinq exemplaires");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void uniqueEquipmentNeedsOneSample(GameTestHelper helper) {
        helper.assertValueEqual(DynamicItemValueController.learnedTradeMinimum(
                        new ItemStack(Items.IRON_SWORD)), 1,
                "Un équipement unique doit pouvoir être appris dès sa récupération");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void diamondArmorCostsMoreThanIron(GameTestHelper helper) {
        UUID merchant = UUID.fromString("11111111-2222-3333-4444-555555555555");
        int iron = DynamicItemValueController.emeraldPrice(merchant,
                new ItemStack(Items.IRON_CHESTPLATE), 3L, 0);
        int diamond = DynamicItemValueController.emeraldPrice(merchant,
                new ItemStack(Items.DIAMOND_CHESTPLATE), 3L, 0);
        helper.assertTrue(diamond > iron,
                "La matière et la rareté doivent modifier le prix réel");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void damagedToolIsCheaper(GameTestHelper helper) {
        ItemStack intact = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack damaged = intact.copy();
        damaged.setDamageValue(damaged.getMaxDamage() - 1);
        helper.assertTrue(DynamicItemValueController.unitValue(intact)
                        > DynamicItemValueController.unitValue(damaged),
                "La durabilité restante doit influencer la valeur");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void everyCraftProfessionHasProducts(GameTestHelper helper) {
        List<VillagerProfession> professions = List.of(
                VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH,
                VillagerProfession.WEAPONSMITH, VillagerProfession.BUTCHER,
                VillagerProfession.FLETCHER, VillagerProfession.SHEPHERD,
                VillagerProfession.LIBRARIAN, VillagerProfession.CARTOGRAPHER,
                VillagerProfession.MASON, VillagerProfession.LEATHERWORKER);
        helper.assertTrue(professions.stream().allMatch(profession ->
                        !VillagerProductionController.catalog(profession).isEmpty()),
                "Chaque métier artisanal doit avoir un catalogue varié");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void armorerProducesSeveralItemFamilies(GameTestHelper helper) {
        List<ItemStack> catalog = VillagerProductionController.catalog(
                VillagerProfession.ARMORER);
        helper.assertTrue(catalog.size() >= 10
                        && catalog.stream().anyMatch(stack -> stack.is(Items.SHIELD))
                        && catalog.stream().anyMatch(stack -> stack.is(Items.DIAMOND_CHESTPLATE)),
                "L'armurier ne doit pas produire un objet fixe unique");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void villageBlocksCanBeRemembered(GameTestHelper helper) {
        helper.assertTrue(VillageEvolutionController.isVillageArchitecture(
                        Blocks.OAK_PLANKS.defaultBlockState())
                        && VillageEvolutionController.isVillageArchitecture(
                        Blocks.BREWING_STAND.defaultBlockState()),
                "Maisons et postes de métier doivent entrer dans la mémoire de réparation");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void naturalGroundIsNotRebuiltAsHouse(GameTestHelper helper) {
        helper.assertFalse(VillageEvolutionController.isVillageArchitecture(
                        Blocks.GRASS_BLOCK.defaultBlockState()),
                "Le terrain naturel ne doit pas être copié par la reconstruction");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void activeProjectSurvivesSerialization(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        BlockPos center = new BlockPos(10, 70, 10);
        BlockPos origin = new BlockPos(20, 69, 20);
        UUID builder = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String template = "minecraft:village/plains/houses/plains_temple_3";
        data.begin(center, origin, "WORKSHOP", "cleric", template,
                "CLOCKWISE_90", 47, builder);
        CompoundTag tag = data.save(new CompoundTag(), null);
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
                "Le modèle vanilla exact d'un chantier incomplet doit reprendre après redémarrage");
        helper.succeed();
    }

    @GameTest(batch = "village_production_evolution", template = "empty")
    public static void clearingProjectIsPersistent(GameTestHelper helper) {
        VillageEvolutionSavedData data = new VillageEvolutionSavedData();
        data.begin(BlockPos.ZERO, BlockPos.ZERO, "HOUSE", "none", 1, null);
        data.clear();
        CompoundTag tag = data.save(new CompoundTag(), null);
        helper.assertFalse(tag.getBoolean("active"),
                "Un chantier terminé ne doit pas réapparaître au chargement");
        helper.succeed();
    }
}
