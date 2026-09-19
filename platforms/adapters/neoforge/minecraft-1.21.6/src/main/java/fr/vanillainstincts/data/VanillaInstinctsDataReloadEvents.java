package fr.vanillainstincts.data;

import net.minecraft.resources.ResourceLocation;
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("vanillainstincts", path);
    }
}
