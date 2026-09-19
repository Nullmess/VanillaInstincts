package fr.vanillainstincts.event;

import fr.vanillainstincts.config.VanillaInstinctsConfigEvents;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;

/** Registers feature-owned handlers on the Forge event bus. */
public final class VanillaInstinctsEventRegistrar {
    private VanillaInstinctsEventRegistrar() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(VanillaInstinctsConfigEvents::onLoading);
        modBus.addListener(VanillaInstinctsConfigEvents::onReloading);

        MinecraftForge.EVENT_BUS.addListener(CommandEvents::onServerStarting);

        MinecraftForge.EVENT_BUS.addListener(CreatureLifecycleEvents::onLivingTick);
        MinecraftForge.EVENT_BUS.addListener(CreatureLifecycleEvents::onEntityJoinWorld);

        MinecraftForge.EVENT_BUS.addListener(ServerLifecycleEvents::onServerTickStart);
        MinecraftForge.EVENT_BUS.addListener(ServerLifecycleEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ServerLifecycleEvents::onWorldUnload);

        MinecraftForge.EVENT_BUS.addListener(MobPossessionManager::onEnderPearlTeleport);
        MinecraftForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseStart);
        MinecraftForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseFinish);
        MinecraftForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseStop);

        MinecraftForge.EVENT_BUS.addListener(WorldInteractionEvents::onSleepingTimeCheck);
        MinecraftForge.EVENT_BUS.addListener(WorldInteractionEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(WorldInteractionEvents::onRightClickItem);
        MinecraftForge.EVENT_BUS.addListener(WorldInteractionEvents::onCropBroken);
        MinecraftForge.EVENT_BUS.addListener(WorldInteractionEvents::onFarmlandTrample);

        MinecraftForge.EVENT_BUS.addListener(VillageInteractionEvents::onItemToss);
        MinecraftForge.EVENT_BUS.addListener(VillageInteractionEvents::onItemPickup);

        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onExplosion);
        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onProjectileImpact);
        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onLivingDamage);
        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(CombatEvents::onLivingDrops);
    }
}
