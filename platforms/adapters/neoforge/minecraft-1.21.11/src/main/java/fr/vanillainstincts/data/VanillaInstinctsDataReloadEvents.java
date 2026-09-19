package fr.vanillainstincts.data;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/** Server datapack listeners for the data-driven AI foundations. */
public final class VanillaInstinctsDataReloadEvents {
    private VanillaInstinctsDataReloadEvents() {
    }

    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(id("village_path_preferences"), new VillagePathPreferenceManager());
        event.addListener(id("perception_profiles"), new PerceptionProfileManager());
        event.addListener(id("mob_relations"), new MobRelationManager());
        event.addListener(id("farmer_crops"), new FarmerCropRegistry());
        event.addListener(id("animal_welfare_profiles"), new AnimalWelfareProfileManager());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("vanillainstincts", path);
    }
}
