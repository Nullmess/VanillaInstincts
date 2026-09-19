package fr.vanillainstincts.village;

import java.util.List;
import java.util.Locale;
import net.minecraft.util.ResourceLocation;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Maps vanilla village templates and workstations to profession roles. */
public final class VillageTemplateClassifier {
    private static final List<LegacyVillagerProfession> PROFESSION_ORDER = fr.vanillainstincts.compat.LegacyJava8.listOf(
            LegacyVillagerProfession.ARMORER, LegacyVillagerProfession.TOOLSMITH,
            LegacyVillagerProfession.WEAPONSMITH, LegacyVillagerProfession.CLERIC,
            LegacyVillagerProfession.FISHERMAN, LegacyVillagerProfession.FARMER,
            LegacyVillagerProfession.LIBRARIAN, LegacyVillagerProfession.CARTOGRAPHER,
            LegacyVillagerProfession.BUTCHER, LegacyVillagerProfession.FLETCHER,
            LegacyVillagerProfession.SHEPHERD, LegacyVillagerProfession.LEATHERWORKER,
            LegacyVillagerProfession.MASON);

    private VillageTemplateClassifier() {
    }

    public static boolean hasBed(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        return plan != null && plan.bedCount() > 0;
    }

    public static VillageEvolutionController.BuildingKind kindFor(
            VanillaVillageStructureCatalog.TemplatePlan plan,
            LegacyVillagerProfession profession) {
        if (profession != null && profession != LegacyVillagerProfession.NONE) {
            return VillageEvolutionController.BuildingKind.WORKSHOP;
        }
        return hasBed(plan)
                ? VillageEvolutionController.BuildingKind.HOUSE
                : VillageEvolutionController.BuildingKind.COMMUNITY;
    }

    public static LegacyVillagerProfession professionFor(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        if (plan == null) return LegacyVillagerProfession.NONE;
        for (LegacyVillagerProfession profession : PROFESSION_ORDER) {
            Block workstation = workstation(profession);
            if (workstation != null && plan.contains(workstation)) {
                return profession;
            }
        }
        return professionFromTemplateName(plan.id());
    }

    public static LegacyVillagerProfession professionFromTemplateName(
            ResourceLocation id) {
        if (id == null) return LegacyVillagerProfession.NONE;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(id).toLowerCase(Locale.ROOT);
        if (containsAny(path, "armorer")) return LegacyVillagerProfession.ARMORER;
        if (containsAny(path, "tool_smith", "toolsmith")) {
            return LegacyVillagerProfession.TOOLSMITH;
        }
        if (containsAny(path, "weapon_smith", "weaponsmith")) {
            return LegacyVillagerProfession.WEAPONSMITH;
        }
        if (containsAny(path, "temple", "cleric")) {
            return LegacyVillagerProfession.CLERIC;
        }
        if (containsAny(path, "fisher")) return LegacyVillagerProfession.FISHERMAN;
        if (containsAny(path, "farm")) return LegacyVillagerProfession.FARMER;
        if (containsAny(path, "library", "librarian")) {
            return LegacyVillagerProfession.LIBRARIAN;
        }
        if (containsAny(path, "cartographer")) {
            return LegacyVillagerProfession.CARTOGRAPHER;
        }
        if (containsAny(path, "butcher")) return LegacyVillagerProfession.BUTCHER;
        if (containsAny(path, "fletcher")) return LegacyVillagerProfession.FLETCHER;
        if (containsAny(path, "shepherd")) return LegacyVillagerProfession.SHEPHERD;
        if (containsAny(path, "tannery", "leatherworker")) {
            return LegacyVillagerProfession.LEATHERWORKER;
        }
        if (containsAny(path, "mason")) return LegacyVillagerProfession.MASON;
        return LegacyVillagerProfession.NONE;
    }

    public static LegacyVillagerProfession professionForWorkstation(Block block) {
        if (block == null) return LegacyVillagerProfession.NONE;
        for (LegacyVillagerProfession profession : PROFESSION_ORDER) {
            if (workstation(profession) == block) return profession;
        }
        return LegacyVillagerProfession.NONE;
    }

    public static boolean isWorkstation(Block block) {
        return professionForWorkstation(block) != LegacyVillagerProfession.NONE;
    }

    public static Block workstation(LegacyVillagerProfession profession) {
        if (profession == LegacyVillagerProfession.FARMER) return Blocks.FARMLAND;
        if (profession == LegacyVillagerProfession.FISHERMAN) return Blocks.CHEST;
        if (profession == LegacyVillagerProfession.SHEPHERD) return Blocks.WOOL;
        if (profession == LegacyVillagerProfession.FLETCHER) {
            return Blocks.CRAFTING_TABLE;
        }
        if (profession == LegacyVillagerProfession.LIBRARIAN) return Blocks.BOOKSHELF;
        if (profession == LegacyVillagerProfession.CARTOGRAPHER) {
            return Blocks.CRAFTING_TABLE;
        }
        if (profession == LegacyVillagerProfession.CLERIC) return Blocks.BREWING_STAND;
        if (profession == LegacyVillagerProfession.ARMORER) return Blocks.ANVIL;
        if (profession == LegacyVillagerProfession.TOOLSMITH) return Blocks.ANVIL;
        if (profession == LegacyVillagerProfession.WEAPONSMITH) return Blocks.ANVIL;
        if (profession == LegacyVillagerProfession.BUTCHER) return Blocks.FURNACE;
        if (profession == LegacyVillagerProfession.LEATHERWORKER) return Blocks.CAULDRON;
        if (profession == LegacyVillagerProfession.MASON) return Blocks.STONEBRICK;
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
