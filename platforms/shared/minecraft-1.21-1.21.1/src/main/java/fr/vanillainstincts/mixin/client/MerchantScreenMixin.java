package fr.vanillainstincts.mixin.client;

import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.GrandMasterMerchantClientState.Snapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends the native villager rank bar and title through Grand Master. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin
        extends AbstractContainerScreen<MerchantMenu> {
    private static final ResourceLocation EXPERIENCE_BAR_BACKGROUND =
            ResourceLocation.withDefaultNamespace(
                    "container/villager/experience_bar_background");
    private static final ResourceLocation EXPERIENCE_BAR_CURRENT =
            ResourceLocation.withDefaultNamespace(
                    "container/villager/experience_bar_current");
    private static final int PROGRESS_BAR_X = 136;
    private static final int PROGRESS_BAR_Y = 16;
    private static final int PROGRESS_BAR_WIDTH = 102;
    private static final int PROGRESS_BAR_HEIGHT = 5;
    private static final int MERCHANT_PANEL_X = 107;
    private static final int TITLE_TOP = 4;
    private static final int TITLE_BOTTOM = 15;
    private static final int VANILLA_LABEL_COLOR = 0x404040;
    private static final int VANILLA_PANEL_COLOR = 0xFFC6C6C6;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory inventory,
                                  Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void renderGrandMasterProgress(GuiGraphics graphics,
                                            float partialTick,
                                            int mouseX, int mouseY,
                                            CallbackInfo callback) {
        if (menu.getTraderLevel() < 5) return;

        Snapshot snapshot = GrandMasterMerchantClientState.forContainer(
                menu.containerId);

        // Grand Master is the final rank: once promoted, the progression bar
        // disappears just like vanilla hides it at its own final rank.
        if (snapshot != null && snapshot.grandMaster()) return;

        // Vanilla stops drawing the profession XP bar at level 5 because
        // Master is normally final. Keep the native bar while progressing
        // from Master to Grand Master.
        int x = leftPos + PROGRESS_BAR_X;
        int y = topPos + PROGRESS_BAR_Y;
        graphics.blitSprite(EXPERIENCE_BAR_BACKGROUND, x, y,
                PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT);

        if (snapshot == null) return;

        int width = Mth.clamp(Mth.floor(snapshot.progress()
                * PROGRESS_BAR_WIDTH), 0, PROGRESS_BAR_WIDTH);
        if (width > 0) {
            graphics.blitSprite(EXPERIENCE_BAR_CURRENT,
                    PROGRESS_BAR_WIDTH, PROGRESS_BAR_HEIGHT,
                    0, 0, x, y, width, PROGRESS_BAR_HEIGHT);
        }
    }

    @Inject(method = "renderLabels", at = @At("TAIL"))
    private void renderGrandMasterTitle(GuiGraphics graphics,
                                         int mouseX, int mouseY,
                                         CallbackInfo callback) {
        Snapshot snapshot = GrandMasterMerchantClientState.forContainer(
                menu.containerId);
        if (snapshot == null || !snapshot.grandMaster()
                || menu.getTraderLevel() < 5) {
            return;
        }

        // Erase only the vanilla title strip, then redraw with the same
        // vanilla font/color/no-shadow rendering. This changes the rank text
        // only; it deliberately avoids drawCenteredString, which adds a
        // shadow and made the previous version look dark/bold.
        graphics.fill(MERCHANT_PANEL_X, TITLE_TOP,
                imageWidth - 4, TITLE_BOTTOM, VANILLA_PANEL_COLOR);
        String profession = professionPart(title.getString());
        Component grandMaster = Component.literal(profession + " - ")
                .append(Component.translatable(
                        "ui.vanillainstincts.grand_master.rank"));
        int center = MERCHANT_PANEL_X
                + (imageWidth - MERCHANT_PANEL_X) / 2;
        int x = center - font.width(grandMaster) / 2;
        graphics.drawString(font, grandMaster, x, titleLabelY,
                VANILLA_LABEL_COLOR, false);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void clearPreviousGrandMasterState(CallbackInfo callback) {
        GrandMasterMerchantClientState.clear(menu.containerId);
    }

    private static String professionPart(String title) {
        if (title == null || title.isBlank()) return "Villageois";
        int separator = Math.max(title.lastIndexOf(" - "),
                title.lastIndexOf(" – "));
        return separator > 0 ? title.substring(0, separator) : title;
    }

}
