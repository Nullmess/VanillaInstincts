package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Renders the possessed mob in the ordinary vanilla player inventory preview. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenPossessionMixin {
    @ModifyArg(
            method = "extractBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"
            ),
            index = 9,
            require = 0
    )
    private LivingEntity vanillaInstincts$renderPossessedMob(LivingEntity original) {
        if (!MobPossessionClientState.active()) return original;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return original;
        Entity entity = minecraft.level.getEntity(MobPossessionClientState.entityId());
        return entity instanceof Mob mob ? mob : original;
    }
}
