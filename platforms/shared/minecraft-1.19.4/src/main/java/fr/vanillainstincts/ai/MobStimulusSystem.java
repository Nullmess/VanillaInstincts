package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.StimulusType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Single entry point for world/combat stimuli that feed fair sensory memory.
 * The emitter describes what happened and where; Perception 3.0 decides which
 * observers can hear it and stores a deliberately imprecise point of interest.
 */
public final class MobStimulusSystem {
    private MobStimulusSystem() {
    }

    public static void emit(ServerLevel level, StimulusType type,
                            BlockPos position, double radius,
                            int memoryTicks, LivingEntity source) {
        emit(level, type, position, radius, memoryTicks, source,
                defaultConfidence(type));
    }

    public static void emit(ServerLevel level, StimulusType type,
                            BlockPos position, double radius,
                            int memoryTicks, LivingEntity source,
                            double confidence) {
        if (level == null || position == null || type == null
                || radius <= 0.0D || memoryTicks <= 0) {
            return;
        }
        MobPerceptionMemory.broadcastProfiledStimulus(level, position,
                radius, memoryTicks, source, type, confidence);
    }

    public static double defaultConfidence(StimulusType type) {
        if (type == null) return 0.5D;
        return switch (type) {
            case DAMAGE, EXPLOSION -> 0.95D;
            case PROJECTILE_IMPACT -> 0.85D;
            case BLOCK_BREAK -> 0.80D;
            case ITEM_USE -> 0.65D;
            case ALLY_ALERT -> 0.90D;
            case FOOD -> 0.60D;
            case VISUAL -> 1.0D;
            case SOUND -> 0.70D;
        };
    }
}
