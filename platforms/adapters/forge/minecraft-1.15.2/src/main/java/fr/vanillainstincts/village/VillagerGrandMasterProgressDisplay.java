package fr.vanillainstincts.village;

import fr.vanillainstincts.network.GrandMasterProgressPayload;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerProfession;

/** Synchronizes rank progress into the currently open merchant screen. */
public final class VillagerGrandMasterProgressDisplay {
    private static final long RESEND_INTERVAL_TICKS = 20L;
    private static final Map<UUID, SentState> SENT = new HashMap<>();

    private VillagerGrandMasterProgressDisplay() {
    }

    public static void update(VillagerEntity villager, ServerWorld level,
                              long currentDay) {
        if (villager == null || level == null
                || !(villager.getTradingPlayer() instanceof ServerPlayerEntity)
                || villager.getVillagerData().getLevel() < 5) {
            return;
        } ServerPlayerEntity player = (ServerPlayerEntity) (villager.getTradingPlayer());

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
            ServerPlayerEntity player = server.getPlayerList().getPlayer(
                    entry.getKey());
            SentState display = entry.getValue();
            if (player == null || !player.isAlive()) {
                iterator.remove();
                continue;
            }
            Entity entity = player.getLevel().getEntity(display.villagerId());
            if (!(entity instanceof VillagerEntity)
                    || ((VillagerEntity) (entity)).getTradingPlayer() != player
                    || ((VillagerEntity) (entity)).isBaby()
                    || ((VillagerEntity) (entity)).getVillagerData().getLevel() < 5
                    || ((VillagerEntity) (entity)).getVillagerData().getProfession()
                    == VillagerProfession.NONE
                    || ((VillagerEntity) (entity)).getVillagerData().getProfession()
                    == VillagerProfession.NITWIT
                    || player.containerMenu.containerId
                    != display.containerId()) {
                iterator.remove();
            }
        }
    }

    public static void remove(ServerPlayerEntity player) {
        if (player != null) SENT.remove(player.getUUID());
    }

    public static void clearLevel(ServerWorld level) {
        if (level == null || SENT.isEmpty()) return;
        Iterator<Map.Entry<UUID, SentState>> iterator = SENT.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            ServerPlayerEntity player = level.getServer().getPlayerList()
                    .getPlayer(iterator.next().getKey());
            if (player != null && player.getLevel() == level) {
                iterator.remove();
            }
        }
    }

    private static class SentState {
        private final UUID villagerId;
        private final int containerId;
        private final int trades;
        private final int days;
        private final boolean grandMaster;
        private final long sentAtGameTime;

        public SentState(UUID villagerId, int containerId, int trades, int days, boolean grandMaster, long sentAtGameTime) {
            this.villagerId = villagerId;
            this.containerId = containerId;
            this.trades = trades;
            this.days = days;
            this.grandMaster = grandMaster;
            this.sentAtGameTime = sentAtGameTime;
        }

        public UUID villagerId() { return this.villagerId; }

        public int containerId() { return this.containerId; }

        public int trades() { return this.trades; }

        public int days() { return this.days; }

        public boolean grandMaster() { return this.grandMaster; }

        public long sentAtGameTime() { return this.sentAtGameTime; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SentState)) return false;
            SentState that = (SentState) other;
            return java.util.Objects.equals(this.villagerId, that.villagerId) && this.containerId == that.containerId && this.trades == that.trades && this.days == that.days && this.grandMaster == that.grandMaster && this.sentAtGameTime == that.sentAtGameTime;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.villagerId, this.containerId, this.trades, this.days, this.grandMaster, this.sentAtGameTime); }

        @Override
        public String toString() {
            return "SentState[" + "villagerId=" + this.villagerId + ", " + "containerId=" + this.containerId + ", " + "trades=" + this.trades + ", " + "days=" + this.days + ", " + "grandMaster=" + this.grandMaster + ", " + "sentAtGameTime=" + this.sentAtGameTime + "]";
        }

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
