package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientState;
import fr.vanillainstincts.network.PossessionActionPayload;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes possession own vanilla client behaviours that conflict with it:
 * Spectator Sneak camera detaching and player-inventory key actions.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftPossessionInputMixin {
    @Unique
    private static KeyBinding vanillaInstincts$swapOffhandKey;
    @Unique
    private static boolean vanillaInstincts$swapOffhandResolved;
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

        if (vanillaInstincts$consumeSwapOffhand(minecraft)) {
            VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                    PossessionActionPayload.SWAP_OFFHAND));
            callback.cancel();
            return;
        }

        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            if (!minecraft.options.keyHotbarSlots[slot].consumeClick()) continue;
            minecraft.player.inventory.selected = slot;
            VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                    PossessionActionPayload.selectSlot(slot)));
            callback.cancel();
            return;
        }
    }
    @Unique
    private static boolean vanillaInstincts$consumeSwapOffhand(
            Minecraft minecraft) {
        if (!vanillaInstincts$swapOffhandResolved) {
            vanillaInstincts$swapOffhandResolved = true;
            vanillaInstincts$swapOffhandKey =
                    vanillaInstincts$findSwapOffhandKey(minecraft);
        }
        return vanillaInstincts$swapOffhandKey != null
                && vanillaInstincts$swapOffhandKey.consumeClick();
    }

    @Unique
    private static KeyBinding vanillaInstincts$findSwapOffhandKey(
            Minecraft minecraft) {
        if (minecraft == null || minecraft.options == null) return null;
        Object options = minecraft.options;
        for (Field field : options.getClass().getDeclaredFields()) {
            if (!KeyBinding.class.isAssignableFrom(field.getType())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(options);
                if (value instanceof KeyBinding
                        && vanillaInstincts$isSwapOffhandKey((KeyBinding) value)) {
                    return (KeyBinding) value;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Continue with the next key binding.
            }
        }
        return null;
    }

    @Unique
    private static boolean vanillaInstincts$isSwapOffhandKey(KeyBinding key) {
        for (Method method : key.getClass().getMethods()) {
            if (method.getParameterTypes().length != 0
                    || method.getReturnType() != String.class) {
                continue;
            }
            try {
                if ("key.swapOffhand".equals(method.invoke(key))) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Ignore unrelated string accessors.
            }
        }
        return false;
    }

}
