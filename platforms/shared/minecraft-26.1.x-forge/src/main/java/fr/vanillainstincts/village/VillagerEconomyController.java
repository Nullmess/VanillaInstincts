package fr.vanillainstincts.village;

import fr.vanillainstincts.core.rules.ProfessionRules;
import fr.vanillainstincts.core.rules.VillageSocialRules;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
/** Hausse cohérente des prix après violence, vol ou sabotage répété. */
public final class VillagerEconomyController {
    public enum Incident {
        HIT,
        THEFT,
        CROP_SABOTAGE
    }

    private static final Map<ServerLevel, Map<IncidentKey, IncidentWindow>>
            INCIDENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerEconomyController() {
    }

    public static int record(Villager merchant, ServerPlayer player,
                             Incident incident, long gameTime) {
        if (merchant == null || player == null || incident == null
                || !(merchant.level() instanceof ServerLevel level)) {
            return 0;
        }
        int amount = switch (incident) {
            case HIT -> ProfessionRules.VILLAGER_PRICE_PENALTY_HIT;
            case THEFT -> ProfessionRules.VILLAGER_PRICE_PENALTY_THEFT;
            case CROP_SABOTAGE -> ProfessionRules.VILLAGER_PRICE_PENALTY_CROP;
        };
        applyPenalty(merchant, amount);

        IncidentKey key = new IncidentKey(merchant.getUUID(), player.getUUID(),
                incident);
        Map<IncidentKey, IncidentWindow> levelIncidents = levelIncidents(level);
        IncidentWindow previous = levelIncidents.get(key);
        int count = previous == null
                || gameTime - previous.lastAt
                > VillageSocialRules.FARMER_SABOTAGE_WINDOW_TICKS
                ? 1 : previous.count + 1;
        levelIncidents.put(key, new IncidentWindow(count, gameTime));
        merchant.getPersistentData().putLong("vanillainstincts_last_price_incident_at",
                gameTime);
        merchant.getPersistentData().putInt("vanillainstincts_price_incident_score",
                Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                        fr.vanillainstincts.persistence.NbtCompat.getInt(merchant.getPersistentData(), "vanillainstincts_price_incident_score")
                                + amount));
        return count;
    }

    public static void maintain(Villager merchant) {
        if (merchant == null) return;
        int score = Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                Math.max(0, fr.vanillainstincts.persistence.NbtCompat.getInt(merchant.getPersistentData(), "vanillainstincts_price_incident_score")));
        if (score <= 0) return;
        for (MerchantOffer offer : merchant.getOffers()) {
            if (offer.getSpecialPriceDiff() < score) {
                offer.setSpecialPriceDiff(score);
            }
        }
    }

    public static void applyPenalty(AbstractVillager merchant, int amount) {
        if (merchant == null || amount <= 0) return;
        for (MerchantOffer offer : merchant.getOffers()) {
            int next = cappedPenalty(offer.getSpecialPriceDiff(), amount);
            offer.setSpecialPriceDiff(next);
        }
    }

    public static int cappedPenalty(int current, int added) {
        return Math.max(0, Math.min(ProfessionRules.VILLAGER_PRICE_PENALTY_MAX,
                Math.max(0, current) + Math.max(0, added)));
    }

    public static boolean shouldAlertForCropSabotage(int incidentCount) {
        return incidentCount >= VillageSocialRules.FARMER_SABOTAGE_ALERT_THRESHOLD;
    }

    public static void clearLevel(ServerLevel level) {
        INCIDENTS.remove(level);
    }

    private static Map<IncidentKey, IncidentWindow> levelIncidents(
            ServerLevel level) {
        synchronized (INCIDENTS) {
            return INCIDENTS.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private record IncidentKey(UUID villagerId, UUID playerId,
                               Incident incident) {
    }

    private record IncidentWindow(int count, long lastAt) {
    }
}
