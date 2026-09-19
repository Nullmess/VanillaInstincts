package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import java.util.List;

/** Immutable bounded surface path produced by Spider Navigation 2.0. */
public class SpiderSurfacePath {
    private final List<SpiderSurfaceNode> nodes;
    private final boolean reachedGoal;
    private final int expandedNodes;
    private final double remainingDistanceSqr;

    public List<SpiderSurfaceNode> nodes() { return this.nodes; }

    public boolean reachedGoal() { return this.reachedGoal; }

    public int expandedNodes() { return this.expandedNodes; }

    public double remainingDistanceSqr() { return this.remainingDistanceSqr; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SpiderSurfacePath)) return false;
        SpiderSurfacePath that = (SpiderSurfacePath) other;
        return java.util.Objects.equals(this.nodes, that.nodes) && this.reachedGoal == that.reachedGoal && this.expandedNodes == that.expandedNodes && Double.compare(this.remainingDistanceSqr, that.remainingDistanceSqr) == 0;
    }

    @Override
    public int hashCode() { return java.util.Objects.hash(this.nodes, this.reachedGoal, this.expandedNodes, this.remainingDistanceSqr); }

    @Override
    public String toString() {
        return "SpiderSurfacePath[" + "nodes=" + this.nodes + ", " + "reachedGoal=" + this.reachedGoal + ", " + "expandedNodes=" + this.expandedNodes + ", " + "remainingDistanceSqr=" + this.remainingDistanceSqr + "]";
    }

    public SpiderSurfacePath(List<SpiderSurfaceNode> nodes, boolean reachedGoal, int expandedNodes, double remainingDistanceSqr) {
        nodes = fr.vanillainstincts.compat.LegacyJava8.copyList(nodes);
        expandedNodes = Math.max(0, expandedNodes);
        remainingDistanceSqr = Math.max(0.0D, remainingDistanceSqr);
    
        this.nodes = nodes;
        this.reachedGoal = reachedGoal;
        this.expandedNodes = expandedNodes;
        this.remainingDistanceSqr = remainingDistanceSqr;
    }

    public boolean isUseful() {
        return nodes.size() >= 2 && usesSpecialSurface();
    }

    public boolean usesSpecialSurface() {
        return nodes.stream().anyMatch(node ->
                node.mode() == SpiderSurfaceMode.WALL
                        || node.mode() == SpiderSurfaceMode.CEILING);
    }

    public boolean usesCeiling() {
        return nodes.stream().anyMatch(node ->
                node.mode() == SpiderSurfaceMode.CEILING);
    }

    public boolean usesWall() {
        return nodes.stream().anyMatch(node ->
                node.mode() == SpiderSurfaceMode.WALL);
    }
}
