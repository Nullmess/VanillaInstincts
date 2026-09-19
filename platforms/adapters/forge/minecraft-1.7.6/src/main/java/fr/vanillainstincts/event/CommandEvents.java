package fr.vanillainstincts.event;

import fr.vanillainstincts.command.AiDiagnosticCommands;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

/** Command registration owned by the diagnostics feature. */
public final class CommandEvents {
    private CommandEvents() {
    }

    public static void onServerStarting(FMLServerStartingEvent event) {
        AiDiagnosticCommands.register(event.getCommandDispatcher());
    }
}
