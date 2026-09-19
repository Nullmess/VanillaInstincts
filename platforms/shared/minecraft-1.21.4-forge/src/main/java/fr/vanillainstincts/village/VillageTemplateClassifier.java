package fr.vanillainstincts.village;

import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Maps vanilla village templates and workstations to profession roles. */
public final class VillageTemplateClassifier {
    private static final List<VillagerProfession> PROFESSION_ORDER = List.of(
            VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH,
            VillagerProfession.WEAPONSMITH, VillagerProfession.CLERIC,
            VillagerProfession.FISHERMAN, VillagerProfession.FARMER,
            VillagerProfession.LIBRARIAN, VillagerProfession.CARTOGRAPHER,
            VillagerProfession.BUTCHER, VillagerProfession.FLETCHER,
            VillagerProfession.SHEPHERD, VillagerProfession.LEATHERWORKER,
            VillagerProfession.MASON);

    private VillageTemplateClassifier() {
    }

    public static boolean hasBed(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        return plan != null && plan.bedCount() > 0;
    }

    public static VillageEvolutionController.BuildingKind kindFor(
            VanillaVillageStructureCatalog.TemplatePlan plan,
            VillagerProfession profession) {
        if (profession != null && profession != VillagerProfession.NONE) {
            return VillageEvolutionController.BuildingKind.WORKSHOP;
        }
        return hasBed(plan)
                ? VillageEvolutionController.BuildingKind.HOUSE
                : VillageEvolutionController.BuildingKind.COMMUNITY;
    }

    public static VillagerProfession professionFor(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        if (plan == null) return VillagerProfession.NONE;
        for (VillagerProfession profession : PROFESSION_ORDER) {
            Block workstation = workstation(profession);
            if (workstation != null && plan.contains(workstation)) {
                return profession;
            }
        }
        return professionFromTemplateName(plan.id());
    }

    public static VillagerProfession professionFromTemplateName(
            ResourceLocation id) {
        if (id == null) return VillagerProfession.NONE;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "armorer")) return VillagerProfession.ARMORER;
        if (containsAny(path, "tool_smith", "toolsmith")) {
            return VillagerProfession.TOOLSMITH;
        }
        if (containsAny(path, "weapon_smith", "weaponsmith")) {
            return VillagerProfession.WEAPONSMITH;
        }
        if (containsAny(path, "temple", "cleric")) {
            return VillagerProfession.CLERIC;
        }
        if (containsAny(path, "fisher")) return VillagerProfession.FISHERMAN;
        if (containsAny(path, "farm")) return VillagerProfession.FARMER;
        if (containsAny(path, "library", "librarian")) {
            return VillagerProfession.LIBRARIAN;
        }
        if (containsAny(path, "cartographer")) {
            return VillagerProfession.CARTOGRAPHER;
        }
        if (containsAny(path, "butcher")) return VillagerProfession.BUTCHER;
        if (containsAny(path, "fletcher")) return VillagerProfession.FLETCHER;
        if (containsAny(path, "shepherd")) return VillagerProfession.SHEPHERD;
        if (containsAny(path, "tannery", "leatherworker")) {
            return VillagerProfession.LEATHERWORKER;
        }
        if (containsAny(path, "mason")) return VillagerProfession.MASON;
        return VillagerProfession.NONE;
    }

    public static VillagerProfession professionForWorkstation(Block block) {
        if (block == null) return VillagerProfession.NONE;
        for (VillagerProfession profession : PROFESSION_ORDER) {
            if (workstation(profession) == block) return profession;
        }
        return VillagerProfession.NONE;
    }

    public static boolean isWorkstation(Block block) {
        return professionForWorkstation(block) != VillagerProfession.NONE;
    }

    public static Block workstation(VillagerProfession profession) {
        if (profession == VillagerProfession.FARMER) return Blocks.COMPOSTER;
        if (profession == VillagerProfession.FISHERMAN) return Blocks.BARREL;
        if (profession == VillagerProfession.SHEPHERD) return Blocks.LOOM;
        if (profession == VillagerProfession.FLETCHER) {
            return Blocks.FLETCHING_TABLE;
        }
        if (profession == VillagerProfession.LIBRARIAN) return Blocks.LECTERN;
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return Blocks.CARTOGRAPHY_TABLE;
        }
        if (profession == VillagerProfession.CLERIC) return Blocks.BREWING_STAND;
        if (profession == VillagerProfession.ARMORER) return Blocks.BLAST_FURNACE;
        if (profession == VillagerProfession.TOOLSMITH) return Blocks.SMITHING_TABLE;
        if (profession == VillagerProfession.WEAPONSMITH) return Blocks.GRINDSTONE;
        if (profession == VillagerProfession.BUTCHER) return Blocks.SMOKER;
        if (profession == VillagerProfession.LEATHERWORKER) return Blocks.CAULDRON;
        if (profession == VillagerProfession.MASON) return Blocks.STONECUTTER;
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
