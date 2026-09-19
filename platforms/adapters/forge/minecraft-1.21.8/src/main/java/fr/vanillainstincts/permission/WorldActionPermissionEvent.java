package fr.vanillainstincts.permission;

import fr.vanillainstincts.core.permission.WorldActionType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.bus.CancellableEventBus;
import net.minecraftforge.eventbus.api.event.MutableEvent;
import net.minecraftforge.eventbus.api.event.characteristic.Cancellable;
import org.jetbrains.annotations.Nullable;

/** Public cancellable bridge for claim and protection integrations. */
public final class WorldActionPermissionEvent extends MutableEvent implements Cancellable {
    public static final CancellableEventBus<WorldActionPermissionEvent> BUS =
            CancellableEventBus.create(WorldActionPermissionEvent.class);
    private final ServerLevel level;
    @Nullable
    private final Entity actor;
    private final WorldActionType action;
    private final List<BlockPos> positions;
    @Nullable
    private final BlockState replacement;

    public WorldActionPermissionEvent(ServerLevel level,
                                      @Nullable Entity actor,
                                      WorldActionType action,
                                      List<BlockPos> positions,
                                      @Nullable BlockState replacement) {
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.positions = List.copyOf(positions);
        this.replacement = replacement;
    }

    public ServerLevel getLevel() { return level; }
    @Nullable public Entity getActor() { return actor; }
    public WorldActionType getAction() { return action; }
    public List<BlockPos> getPositions() { return positions; }
    @Nullable public BlockState getReplacement() { return replacement; }
}
