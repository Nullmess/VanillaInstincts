package fr.vanillainstincts.data;

import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Server datapack listeners for the data-driven AI foundations. */
public final class VanillaInstinctsDataReloadEvents {
    private VanillaInstinctsDataReloadEvents() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VillagePathPreferenceManager());
        event.addListener(new PerceptionProfileManager());
        event.addListener(new MobRelationManager());
        event.addListener(new FarmerCropRegistry());
        event.addListener(new AnimalWelfareProfileManager());
    }
}
