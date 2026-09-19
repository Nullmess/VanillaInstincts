package fr.vanillainstincts.core.performance;

import java.util.Objects;

/** Read-only scheduler metrics for tests, diagnostics and future integrations. */
public final class SchedulerTelemetry {
    private final LoadTier tier; private final long averageTickNanos, spentAiNanos, effectiveTimeBudgetNanos; private final int effectiveCostBudget, usedCost, acceptedClaims, rejectedClaims, rejectedCost;
    public SchedulerTelemetry(LoadTier tier,long averageTickNanos,long spentAiNanos,int effectiveCostBudget,long effectiveTimeBudgetNanos,int usedCost,int acceptedClaims,int rejectedClaims,int rejectedCost){this.tier=tier==null?LoadTier.NORMAL:tier;this.averageTickNanos=Math.max(0L,averageTickNanos);this.spentAiNanos=Math.max(0L,spentAiNanos);this.effectiveCostBudget=Math.max(1,effectiveCostBudget);this.effectiveTimeBudgetNanos=Math.max(1L,effectiveTimeBudgetNanos);this.usedCost=Math.max(0,usedCost);this.acceptedClaims=Math.max(0,acceptedClaims);this.rejectedClaims=Math.max(0,rejectedClaims);this.rejectedCost=Math.max(0,rejectedCost);}
    public LoadTier tier(){return tier;} public long averageTickNanos(){return averageTickNanos;} public long spentAiNanos(){return spentAiNanos;} public int effectiveCostBudget(){return effectiveCostBudget;} public long effectiveTimeBudgetNanos(){return effectiveTimeBudgetNanos;} public int usedCost(){return usedCost;} public int acceptedClaims(){return acceptedClaims;} public int rejectedClaims(){return rejectedClaims;} public int rejectedCost(){return rejectedCost;}
    public double rejectionRatio(){int total=acceptedClaims+rejectedClaims;return total==0?0.0D:rejectedClaims/(double)total;}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof SchedulerTelemetry))return false;SchedulerTelemetry x=(SchedulerTelemetry)o;return tier==x.tier&&averageTickNanos==x.averageTickNanos&&spentAiNanos==x.spentAiNanos&&effectiveCostBudget==x.effectiveCostBudget&&effectiveTimeBudgetNanos==x.effectiveTimeBudgetNanos&&usedCost==x.usedCost&&acceptedClaims==x.acceptedClaims&&rejectedClaims==x.rejectedClaims&&rejectedCost==x.rejectedCost;}
    @Override public int hashCode(){return Objects.hash(tier,averageTickNanos,spentAiNanos,effectiveCostBudget,effectiveTimeBudgetNanos,usedCost,acceptedClaims,rejectedClaims,rejectedCost);}
    @Override public String toString(){return "SchedulerTelemetry[tier="+tier+", averageTickNanos="+averageTickNanos+", spentAiNanos="+spentAiNanos+", effectiveCostBudget="+effectiveCostBudget+", effectiveTimeBudgetNanos="+effectiveTimeBudgetNanos+", usedCost="+usedCost+", acceptedClaims="+acceptedClaims+", rejectedClaims="+rejectedClaims+", rejectedCost="+rejectedCost+"]";}
}
