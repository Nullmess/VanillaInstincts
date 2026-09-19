package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.item.ItemStack;
/** Hausse cohérente des prix après violence, vol ou sabotage répété. */
public final class VillagerEconomyController {
    public enum Incident {
        HIT,
        THEFT,
        CROP_SABOTAGE
    }

    private static final Map<WorldServer, Map<IncidentKey, IncidentWindow>>
            INCIDENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerEconomyController() {
    }

    public static int record(EntityVillager merchant, EntityPlayerMP player,
                             Incident incident, long gameTime) {
        if (merchant == null || player == null || incident == null
                || !(merchant.worldObj instanceof WorldServer)) {
            return 0;
        } WorldServer level = (WorldServer) (merchant.worldObj);
        int amount = fr.vanillainstincts.compat.LegacyJava8.eval(() -> { switch ((incident)) { case HIT:  return ProfessionRules.VILLAGER_PRICE_PENALTY_HIT; case THEFT:  return ProfessionRules.VILLAGER_PRICE_PENALTY_THEFT; case CROP_SABOTAGE:  return ProfessionRules.VILLAGER_PRICE_PENALTY_CROP;  default: throw new AssertionError("Unexpected switch value"); } });
        applyPenalty(merchant, amount);

        IncidentKey key = new IncidentKey(merchant.getUniqueID(), player.getUniqueID(),
                incident);
        Map<IncidentKey, IncidentWindow> levelIncidents = levelIncidents(level);
        IncidentWindow previous = levelIncidents.get(key);
        int count = previous == null
                || gameTime - previous.lastAt
                > VillageSocialRules.FARMER_SABOTAGE_WINDOW_TICKS
                ? 1 : previous.count + 1;
        levelIncidents.put(key, new IncidentWindow(count, gameTime));
        merchant.getEntityData().setLong("vanillainstincts_last_price_incident_at",
                gameTime);
        merchant.getEntityData().setInteger("vanillainstincts_price_incident_score",
                Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                        merchant.getEntityData()
                                .getInteger("vanillainstincts_price_incident_score")
                                + amount));
        return count;
    }

    public static void maintain(EntityVillager merchant) {
        if (merchant == null) return;
        int score = Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                Math.max(0, merchant.getEntityData()
                        .getInteger("vanillainstincts_price_incident_score")));
        // 1.12 MerchantRecipe has no post-1.14 special-price field.
        // Penalties are applied eagerly in applyPenalty(); keep the persisted
        // incident score so newly created recovered offers can include it.
    }

    public static void applyPenalty(EntityVillager merchant, int amount) {
        if (merchant == null || amount <= 0) return;
        for (MerchantRecipe offer : fr.vanillainstincts.compat.Minecraft112Compat.offers(merchant)) {
            ItemStack buy = offer.getItemToBuy();
            if (buy == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(buy) || buy.getItem() != net.minecraft.init.Items.emerald) {
                continue;
            }
            fr.vanillainstincts.compat.Minecraft110ItemStackCompat.setCount(buy, Math.max(1, Math.min(64,
                    buy.stackSize + Math.max(0, amount))));
        }
    }

    public static int cappedPenalty(int current, int added) {
        return Math.max(0, Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                Math.max(0, current) + Math.max(0, added)));
    }

    public static boolean shouldAlertForCropSabotage(int incidentCount) {
        return incidentCount >= VillageSocialRules.FARMER_SABOTAGE_ALERT_THRESHOLD;
    }

    public static void clearLevel(WorldServer level) {
        INCIDENTS.remove(level);
    }

    private static Map<IncidentKey, IncidentWindow> levelIncidents(
            WorldServer level) {
        synchronized (INCIDENTS) {
            return INCIDENTS.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private static class IncidentKey {
        private final UUID villagerId;
        private final UUID playerId;
        private final Incident incident;

        public IncidentKey(UUID villagerId, UUID playerId, Incident incident) {
            this.villagerId = villagerId;
            this.playerId = playerId;
            this.incident = incident;
        }

        public UUID villagerId() { return this.villagerId; }

        public UUID playerId() { return this.playerId; }

        public Incident incident() { return this.incident; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IncidentKey)) return false;
            IncidentKey that = (IncidentKey) other;
            return java.util.Objects.equals(this.villagerId, that.villagerId) && java.util.Objects.equals(this.playerId, that.playerId) && java.util.Objects.equals(this.incident, that.incident);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.villagerId, this.playerId, this.incident); }

        @Override
        public String toString() {
            return "IncidentKey[" + "villagerId=" + this.villagerId + ", " + "playerId=" + this.playerId + ", " + "incident=" + this.incident + "]";
        }

    }

    private static class IncidentWindow {
        private final int count;
        private final long lastAt;

        public IncidentWindow(int count, long lastAt) {
            this.count = count;
            this.lastAt = lastAt;
        }

        public int count() { return this.count; }

        public long lastAt() { return this.lastAt; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IncidentWindow)) return false;
            IncidentWindow that = (IncidentWindow) other;
            return this.count == that.count && this.lastAt == that.lastAt;
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.count, this.lastAt); }

        @Override
        public String toString() {
            return "IncidentWindow[" + "count=" + this.count + ", " + "lastAt=" + this.lastAt + "]";
        }

    }
}
