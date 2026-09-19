package fr.vanillainstincts.core.performance;

import java.util.Objects;

/** Immutable load sample state with immediate degradation and delayed recovery. */
public final class AdaptiveLoadState {
    private final long averageTickNanos; private final LoadTier tier; private final int healthySamples;
    public AdaptiveLoadState(long averageTickNanos, LoadTier tier, int healthySamples){ this.averageTickNanos=Math.max(0L,averageTickNanos); this.tier=tier==null?LoadTier.NORMAL:tier; this.healthySamples=Math.max(0,healthySamples);}
    public long averageTickNanos(){return averageTickNanos;} public LoadTier tier(){return tier;} public int healthySamples(){return healthySamples;}
    public static AdaptiveLoadState initial(){return new AdaptiveLoadState(0L,LoadTier.NORMAL,0);}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof AdaptiveLoadState))return false;AdaptiveLoadState x=(AdaptiveLoadState)o;return averageTickNanos==x.averageTickNanos&&healthySamples==x.healthySamples&&tier==x.tier;}
    @Override public int hashCode(){return Objects.hash(averageTickNanos,tier,healthySamples);}
    @Override public String toString(){return "AdaptiveLoadState[averageTickNanos="+averageTickNanos+", tier="+tier+", healthySamples="+healthySamples+"]";}
}
