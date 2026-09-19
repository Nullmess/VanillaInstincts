package fr.vanillainstincts.mixin;

import fr.vanillainstincts.entity.GrandMasterSyncedData;
import fr.vanillainstincts.village.VillagerGrandMasterController;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a real synchronized Grand Master bit to every villager. */
@Mixin(VillagerEntity.class)
public abstract class VillagerGrandMasterDataMixin
        implements GrandMasterSyncedData {
    @Unique
    private static final DataParameter<Boolean>
            VANILLA_INSTINCTS_GRAND_MASTER = EntityDataManager.defineId(
                    VillagerEntity.class, DataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void vanillainstincts$defineGrandMasterData(CallbackInfo callback) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        self.getEntityData().define(VANILLA_INSTINCTS_GRAND_MASTER, false);
    }

    @Override
    public boolean vanillainstincts$isGrandMasterSynced() {
        VillagerEntity self = (VillagerEntity) (Object) this;
        return self.getEntityData().get(VANILLA_INSTINCTS_GRAND_MASTER)
                || VillagerGrandMasterController.hasNetheriteMarker(self);
    }

    @Override
    public void vanillainstincts$setGrandMasterSynced(boolean value) {
        VillagerEntity self = (VillagerEntity) (Object) this;
        self.getEntityData().set(VANILLA_INSTINCTS_GRAND_MASTER, value);
    }
}
