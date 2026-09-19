package fr.vanillainstincts.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected movement vector introduced in Minecraft 1.21.5. */
@Mixin(ClientInput.class)
public interface ClientInputMoveVectorAccessor {
    @Accessor("moveVector")
    void vanillaInstincts$setMoveVector(Vec2 value);
}
