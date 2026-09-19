package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.config.ConfigSnapshot;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.performance.AdaptiveLoadPolicy;
import fr.vanillainstincts.core.performance.AdaptiveLoadState;
import fr.vanillainstincts.core.performance.LoadTier;
import fr.vanillainstincts.core.performance.SchedulerPriority;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import fr.vanillainstincts.core.rules.PerformanceRules;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;

/** Shared adaptive scheduler and internal performance telemetry. */
public final class VanillaInstinctsScheduler {
    private static final Map<WorldServer, TickBudget> BUDGETS =
            new WeakHashMap<>();

    private VanillaInstinctsScheduler() {
    }

    public static boolean isScheduled(Entity entity, int intervalTicks) {
        if (entity == null) return false;
        int interval = Math.max(1, intervalTicks);
        if (entity.worldObj instanceof WorldServer) { WorldServer level = (WorldServer) (entity.worldObj); 
            TickBudget budget;
            SchedulerPriority priority;
            synchronized (BUDGETS) {
                budget = current(level);
                priority = priorityFor(level, entity, budget);
            }
            interval = AdaptiveLoadPolicy.cadence(interval,
                    budget.loadState.tier(), priority);
        }
        return Math.floorMod(entity.worldObj.getTotalWorldTime() + entity.getEntityId(),
                interval) == 0L;
    }

    public static boolean claim(WorldServer level) {
        return claim(level, 1, SchedulerPriority.NORMAL);
    }

    public static boolean claim(WorldServer level, int cost) {
        return claim(level, cost, SchedulerPriority.NORMAL);
    }

    public static boolean claim(WorldServer level, Entity entity, int cost) {
        if (level == null || entity == null) return false;
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            return claimLocked(budget, cost,
                    priorityFor(level, entity, budget));
        }
    }

    public static boolean claim(WorldServer level, int cost,
                                SchedulerPriority priority) {
        if (level == null) return false;
        synchronized (BUDGETS) {
            return claimLocked(current(level), cost, priority);
        }
    }

    public static long beginWork() {
        return System.nanoTime();
    }

    public static void recordWork(WorldServer level, long startedAtNanos) {
        if (level == null || startedAtNanos <= 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startedAtNanos);
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            budget.spentNanos = saturatedAdd(budget.spentNanos, elapsed);
        }
    }

    public static int usedCost(WorldServer level) {
        synchronized (BUDGETS) {
            return current(level).usedCost;
        }
    }

    public static int rejectedCost(WorldServer level) {
        synchronized (BUDGETS) {
            return current(level).rejectedCost;
        }
    }

    public static int remainingCost(WorldServer level) {
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            return Math.max(0, budget.effectiveCostBudget
                    - budget.usedCost);
        }
    }

    public static LoadTier loadTier(WorldServer level) {
        synchronized (BUDGETS) {
            return current(level).loadState.tier();
        }
    }

    public static SchedulerPriority priority(WorldServer level,
                                             Entity entity) {
        if (level == null || entity == null) {
            return SchedulerPriority.BACKGROUND;
        }
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            return priorityFor(level, entity, budget);
        }
    }

    public static int precisionLimit(WorldServer level, Entity entity,
                                     int configuredLimit, int minimum) {
        if (level == null) return Math.max(0, configuredLimit);
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            SchedulerPriority priority = entity == null
                    ? SchedulerPriority.NORMAL
                    : priorityFor(level, entity, budget);
            return AdaptiveLoadPolicy.candidateLimit(configuredLimit,
                    minimum, budget.loadState.tier(), priority);
        }
    }

    public static int precisionLimit(WorldServer level, int configuredLimit,
                                     int minimum) {
        return precisionLimit(level, null, configuredLimit, minimum);
    }

    public static int heavyTaskMultiplier(WorldServer level) {
        synchronized (BUDGETS) {
            return current(level).loadState.tier().heavyTaskMultiplier();
        }
    }

    public static SchedulerTelemetry telemetry(WorldServer level) {
        synchronized (BUDGETS) {
            TickBudget budget = current(level);
            return new SchedulerTelemetry(budget.loadState.tier(),
                    budget.loadState.averageTickNanos(),
                    budget.spentNanos, budget.effectiveCostBudget,
                    budget.effectiveTimeBudgetNanos, budget.usedCost,
                    budget.acceptedClaims, budget.rejectedClaims,
                    budget.rejectedCost);
        }
    }

    public static int cadenceMultiplier(long tickNanos) {
        long target = PerformanceRules.TARGET_TICK_NANOS;
        if (tickNanos <= target) return 1;
        long ratio = (tickNanos + target - 1L) / target;
        return (int) Math.max(1L, Math.min(16L, ratio));
    }

    public static void clearLevel(WorldServer level) {
        synchronized (BUDGETS) {
            BUDGETS.remove(level);
        }
    }

    private static boolean claimLocked(TickBudget budget, int cost,
                                       SchedulerPriority priority) {
        int normalized = Math.max(1, cost);
        SchedulerPriority safePriority = priority == null
                ? SchedulerPriority.NORMAL : priority;
        ConfigSnapshot config = budget.configSnapshot == null
                ? RuntimeConfig.snapshot() : budget.configSnapshot;
        boolean timeAvailable = budget.spentNanos
                < budget.effectiveTimeBudgetNanos;
        boolean costAvailable = AdaptiveLoadPolicy.reservationAllowed(
                budget.usedCost, normalized, budget.effectiveCostBudget,
                safePriority, config.urgentBudgetReserve());
        if (!timeAvailable || !costAvailable) {
            budget.rejectedClaims++;
            budget.rejectedCost = saturatedAdd(budget.rejectedCost,
                    normalized);
            return false;
        }
        budget.acceptedClaims++;
        budget.usedCost = saturatedAdd(budget.usedCost, normalized);
        return true;
    }

    private static TickBudget current(WorldServer level) {
        TickBudget budget = BUDGETS.computeIfAbsent(level,
                ignored -> new TickBudget());
        long gameTime = level.getTotalWorldTime();
        if (budget.gameTime == gameTime) return budget;

        long now = System.nanoTime();
        ConfigSnapshot config = RuntimeConfig.snapshot();
        budget.configSnapshot = config;
        updateLoad(budget, gameTime, now, config);
        LoadTier tier = config.adaptiveLoadShedding()
                ? budget.loadState.tier() : LoadTier.NORMAL;
        budget.gameTime = gameTime;
        budget.lastTickNano = now;
        budget.spentNanos = 0L;
        budget.usedCost = 0;
        budget.rejectedCost = 0;
        budget.acceptedClaims = 0;
        budget.rejectedClaims = 0;
        budget.priorities.clear();
        budget.effectiveCostBudget = AdaptiveLoadPolicy
                .effectiveCostBudget(
                        config.maxDecisionCostPerLevelTick(), tier,
                        config.minimumAdaptiveBudgetFactor());
        budget.effectiveTimeBudgetNanos = AdaptiveLoadPolicy
                .effectiveTimeBudget(config.maxAiNanosPerLevelTick(), tier,
                        config.minimumAdaptiveBudgetFactor());
        return budget;
    }

    private static void updateLoad(TickBudget budget, long gameTime,
                                   long now, ConfigSnapshot config) {
        if (budget.lastTickNano == 0L) {
            budget.loadState = AdaptiveLoadState.initial();
            return;
        }
        long gameTimeGap = budget.gameTime == Long.MIN_VALUE
                ? 1L : Math.max(1L, gameTime - budget.gameTime);
        long observed = Math.max(1L, now - budget.lastTickNano);
        if (gameTimeGap > 1L
                || observed > config.overloadTickNanos() * 4L) {
            observed = config.targetTickNanos();
        }
        if (!config.adaptiveLoadShedding()) {
            long average = AdaptiveLoadPolicy.smooth(
                    budget.loadState.averageTickNanos(), observed);
            budget.loadState = new AdaptiveLoadState(average,
                    LoadTier.NORMAL, 0);
            return;
        }
        budget.loadState = AdaptiveLoadPolicy.sample(budget.loadState,
                observed, budget.acceptedClaims, budget.rejectedClaims,
                config.targetTickNanos(), config.overloadTickNanos(),
                config.loadRecoverySamples());
    }

    private static SchedulerPriority priorityFor(WorldServer level,
                                                 Entity entity,
                                                 TickBudget budget) {
        SchedulerPriority cached = budget.priorities.get(entity.getEntityId());
        if (cached != null) return cached;
        boolean playerCombat = entity instanceof EntityLiving
                && ((EntityLiving) (entity)).getAttackTarget() instanceof EntityPlayer;
        EntityPlayer nearestPlayer = fr.vanillainstincts.compat.Minecraft112Compat.nearestPlayer(
                level, entity.posX, entity.posY, entity.posZ,
                PerformanceRules.ACTIVE_PLAYER_DISTANCE,
                candidate -> candidate.isEntityAlive() && !candidate.isSpectator());
        double nearestDistanceSqr = nearestPlayer == null
                ? Double.POSITIVE_INFINITY
                : fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(entity, nearestPlayer);
        SchedulerPriority priority = AdaptiveLoadPolicy.priority(
                playerCombat, nearestDistanceSqr,
                PerformanceRules.NEAR_PLAYER_DISTANCE,
                PerformanceRules.ACTIVE_PLAYER_DISTANCE);
        budget.priorities.put(entity.getEntityId(), priority);
        return priority;
    }

    private static int saturatedAdd(int left, int right) {
        if (right > 0 && left > Integer.MAX_VALUE - right) {
            return Integer.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static final class TickBudget {
        private long gameTime = Long.MIN_VALUE;
        private long lastTickNano;
        private AdaptiveLoadState loadState = AdaptiveLoadState.initial();
        private ConfigSnapshot configSnapshot;
        private long spentNanos;
        private int effectiveCostBudget = 1;
        private long effectiveTimeBudgetNanos = 1L;
        private int usedCost;
        private int rejectedCost;
        private int acceptedClaims;
        private int rejectedClaims;
        private final Map<Integer, SchedulerPriority> priorities =
                new HashMap<>();
    }
}
