package fr.vanillainstincts.permission;

import fr.vanillainstincts.core.permission.WorldActionType;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.block.BlockState;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;
import javax.annotation.Nullable;

/** Public cancellable bridge for claim and protection integrations. */
@Cancelable
public final class WorldActionPermissionEvent extends Event {
    private final ServerWorld level;
    @Nullable
    private final Entity actor;
    private final WorldActionType action;
    private final List<BlockPos> positions;
    @Nullable
    private final BlockState replacement;

    public WorldActionPermissionEvent(ServerWorld level,
                                      @Nullable Entity actor,
                                      WorldActionType action,
                                      List<BlockPos> positions,
                                      @Nullable BlockState replacement) {
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.positions = fr.vanillainstincts.compat.LegacyJava8.copyList(positions);
        this.replacement = replacement;
    }

    public ServerWorld getLevel() { return level; }
    @Nullable public Entity getActor() { return actor; }
    public WorldActionType getAction() { return action; }
    public List<BlockPos> getPositions() { return positions; }
    @Nullable public BlockState getReplacement() { return replacement; }
}
