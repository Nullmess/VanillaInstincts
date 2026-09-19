package fr.vanillainstincts.gametest.village;

import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.core.policy.VillageTemplateSelectionPolicy;
import java.util.HashSet;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VanillaInstincts.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageTemplateSelectionGameTests {
    private VillageTemplateSelectionGameTests() {
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void unusedTemplatesComeFirst(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{1, 0, 2}, new boolean[]{true, true, false},
                false, 0L, 6);
        helper.assertTrue(order.equals(List.of(1)),
                "Le modèle inédit doit être prioritaire");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void allUsedContinuesBalancedGrowth(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{1, 1, 2}, new boolean[]{true, false, true},
                false, 0L, 6);
        helper.assertTrue(order.equals(List.of(0, 1)),
                "Le cycle doit reprendre avec les modèles les moins utilisés");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void housingFiltersNonHousing(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0}, new boolean[]{false, true, false},
                true, 0L, 6);
        helper.assertTrue(order.equals(List.of(1)),
                "Une urgence de lits doit choisir une maison");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void housingUsesLeastRepeatedModel(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{3, 1, 2}, new boolean[]{true, true, true},
                true, 0L, 6);
        helper.assertTrue(order.equals(List.of(1)),
                "La maison la moins répétée doit être choisie");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void selectorIsDeterministic(GameTestHelper helper) {
        List<Integer> first = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0}, new boolean[]{true, true, true},
                false, 42L, 6);
        List<Integer> second = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0}, new boolean[]{true, true, true},
                false, 42L, 6);
        helper.assertTrue(first.equals(second),
                "La rotation doit rester déterministe");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void selectorRotatesCandidates(GameTestHelper helper) {
        List<Integer> first = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0}, new boolean[]{true, true, true},
                false, 0L, 6);
        List<Integer> second = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0}, new boolean[]{true, true, true},
                false, 1L, 6);
        helper.assertFalse(first.equals(second),
                "Le sélecteur doit répartir les modèles");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void limitBoundsSiteAttempts(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0, 0},
                new boolean[]{true, true, true, true}, false, 0L, 2);
        helper.assertValueEqual(order.size(), 2,
                "Le budget de modèles doit être borné");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void orderContainsNoDuplicate(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0, 0, 0},
                new boolean[]{true, true, true, true}, false, 9L, 6);
        helper.assertValueEqual(new HashSet<>(order).size(), order.size(),
                "Un modèle ne doit apparaître qu'une fois par audit");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void negativeUsageIsNormalized(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{-2, 1}, new boolean[]{true, true}, false, 0L, 6);
        helper.assertTrue(order.equals(List.of(0)),
                "Une ancienne valeur négative doit être traitée comme zéro");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void missingHousingTemplateReturnsEmpty(
            GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0, 0}, new boolean[]{false, false}, true, 0L, 6);
        helper.assertTrue(order.isEmpty(),
                "Aucun faux logement ne doit être sélectionné");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void invalidArraysReturnEmpty(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0}, new boolean[]{true, false}, false, 0L, 6);
        helper.assertTrue(order.isEmpty(),
                "Une entrée incohérente doit être refusée");
        helper.succeed();
    }

    @GameTest(batch = "village_template_selection", templateNamespace = "vanillainstincts",
            template = "empty")
    public static void zeroLimitReturnsEmpty(GameTestHelper helper) {
        List<Integer> order = VillageTemplateSelectionPolicy.order(
                new int[]{0}, new boolean[]{true}, false, 0L, 0);
        helper.assertTrue(order.isEmpty(),
                "Un budget nul ne doit lancer aucun audit");
        helper.succeed();
    }
}
