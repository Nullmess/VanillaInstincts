package fr.vanillainstincts.village;

import fr.vanillainstincts.network.GrandMasterProgressPayload;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Synchronizes rank progress into the currently open merchant screen. */
public final class VillagerGrandMasterProgressDisplay {
    private static final long RESEND_INTERVAL_TICKS = 20L;
    private static final Map<UUID, SentState> SENT = new HashMap<>();

    private VillagerGrandMasterProgressDisplay() {
    }

    public static void update(Villager villager, ServerLevel level,
                              long currentDay) {
        if (villager == null || level == null
                || !(villager.getTradingPlayer() instanceof ServerPlayer player)
                || villager.getVillagerData().getLevel() < 5) {
            return;
        }

        int containerId = player.containerMenu.containerId;
        int trades = VillagerGrandMasterController.masterTradeCount(villager);
        int days = (int) Math.min(Integer.MAX_VALUE,
                VillagerGrandMasterController.masteredDays(villager,
                        currentDay));
        boolean grandMaster = VillagerGrandMasterController
                .isGrandMaster(villager);
        long gameTime = level.getGameTime();
        SentState previous = SENT.get(player.getUUID());
        boolean unchanged = previous != null
                && previous.samePayload(villager.getUUID(), containerId,
                trades, days, grandMaster);
        if (unchanged
                && gameTime - previous.sentAtGameTime()
                < RESEND_INTERVAL_TICKS) {
            return;
        }

        SentState next = new SentState(villager.getUUID(), containerId,
                trades, days, grandMaster, gameTime);
        SENT.put(player.getUUID(), next);
        VanillaInstinctsNetwork.sendToPlayer(player,
                new GrandMasterProgressPayload(containerId, trades, days,
                        grandMaster));
    }

    public static void tick(MinecraftServer server) {
        if (server == null || SENT.isEmpty()) return;
        Iterator<Map.Entry<UUID, SentState>> iterator = SENT.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SentState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(
                    entry.getKey());
            SentState display = entry.getValue();
            if (player == null || !player.isAlive()) {
                iterator.remove();
                continue;
            }
            Entity entity = player.getLevel().getEntity(display.villagerId());
            if (!(entity instanceof Villager villager)
                    || villager.getTradingPlayer() != player
                    || villager.isBaby()
                    || villager.getVillagerData().getLevel() < 5
                    || villager.getVillagerData().getProfession()
                    == VillagerProfession.NONE
                    || villager.getVillagerData().getProfession()
                    == VillagerProfession.NITWIT
                    || player.containerMenu.containerId
                    != display.containerId()) {
                iterator.remove();
            }
        }
    }

    public static void remove(ServerPlayer player) {
        if (player != null) SENT.remove(player.getUUID());
    }

    public static void clearLevel(ServerLevel level) {
        if (level == null || SENT.isEmpty()) return;
        Iterator<Map.Entry<UUID, SentState>> iterator = SENT.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            ServerPlayer player = level.getServer().getPlayerList()
                    .getPlayer(iterator.next().getKey());
            if (player != null && player.getLevel() == level) {
                iterator.remove();
            }
        }
    }

    private record SentState(UUID villagerId, int containerId, int trades,
                             int days, boolean grandMaster,
                             long sentAtGameTime) {
        private boolean samePayload(UUID nextVillagerId, int nextContainerId,
                                    int nextTrades, int nextDays,
                                    boolean nextGrandMaster) {
            return villagerId.equals(nextVillagerId)
                    && containerId == nextContainerId
                    && trades == nextTrades
                    && days == nextDays
                    && grandMaster == nextGrandMaster;
        }
    }
}
