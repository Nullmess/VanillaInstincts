package fr.vanillainstincts.core.performance;

/** Pure adaptive scheduling policy independent from Minecraft and NeoForge. */
public final class AdaptiveLoadPolicy {
    private AdaptiveLoadPolicy() {
    }

    public static AdaptiveLoadState sample(AdaptiveLoadState previous,
                                           long observedTickNanos,
                                           int acceptedClaims,
                                           int rejectedClaims,
                                           long targetTickNanos,
                                           long overloadTickNanos,
                                           int recoverySamples) {
        AdaptiveLoadState current = previous == null
                ? AdaptiveLoadState.initial() : previous;
        long target = Math.max(1L, targetTickNanos);
        long overload = Math.max(target + 1L, overloadTickNanos);
        long average = smooth(current.averageTickNanos(),
                Math.max(1L, observedTickNanos));
        LoadTier desired = classify(average, acceptedClaims, rejectedClaims,
                target, overload);

        if (desired.ordinal() > current.tier().ordinal()) {
            return new AdaptiveLoadState(average, desired, 0);
        }
        if (desired.ordinal() == current.tier().ordinal()) {
            return new AdaptiveLoadState(average, current.tier(), 0);
        }

        int healthy = current.healthySamples() + 1;
        int required = Math.max(1, recoverySamples);
        if (healthy < required) {
            return new AdaptiveLoadState(average, current.tier(), healthy);
        }
        return new AdaptiveLoadState(average,
                current.tier().oneStepTowardNormal(), 0);
    }

    public static LoadTier classify(long averageTickNanos,
                                    int acceptedClaims,
                                    int rejectedClaims,
                                    long targetTickNanos,
                                    long overloadTickNanos) {
        long target = Math.max(1L, targetTickNanos);
        long overload = Math.max(target + 1L, overloadTickNanos);
        int accepted = Math.max(0, acceptedClaims);
        int rejected = Math.max(0, rejectedClaims);
        int total = accepted + rejected;
        double rejectionRatio = total == 0 ? 0.0D : rejected / (double) total;

        if (averageTickNanos >= overload || rejectionRatio >= 0.50D) {
            return LoadTier.OVERLOADED;
        }
        long stressedThreshold = target + (overload - target) / 2L;
        if (averageTickNanos >= stressedThreshold
                || rejectionRatio >= 0.25D) {
            return LoadTier.STRESSED;
        }
        if (averageTickNanos > target || rejected > 0) {
            return LoadTier.BUSY;
        }
        return LoadTier.NORMAL;
    }

    public static long smooth(long previousAverage, long observed) {
        long safeObserved = Math.max(1L, observed);
        if (previousAverage <= 0L) return safeObserved;
        long previousPart = previousAverage - previousAverage / 8L;
        long observedPart = safeObserved / 8L;
        long remainder = safeObserved % 8L >= 4L ? 1L : 0L;
        return saturatedAdd(previousPart, observedPart + remainder);
    }

    public static int effectiveCostBudget(int configuredBudget,
                                          LoadTier tier,
                                          double minimumFactor) {
        int base = Math.max(1, configuredBudget);
        double factor = effectiveFactor(tier, minimumFactor);
        return Math.max(1, (int) Math.floor(base * factor));
    }

    public static long effectiveTimeBudget(long configuredBudget,
                                           LoadTier tier,
                                           double minimumFactor) {
        long base = Math.max(1L, configuredBudget);
        double factor = effectiveFactor(tier, minimumFactor);
        return Math.max(1L, (long) Math.floor(base * factor));
    }

    public static int cadence(int configuredInterval, LoadTier tier,
                              SchedulerPriority priority) {
        int base = Math.max(1, configuredInterval);
        LoadTier safeTier = tier == null ? LoadTier.NORMAL : tier;
        SchedulerPriority safePriority = priority == null
                ? SchedulerPriority.NORMAL : priority;
        long result = (long) base * safeTier.cadenceMultiplier()
                * safePriority.cadenceMultiplier();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, result));
    }

    public static int candidateLimit(int configuredLimit, int minimum,
                                     LoadTier tier,
                                     SchedulerPriority priority) {
        int base = Math.max(0, configuredLimit);
        int floor = Math.max(0, Math.min(base, minimum));
        LoadTier safeTier = tier == null ? LoadTier.NORMAL : tier;
        SchedulerPriority safePriority = priority == null
                ? SchedulerPriority.NORMAL : priority;
        double factor = safeTier.precisionFactor()
                * safePriority.precisionMultiplier();
        int scaled = (int) Math.floor(base * factor);
        return Math.max(floor, Math.min(base, scaled));
    }

    public static long heavyTaskGap(long configuredGap, LoadTier tier) {
        long base = Math.max(0L, configuredGap);
        int multiplier = tier == null ? 1 : tier.heavyTaskMultiplier();
        if (base > Long.MAX_VALUE / Math.max(1, multiplier)) {
            return Long.MAX_VALUE;
        }
        return base * multiplier;
    }

    public static boolean reservationAllowed(int usedCost, int requestedCost,
                                             int effectiveBudget,
                                             SchedulerPriority priority,
                                             double urgentReserveFraction) {
        int used = Math.max(0, usedCost);
        int requested = Math.max(1, requestedCost);
        int budget = Math.max(1, effectiveBudget);
        SchedulerPriority safePriority = priority == null
                ? SchedulerPriority.NORMAL : priority;
        int limit = budget;
        if (!safePriority.mayUseReserve()) {
            double fraction = clamp(urgentReserveFraction, 0.0D, 0.75D);
            limit = Math.max(1, budget - (int) Math.ceil(budget * fraction));
        }
        return used <= limit - requested;
    }

    public static SchedulerPriority priority(boolean activePlayerCombat,
                                             double nearestPlayerDistanceSqr,
                                             double nearDistance,
                                             double activeDistance) {
        if (activePlayerCombat) return SchedulerPriority.CRITICAL;
        double distance = Math.max(0.0D, nearestPlayerDistanceSqr);
        double near = Math.max(1.0D, nearDistance);
        double active = Math.max(near, activeDistance);
        if (distance <= near * near) return SchedulerPriority.NEAR_PLAYER;
        if (distance <= active * active) return SchedulerPriority.NORMAL;
        return SchedulerPriority.BACKGROUND;
    }

    private static double effectiveFactor(LoadTier tier,
                                          double minimumFactor) {
        LoadTier safeTier = tier == null ? LoadTier.NORMAL : tier;
        double minimum = clamp(minimumFactor, 0.05D, 1.0D);
        return Math.max(minimum, safeTier.budgetFactor());
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
