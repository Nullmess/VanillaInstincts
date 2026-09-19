package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.bridge.VillagerGrandMasterRenderStateBridge;
import fr.vanillainstincts.entity.GrandMasterSyncedData;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Copies the synchronized Grand Master state before profession layers render. */
@Mixin(VillagerRenderer.class)
public abstract class VillagerRendererGrandMasterStateMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void vanillaInstincts$copyGrandMasterState(
            Villager villager, VillagerRenderState state, float partialTick,
            CallbackInfo callback) {
        boolean grandMaster = villager instanceof GrandMasterSyncedData data
                && data.vanillainstincts$isGrandMasterSynced();
        ((VillagerGrandMasterRenderStateBridge) (Object) state)
                .vanillaInstincts$setGrandMaster(grandMaster);
    }
}
