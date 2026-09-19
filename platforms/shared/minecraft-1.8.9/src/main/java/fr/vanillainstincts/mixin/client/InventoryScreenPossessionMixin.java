package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.MobPossessionClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Renders the possessed mob in the ordinary vanilla player inventory preview. */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenPossessionMixin {
    @ModifyArg(
            method = "renderBg",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/inventory/InventoryScreen;renderEntityInInventory(Lcom/mojang/blaze3d/matrix/MatrixStack;IIIFFLnet/minecraft/entity/EntityLivingBase;)V"
            ),
            index = 6,
            require = 0
    )
    private EntityLivingBase vanillaInstincts$renderPossessedMob(EntityLivingBase original) {
        if (!MobPossessionClientState.active()) return original;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world == null) return original;
        Entity entity = minecraft.world.getEntity(MobPossessionClientState.entityId());
        return entity instanceof EntityLiving ? ((EntityLiving) (entity)) : original;
    }
}
