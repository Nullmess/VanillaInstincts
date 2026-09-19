package fr.vanillainstincts.event;

import fr.vanillainstincts.ai.VanillaInstinctsController;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Minimal legacy event bridge while the 1.12 adapter is ported feature-by-feature. */
public final class LegacyForgeEvents {
    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving instanceof EntityLiving) {
            EntityLiving mob = (EntityLiving) event.entityLiving;
            if (!mob.worldObj.isRemote) {
                VanillaInstinctsController.tick(mob);
            }
        }
    }
}
