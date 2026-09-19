package fr.vanillainstincts.client;

import fr.vanillainstincts.network.VanillaInstinctsNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Vector3f;
import fr.vanillainstincts.VanillaInstincts;
import fr.vanillainstincts.network.PossessionActionPayload;
import fr.vanillainstincts.network.PossessionInputPayload;
import fr.vanillainstincts.mixin.client.LevelRendererPossessionOutlineAccessor;
import fr.vanillainstincts.mixin.client.LivingEntityRendererLayersAccessor;
import fr.vanillainstincts.possession.VanillaMobPossessionCatalog;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;

/** Client input synchronization and survival-style HUD for mob possession. */
@EventBusSubscriber(modid = VanillaInstincts.MOD_ID, value = Dist.CLIENT)
public final class MobPossessionClientEvents {
    // Minecraft 1.18.2 uses the legacy GUI sprite sheets.
    private static final ResourceLocation GUI_ICONS =
            vanilla("textures/gui/icons.png");
    private static final ResourceLocation GUI_WIDGETS =
            vanilla("textures/gui/widgets.png");

    private static int lastEntityId = -1;
    private static int selectedSlot;
    private static int creativeFlightEntityId = -1;
    private static boolean creativeFlightPreviousNoGravity;
    private static final Map<Integer, VisualEquipmentSnapshot>
            VISUAL_EQUIPMENT_BACKUPS = new HashMap<>();

    private MobPossessionClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
            minecraft.player.getInventory().selected = selectedSlot;
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
        minecraft.player.absMoveTo(mob.getX(), mob.getY(), mob.getZ(), yaw, pitch);
        // The local player's own vanilla movement is suppressed while the mob
        // is the body. Keeping this hidden body stationary avoids a second
        // independently predicted position fighting container reach/camera.
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.setOnGround(mob.isOnGround());

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

        VanillaInstinctsNetwork.sendToServer(new PossessionInputPayload(flags,
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
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    /** Mouse wheel behaves like Survival hotbar scrolling, not spectator speed. */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollEvent event) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) return;
        double delta = forgeScrollDelta(event);
        if (delta == 0.0D) return;
        int direction = delta > 0.0D ? -1 : 1;
        selectHotbar(minecraft, Math.floorMod(selectedSlot + direction, 9));
        event.setCanceled(true);
    }

    /**
     * Forge 51.x changed the mouse-scroll event shape while the 1.21 line was
     * being developed. Keep this adapter binary-tolerant across those Forge
     * builds without coupling the source to a getter that is absent in 51.0.33.
     */
    private static double forgeScrollDelta(InputEvent.MouseScrollEvent event) {
        String[] methodNames = {
                "getScrollDeltaY", "getScrollDelta", "scrollDeltaY", "scrollDelta",
                "getScrollDeltaX", "scrollDeltaX"
        };
        for (String methodName : methodNames) {
            try {
                Object value = event.getClass().getMethod(methodName).invoke(event);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Forge API shape.
            }
        }

        String[] fieldNames = {
                "scrollDeltaY", "scrollDelta", "scrollY", "deltaY",
                "scrollDeltaX", "scrollX", "deltaX"
        };
        for (String fieldName : fieldNames) {
            try {
                var field = event.getClass().getDeclaredField(fieldName);
                if (!field.trySetAccessible()) continue;
                Object value = field.get(event);
                if (value instanceof Number number) {
                    return number.doubleValue();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next Forge API shape.
            }
        }
        return 0.0D;
    }

    private static void selectHotbar(Minecraft minecraft, int slot) {
        selectedSlot = Math.max(0, Math.min(8, slot));
        minecraft.player.getInventory().selected = selectedSlot;
        VanillaInstinctsNetwork.sendToServer(new PossessionActionPayload(
                PossessionActionPayload.selectSlot(selectedSlot)));
    }

    @SubscribeEvent
    public static void onInteraction(
            InputEvent.ClickInputEvent event) {
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
        if (minecraft.player != null && event.getEntity() == minecraft.player) {
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
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
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
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        VisualEquipmentSnapshot snapshot =
                VISUAL_EQUIPMENT_BACKUPS.remove(mob.getId());
        if (snapshot != null) snapshot.restore(mob);
    }

    private static boolean rendererSupportsHumanoidArmor(
            RenderLivingEvent<?, ?> event) {
        return rendererLayers(event).stream().anyMatch(layer ->
                "HumanoidArmorLayer".equals(layer.getClass().getSimpleName()));
    }

    private static boolean rendererSupportsHandItems(RenderLivingEvent<?, ?> event) {
        return rendererLayers(event).stream().anyMatch(layer -> {
            String name = layer.getClass().getSimpleName();
            return name.contains("ItemInHand") || name.contains("HeldItem")
                    || name.contains("HoldsItem") || name.contains("CarryingItem")
                    || name.contains("CrossedArmsItem")
                    || name.contains("WitchItem");
        });
    }

    private static java.util.List<?> rendererLayers(RenderLivingEvent<?, ?> event) {
        return ((LivingEntityRendererLayersAccessor) (Object) event.getRenderer())
                .vanillaInstincts$getLayers();
    }

    /**
     * Draw the normal vanilla block-selection wireframe while a mob is the
     * camera. Vanilla suppresses this path for spectator-style cameras, so
     * possession explicitly invokes LevelRenderer's own hit-outline renderer
     * with the possessed mob as the collision-context entity.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!MobPossessionClientState.active()
                || event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
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
        LevelRendererPossessionOutlineAccessor renderer =
                (LevelRendererPossessionOutlineAccessor) (Object)
                        event.getLevelRenderer();
        var buffers = renderer.vanillaInstincts$getRenderBuffers()
                .bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack outlinePose = new PoseStack();
        outlinePose.last().pose().load(event.getPoseStack().last().pose());
        renderer.vanillaInstincts$renderHitOutline(outlinePose, lines,
                mob, camera.x, camera.y, camera.z, pos, state);
        buffers.endBatch(RenderType.lines());
    }

    /** Forge 51 has no generic HUD layer events; the shared Gui mixin calls this. */
    public static boolean shouldSuppressVanillaHudFromMixin() {
        return MobPossessionClientState.active();
    }

    /** Render the possession HUD after vanilla Gui rendering on Forge. */
    public static void renderPossessionHudFromMixin(PoseStack poseStack) {
        if (!MobPossessionClientState.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        Mob mob = possessedMob(minecraft);
        if (mob == null) return;

        renderPossessionCrosshair(poseStack, minecraft);
        renderMobHotbar(poseStack, minecraft, mob);
        if (minecraft.player != null && !minecraft.player.isCreative()) {
            renderMobVitals(poseStack, minecraft, mob);
        }
        renderFirstPersonMobArm(poseStack, minecraft, mob);
    }

    private static int guiWidth(Minecraft minecraft) {
        return minecraft.getWindow().getGuiScaledWidth();
    }

    private static int guiHeight(Minecraft minecraft) {
        return minecraft.getWindow().getGuiScaledHeight();
    }

    private static void bind(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
    }

    private static void blit(PoseStack poseStack, ResourceLocation texture,
                             int x, int y, int u, int v, int width, int height) {
        bind(texture);
        GuiComponent.blit(poseStack, x, y, u, v, width, height, 256, 256);
    }

    private static void renderPossessionCrosshair(PoseStack poseStack,
                                                   Minecraft minecraft) {
        if (minecraft.options.hideGui || minecraft.screen != null) return;
        int x = guiWidth(minecraft) / 2 - 7;
        int y = guiHeight(minecraft) / 2 - 7;
        blit(poseStack, GUI_ICONS, x, y, 0, 0, 15, 15);
    }

    private static void renderMobHotbar(PoseStack poseStack,
                                        Minecraft minecraft, Mob mob) {
        if (minecraft.player == null) return;
        int center = guiWidth(minecraft) / 2;
        int y = guiHeight(minecraft) - 22;
        blit(poseStack, GUI_WIDGETS, center - 91, y, 0, 0, 182, 22);
        int selected = Math.max(0, Math.min(8,
                minecraft.player.getInventory().selected));
        selectedSlot = selected;
        blit(poseStack, GUI_WIDGETS, center - 92 + selected * 20,
                y - 1, 0, 22, 24, 22);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int x = center - 88 + slot * 20;
            minecraft.getItemRenderer().renderAndDecorateItem(stack, x, y + 3);
            minecraft.getItemRenderer().renderGuiItemDecorations(
                    minecraft.font, stack, x, y + 3);
        }
        ItemStack offHand = minecraft.player.getOffhandItem();
        if (!offHand.isEmpty()) {
            minecraft.getItemRenderer().renderAndDecorateItem(
                    offHand, center + 96, y + 3);
            minecraft.getItemRenderer().renderGuiItemDecorations(
                    minecraft.font, offHand, center + 96, y + 3);
        }
    }

    private static void renderMobVitals(PoseStack poseStack,
                                        Minecraft minecraft, Mob mob) {
        int center = guiWidth(minecraft) / 2;
        int baseline = guiHeight(minecraft) - 39;
        renderHearts(poseStack, minecraft, mob, center - 91, baseline);
        renderArmor(poseStack, mob.getArmorValue(), center - 91,
                baseline - 10);
        renderFood(poseStack, minecraft, center + 91, baseline);
        renderAir(poseStack, mob, center + 91, baseline - 10);

        float absorption = displayedAbsorption(mob);
        String hearts = format(mob.getHealth() / 2.0F) + "/"
                + format(mob.getMaxHealth() / 2.0F) + " \u2665"
                + (absorption > 0.0F
                ? " +" + format(absorption / 2.0F) + " \u2665" : "");
        minecraft.font.drawShadow(poseStack, hearts,
                center - 91, baseline - 20, 0xFFFFFF);
        ComponentName.drawCenteredShadow(poseStack, minecraft,
                mob.getDisplayName(), center, baseline - 30, 0xFFFFFF);
    }

    private static void renderHearts(PoseStack poseStack,
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
                drawHeart(poseStack, x + column * 8, iconY,
                        health - i * 2.0F);
            }
            for (int i = 0; i < absorptionHearts; i++) {
                int index = exactHearts + i;
                int row = index / 10;
                int column = index % 10;
                int iconY = y - (rows - 1 - row) * 10;
                drawAbsorptionHeart(poseStack, x + column * 8, iconY,
                        absorption - i * 2.0F);
            }
            return;
        }

        float normalized = MthClamp(health / maxHealth, 0.0F, 1.0F) * 20.0F;
        for (int i = 0; i < 10; i++) {
            drawHeart(poseStack, x + i * 8, y, normalized - i * 2.0F);
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

    private static void drawAbsorptionHeart(PoseStack poseStack, int x,
                                             int y, float remaining) {
        blit(poseStack, GUI_ICONS, x, y, 16, 0, 9, 9);
        if (remaining >= 2.0F) {
            blit(poseStack, GUI_ICONS, x, y, 160, 0, 9, 9);
        } else if (remaining > 0.0F) {
            blit(poseStack, GUI_ICONS, x, y, 169, 0, 9, 9);
        }
    }

    private static void drawHeart(PoseStack poseStack, int x, int y,
                                  float remaining) {
        blit(poseStack, GUI_ICONS, x, y, 16, 0, 9, 9);
        if (remaining >= 2.0F) {
            blit(poseStack, GUI_ICONS, x, y, 52, 0, 9, 9);
        } else if (remaining > 0.0F) {
            blit(poseStack, GUI_ICONS, x, y, 61, 0, 9, 9);
        }
    }

    private static void renderArmor(PoseStack poseStack, int armor,
                                    int x, int y) {
        if (armor <= 0) return;
        int points = Math.min(20, armor);
        for (int i = 0; i < 10; i++) {
            int remaining = points - i * 2;
            int u = remaining >= 2 ? 34 : remaining == 1 ? 25 : 16;
            blit(poseStack, GUI_ICONS, x + i * 8, y, u, 9, 9, 9);
        }
    }

    private static void renderFood(PoseStack poseStack, Minecraft minecraft,
                                   int right, int y) {
        if (minecraft.player == null) return;
        int food = Math.max(0, Math.min(20,
                minecraft.player.getFoodData().getFoodLevel()));
        for (int i = 0; i < 10; i++) {
            int x = right - 9 - i * 8;
            blit(poseStack, GUI_ICONS, x, y, 16, 27, 9, 9);
            int remaining = food - i * 2;
            if (remaining >= 2) {
                blit(poseStack, GUI_ICONS, x, y, 52, 27, 9, 9);
            } else if (remaining == 1) {
                blit(poseStack, GUI_ICONS, x, y, 61, 27, 9, 9);
            }
        }
    }

    private static void renderAir(PoseStack poseStack, Mob mob,
                                  int right, int y) {
        int maxAir = Math.max(1, mob.getMaxAirSupply());
        int air = mob.getAirSupply();
        if (air >= maxAir && !mob.isUnderWater()) return;
        int bubbles = Math.min(10,
                (int) Math.ceil(Math.max(0, air) * 10.0D / maxAir));
        for (int i = 0; i < 10; i++) {
            int x = right - 9 - i * 8;
            int u = i < bubbles ? 16 : 25;
            blit(poseStack, GUI_ICONS, x, y, u, 18, 9, 9);
        }
    }

    private static void renderFirstPersonMobArm(PoseStack poseStack,
                                                 Minecraft minecraft,
                                                 Mob mob) {
        var renderer = minecraft.getEntityRenderDispatcher().getRenderer(mob);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)
                || !(livingRenderer.getModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        ResourceLocation texture = renderer.getTextureLocation(mob);
        poseStack.pushPose();
        poseStack.translate(guiWidth(minecraft) - 28.0D,
                guiHeight(minecraft) + 12.0D, 180.0D);
        applyFirstPersonSwing(poseStack, mob);
        poseStack.mulPose(Vector3f.XP.rotationDegrees(-18.0F));
        poseStack.mulPose(Vector3f.YP.rotationDegrees(28.0F));
        poseStack.mulPose(Vector3f.ZP.rotationDegrees(8.0F));
        poseStack.scale(3.4F, -3.4F, 3.4F);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.rightArm.render(poseStack, consumer, 0x00F000F0,
                OverlayTexture.NO_OVERLAY);
        buffers.endBatch();
        poseStack.popPose();
    }

    private static final class ComponentName {
        private static void drawCenteredShadow(PoseStack poseStack,
                                               Minecraft minecraft,
                                               net.minecraft.network.chat.Component text,
                                               int center, int y, int color) {
            float x = center - minecraft.font.width(text) / 2.0F;
            minecraft.font.drawShadow(poseStack, text, x, y, color);
        }
    }

    private static void applyFirstPersonSwing(PoseStack pose, Mob mob) {
        float swing = Mth.clamp(mob.getAttackAnim(1.0F), 0.0F, 1.0F);
        if (swing <= 0.0F) return;
        // Mirrors the shape of vanilla's first-person attack swing: a smooth
        // down/forward arc followed by recovery, while retaining the mob skin.
        float root = Mth.sqrt(swing);
        float arc = Mth.sin(root * (float) Math.PI);
        float follow = Mth.sin(swing * (float) Math.PI);
        pose.translate(-arc * 7.0F, follow * 8.0F, 0.0F);
        pose.mulPose(Vector3f.ZP.rotationDegrees(arc * 24.0F));
        pose.mulPose(Vector3f.XP.rotationDegrees(-follow * 36.0F));
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
                && mob.isInWaterOrBubble();
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
            if (sneak && mob.isOnGround()) {
                Vec3 safe = predictSneakEdge(mob, dx, dz);
                dx = safe.x;
                dz = safe.z;
            }
            double dy = 0.0D;
            if (jump && mob.isOnGround()) {
                // Visual one-tick lead only; authoritative jump height remains
                // the possessed mob's jump control on the server.
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
        // The END client tick runs after the mob's ordinary client tick. A
        // direct prediction move here therefore happens after vanilla already
        // calculated its limb/walk state. Recalculate that state immediately
        // after the predicted move so every vanilla model sees coherent
        // walking/running animation instead of a frozen or jerky limb phase.
        mob.move(net.minecraft.world.entity.MoverType.PLAYER, movement);
        mob.calculateEntityAnimation(mob, includeVerticalAnimation);
    }

    private static Vec3 predictSneakEdge(Mob mob, double dx, double dz) {
        AABB box = mob.getBoundingBox();
        double safeX = dx;
        double safeZ = dz;
        if (mob.level.noCollision(mob, box.move(dx, -0.55D, 0.0D))) safeX = 0.0D;
        if (mob.level.noCollision(mob, box.move(0.0D, -0.55D, dz))) safeZ = 0.0D;
        if (mob.level.noCollision(mob, box.move(safeX, -0.55D, safeZ))) {
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
        return new ResourceLocation("minecraft", path);
    }
}
