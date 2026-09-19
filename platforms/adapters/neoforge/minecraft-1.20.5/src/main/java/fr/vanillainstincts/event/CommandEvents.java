package fr.vanillainstincts.event;

import fr.vanillainstincts.command.AiDiagnosticCommands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Command registration owned by the diagnostics feature. */
public final class CommandEvents {
    private CommandEvents() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AiDiagnosticCommands.register(event.getDispatcher());
    }
}
