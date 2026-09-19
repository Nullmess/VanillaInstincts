package fr.vanillainstincts.permission;

import fr.vanillainstincts.core.permission.WorldActionType;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;
import javax.annotation.Nullable;

/** Public cancellable bridge for claim and protection integrations. */
@Cancelable
public final class WorldActionPermissionEvent extends Event {
    private final WorldServer level;
    @Nullable
    private final Entity actor;
    private final WorldActionType action;
    private final List<BlockPos> positions;
    @Nullable
    private final IBlockState replacement;

    public WorldActionPermissionEvent(WorldServer level,
                                      @Nullable Entity actor,
                                      WorldActionType action,
                                      List<BlockPos> positions,
                                      @Nullable IBlockState replacement) {
        this.level = level;
        this.actor = actor;
        this.action = action;
        this.positions = fr.vanillainstincts.compat.LegacyJava8.copyList(positions);
        this.replacement = replacement;
    }

    public WorldServer getLevel() { return level; }
    @Nullable public Entity getActor() { return actor; }
    public WorldActionType getAction() { return action; }
    public List<BlockPos> getPositions() { return positions; }
    @Nullable public IBlockState getReplacement() { return replacement; }
}
