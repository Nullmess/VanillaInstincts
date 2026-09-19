package fr.vanillainstincts.event;

import fr.vanillainstincts.config.VanillaInstinctsConfigEvents;
import fr.vanillainstincts.data.VanillaInstinctsDataReloadEvents;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Registers feature-owned handlers on the NeoForge event bus. */
public final class VanillaInstinctsEventRegistrar {
    private VanillaInstinctsEventRegistrar() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(VanillaInstinctsConfigEvents::onLoading);
        modBus.addListener(VanillaInstinctsConfigEvents::onReloading);

        NeoForge.EVENT_BUS.addListener(VanillaInstinctsDataReloadEvents::onAddReloadListeners);

        NeoForge.EVENT_BUS.addListener(CommandEvents::onRegisterCommands);

        NeoForge.EVENT_BUS.addListener(CreatureLifecycleEvents::onEntityTick);
        NeoForge.EVENT_BUS.addListener(CreatureLifecycleEvents::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(CreatureLifecycleEvents::onEntityLeaveLevel);

        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onServerTickStart);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerChangeGameMode);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ServerLifecycleEvents::onLevelUnload);

        NeoForge.EVENT_BUS.addListener(MobPossessionManager::onEnderPearlTeleport);
        NeoForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseStart);
        NeoForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseFinish);
        NeoForge.EVENT_BUS.addListener(MobPossessionManager::onItemUseStop);

        NeoForge.EVENT_BUS.addListener(WorldInteractionEvents::onSleepingTimeCheck);
        NeoForge.EVENT_BUS.addListener(WorldInteractionEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(WorldInteractionEvents::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(WorldInteractionEvents::onCropBroken);
        NeoForge.EVENT_BUS.addListener(WorldInteractionEvents::onFarmlandTrample);

        NeoForge.EVENT_BUS.addListener(VillageInteractionEvents::onItemToss);
        NeoForge.EVENT_BUS.addListener(VillageInteractionEvents::onTradeWithVillager);
        NeoForge.EVENT_BUS.addListener(VillageInteractionEvents::onLivingConversion);
        NeoForge.EVENT_BUS.addListener(VillageInteractionEvents::onItemPickup);

        NeoForge.EVENT_BUS.addListener(CombatEvents::onExplosion);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLivingDrops);
    }
}
