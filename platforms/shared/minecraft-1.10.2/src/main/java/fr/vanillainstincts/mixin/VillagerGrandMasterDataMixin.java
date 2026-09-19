package fr.vanillainstincts.mixin;

import fr.vanillainstincts.entity.GrandMasterSyncedData;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.entity.passive.EntityVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a real synchronized Grand Master bit to every villager. */
@Mixin(EntityVillager.class)
public abstract class VillagerGrandMasterDataMixin
        implements GrandMasterSyncedData {
    @Unique
    private static final DataParameter<Boolean>
            VANILLA_INSTINCTS_GRAND_MASTER = EntityDataManager.defineId(
                    EntityVillager.class, DataSerializers.BOOLEAN);

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void vanillainstincts$defineGrandMasterData(CallbackInfo callback) {
        EntityVillager self = (EntityVillager) (Object) this;
        self.getEntityData().define(VANILLA_INSTINCTS_GRAND_MASTER, false);
    }

    @Override
    public boolean vanillainstincts$isGrandMasterSynced() {
        EntityVillager self = (EntityVillager) (Object) this;
        return self.getEntityData().get(VANILLA_INSTINCTS_GRAND_MASTER);
    }

    @Override
    public void vanillainstincts$setGrandMasterSynced(boolean value) {
        EntityVillager self = (EntityVillager) (Object) this;
        self.getEntityData().set(VANILLA_INSTINCTS_GRAND_MASTER, value);
    }
}
