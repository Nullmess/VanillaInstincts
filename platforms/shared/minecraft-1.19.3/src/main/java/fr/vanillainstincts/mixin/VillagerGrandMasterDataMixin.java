package fr.vanillainstincts.mixin;

import fr.vanillainstincts.entity.GrandMasterSyncedData;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a real synchronized Grand Master bit to every villager. */
@Mixin(Villager.class)
public abstract class VillagerGrandMasterDataMixin
        implements GrandMasterSyncedData {
    @Unique
    private static final EntityDataAccessor<Boolean>
            VANILLA_INSTINCTS_GRAND_MASTER = SynchedEntityData.defineId(
                    Villager.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void vanillainstincts$defineGrandMasterData(CallbackInfo callback) {
        Villager self = (Villager) (Object) this;
        self.getEntityData().define(VANILLA_INSTINCTS_GRAND_MASTER, false);
    }

    @Override
    public boolean vanillainstincts$isGrandMasterSynced() {
        Villager self = (Villager) (Object) this;
        return self.getEntityData().get(VANILLA_INSTINCTS_GRAND_MASTER)
                || VillagerGrandMasterController.hasNetheriteMarker(self);
    }

    @Override
    public void vanillainstincts$setGrandMasterSynced(boolean value) {
        Villager self = (Villager) (Object) this;
        self.getEntityData().set(VANILLA_INSTINCTS_GRAND_MASTER, value);
    }
}
