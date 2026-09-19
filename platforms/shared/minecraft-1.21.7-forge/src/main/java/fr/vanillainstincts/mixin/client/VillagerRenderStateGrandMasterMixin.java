package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.bridge.VillagerGrandMasterRenderStateBridge;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores Grand Master directly on the reusable villager render state. */
@Mixin(VillagerRenderState.class)
public abstract class VillagerRenderStateGrandMasterMixin
        implements VillagerGrandMasterRenderStateBridge {
    @Unique
    private boolean vanillaInstincts$grandMaster;

    @Override
    public boolean vanillaInstincts$isGrandMaster() {
        return vanillaInstincts$grandMaster;
    }

    @Override
    public void vanillaInstincts$setGrandMaster(boolean value) {
        vanillaInstincts$grandMaster = value;
    }
}
