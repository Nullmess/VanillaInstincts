package fr.vanillainstincts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.client.render.LivingRenderContext;
import fr.vanillainstincts.network.PossessionActionPayload;
import fr.vanillainstincts.network.PossessionInputPayload;
import fr.vanillainstincts.client.bridge.EntityRenderStateEntityBridge;
import fr.vanillainstincts.mixin.client.LivingEntityRendererLayersAccessor;
import fr.vanillainstincts.possession.VanillaMobPossessionCatalog;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client input synchronization and survival-style HUD for mob possession. */
@EventBusSubscriber(modid = VanillaInstincts.MOD_ID, value = Dist.CLIENT)
public final class MobPossessionClientEvents {
    private static final ResourceLocation CROSSHAIR = vanilla("hud/crosshair");
    private static final ResourceLocation HOTBAR = vanilla("hud/hotbar");
    private static final ResourceLocation HEART_CONTAINER =
            vanilla("hud/heart/container");
    private static final ResourceLocation HEART_FULL = vanilla("hud/heart/full");
    private static final ResourceLocation HEART_HALF = vanilla("hud/heart/half");
    private static final ResourceLocation HEART_ABSORBING_FULL =
            vanilla("hud/heart/absorbing_full");
    private static final ResourceLocation HEART_ABSORBING_HALF =
            vanilla("hud/heart/absorbing_half");
    private static final ResourceLocation ARMOR_EMPTY = vanilla("hud/armor_empty");
    private static final ResourceLocation ARMOR_HALF = vanilla("hud/armor_half");
    private static final ResourceLocation ARMOR_FULL = vanilla("hud/armor_full");
    private static final ResourceLocation FOOD_EMPTY = vanilla("hud/food_empty");
    private static final ResourceLocation FOOD_FULL = vanilla("hud/food_full");
    private static final ResourceLocation FOOD_HALF = vanilla("hud/food_half");
    private static final ResourceLocation AIR = vanilla("hud/air");
    private static final ResourceLocation AIR_BURSTING = vanilla("hud/air_bursting");

    private static final ResourceLocation HOTBAR_SELECTION =
            vanilla("hud/hotbar_selection");
    private static int lastEntityId = -1;
    private static int selectedSlot;
    private static int creativeFlightEntityId = -1;
    private static boolean creativeFlightPreviousNoGravity;
    private static final Map<Integer, VisualEquipmentSnapshot>
            VISUAL_EQUIPMENT_BACKUPS = new HashMap<>();

    private MobPossessionClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            MobPossessionClientState.clear();
            lastEntityId = -1;
            return;
        }
        if (!MobPossessionClientState.active()) {
            restoreCreativePredictionGravity(minecraft);
            if (lastEntityId != -1) {
                minecraft.player.noPhysics = minecraft.player.isSpectator();
            }
            lastEntityId = -1;
            return;
        }

        minecraft.player.noPhysics = true;
        Mob mob = possessedMob(minecraft);
        if (mob == null) return;
        updateCreativePredictionGravity(minecraft, mob);
        if (minecraft.getCameraEntity() != mob) {
            minecraft.setCameraEntity(mob);
        }
        if (lastEntityId != mob.getId()) {
            minecraft.player.setYRot(mob.getYRot());
            minecraft.player.setXRot(mob.getXRot());
            selectedSlot = 0;
            minecraft.player.getInventory().setSelectedSlot(selectedSlot);
            lastEntityId = mob.getId();
        }

        float yaw = minecraft.player.getYRot();
        float pitch = minecraft.player.getXRot();
        mob.setYRot(yaw);
        mob.setXRot(pitch);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        // Keep the invisible spectator body co-located with the possessed mob.
        // This avoids a second stale player position for container reach,
        // interaction distance and the position used after possession ends.
        minecraft.player.absSnapTo(mob.getX(), mob.getY(), mob.getZ(), yaw, pitch);
        // The local player's own vanilla movement is suppressed while the mob
        // is the body. Keeping this hidden body stationary avoids a second
        // independently predicted position fighting container reach/camera.
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.setOnGround(mob.onGround());

        int flags = 0;
        boolean gameplayInput = minecraft.screen == null;
        if (gameplayInput && minecraft.options.keyUp.isDown()) {
            flags |= PossessionInputPayload.FORWARD;
        }
        if (gameplayInput && minecraft.options.keyDown.isDown()) {
            flags |= PossessionInputPayload.BACK;
        }
        if (gameplayInput && minecraft.options.keyLeft.isDown()) {
            flags |= PossessionInputPayload.LEFT;
        }
        if (gameplayInput && minecraft.options.keyRight.isDown()) {
            flags |= PossessionInputPayload.RIGHT;
        }
        if (gameplayInput && minecraft.options.keyJump.isDown()) {
            flags |= PossessionInputPayload.JUMP;
        }
        if (gameplayInput && minecraft.options.keyShift.isDown()) {
            flags |= PossessionInputPayload.SNEAK;
            flags |= PossessionInputPayload.DESCEND;
        }
        if (gameplayInput && minecraft.options.keySprint.isDown()) {
            flags |= PossessionInputPayload.SPRINT;
        }
        if (gameplayInput && minecraft.options.keyAttack.isDown()) {
            flags |= PossessionInputPayload.ATTACK;
        }
        if (gameplayInput && minecraft.options.keyUse.isDown()) {
            flags |= PossessionInputPayload.USE;
        }

        // Client prediction: the server remains authoritative, but move the
        // rendered/camera mob one local tick immediately from the same keys.
        // Server entity updates then reconcile this small prediction exactly
        // like ordinary player movement, removing the remote-entity "delay".
        predictLocalMovement(mob, flags, yaw, minecraft.player.isCreative());

        PacketDistributor.sendToServer(new PossessionInputPayload(flags,
                Math.round(yaw * 100.0F), Math.round(pitch * 100.0F)));
    }

    private static void updateCreativePredictionGravity(Minecraft minecraft,
                                                        Mob mob) {
        if (minecraft.player != null && minecraft.player.isCreative()) {
            if (creativeFlightEntityId != mob.getId()) {
                restoreCreativePredictionGravity(minecraft);
                creativeFlightEntityId = mob.getId();
                creativeFlightPreviousNoGravity = mob.isNoGravity();
            }
            mob.setNoGravity(true);
            return;
        }
        restoreCreativePredictionGravity(minecraft);
    }

    private static void restoreCreativePredictionGravity(Minecraft minecraft) {
        if (creativeFlightEntityId == -1 || minecraft.level == null) return;
        Entity previous = minecraft.level.getEntity(creativeFlightEntityId);
        if (previous instanceof Mob mob) {
            mob.setNoGravity(creativeFlightPreviousNoGravity);
        }
        creativeFlightEntityId = -1;
        creativeFlightPreviousNoGravity = false;
    }

    /**
     * Raw key mappings are read above for the possessed mob. Zero the actual
     * LocalPlayer Input afterwards so the invisible controller body does not
     * also walk/fly/sneak independently and send conflicting vanilla motion.
     */
    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) return;
        var input = event.getInput();
        ((fr.vanillainstincts.mixin.client.ClientInputMoveVectorAccessor) (Object) input)
                .vanillaInstincts$setMoveVector(new net.minecraft.world.phys.Vec2(0.0F, 0.0F));
    }

    /** Mouse wheel behaves like Survival hotbar scrolling, not spectator speed. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        double delta = event.getScrollDeltaY();
        if (delta == 0.0D) return;
        int direction = delta > 0.0D ? -1 : 1;
        selectHotbar(minecraft, Math.floorMod(selectedSlot + direction, 9));
        event.setCanceled(true);
    }

    private static void selectHotbar(Minecraft minecraft, int slot) {
        selectedSlot = Math.max(0, Math.min(8, slot));
        minecraft.player.getInventory().setSelectedSlot(selectedSlot);
        PacketDistributor.sendToServer(new PossessionActionPayload(
                PossessionActionPayload.selectSlot(selectedSlot)));
    }

    @SubscribeEvent
    public static void onInteraction(
            InputEvent.InteractionKeyMappingTriggered event) {
        if (!MobPossessionClientState.active()) return;
        if (event.isAttack() || event.isUseItem()) {
            // Held state is sent by PossessionInputPayload every client tick.
            // Keep the local LivingEntity swing state for immediate vanilla-like
            // first-person feedback, while cancelling spectator interaction.
            if (event.isAttack()) {
                Minecraft minecraft = Minecraft.getInstance();
                Mob mob = possessedMob(minecraft);
                if (mob != null) mob.swing(InteractionHand.MAIN_HAND);
                if (minecraft.player != null) {
                    minecraft.player.swing(InteractionHand.MAIN_HAND);
                }
                event.setSwingHand(true);
            } else {
                event.setSwingHand(false);
            }
            event.setCanceled(true);
        }
    }

    /**
     * The hidden LocalPlayer is only a controller/inventory shell during
     * possession. It is intentionally co-located with the mob, so rendering
     * that player would superimpose the player's armor/body over the mob in
     * third person and around the camera in first person. Never render the
     * local controller body while a possession is active.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null
                && renderedEntity(event.getRenderState()) == minecraft.player) {
            event.setCanceled(true);
        }
    }

    /**
     * Keep the possession inventory functional without inventing equipment
     * visuals that the mob's vanilla renderer does not have. Unsupported hand
     * and humanoid-armor slots are hidden only for the duration of rendering;
     * the real client/server equipment state is restored immediately after.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre event) {
        Entity rendered = renderedEntity(event.getRenderState());
        if (rendered instanceof LivingEntity living) {
            LivingRenderContext.begin(living, event.getPartialTick());
        }
        if (!(rendered instanceof Mob mob)) return;
        if (!MobPossessionClientState.active()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (mob.getId() == MobPossessionClientState.entityId()
                && minecraft.getCameraEntity() == mob
                && minecraft.options.getCameraType().isFirstPerson()) {
            // First-person possession must not render the controlled mob's own
            // body, armor or held-item layers around the camera. The custom HUD
            // arm is rendered separately; vanilla first-person item rendering is
            // cancelled by a dedicated mixin.
            event.setCanceled(true);
            LivingRenderContext.end(mob);
            return;
        }

        if (mob.getId() != MobPossessionClientState.entityId()
                || !VanillaMobPossessionCatalog.isVanilla(mob)) {
            return;
        }
        boolean hideHands = !rendererSupportsHandItems(event);
        boolean hideArmor = !rendererSupportsHumanoidArmor(event);
        if (!hideHands && !hideArmor) return;

        VisualEquipmentSnapshot snapshot = VisualEquipmentSnapshot.capture(mob);
        VISUAL_EQUIPMENT_BACKUPS.put(mob.getId(), snapshot);
        if (hideHands) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }
        if (hideArmor) {
            mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            mob.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLivingPost(RenderLivingEvent.Post event) {
        Entity rendered = renderedEntity(event.getRenderState());
        if (rendered instanceof Mob mob) {
            VisualEquipmentSnapshot snapshot =
                    VISUAL_EQUIPMENT_BACKUPS.remove(mob.getId());
            if (snapshot != null) snapshot.restore(mob);
        }
        if (rendered instanceof LivingEntity living) {
            LivingRenderContext.end(living);
        }
    }

    private static Entity renderedEntity(EntityRenderState state) {
        if ((Object) state instanceof EntityRenderStateEntityBridge bridge) {
            return bridge.vanillaInstincts$getEntity();
        }
        return null;
    }

    private static boolean rendererSupportsHumanoidArmor(
            RenderLivingEvent event) {
        return rendererLayers(event).stream().anyMatch(layer ->
                "HumanoidArmorLayer".equals(layer.getClass().getSimpleName()));
    }

    private static boolean rendererSupportsHandItems(RenderLivingEvent event) {
        return rendererLayers(event).stream().anyMatch(layer -> {
            String name = layer.getClass().getSimpleName();
            return name.contains("ItemInHand") || name.contains("HeldItem")
                    || name.contains("HoldsItem") || name.contains("CarryingItem")
                    || name.contains("CrossedArmsItem")
                    || name.contains("WitchItem");
        });
    }

    private static java.util.List<?> rendererLayers(RenderLivingEvent event) {
        return ((LivingEntityRendererLayersAccessor) (Object) event.getRenderer())
                .vanillaInstincts$getLayers();
    }

    /**
     * Draw the normal vanilla block-selection wireframe while a mob is the
     * camera. Vanilla suppresses this path for spectator-style cameras, so
     * possession explicitly draws the block shape with the possessed mob
     * as the collision-context entity.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!MobPossessionClientState.active()
                || event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Mob mob = possessedMob(minecraft);
        if (mob == null) return;

        // Preserve an entity target exactly like vanilla: do not draw a block
        // outline through a mob/player that currently owns the crosshair. For
        // block/miss targeting, raycast directly from the possessed mob every
        // rendered frame so spectator camera rules cannot suppress/stale it.
        HitResult current = minecraft.hitResult;
        if (current != null && current.getType() == HitResult.Type.ENTITY) {
            return;
        }
        HitResult target = mob.pick(5.0D, 1.0F, false);
        if (!(target instanceof BlockHitResult blockHit)
                || target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        var pos = blockHit.getBlockPos();
        var state = minecraft.level.getBlockState(pos);
        if (state.isAir()
                || !minecraft.level.getWorldBorder().isWithinBounds(pos)) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        var outline = state.getShape(minecraft.level, pos,
                CollisionContext.of(mob));
        ShapeRenderer.renderShape(event.getPoseStack(), lines, outline,
                pos.getX() - camera.x, pos.getY() - camera.y,
                pos.getZ() - camera.z, 0x66000000);
        buffers.endBatch(RenderType.lines());
    }

    /** Hide spectator/player HUD layers replaced by the possessed mob HUD. */
    @SubscribeEvent
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (!MobPossessionClientState.active()) return;
        ResourceLocation layer = event.getName();
        if (layer.equals(VanillaGuiLayers.CROSSHAIR)
                || layer.equals(VanillaGuiLayers.HOTBAR)
                || layer.equals(VanillaGuiLayers.SPECTATOR_TOOLTIP)
                || layer.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layer.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || layer.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layer.equals(VanillaGuiLayers.AIR_LEVEL)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_BAR)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Mob mob = possessedMob(minecraft);
        if (mob == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        renderPossessionCrosshair(graphics, minecraft);
        renderMobHotbar(graphics, minecraft, mob);
        if (minecraft.player != null && !minecraft.player.isCreative()) {
            renderMobVitals(graphics, minecraft, mob);
        }
        renderFirstPersonMobArm(graphics, minecraft, mob);
    }

    /**
     * Possession owns the crosshair instead of relying on Gui#getCameraPlayer.
     * Vanilla can suppress its layer when the camera entity is a Mob, so draw
     * the exact vanilla sprite at screen center for every active possession.
     */
    private static void renderPossessionCrosshair(GuiGraphics graphics,
                                                   Minecraft minecraft) {
        if (minecraft.options.hideGui || minecraft.screen != null) return;
        int x = graphics.guiWidth() / 2 - 7;
        int y = graphics.guiHeight() / 2 - 7;
        graphics.blitSprite(RenderType::guiTextured, CROSSHAIR, x, y, 15, 15);
    }

    private static void renderMobHotbar(GuiGraphics graphics,
                                        Minecraft minecraft, Mob mob) {
        if (minecraft.player == null) return;
        int center = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 22;
        graphics.blitSprite(RenderType::guiTextured, HOTBAR, center - 91, y, 182, 22);
        int selected = Math.max(0, Math.min(8,
                minecraft.player.getInventory().getSelectedSlot()));
        selectedSlot = selected;
        graphics.blitSprite(RenderType::guiTextured, HOTBAR_SELECTION, center - 92 + selected * 20,
                y - 1, 24, 23);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int x = center - 88 + slot * 20;
            graphics.renderItem(mob, stack, x, y + 3, slot);
            graphics.renderItemDecorations(minecraft.font, stack, x, y + 3);
        }
        ItemStack offHand = minecraft.player.getOffhandItem();
        if (!offHand.isEmpty()) {
            graphics.renderItem(mob, offHand, center + 96, y + 3, 40);
            graphics.renderItemDecorations(minecraft.font, offHand,
                    center + 96, y + 3);
        }
    }

    private static void renderMobVitals(GuiGraphics graphics,
                                        Minecraft minecraft, Mob mob) {
        int center = graphics.guiWidth() / 2;
        int baseline = graphics.guiHeight() - 39;
        renderHearts(graphics, minecraft, mob, center - 91, baseline);
        renderArmor(graphics, mob.getArmorValue(), center - 91,
                baseline - 10);
        renderFood(graphics, minecraft, center + 91, baseline);
        renderAir(graphics, mob, center + 91, baseline - 10);

        float absorption = displayedAbsorption(mob);
        String hearts = format(mob.getHealth() / 2.0F) + "/"
                + format(mob.getMaxHealth() / 2.0F) + " \u2665"
                + (absorption > 0.0F
                ? " +" + format(absorption / 2.0F) + " \u2665" : "");
        graphics.drawString(minecraft.font, hearts,
                center - 91, baseline - 20, 0xFFFFFF, true);
        graphics.drawCenteredString(minecraft.font, mob.getDisplayName(),
                center, baseline - 30, 0xFFFFFF);
    }

    private static void renderHearts(GuiGraphics graphics,
                                     Minecraft minecraft, Mob mob,
                                     int x, int y) {
        float maxHealth = Math.max(1.0F, mob.getMaxHealth());
        float health = Math.max(0.0F, mob.getHealth());
        float absorption = displayedAbsorption(mob);
        int exactHearts = (int) Math.ceil(maxHealth / 2.0F);
        int absorptionHearts = (int) Math.ceil(absorption / 2.0F);
        int totalHearts = exactHearts + absorptionHearts;
        if (exactHearts <= 20 && totalHearts <= 30) {
            int rows = Math.max(1, (totalHearts + 9) / 10);
            for (int i = 0; i < exactHearts; i++) {
                int row = i / 10;
                int column = i % 10;
                int iconY = y - (rows - 1 - row) * 10;
                drawHeart(graphics, x + column * 8, iconY,
                        health - i * 2.0F);
            }
            for (int i = 0; i < absorptionHearts; i++) {
                int index = exactHearts + i;
                int row = index / 10;
                int column = index % 10;
                int iconY = y - (rows - 1 - row) * 10;
                drawAbsorptionHeart(graphics, x + column * 8, iconY,
                        absorption - i * 2.0F);
            }
            return;
        }

        float normalized = MthClamp(health / maxHealth, 0.0F, 1.0F) * 20.0F;
        for (int i = 0; i < 10; i++) {
            drawHeart(graphics, x + i * 8, y, normalized - i * 2.0F);
        }
    }

    private static float displayedAbsorption(Mob mob) {
        float amount = Math.max(0.0F, mob.getAbsorptionAmount());
        var effect = mob.getEffect(MobEffects.ABSORPTION);
        if (effect != null) {
            amount = Math.max(amount, 4.0F * (effect.getAmplifier() + 1));
        }
        return amount;
    }

    private static void drawAbsorptionHeart(GuiGraphics graphics, int x,
                                             int y, float remaining) {
        graphics.blitSprite(RenderType::guiTextured, HEART_CONTAINER, x, y, 9, 9);
        if (remaining >= 2.0F) {
            graphics.blitSprite(RenderType::guiTextured, HEART_ABSORBING_FULL, x, y, 9, 9);
        } else if (remaining > 0.0F) {
            graphics.blitSprite(RenderType::guiTextured, HEART_ABSORBING_HALF, x, y, 9, 9);
        }
    }

    private static void drawHeart(GuiGraphics graphics, int x, int y,
                                  float remaining) {
        graphics.blitSprite(RenderType::guiTextured, HEART_CONTAINER, x, y, 9, 9);
        if (remaining >= 2.0F) {
            graphics.blitSprite(RenderType::guiTextured, HEART_FULL, x, y, 9, 9);
        } else if (remaining > 0.0F) {
            graphics.blitSprite(RenderType::guiTextured, HEART_HALF, x, y, 9, 9);
        }
    }

    private static void renderArmor(GuiGraphics graphics, int armor,
                                    int x, int y) {
        if (armor <= 0) return;
        int points = Math.min(20, armor);
        for (int i = 0; i < 10; i++) {
            int remaining = points - i * 2;
            ResourceLocation sprite = remaining >= 2 ? ARMOR_FULL
                    : remaining == 1 ? ARMOR_HALF : ARMOR_EMPTY;
            graphics.blitSprite(RenderType::guiTextured, sprite, x + i * 8, y, 9, 9);
        }
    }

    private static void renderFood(GuiGraphics graphics, Minecraft minecraft,
                                   int right, int y) {
        if (minecraft.player == null) return;
        int food = Math.max(0, Math.min(20,
                minecraft.player.getFoodData().getFoodLevel()));
        for (int i = 0; i < 10; i++) {
            int x = right - 9 - i * 8;
            graphics.blitSprite(RenderType::guiTextured, FOOD_EMPTY, x, y, 9, 9);
            int remaining = food - i * 2;
            if (remaining >= 2) {
                graphics.blitSprite(RenderType::guiTextured, FOOD_FULL, x, y, 9, 9);
            } else if (remaining == 1) {
                graphics.blitSprite(RenderType::guiTextured, FOOD_HALF, x, y, 9, 9);
            }
        }
    }

    private static void renderAir(GuiGraphics graphics, Mob mob,
                                  int right, int y) {
        int maxAir = Math.max(1, mob.getMaxAirSupply());
        int air = mob.getAirSupply();
        if (air >= maxAir && !mob.isUnderWater()) return;
        int bubbles = Math.min(10,
                (int) Math.ceil(Math.max(0, air) * 10.0D / maxAir));
        for (int i = 0; i < 10; i++) {
            int x = right - 9 - i * 8;
            ResourceLocation sprite = i < bubbles ? AIR : AIR_BURSTING;
            graphics.blitSprite(RenderType::guiTextured, sprite, x, y, 9, 9);
        }
    }

    /**
     * Render the possessed mob's own textured right arm when its actual model
     * is humanoid. This deliberately does not synthesize a Steve arm for
     * spiders, fish, quadrupeds or other shapes that do not have one.
     */
    private static void renderFirstPersonMobArm(GuiGraphics graphics,
                                                 Minecraft minecraft,
                                                 Mob mob) {
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(mob);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?>)) {
            return;
        }

        // Minecraft 1.21.2 moved living renderers to reusable render-state
        // objects. Use the renderer's own extraction path so the arm texture is
        // resolved from the exact same state as the ordinary mob renderer.
        @SuppressWarnings({"rawtypes", "unchecked"})
        LivingEntityRenderer livingRenderer = (LivingEntityRenderer) renderer;
        if (!(livingRenderer.getModel() instanceof HumanoidModel<?> model)) {
            return;
        }
        var renderState = livingRenderer.createRenderState();
        livingRenderer.extractRenderState(mob, renderState, 1.0F);
        if (!(renderState instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState livingState)) {
            return;
        }
        ResourceLocation texture = livingRenderer.getTextureLocation(livingState);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        // First-person placement: the model part is rendered with the mob's
        // own texture, so zombies/skeletons/piglins/etc. remain visually theirs.
        pose.translate(graphics.guiWidth() - 28.0D,
                graphics.guiHeight() + 12.0D, 180.0D);
        applyFirstPersonSwing(pose, mob);
        pose.mulPose(Axis.XP.rotationDegrees(-18.0F));
        pose.mulPose(Axis.YP.rotationDegrees(28.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(8.0F));
        pose.scale(3.4F, -3.4F, 3.4F);
        graphics.drawSpecial(buffers -> {
            VertexConsumer consumer = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(texture));
            model.rightArm.render(pose, consumer, 0x00F000F0,
                    OverlayTexture.NO_OVERLAY);
        });
        pose.popPose();
    }

    /**
     * The possession HUD intentionally renders no held item in first person.
     * The hotbar still shows the selected stack, but camera-space item bob/swing
     * would clip through arbitrary mob heads and is not a vanilla mob feature.
     */

    private static void applyFirstPersonSwing(PoseStack pose, Mob mob) {
        float swing = Mth.clamp(mob.getAttackAnim(1.0F), 0.0F, 1.0F);
        if (swing <= 0.0F) return;
        // Mirrors the shape of vanilla's first-person attack swing: a smooth
        // down/forward arc followed by recovery, while retaining the mob skin.
        float root = Mth.sqrt(swing);
        float arc = Mth.sin(root * (float) Math.PI);
        float follow = Mth.sin(swing * (float) Math.PI);
        pose.translate(-arc * 7.0F, follow * 8.0F, 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(arc * 24.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-follow * 36.0F));
    }

    private static void predictLocalMovement(Mob mob, int flags, float yaw,
                                             boolean creativeControl) {
        VanillaMobPossessionCatalog.Movement movement =
                VanillaMobPossessionCatalog.movement(mob);

        float forward = inputAxis(flags, PossessionInputPayload.FORWARD,
                PossessionInputPayload.BACK);
        float strafe = inputAxis(flags, PossessionInputPayload.LEFT,
                PossessionInputPayload.RIGHT);
        boolean jump = (flags & PossessionInputPayload.JUMP) != 0;
        boolean sneak = (flags & PossessionInputPayload.SNEAK) != 0;
        boolean descend = (flags & PossessionInputPayload.DESCEND) != 0;
        boolean sprint = (flags & PossessionInputPayload.SPRINT) != 0 && !sneak;

        // Mirror the authoritative pose flags immediately so renderers whose
        // animations depend on sprint/sneak state do not wait for a round trip.
        mob.setShiftKeyDown(sneak);
        mob.setSprinting(sprint);

        if (creativeControl) {
            Vec3 facing = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 right = new Vec3(-facing.z, 0.0D, facing.x);
            Vec3 desired = facing.scale(forward)
                    .add(right.scale(-strafe))
                    .add(0.0D, jump ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
            if (desired.lengthSqr() < 1.0E-5D) {
                mob.setDeltaMovement(Vec3.ZERO);
            } else {
                Vec3 predicted = desired.normalize()
                        .scale(sprint ? 0.45D : 0.25D);
                applyPredictedMove(mob, predicted, true);
                mob.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }

        if (movement == VanillaMobPossessionCatalog.Movement.STATIONARY
                || movement == VanillaMobPossessionCatalog.Movement.DRAGON) {
            return;
        }

        double baseSpeed = mob.getAttribute(Attributes.MOVEMENT_SPEED) == null
                ? 0.10D : Mth.clamp(mob.getAttributeValue(Attributes.MOVEMENT_SPEED),
                0.02D, 1.50D);
        double multiplier = sprint ? 1.30D : sneak ? 0.30D : 1.0D;
        double speed = baseSpeed * multiplier;

        boolean free3d = movement == VanillaMobPossessionCatalog.Movement.FLYING
                || (movement == VanillaMobPossessionCatalog.Movement.AQUATIC
                || movement == VanillaMobPossessionCatalog.Movement.AMPHIBIOUS)
                && mob.isInWater();
        Vec3 predicted;
        if (free3d) {
            Vec3 look = mob.getLookAngle();
            Vec3 right = new Vec3(-look.z, 0.0D, look.x);
            if (right.lengthSqr() > 1.0E-6D) right = right.normalize();
            predicted = look.scale(forward)
                    .add(right.scale(-strafe))
                    .add(0.0D, jump ? 1.0D : descend ? -1.0D : 0.0D, 0.0D);
            if (predicted.lengthSqr() > 1.0E-5D) {
                predicted = predicted.normalize().scale(speed);
            } else {
                return;
            }
        } else if (movement == VanillaMobPossessionCatalog.Movement.AQUATIC) {
            return;
        } else {
            Vec3 facing = Vec3.directionFromRotation(0.0F, yaw);
            Vec3 right = new Vec3(-facing.z, 0.0D, facing.x);
            Vec3 desired = facing.scale(forward).add(right.scale(-strafe));
            if (desired.lengthSqr() < 1.0E-5D && !jump) return;
            if (desired.lengthSqr() > 1.0E-5D) desired = desired.normalize();
            double dx = desired.x * speed;
            double dz = desired.z * speed;
            if (sneak && mob.onGround()) {
                Vec3 safe = predictSneakEdge(mob, dx, dz);
                dx = safe.x;
                dz = safe.z;
            }
            double dy = 0.0D;
            if (jump && mob.onGround()) {
                // Visual one-tick lead only; authoritative jump height remains
                // the possessed mob's jumpFromGround() on the server.
                dy = Math.max(0.0D, mob.getDeltaMovement().y) + 0.20D;
            }
            predicted = new Vec3(dx, dy, dz);
        }

        if (predicted.lengthSqr() > 1.0E-8D) {
            applyPredictedMove(mob, predicted, free3d);
        }
    }

    private static void applyPredictedMove(Mob mob, Vec3 movement,
                                           boolean includeVerticalAnimation) {
        // ClientTickEvent.Post runs after the mob's ordinary client tick. A
        // direct prediction move here therefore happens after vanilla already
        // calculated its limb/walk state. Recalculate that state immediately
        // after the predicted move so every vanilla model sees coherent
        // walking/running animation instead of a frozen or jerky limb phase.
        mob.move(net.minecraft.world.entity.MoverType.PLAYER, movement);
        mob.calculateEntityAnimation(includeVerticalAnimation);
    }

    private static Vec3 predictSneakEdge(Mob mob, double dx, double dz) {
        AABB box = mob.getBoundingBox();
        double safeX = dx;
        double safeZ = dz;
        if (mob.level().noCollision(mob, box.move(dx, -0.55D, 0.0D))) safeX = 0.0D;
        if (mob.level().noCollision(mob, box.move(0.0D, -0.55D, dz))) safeZ = 0.0D;
        if (mob.level().noCollision(mob, box.move(safeX, -0.55D, safeZ))) {
            safeX = 0.0D;
            safeZ = 0.0D;
        }
        return new Vec3(safeX, 0.0D, safeZ);
    }

    private static float inputAxis(int flags, int positive, int negative) {
        return ((flags & positive) != 0 ? 1.0F : 0.0F)
                - ((flags & negative) != 0 ? 1.0F : 0.0F);
    }

    private static Mob possessedMob(Minecraft minecraft) {
        if (minecraft.level == null || !MobPossessionClientState.active()) {
            return null;
        }
        Entity entity = minecraft.level.getEntity(
                MobPossessionClientState.entityId());
        return entity instanceof Mob mob ? mob : null;
    }

    private static String format(float value) {
        float rounded = Math.round(value * 10.0F) / 10.0F;
        if (rounded == Math.rint(rounded)) {
            return Integer.toString((int) rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", rounded);
    }

    private static float MthClamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record VisualEquipmentSnapshot(
            ItemStack mainHand, ItemStack offHand, ItemStack head,
            ItemStack chest, ItemStack legs, ItemStack feet) {
        private static VisualEquipmentSnapshot capture(Mob mob) {
            return new VisualEquipmentSnapshot(
                    mob.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                    mob.getItemBySlot(EquipmentSlot.OFFHAND).copy(),
                    mob.getItemBySlot(EquipmentSlot.HEAD).copy(),
                    mob.getItemBySlot(EquipmentSlot.CHEST).copy(),
                    mob.getItemBySlot(EquipmentSlot.LEGS).copy(),
                    mob.getItemBySlot(EquipmentSlot.FEET).copy());
        }

        private void restore(Mob mob) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
            mob.setItemSlot(EquipmentSlot.OFFHAND, offHand);
            mob.setItemSlot(EquipmentSlot.HEAD, head);
            mob.setItemSlot(EquipmentSlot.CHEST, chest);
            mob.setItemSlot(EquipmentSlot.LEGS, legs);
            mob.setItemSlot(EquipmentSlot.FEET, feet);
        }
    }

    private static ResourceLocation vanilla(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
