package fr.vanillainstincts.testcompat.mixin;

import fr.vanillainstincts.VanillaInstincts;
import net.minecraft.server.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 55 still routes forge_userdev_server_gametest through
 * net.minecraftforge.gametest.GameTestMain -> net.minecraft.server.Main.
 * Minecraft 1.21.5 moved the real registry/datapack runner to
 * net.minecraft.gametest.Main. Keep Forge's server entrypoint long enough for
 * ServerModLoader.load() to initialize FML/ModList, then hand control to the
 * new vanilla runner before the normal dedicated server is created.
 *
 * This class exists only in the dedicated Forge 1.21.5 GameTest runtime JAR.
 */
@Mixin(Main.class)
public abstract class Forge1215GameTestMainMixin {
    private static final String ENABLE_PROPERTY = "vanillainstincts.forge1215.gametest.bridge";
    private static final String TESTS_PROPERTY = "vanillainstincts.forge1215.gametest.tests";
    private static final String REPORT_PROPERTY = "vanillainstincts.forge1215.gametest.report";
    private static final String PACKS_PROPERTY = "vanillainstincts.forge1215.gametest.packs";
    private static final String UNIVERSE_PROPERTY = "vanillainstincts.forge1215.gametest.universe";

    @Inject(
            method = "main",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/server/loading/ServerModLoader;load()V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            cancellable = true
    )
    private static void vanillainstincts$run1215RegistryGameTests(String[] originalArgs, CallbackInfo ci) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }

        String packs = System.getProperty(PACKS_PROPERTY, "mods");
        String tests = System.getProperty(TESTS_PROPERTY, "vanillainstincts:*");
        String report = System.getProperty(REPORT_PROPERTY, "gametest-results.xml");
        String universe = System.getProperty(UNIVERSE_PROPERTY, "gametestserver");

        VanillaInstincts.LOGGER.info(
                "Vanilla Instincts Forge 1.21.5 GameTest bridge: FML initialized; starting net.minecraft.gametest.Main ({})",
                tests
        );

        try {
            net.minecraft.gametest.Main.main(new String[] {
                    "--packs", packs,
                    "--tests", tests,
                    "--report", report,
                    "--universe", universe
            });
        } catch (Exception exception) {
            throw new RuntimeException("Forge 1.21.5 GameTest bridge failed", exception);
        }

        ci.cancel();
    }
}
