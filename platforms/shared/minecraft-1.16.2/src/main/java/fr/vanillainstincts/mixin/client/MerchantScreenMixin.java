package fr.vanillainstincts.mixin.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import fr.vanillainstincts.client.GrandMasterMerchantClientState;
import fr.vanillainstincts.client.GrandMasterMerchantClientState.Snapshot;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.MerchantScreen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.MerchantContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extends the native villager rank bar and title through Grand Master. */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin
        extends ContainerScreen<MerchantContainer> {
    private static final ResourceLocation VILLAGER_TEXTURE =
            new ResourceLocation("minecraft", "textures/gui/container/villager2.png");
    private static final int PROGRESS_BAR_X = 136;
    private static final int PROGRESS_BAR_Y = 16;
    private static final int PROGRESS_BAR_WIDTH = 102;
    private static final int PROGRESS_BAR_HEIGHT = 5;
    private static final int MERCHANT_PANEL_X = 107;
    private static final int TITLE_TOP = 4;
    private static final int TITLE_BOTTOM = 15;
    private static final int VANILLA_LABEL_COLOR = 0x404040;
    private static final int VANILLA_PANEL_COLOR = 0xFFC6C6C6;

    protected MerchantScreenMixin(MerchantContainer menu, PlayerInventory inventory,
                                  ITextComponent title) {
        super(menu, inventory, title);
    }

    @Inject(method = "renderBg", at = @At("TAIL"), require = 0)
    private void renderGrandMasterProgress(MatrixStack poseStack,
                                            float partialTick,
                                            int mouseX, int mouseY,
                                            CallbackInfo callback) {
        if (menu.getTraderLevel() < 5) return;
        Snapshot snapshot = GrandMasterMerchantClientState.forContainer(menu.containerId);
        if (snapshot != null && snapshot.grandMaster()) return;

        int x = leftPos + PROGRESS_BAR_X;
        int y = topPos + PROGRESS_BAR_Y;
        AbstractGui.fill(poseStack, x, y, x + PROGRESS_BAR_WIDTH,
                y + PROGRESS_BAR_HEIGHT, 0xFF8B8B8B);
        AbstractGui.fill(poseStack, x + 1, y + 1,
                x + PROGRESS_BAR_WIDTH - 1,
                y + PROGRESS_BAR_HEIGHT - 1, 0xFF3A3A3A);

        if (snapshot == null) return;
        int width = MathHelper.clamp(MathHelper.floor(snapshot.progress()
                * PROGRESS_BAR_WIDTH), 0, PROGRESS_BAR_WIDTH);
        if (width > 0) {
            Minecraft.getInstance().getTextureManager().bind(VILLAGER_TEXTURE);
            AbstractGui.blit(poseStack, x, y, 0, 182,
                    width, PROGRESS_BAR_HEIGHT, 256, 256);
        }
    }

    @Inject(method = "renderLabels", at = @At("TAIL"), require = 0)
    private void renderGrandMasterTitle(MatrixStack poseStack,
                                         int mouseX, int mouseY,
                                         CallbackInfo callback) {
        Snapshot snapshot = GrandMasterMerchantClientState.forContainer(menu.containerId);
        if (snapshot == null || !snapshot.grandMaster()
                || menu.getTraderLevel() < 5) return;

        AbstractGui.fill(poseStack, MERCHANT_PANEL_X, TITLE_TOP,
                imageWidth - 4, TITLE_BOTTOM, VANILLA_PANEL_COLOR);
        String profession = professionPart(title.getString());
        ITextComponent grandMaster = new net.minecraft.util.text.StringTextComponent(profession + " - ")
                .append(new net.minecraft.util.text.TranslationTextComponent("ui.vanillainstincts.grand_master.rank"));
        int center = MERCHANT_PANEL_X + (imageWidth - MERCHANT_PANEL_X) / 2;
        int x = center - font.width(grandMaster) / 2;
        font.draw(poseStack, grandMaster, x, titleLabelY, VANILLA_LABEL_COLOR);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void clearPreviousGrandMasterState(CallbackInfo callback) {
        GrandMasterMerchantClientState.clear(menu.containerId);
    }

    private static String professionPart(String title) {
        if (title == null || title.trim().isEmpty()) return "Villageois";
        int separator = Math.max(title.lastIndexOf(" - "), title.lastIndexOf(" – "));
        return separator > 0 ? title.substring(0, separator) : title;
    }
}
