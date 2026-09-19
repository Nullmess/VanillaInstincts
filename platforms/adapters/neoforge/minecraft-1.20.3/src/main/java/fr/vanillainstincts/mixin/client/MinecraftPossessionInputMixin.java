package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import fr.vanillainstincts.client.MobPossessionClientState;
import fr.vanillainstincts.network.PossessionActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes possession own vanilla client behaviours that conflict with it:
 * Spectator Sneak camera detaching and player-inventory key actions.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPossessionInputMixin {
    @Inject(method = "setCameraEntity", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$keepPossessionCamera(Entity camera,
                                                        CallbackInfo callback) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.level == null) return;
        Entity possessed = minecraft.level.getEntity(
                MobPossessionClientState.entityId());
        if (possessed != null && camera != possessed) {
            callback.cancel();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void vanillaInstincts$handlePossessionKeys(CallbackInfo callback) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.player == null || minecraft.screen != null) return;

        if (minecraft.options.keyInventory.consumeClick()) {
            // The ordinary vanilla player inventory screen. During possession
            // its backing InventoryMenu is the mob's temporary player body.
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            callback.cancel();
            return;
        }

        if (minecraft.options.keyDrop.consumeClick()) {
            VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                    Screen.hasControlDown()
                            ? PossessionActionPayload.DROP_STACK
                            : PossessionActionPayload.DROP_ONE));
            callback.cancel();
            return;
        }

        if (minecraft.options.keySwapOffhand.consumeClick()) {
            VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                    PossessionActionPayload.SWAP_OFFHAND));
            callback.cancel();
            return;
        }

        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            if (!minecraft.options.keyHotbarSlots[slot].consumeClick()) continue;
            minecraft.player.getInventory().selected = slot;
            VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                    PossessionActionPayload.selectSlot(slot)));
            callback.cancel();
            return;
        }
    }
}
