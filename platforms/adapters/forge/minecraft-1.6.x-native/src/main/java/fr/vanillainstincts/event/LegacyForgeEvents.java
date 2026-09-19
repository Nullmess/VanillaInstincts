package fr.vanillainstincts.event;

import fr.vanillainstincts.ai.LegacyMobInstincts;
import net.minecraft.entity.EntityLiving;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingEvent;

/** Forge 1.6.x event bridge. Forge 1.6.x uses @ForgeSubscribe, not FML's 1.7+ @SubscribeEvent. */
public final class LegacyForgeEvents {
    @ForgeSubscribe
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event == null || !(event.entityLiving instanceof EntityLiving)) {
            return;
        }

        EntityLiving mob = (EntityLiving) event.entityLiving;
        if (mob.worldObj == null || mob.worldObj.isRemote) {
            return;
        }

        LegacyMobInstincts.tick(mob);
    }
}
