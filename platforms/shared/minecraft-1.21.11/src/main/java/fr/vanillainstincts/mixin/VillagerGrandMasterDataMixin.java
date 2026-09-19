package fr.vanillainstincts.mixin;

import fr.vanillainstincts.entity.GrandMasterSyncedData;
import fr.vanillainstincts.registry.VanillaInstinctsMobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 1.21.11-compatible Grand Master synchronization.
 *
 * NeoForge rejects registering SynchedEntityData accessors on foreign vanilla
 * entity classes. Vanilla Instincts already has a hidden Grand Master mob
 * effect, which Minecraft synchronizes to clients, so that marker is the
 * authoritative synchronized state on this target.
 */
@Mixin(Villager.class)
public abstract class VillagerGrandMasterDataMixin
        implements GrandMasterSyncedData {

    @Unique
    private boolean vanillaInstincts$grandMasterClientMarker;

    @Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
    private void vanillaInstincts$receiveGrandMasterMarker(
            byte eventId, CallbackInfo callback) {
        if (eventId == GrandMasterSyncedData.GRAND_MASTER_ON_EVENT) {
            vanillaInstincts$grandMasterClientMarker = true;
        } else if (eventId == GrandMasterSyncedData.GRAND_MASTER_OFF_EVENT) {
            vanillaInstincts$grandMasterClientMarker = false;
        }
    }

    @Override
    public boolean vanillainstincts$isGrandMasterSynced() {
        Villager self = (Villager) (Object) this;
        return vanillaInstincts$grandMasterClientMarker
                || self.hasEffect(VanillaInstinctsMobEffects.GRAND_MASTER);
    }

    @Override
    public void vanillainstincts$setGrandMasterSynced(boolean value) {
        Villager self = (Villager) (Object) this;
        if (value) {
            if (!self.hasEffect(VanillaInstinctsMobEffects.GRAND_MASTER)) {
                self.addEffect(new MobEffectInstance(
                        VanillaInstinctsMobEffects.GRAND_MASTER,
                        MobEffectInstance.INFINITE_DURATION, 0,
                        true, false, false));
            }
        } else if (self.hasEffect(VanillaInstinctsMobEffects.GRAND_MASTER)) {
            self.removeEffect(VanillaInstinctsMobEffects.GRAND_MASTER);
        }
    }
}
