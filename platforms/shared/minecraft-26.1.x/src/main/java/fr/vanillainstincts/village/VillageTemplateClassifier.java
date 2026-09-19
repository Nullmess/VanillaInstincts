package fr.vanillainstincts.village;

import java.util.List;
import java.util.Locale;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Maps vanilla village templates and workstations to profession roles. */
public final class VillageTemplateClassifier {
    private static final List<VillagerProfession> PROFESSION_ORDER = List.of(
            VillagerProfessionCompat.value(VillagerProfession.ARMORER), VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH),
            VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH), VillagerProfessionCompat.value(VillagerProfession.CLERIC),
            VillagerProfessionCompat.value(VillagerProfession.FISHERMAN), VillagerProfessionCompat.value(VillagerProfession.FARMER),
            VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN), VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER),
            VillagerProfessionCompat.value(VillagerProfession.BUTCHER), VillagerProfessionCompat.value(VillagerProfession.FLETCHER),
            VillagerProfessionCompat.value(VillagerProfession.SHEPHERD), VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER),
            VillagerProfessionCompat.value(VillagerProfession.MASON));

    private VillageTemplateClassifier() {
    }

    public static boolean hasBed(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        return plan != null && plan.bedCount() > 0;
    }

    public static VillageEvolutionController.BuildingKind kindFor(
            VanillaVillageStructureCatalog.TemplatePlan plan,
            VillagerProfession profession) {
        if (profession != null && profession != VillagerProfessionCompat.value(VillagerProfession.NONE)) {
            return VillageEvolutionController.BuildingKind.WORKSHOP;
        }
        return hasBed(plan)
                ? VillageEvolutionController.BuildingKind.HOUSE
                : VillageEvolutionController.BuildingKind.COMMUNITY;
    }

    public static VillagerProfession professionFor(
            VanillaVillageStructureCatalog.TemplatePlan plan) {
        if (plan == null) return VillagerProfessionCompat.value(VillagerProfession.NONE);
        for (VillagerProfession profession : PROFESSION_ORDER) {
            Block workstation = workstation(profession);
            if (workstation != null && plan.contains(workstation)) {
                return profession;
            }
        }
        return professionFromTemplateName(plan.id());
    }

    public static VillagerProfession professionFromTemplateName(
            Identifier id) {
        if (id == null) return VillagerProfessionCompat.value(VillagerProfession.NONE);
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "armorer")) return VillagerProfessionCompat.value(VillagerProfession.ARMORER);
        if (containsAny(path, "tool_smith", "toolsmith")) {
            return VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH);
        }
        if (containsAny(path, "weapon_smith", "weaponsmith")) {
            return VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH);
        }
        if (containsAny(path, "temple", "cleric")) {
            return VillagerProfessionCompat.value(VillagerProfession.CLERIC);
        }
        if (containsAny(path, "fisher")) return VillagerProfessionCompat.value(VillagerProfession.FISHERMAN);
        if (containsAny(path, "farm")) return VillagerProfessionCompat.value(VillagerProfession.FARMER);
        if (containsAny(path, "library", "librarian")) {
            return VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN);
        }
        if (containsAny(path, "cartographer")) {
            return VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER);
        }
        if (containsAny(path, "butcher")) return VillagerProfessionCompat.value(VillagerProfession.BUTCHER);
        if (containsAny(path, "fletcher")) return VillagerProfessionCompat.value(VillagerProfession.FLETCHER);
        if (containsAny(path, "shepherd")) return VillagerProfessionCompat.value(VillagerProfession.SHEPHERD);
        if (containsAny(path, "tannery", "leatherworker")) {
            return VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER);
        }
        if (containsAny(path, "mason")) return VillagerProfessionCompat.value(VillagerProfession.MASON);
        return VillagerProfessionCompat.value(VillagerProfession.NONE);
    }

    public static VillagerProfession professionForWorkstation(Block block) {
        if (block == null) return VillagerProfessionCompat.value(VillagerProfession.NONE);
        for (VillagerProfession profession : PROFESSION_ORDER) {
            if (workstation(profession) == block) return profession;
        }
        return VillagerProfessionCompat.value(VillagerProfession.NONE);
    }

    public static boolean isWorkstation(Block block) {
        return professionForWorkstation(block) != VillagerProfessionCompat.value(VillagerProfession.NONE);
    }

    public static Block workstation(VillagerProfession profession) {
        if (profession == VillagerProfessionCompat.value(VillagerProfession.FARMER)) return Blocks.COMPOSTER;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.FISHERMAN)) return Blocks.BARREL;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.SHEPHERD)) return Blocks.LOOM;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.FLETCHER)) {
            return Blocks.FLETCHING_TABLE;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.LIBRARIAN)) return Blocks.LECTERN;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CARTOGRAPHER)) {
            return Blocks.CARTOGRAPHY_TABLE;
        }
        if (profession == VillagerProfessionCompat.value(VillagerProfession.CLERIC)) return Blocks.BREWING_STAND;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.ARMORER)) return Blocks.BLAST_FURNACE;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.TOOLSMITH)) return Blocks.SMITHING_TABLE;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.WEAPONSMITH)) return Blocks.GRINDSTONE;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.BUTCHER)) return Blocks.SMOKER;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.LEATHERWORKER)) return Blocks.CAULDRON;
        if (profession == VillagerProfessionCompat.value(VillagerProfession.MASON)) return Blocks.STONECUTTER;
        return null;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }
}
