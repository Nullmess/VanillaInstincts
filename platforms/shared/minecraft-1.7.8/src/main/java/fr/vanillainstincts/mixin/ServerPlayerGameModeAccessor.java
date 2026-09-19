package fr.vanillainstincts.mixin;

import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Internal game-mode access used only while proxying mob block/item actions. */
@Mixin(PlayerInteractionManager.class)
public interface ServerPlayerGameModeAccessor {
    @Accessor("gameModeForPlayer")
    GameType vanillaInstincts$getGameModeForPlayer();

    @Accessor("gameModeForPlayer")
    void vanillaInstincts$setGameModeForPlayer(GameType gameType);
}
