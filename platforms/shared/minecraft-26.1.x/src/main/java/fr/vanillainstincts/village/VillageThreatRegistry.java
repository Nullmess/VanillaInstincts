package fr.vanillainstincts.village;

import fr.vanillainstincts.core.model.VillageThreatType;
import fr.vanillainstincts.core.rules.GolemRules;
import fr.vanillainstincts.core.rules.VillageDefenseRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
/**
 * Mémoire locale des agressions crédibles. Contrairement à l'alerte de fuite,
 * ce registre conserve l'identité de l'agresseur et peut donc fournir une cible
 * réelle aux golems sans accuser un joueur après une chute ou du feu.
 */
public final class VillageThreatRegistry {
    private static final int CELL_SIZE = 32;
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);
    private static final Map<ServerLevel, Map<Long, List<Threat>>> LEVELS =
            new WeakHashMap<>();
    private static final Map<ServerLevel, Long> LAST_CLEANUP =
            new WeakHashMap<>();

    private VillageThreatRegistry() {
    }

    public record ThreatSnapshot(long id, UUID aggressorId,
                                 VillageThreatType type,
                                 BlockPos lastKnownPosition,
                                 BlockPos villageAnchor,
                                 int severity, long until,
                                 UUID witnessId,
                                 boolean defenseActivated) {
        public boolean active(long gameTime) {
            return gameTime <= until;
        }
    }

    public static ThreatSnapshot reportVillagerAttack(
            Villager witness, ServerLevel level, Entity attacker,
            float damage, long gameTime) {
        return report(witness, level, attacker, damage, gameTime,
                VillageDefenseRules.VILLAGE_THREAT_MEMORY_TICKS, false);
    }

    public static ThreatSnapshot reportVillagerPursuit(
            Villager witness, ServerLevel level, LivingEntity pursuer,
            long gameTime) {
        return report(witness, level, pursuer, 1.0F, gameTime,
                VillageDefenseRules.VILLAGE_THREAT_MEMORY_TICKS, false);
    }

    public static ThreatSnapshot reportGolemAttack(
            IronGolem witness, ServerLevel level, Entity attacker,
            float damage, long gameTime) {
        return report(witness, level, attacker, damage + 2.0F, gameTime,
                VillageDefenseRules.VILLAGE_THREAT_MEMORY_TICKS
                        + GolemRules.GOLEM_ANGER_EXTENSION_TICKS, true);
    }

    public static ThreatSnapshot reportDefenseSignal(
            IronGolem witness, ServerLevel level, LivingEntity attacker,
            long gameTime) {
        return report(witness, level, attacker, 2.0F, gameTime,
                GolemRules.GOLEM_ALLY_SIGNAL_TICKS, true);
    }

    private static ThreatSnapshot report(Entity witness, ServerLevel level,
                                         Entity attacker, float damage,
                                         long gameTime, int duration,
                                         boolean defenseActivated) {
        if (witness == null || level == null || attacker == null
                || attacker == witness || !attacker.isAlive()) {
            return null;
        }
        VillageThreatType type = classify(attacker);
        if (!type.hasAggressor()) {
            return null;
        }
        BlockPos position = attacker.blockPosition().immutable();
        BlockPos anchor = witness.blockPosition().immutable();
        int severity = severity(type, damage);
        long until = gameTime + Math.max(20, duration);
        UUID aggressorId = attacker.getUUID();
        UUID witnessId = witness.getUUID();

        synchronized (LEVELS) {
            cleanupLocked(level, gameTime);
            Map<Long, List<Threat>> cells = LEVELS.computeIfAbsent(level,
                    ignored -> new HashMap<>());
            long cell = cellKey(anchor);
            List<Threat> threats = cells.computeIfAbsent(cell,
                    ignored -> new ArrayList<>());
            Threat previous = threats.stream()
                    .filter(value -> value.aggressorId.equals(aggressorId))
                    .max(Comparator.comparingLong(value -> value.until))
                    .orElse(null);
            if (previous != null) {
                previous.lastKnownPosition = position;
                previous.villageAnchor = anchor;
                previous.severity = Math.max(previous.severity, severity);
                previous.until = Math.max(previous.until, until);
                previous.witnessId = witnessId;
                previous.defenseActivated |= defenseActivated;
                return previous.snapshot();
            }
            Threat created = new Threat(NEXT_ID.getAndIncrement(),
                    aggressorId, type, position, anchor, severity, until,
                    witnessId, defenseActivated);
            threats.add(created);
            trim(threats);
            return created.snapshot();
        }
    }

    public static boolean activateForDefense(ServerLevel level, long id,
                                             long gameTime) {
        if (level == null || id <= 0L) return false;
        synchronized (LEVELS) {
            cleanupLocked(level, gameTime);
            Map<Long, List<Threat>> cells = LEVELS.get(level);
            if (cells == null) return false;
            for (List<Threat> values : cells.values()) {
                for (Threat threat : values) {
                    if (threat.id == id && gameTime <= threat.until) {
                        threat.defenseActivated = true;
                        return true;
                    }
                }
            }
            return false;
        }
    }

    public static Optional<ThreatSnapshot> nearestThreat(
            ServerLevel level, BlockPos position, long gameTime,
            double radius) {
        if (level == null || position == null) {
            return Optional.empty();
        }
        double radiusSqr = Math.max(1.0D, radius * radius);
        synchronized (LEVELS) {
            cleanupLocked(level, gameTime);
            Map<Long, List<Threat>> cells = LEVELS.get(level);
            if (cells == null) {
                return Optional.empty();
            }
            int cellRadius = Math.max(1,
                    (int) Math.ceil(radius / CELL_SIZE));
            int cx = Math.floorDiv(position.getX(), CELL_SIZE);
            int cz = Math.floorDiv(position.getZ(), CELL_SIZE);
            Threat best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int x = -cellRadius; x <= cellRadius; x++) {
                for (int z = -cellRadius; z <= cellRadius; z++) {
                    List<Threat> values = cells.get(pack(cx + x, cz + z));
                    if (values == null) continue;
                    for (Threat threat : values) {
                        if (gameTime > threat.until
                                || !threat.defenseActivated) continue;
                        // Le registre est indexé par ancre de village : un défenseur
                        // découvre donc les menaces signalées dans sa zone de défense,
                        // même si l'agresseur se trouve déjà quelques blocs plus loin.
                        double anchorDistance = position.distSqr(
                                threat.villageAnchor);
                        if (anchorDistance > radiusSqr) continue;
                        double aggressorDistance = position.distSqr(
                                threat.lastKnownPosition);
                        double score = anchorDistance
                                + aggressorDistance * 0.01D
                                - threat.severity * 18.0D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = threat;
                        }
                    }
                }
            }
            return best == null ? Optional.empty()
                    : Optional.of(best.snapshot());
        }
    }

    public static Optional<ThreatSnapshot> byId(ServerLevel level,
                                                 long id,
                                                 long gameTime) {
        if (level == null || id <= 0L) return Optional.empty();
        synchronized (LEVELS) {
            cleanupLocked(level, gameTime);
            Map<Long, List<Threat>> cells = LEVELS.get(level);
            if (cells == null) return Optional.empty();
            return cells.values().stream().flatMap(List::stream)
                    .filter(value -> value.id == id && gameTime <= value.until)
                    .map(Threat::snapshot).findFirst();
        }
    }

    public static LivingEntity resolveAggressor(ServerLevel level,
                                                 ThreatSnapshot threat) {
        if (level == null || threat == null || threat.aggressorId() == null) {
            return null;
        }
        Entity entity = level.getEntity(threat.aggressorId());
        return entity instanceof LivingEntity living && living.isAlive()
                ? living : null;
    }

    public static boolean sameVillageArea(BlockPos a, BlockPos b) {
        return a != null && b != null
                && a.distSqr(b) <= VillageDefenseRules.VILLAGE_DEFENSE_AREA_RADIUS_SQR;
    }

    /**
     * Oublie immédiatement toutes les menaces liées à une entité morte. Le
     * joueur réapparu conserve son UUID : sans cette suppression, les golems
     * pourraient rattacher l'ancienne alerte à sa nouvelle entité.
     */
    public static int clearAggressor(ServerLevel level, UUID aggressorId) {
        if (level == null || aggressorId == null) {
            return 0;
        }
        synchronized (LEVELS) {
            Map<Long, List<Threat>> cells = LEVELS.get(level);
            if (cells == null) {
                return 0;
            }
            int before = cells.values().stream().mapToInt(List::size).sum();
            cells.values().forEach(values -> values.removeIf(value ->
                    aggressorId.equals(value.aggressorId)));
            cells.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            if (cells.isEmpty()) {
                LEVELS.remove(level);
            }
            int after = cells.values().stream().mapToInt(List::size).sum();
            return Math.max(0, before - after);
        }
    }

    public static void clearExpired(ServerLevel level, long gameTime) {
        synchronized (LEVELS) {
            cleanupLocked(level, gameTime);
        }
    }

    public static void clearLevel(ServerLevel level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
            LAST_CLEANUP.remove(level);
        }
    }

    private static VillageThreatType classify(Entity attacker) {
        if (attacker instanceof Player) return VillageThreatType.PLAYER_ASSAULT;
        if (attacker instanceof Enemy) return VillageThreatType.HOSTILE_MOB;
        return VillageThreatType.HOSTILE_MOB;
    }

    private static int severity(VillageThreatType type, float damage) {
        int base = type == VillageThreatType.PLAYER_ASSAULT ? 3 : 2;
        return Math.max(1, Math.min(5,
                base + (int) Math.floor(Math.max(0.0F, damage) / 4.0F)));
    }

    private static void cleanupLocked(ServerLevel level, long gameTime) {
        Long last = LAST_CLEANUP.get(level);
        if (last != null && gameTime - last
                < VillageDefenseRules.VILLAGE_THREAT_CLEANUP_INTERVAL_TICKS) {
            return;
        }
        LAST_CLEANUP.put(level, gameTime);
        Map<Long, List<Threat>> cells = LEVELS.get(level);
        if (cells == null) return;
        cells.values().forEach(values ->
                values.removeIf(value -> gameTime > value.until));
        cells.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        if (cells.isEmpty()) LEVELS.remove(level);
    }

    private static void trim(List<Threat> threats) {
        while (threats.size() > VillageDefenseRules.VILLAGE_THREATS_PER_CELL) {
            threats.remove(threats.stream()
                    .min(Comparator.comparingInt((Threat value) -> value.severity)
                            .thenComparingLong(value -> value.until))
                    .orElse(threats.get(0)));
        }
    }

    private static long cellKey(BlockPos pos) {
        return pack(Math.floorDiv(pos.getX(), CELL_SIZE),
                Math.floorDiv(pos.getZ(), CELL_SIZE));
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static final class Threat {
        private final long id;
        private final UUID aggressorId;
        private final VillageThreatType type;
        private BlockPos lastKnownPosition;
        private BlockPos villageAnchor;
        private int severity;
        private long until;
        private UUID witnessId;
        private boolean defenseActivated;

        private Threat(long id, UUID aggressorId, VillageThreatType type,
                       BlockPos lastKnownPosition, BlockPos villageAnchor,
                       int severity, long until, UUID witnessId,
                       boolean defenseActivated) {
            this.id = id;
            this.aggressorId = aggressorId;
            this.type = type;
            this.lastKnownPosition = lastKnownPosition;
            this.villageAnchor = villageAnchor;
            this.severity = severity;
            this.until = until;
            this.witnessId = witnessId;
            this.defenseActivated = defenseActivated;
        }

        private ThreatSnapshot snapshot() {
            return new ThreatSnapshot(id, aggressorId, type,
                    lastKnownPosition, villageAnchor, severity, until,
                    witnessId, defenseActivated);
        }
    }
}
