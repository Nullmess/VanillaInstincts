package fr.vanillainstincts.village;

import fr.vanillainstincts.network.GrandMasterProgressPayload;
import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.merchant.villager.VillagerProfession;

/** Synchronizes rank progress into the currently open merchant screen. */
public final class VillagerGrandMasterProgressDisplay {
    private static final long RESEND_INTERVAL_TICKS = 20L;
    private static final Map<UUID, SentState> SENT = new HashMap<>();

    private VillagerGrandMasterProgressDisplay() {
    }

    public static void update(EntityVillager villager, WorldServer level,
                              long currentDay) {
        if (villager == null || level == null
                || !(villager.getTradingPlayer() instanceof EntityPlayerMP)
                || villager.getVillagerData().getLevel() < 5) {
            return;
        } EntityPlayerMP player = (EntityPlayerMP) (villager.getTradingPlayer());

        int containerId = player.containerMenu.containerId;
        int trades = VillagerGrandMasterController.masterTradeCount(villager);
        int days = (int) Math.min(Integer.MAX_VALUE,
                VillagerGrandMasterController.masteredDays(villager,
                        currentDay));
        boolean grandMaster = VillagerGrandMasterController
                .isGrandMaster(villager);
        long gameTime = level.getTotalWorldTime();
        SentState previous = SENT.get(player.getUniqueID());
        boolean unchanged = previous != null
                && previous.samePayload(villager.getUniqueID(), containerId,
                trades, days, grandMaster);
        if (unchanged
                && gameTime - previous.sentAtGameTime()
                < RESEND_INTERVAL_TICKS) {
            return;
        }

        SentState next = new SentState(villager.getUniqueID(), containerId,
                trades, days, grandMaster, gameTime);
        SENT.put(player.getUniqueID(), next);
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
            EntityPlayerMP player = server.getPlayerList().getPlayer(
                    entry.getKey());
            SentState display = entry.getValue();
            if (player == null || !player.isEntityAlive()) {
                iterator.remove();
                continue;
            }
            Entity entity = player.getLevel().getEntity(display.villagerId());
            if (!(entity instanceof EntityVillager)
                    || ((EntityVillager) (entity)).getTradingPlayer() != player
                    || ((EntityVillager) (entity)).isChild()
                    || ((EntityVillager) (entity)).getVillagerData().getLevel() < 5
                    || ((EntityVillager) (entity)).getVillagerData().getProfession()
                    == VillagerProfession.NONE
                    || ((EntityVillager) (entity)).getVillagerData().getProfession()
                    == VillagerProfession.NITWIT
                    || player.containerMenu.containerId
                    != display.containerId()) {
                iterator.remove();
            }
        }
    }

    public static void remove(EntityPlayerMP player) {
        if (player != null) SENT.remove(player.getUniqueID());
    }

    public static void clearLevel(WorldServer level) {
        if (level == null || SENT.isEmpty()) return;
        Iterator<Map.Entry<UUID, SentState>> iterator = SENT.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            EntityPlayerMP player = level.getServer().getPlayerList()
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
