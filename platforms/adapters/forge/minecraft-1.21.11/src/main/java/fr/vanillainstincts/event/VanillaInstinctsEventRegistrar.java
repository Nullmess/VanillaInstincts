package fr.vanillainstincts.event;

import fr.vanillainstincts.config.VanillaInstinctsConfigEvents;
import fr.vanillainstincts.data.VanillaInstinctsDataReloadEvents;
import fr.vanillainstincts.possession.MobPossessionManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.entity.player.TradeWithVillagerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Registers feature-owned handlers on the Forge EventBus 7 event buses. */
public final class VanillaInstinctsEventRegistrar {
    private VanillaInstinctsEventRegistrar() {
    }

    public static void register(BusGroup modBusGroup) {
        ModConfigEvent.Loading.getBus(modBusGroup)
                .addListener(VanillaInstinctsConfigEvents::onLoading);
        ModConfigEvent.Reloading.getBus(modBusGroup)
                .addListener(VanillaInstinctsConfigEvents::onReloading);

        AddReloadListenerEvent.BUS.addListener(
                VanillaInstinctsDataReloadEvents::onAddReloadListeners);
        RegisterCommandsEvent.BUS.addListener(CommandEvents::onRegisterCommands);

        LivingEvent.LivingTickEvent.BUS.addListener(
                CreatureLifecycleEvents::onLivingTick);
        EntityJoinLevelEvent.BUS.addListener(
                CreatureLifecycleEvents::onEntityJoinLevel);
        EntityLeaveLevelEvent.BUS.addListener(
                CreatureLifecycleEvents::onEntityLeaveLevel);

        TickEvent.ServerTickEvent.Pre.BUS.addListener(
                ServerLifecycleEvents::onServerTickStart);
        TickEvent.ServerTickEvent.Post.BUS.addListener(
                ServerLifecycleEvents::onServerTick);
        PlayerEvent.PlayerChangeGameModeEvent.BUS.addListener(
                ServerLifecycleEvents::onPlayerChangeGameMode);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(
                ServerLifecycleEvents::onPlayerLoggedOut);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(
                ServerLifecycleEvents::onPlayerLoggedIn);
        LevelEvent.Unload.BUS.addListener(ServerLifecycleEvents::onLevelUnload);

        EntityTeleportEvent.EnderPearl.BUS.addListener(
                MobPossessionManager::onEnderPearlTeleport);
        LivingEntityUseItemEvent.Start.BUS.addListener(
                MobPossessionManager::onItemUseStart);
        LivingEntityUseItemEvent.Finish.BUS.addListener(
                MobPossessionManager::onItemUseFinish);
        LivingEntityUseItemEvent.Stop.BUS.addListener(
                MobPossessionManager::onItemUseStop);

        SleepingTimeCheckEvent.BUS.addListener(
                WorldInteractionEvents::onSleepingTimeCheck);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(
                WorldInteractionEvents::onRightClickBlock);
        PlayerInteractEvent.RightClickItem.BUS.addListener(
                WorldInteractionEvents::onRightClickItem);
        BlockEvent.BreakEvent.BUS.addListener(WorldInteractionEvents::onCropBroken);
        BlockEvent.FarmlandTrampleEvent.BUS.addListener(
                WorldInteractionEvents::onFarmlandTrample);

        ItemTossEvent.BUS.addListener(VillageInteractionEvents::onItemToss);
        TradeWithVillagerEvent.BUS.addListener(
                VillageInteractionEvents::onTradeWithVillager);
        LivingConversionEvent.Post.BUS.addListener(
                VillageInteractionEvents::onLivingConversion);
        PlayerEvent.ItemPickupEvent.BUS.addListener(
                VillageInteractionEvents::onItemPickup);

        ExplosionEvent.Detonate.BUS.addListener(CombatEvents::onExplosion);
        ProjectileImpactEvent.BUS.addListener(CombatEvents::onProjectileImpact);
        LivingAttackEvent.BUS.addListener(CombatEvents::onLivingAttack);
        LivingDamageEvent.BUS.addListener(CombatEvents::onLivingDamage);
        LivingDeathEvent.BUS.addListener(CombatEvents::onLivingDeath);
        LivingDropsEvent.BUS.addListener(CombatEvents::onLivingDrops);
    }
}
