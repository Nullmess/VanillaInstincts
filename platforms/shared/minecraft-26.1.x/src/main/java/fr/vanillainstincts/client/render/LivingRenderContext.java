package fr.vanillainstincts.client.render;

import net.minecraft.world.entity.LivingEntity;

/**
 * Client render context bridging loader render events with vanilla render-state
 * mixins introduced in Minecraft 1.21.2.
 *
 * <p>Vanilla render layers no longer receive the entity directly. The loader
 * event still knows which entity is being rendered, so keep that association
 * only for the duration of the current render call. This avoids copying entity
 * state into vanilla render-state classes or depending on private renderer
 * internals.</p>
 */
public final class LivingRenderContext {
    private static final ThreadLocal<Entry> CURRENT = new ThreadLocal<>();

    private LivingRenderContext() {
    }

    public static void begin(LivingEntity entity, float partialTick) {
        CURRENT.set(new Entry(entity, partialTick));
    }

    public static void end(LivingEntity entity) {
        Entry current = CURRENT.get();
        if (current != null && current.entity() == entity) {
            CURRENT.remove();
        }
    }

    public static LivingEntity entity() {
        Entry current = CURRENT.get();
        return current == null ? null : current.entity();
    }

    public static float partialTick() {
        Entry current = CURRENT.get();
        return current == null ? 0.0F : current.partialTick();
    }

    private record Entry(LivingEntity entity, float partialTick) {
    }
}
