package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.SpiderSurfaceMode;
import java.util.List;

/** Immutable bounded surface path produced by Spider Navigation 2.0. */
public record SpiderSurfacePath(List<SpiderSurfaceNode> nodes,
                                boolean reachedGoal,
                                int expandedNodes,
                                double remainingDistanceSqr) {
    public SpiderSurfacePath {
        nodes = List.copyOf(nodes);
        expandedNodes = Math.max(0, expandedNodes);
        remainingDistanceSqr = Math.max(0.0D, remainingDistanceSqr);
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
