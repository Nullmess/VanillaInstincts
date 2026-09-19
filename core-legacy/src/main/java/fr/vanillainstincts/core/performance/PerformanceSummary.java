package fr.vanillainstincts.core.performance;

import java.util.Objects;

/** Read-only rolling timing summary exposed by final diagnostics. */
public final class PerformanceSummary {
    private final int samples, capacity; private final long averageAiNanos,p95AiNanos,p99AiNanos,maxAiNanos,averageTickNanos,p95TickNanos,p99TickNanos,maxTickNanos;
    public PerformanceSummary(int samples,int capacity,long averageAiNanos,long p95AiNanos,long p99AiNanos,long maxAiNanos,long averageTickNanos,long p95TickNanos,long p99TickNanos,long maxTickNanos){this.samples=Math.max(0,samples);this.capacity=Math.max(1,capacity);this.averageAiNanos=Math.max(0L,averageAiNanos);this.p95AiNanos=Math.max(0L,p95AiNanos);this.p99AiNanos=Math.max(0L,p99AiNanos);this.maxAiNanos=Math.max(0L,maxAiNanos);this.averageTickNanos=Math.max(0L,averageTickNanos);this.p95TickNanos=Math.max(0L,p95TickNanos);this.p99TickNanos=Math.max(0L,p99TickNanos);this.maxTickNanos=Math.max(0L,maxTickNanos);}
    public int samples(){return samples;} public int capacity(){return capacity;} public long averageAiNanos(){return averageAiNanos;} public long p95AiNanos(){return p95AiNanos;} public long p99AiNanos(){return p99AiNanos;} public long maxAiNanos(){return maxAiNanos;} public long averageTickNanos(){return averageTickNanos;} public long p95TickNanos(){return p95TickNanos;} public long p99TickNanos(){return p99TickNanos;} public long maxTickNanos(){return maxTickNanos;}
    @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof PerformanceSummary))return false;PerformanceSummary x=(PerformanceSummary)o;return samples==x.samples&&capacity==x.capacity&&averageAiNanos==x.averageAiNanos&&p95AiNanos==x.p95AiNanos&&p99AiNanos==x.p99AiNanos&&maxAiNanos==x.maxAiNanos&&averageTickNanos==x.averageTickNanos&&p95TickNanos==x.p95TickNanos&&p99TickNanos==x.p99TickNanos&&maxTickNanos==x.maxTickNanos;}
    @Override public int hashCode(){return Objects.hash(samples,capacity,averageAiNanos,p95AiNanos,p99AiNanos,maxAiNanos,averageTickNanos,p95TickNanos,p99TickNanos,maxTickNanos);}
    @Override public String toString(){return "PerformanceSummary[samples="+samples+", capacity="+capacity+", averageAiNanos="+averageAiNanos+", p95AiNanos="+p95AiNanos+", p99AiNanos="+p99AiNanos+", maxAiNanos="+maxAiNanos+", averageTickNanos="+averageTickNanos+", p95TickNanos="+p95TickNanos+", p99TickNanos="+p99TickNanos+", maxTickNanos="+maxTickNanos+"]";}
}
