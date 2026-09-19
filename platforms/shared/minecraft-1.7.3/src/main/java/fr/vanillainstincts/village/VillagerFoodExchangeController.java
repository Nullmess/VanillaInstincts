package fr.vanillainstincts.village;


import static fr.vanillainstincts.compat.Minecraft115PositionCompat.entityBlockPos;
import fr.vanillainstincts.compat.Minecraft119Compat;
import fr.vanillainstincts.compat.LegacyRegistry;
import fr.vanillainstincts.ai.MobDecisionPlan;
import fr.vanillainstincts.core.decision.ActionOwner;
import fr.vanillainstincts.core.decision.VanillaInstinctsState;
import fr.vanillainstincts.core.model.VillagerSchedulePhase;
import fr.vanillainstincts.core.config.RuntimeConfig;
import fr.vanillainstincts.core.rules.VillageConstructionRules;
import fr.vanillainstincts.core.rules.WorldRules;
import fr.vanillainstincts.tag.VanillaInstinctsTags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityVillager;
import fr.vanillainstincts.compat.LegacyVillagerProfession;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.village.MerchantRecipe;
/** Échanges. */
public final class VillagerFoodExchangeController {
    public static final String VILLAGE_ORIGIN = "vanillainstincts_village_origin";
    public static final String EXCHANGE_GIFT = "vanillainstincts_exchange_gift";
    public static final String EXCHANGE_PAYMENT = "vanillainstincts_exchange_payment";
    public static final String EXCHANGE_SENDER = "vanillainstincts_exchange_sender";
    public static final String EXCHANGE_RECIPIENT = "vanillainstincts_exchange_recipient";
    private static final String EXCHANGE_RECOVERY_AT =
            "vanillainstincts_exchange_recovery_at";
    private static final String EXCHANGE_EXPIRES_AT =
            "vanillainstincts_exchange_expires_at";

    private static final String APPROACH_TARGET =
            "vanillainstincts_social_exchange_approach_target";
    private static final String APPROACH_UNTIL =
            "vanillainstincts_social_exchange_approach_until";
    private static final String SOCIAL_READY_AT =
            "vanillainstincts_social_exchange_ready_at";
    private static final String SOCIAL_ROAM_UNTIL =
            "vanillainstincts_social_exchange_roam_until";
    private static final String SOCIAL_DAY =
            "vanillainstincts_social_exchange_day";
    private static final String SOCIAL_COUNT =
            "vanillainstincts_social_exchange_count";
    private static final String PARTNER_PREFIX =
            "vanillainstincts_social_partner_";

    private static final Map<WorldServer, Map<UUID, Exchange>> EXCHANGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerFoodExchangeController() {
    }

    public static boolean contribute(EntityVillager actor, MobDecisionPlan plan,
                                     WorldServer level, long gameTime) {
        if (actor == null || actor.isChild() || actor.isTrading()
                || activeFor(level, actor.getUniqueID()) != null) {
            return false;
        }
        VillagerSchedulePhase phase = VillagerRoutineController.phaseFor(
                level.getWorldTime());
        if (phase != VillagerSchedulePhase.MIDDAY_BREAK
                && phase != VillagerSchedulePhase.SOCIAL) {
            return false;
        }
        refreshDailyQuota(actor, level.getWorldTime());
        if (!mayExchangeTodayConfigured(actor.getEntityData()
                .getInteger(SOCIAL_COUNT))
                || gameTime < actor.getEntityData()
                .getLong(SOCIAL_READY_AT)) {
            return false;
        }

        Purchase purchase = findProfessionalSeller(level, actor, gameTime);
        if (purchase != null && shouldSeekPurchase(actor,
                level.getWorldTime())) {
            return contributePurchase(actor, purchase, plan, level, gameTime);
        }
        return contributeSale(actor, plan, level, gameTime);
    }

    private static boolean contributeSale(EntityVillager merchant,
                                           MobDecisionPlan plan,
                                           WorldServer level,
                                           long gameTime) {
        int slot = findExchangeSlot(merchant);
        if (slot < 0) return false;
        ItemStack source = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(merchant).getStackInSlot(slot);
        int amount = exchangeAmount(LegacyVillagerProfession.of(merchant),
                source);
        if (amount <= 0) return false;
        ItemStack preview = Minecraft119Compat.copyWithCount(source, amount);
        EntityVillager recipient = findRecipient(level, merchant, preview, gameTime);
        if (recipient == null) return false;

        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(merchant, recipient)
                > VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
            UUID recipientId = recipient.getUniqueID();
            ItemStack acceptedPreview = preview.copy();
            plan.offerNavigation(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE,
                    fr.vanillainstincts.compat.Minecraft17Compat.position(recipient),
                    VillageConstructionRules.FARMER_EXCHANGE_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        rememberApproach(merchant, recipientId, gameTime);
                        showApproach(merchant, recipient, acceptedPreview);
                    });
            return true;
        }

        int acceptedSlot = slot;
        plan.offerSpecial(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                ActionOwner.VILLAGER_SOCIAL,
                VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE + 3,
                WorldRules.STATE_HOLD_FARM_TICKS,
                () -> begin(level, merchant, recipient, acceptedSlot,
                        gameTime, 0, 0));
        return true;
    }

    private static boolean contributePurchase(EntityVillager buyer,
                                               Purchase purchase,
                                               MobDecisionPlan plan,
                                               WorldServer level,
                                               long gameTime) {
        EntityVillager seller = purchase.seller();
        if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(buyer, seller)
                > VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
            UUID sellerId = seller.getUniqueID();
            plan.offerNavigation(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE + 1,
                    fr.vanillainstincts.compat.Minecraft17Compat.position(seller),
                    VillageConstructionRules.FARMER_EXCHANGE_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        rememberApproach(buyer, sellerId, gameTime);
                        showPurchaseApproach(buyer, seller);
                    });
            return true;
        }
        plan.offerSpecial(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                ActionOwner.VILLAGER_SOCIAL,
                VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE + 4,
                WorldRules.STATE_HOLD_FARM_TICKS,
                () -> begin(level, seller, buyer, purchase.slot(), gameTime,
                        purchase.amount(), 1));
        return true;
    }

    /** Approche. */
    public static boolean maintainParticipant(EntityVillager villager,
                                              WorldServer level,
                                              long gameTime) {
        if (villager == null) return false;
        if (fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(villager.getEntityData(), APPROACH_TARGET)) {
            if (gameTime > villager.getEntityData()
                    .getLong(APPROACH_UNTIL)) {
                clearApproach(villager);
                return false;
            }
            EntityVillager target = resolve(level,
                    fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(
                            villager.getEntityData(), APPROACH_TARGET));
            if (target == null || target.isTrading()
                    || partnerBlocked(villager, target, gameTime)) {
                clearApproach(villager);
                return false;
            }
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(villager, target)
                    <= VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
                villager.getNavigator().clearPathEntity();
            } else if (villager.getNavigator().noPath()
                    || Math.floorMod(gameTime + villager.getEntityId(), 10L) == 0L) {
                villager.getNavigator().tryMoveToEntityLiving(target,
                        VillageConstructionRules.FARMER_EXCHANGE_SPEED);
            }
            return true;
        }
        if (shouldSuppressJobReturn(villager, gameTime)) {
            if (Math.floorMod(gameTime + villager.getEntityId(), 20L) == 0L) {
                    }
            return true;
        }
        return false;
    }

    public static boolean shouldSuppressJobReturn(EntityVillager villager,
                                                   long gameTime) {
        return villager != null && gameTime < villager.getEntityData()
                .getLong(SOCIAL_ROAM_UNTIL);
    }

    public static void tickLevel(WorldServer level, long gameTime) {
        Map<UUID, Exchange> exchanges = levelExchanges(level);
        List<UUID> finished = new ArrayList<>();
        for (Exchange exchange : fr.vanillainstincts.compat.LegacyJava8.copyList(exchanges.values())) {
            EntityVillager sender = resolve(level, exchange.senderId);
            EntityVillager recipient = resolve(level, exchange.recipientId);
            if (sender == null || recipient == null
                    || gameTime > exchange.expiresAt) {
                refund(level, exchange, sender);
                finished.add(exchange.exchangeId);
                continue;
            }

            if (exchange.stage == Stage.PAYMENT) {
                EntityItem payment = resolveItem(level, exchange.currentItemId);
                if (payment == null) {
                    VillagerWalletController.credit(recipient,
                            exchange.paymentEmeralds);
                    refundReservedGift(sender, exchange.originalGift);
                    finishInterrupted(sender, recipient, gameTime);
                    finished.add(exchange.exchangeId);
                    continue;
                }
                EntityPlayerMP thief = nearestInterceptor(level, payment);
                if (thief != null) {
                    // Vol
                    steal(level, thief, payment, recipient, gameTime);
                    refundReservedGift(sender, exchange.originalGift);
                    exchange.stolen = true;
                    finishInterrupted(sender, recipient, gameTime);
                    finished.add(exchange.exchangeId);
                    continue;
                }
                if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(sender, payment)
                        <= VillageConstructionRules
                        .SOCIAL_EXCHANGE_PICKUP_DISTANCE_SQR) {
                    VillagerWalletController.credit(sender,
                            exchange.paymentEmeralds);
                    fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(payment);
                    EntityItem gift = spawnReservedItem(level, sender,
                            recipient, exchange.originalGift.copy(), false);
                    exchange.currentItemId = gift.getUniqueID();
                    exchange.stage = Stage.GIFT;
                    sender.setCurrentItemOrArmor(0,
                            exchange.originalGift.copy());
                    recipient.setCurrentItemOrArmor(0,
                            null);
                    sender.swingItem();
                    exchange.expiresAt = gameTime
                            + VillageConstructionRules
                            .FARMER_EXCHANGE_TIMEOUT_TICKS;
                }
                continue;
            }

            EntityItem gift = resolveItem(level, exchange.currentItemId);
            if (gift == null) {
                VillagerWalletController.reverse(sender, recipient,
                        exchange.paymentEmeralds);
                refundReservedGift(sender, exchange.originalGift);
                finishInterrupted(sender, recipient, gameTime);
                finished.add(exchange.exchangeId);
                continue;
            }
            EntityPlayerMP thief = nearestInterceptor(level, gift);
            if (thief != null) {
                // Vol
                steal(level, thief, gift, recipient, gameTime);
                exchange.stolen = true;
                finishInterrupted(sender, recipient, gameTime);
                finished.add(exchange.exchangeId);
                continue;
            }
            if (fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(recipient, gift)
                    <= VillageConstructionRules.SOCIAL_EXCHANGE_PICKUP_DISTANCE_SQR) {
                ItemStack delivered = gift.getEntityItem().copy();
                ItemStack transfer = delivered.copy();
                if (!insertFully(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(recipient), transfer)) {
                    continue;
                }
                fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(recipient).markDirty();
                fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(gift);
                clearVisual(sender, recipient);
                recordDelivered(sender, recipient, delivered,
                        level.getWorldTime(), gameTime);
                finishPayment(sender, gameTime);
                finished.add(exchange.exchangeId);
            }
        }
        finished.forEach(exchanges::remove);
    }

    public static void onPlayerPickup(WorldServer level, EntityPlayerMP player,
                                      EntityItem item, long gameTime) {
        if (level == null || player == null || item == null
                || (!item.getEntityData().getBoolean(EXCHANGE_GIFT)
                && !item.getEntityData().getBoolean(EXCHANGE_PAYMENT))) {
            return;
        }
        UUID senderId = readUuid(item, EXCHANGE_SENDER);
        EntityVillager sender = resolve(level, senderId);
        if (sender != null) reportTheft(sender, player, level, gameTime);
    }

    public static boolean isVillageOrigin(EntityItem item) {
        if (item == null) return false;
        if (item.getEntityData().getBoolean(VILLAGE_ORIGIN)) return true;
        // EntityItem owner is a legacy String in 1.12; Vanilla Instincts
        // marks its own village drops explicitly with VILLAGE_ORIGIN.
        return false;
    }

    public static boolean isVillageOriginData(boolean villageOrigin,
                                              boolean gift,
                                              boolean payment) {
        return villageOrigin || gift || payment;
    }

    public static boolean mayExchangeToday(int completedCount) {
        return completedCount < VillageConstructionRules.SOCIAL_EXCHANGE_DAILY_LIMIT;
    }

    private static boolean mayExchangeTodayConfigured(int completedCount) {
        return completedCount
                < RuntimeConfig.snapshot().socialExchangeDailyLimit();
    }

    public static boolean partnerReady(long readyAt, long gameTime) {
        return gameTime >= readyAt;
    }

    public static boolean intendedVillagerMayCollect(UUID villagerId,
                                                      UUID senderId,
                                                      UUID recipientId,
                                                      boolean payment) {
        if (villagerId == null) return false;
        return payment ? villagerId.equals(senderId)
                : villagerId.equals(recipientId);
    }

    public static int paymentEmeralds(int itemValue) {
        int normalizedValue = Math.max(1, itemValue);
        return Math.max(1, Math.min(64, (normalizedValue + 2) / 3));
    }

    public static int paymentFor(ItemStack stack) {
        if (stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return 1;
        int resale = RecoveredTradeController.emeraldPrice(null, stack);
        return paymentEmeralds(resale);
    }

    public static int paymentFor(WorldServer level, EntityVillager seller,
                                 ItemStack stack) {
        int base = paymentFor(stack);
        if (level == null || seller == null || stack == null
                || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) {
            return base;
        }
        int marketPrice = VillageMarketController.quote(level,
                entityBlockPos(seller), stack, base * 3);
        return paymentEmeralds(marketPrice);
    }

    public static int orphanedExchangeCount(WorldServer level) {
        if (level == null) return 0;
        int count = 0;
        for (Exchange exchange : fr.vanillainstincts.compat.LegacyJava8.copyList(levelExchanges(level).values())) {
            if (resolve(level, exchange.senderId) == null
                    || resolve(level, exchange.recipientId) == null) {
                count++;
            }
        }
        return count;
    }

    public static void clearLevel(WorldServer level) {
        EXCHANGES.remove(level);
    }

    /** Recovers an exchange item after a world reload. */
    public static void tickLoadedExchangeItem(EntityItem item,
                                              WorldServer level,
                                              long gameTime) {
        if (item == null || !item.isEntityAlive()
                || !isExchangeItem(item)
                || gameTime < item.getEntityData()
                .getLong(EXCHANGE_RECOVERY_AT)
                || belongsToActiveExchange(level, item.getUniqueID())) {
            return;
        }
        boolean payment = item.getEntityData()
                .getBoolean(EXCHANGE_PAYMENT);
        UUID senderId = readUuid(item, EXCHANGE_SENDER);
        UUID recipientId = readUuid(item, EXCHANGE_RECIPIENT);
        EntityVillager sender = resolve(level, senderId);
        EntityVillager recipient = resolve(level, recipientId);
        if (payment) {
            EntityVillager payer = sender;
            EntityVillager seller = recipient;
            if (payer == null || seller == null) {
                postponeRecovery(item, gameTime);
                return;
            }
            VillagerWalletController.credit(payer,
                    item.getEntityItem().stackSize);
            restoreHeldGift(seller);
            clearVisual(seller, payer);
            finishInterrupted(seller, payer, gameTime);
            fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);
            return;
        }
        recoverGift(item, sender, recipient, level, gameTime);
    }

    private static void begin(WorldServer level, EntityVillager sender,
                              EntityVillager recipient, int slot, long gameTime,
                              int requestedAmount, int fixedEmeralds) {
        ItemStack source = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender).getStackInSlot(slot);
        int allowedAmount = requestedAmount > 0
                ? professionalSaleAmount(LegacyVillagerProfession.of(sender), source)
                : exchangeAmount(LegacyVillagerProfession.of(sender),
                source);
        int amount = requestedAmount > 0
                ? Math.min(requestedAmount, allowedAmount) : allowedAmount;
        if (amount <= 0) {
            clearVisual(sender, recipient);
            return;
        }
        ItemStack giftStack = Minecraft119Compat.copyWithCount(source, amount);
        if (hasAvailableMerchantOffer(sender, giftStack)) {
            clearVisual(sender, recipient);
            return;
        }
        if (!canFullyInsert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(recipient), giftStack)) {
            clearVisual(sender, recipient);
            return;
        }
        int emeralds = fixedEmeralds > 0 ? fixedEmeralds
                : paymentFor(level, sender, giftStack);
        if (!VillagerWalletController.debit(recipient, emeralds)) {
            clearVisual(sender, recipient);
            return;
        }
        fr.vanillainstincts.compat.Minecraft110ItemStackCompat.shrink(source, amount);
        fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender).markDirty();
        sender.setCurrentItemOrArmor(0, giftStack.copy());
        recipient.setCurrentItemOrArmor(0,
                new ItemStack(Items.emerald, emeralds));
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(sender, recipient, 30.0F, 30.0F);
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(recipient, sender, 30.0F, 30.0F);
        recipient.swingItem();
        UUID paymentId = spawnPayment(level, recipient, sender, emeralds);
        UUID exchangeId = UUID.randomUUID();
        levelExchanges(level).put(exchangeId, new Exchange(exchangeId,
                sender.getUniqueID(), recipient.getUniqueID(), paymentId,
                gameTime + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS,
                emeralds, giftStack.copy()));
    }

    private static EntityItem spawnReservedItem(WorldServer level,
                                                EntityVillager sender,
                                                EntityVillager recipient,
                                                ItemStack stack,
                                                boolean payment) {
        EntityItem item = new EntityItem(level, sender.posX,
                sender.posY + 0.7D, sender.posZ, stack);
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(item, 32_767);
        item.func_145797_a(sender.getUniqueID().toString());
        item.getEntityData().setBoolean(VILLAGE_ORIGIN, true);
        item.getEntityData().setBoolean(payment
                ? EXCHANGE_PAYMENT : EXCHANGE_GIFT, true);
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(item.getEntityData(), EXCHANGE_SENDER, sender.getUniqueID());
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(item.getEntityData(), EXCHANGE_RECIPIENT,
                recipient.getUniqueID());
        item.getEntityData().setLong(EXCHANGE_RECOVERY_AT,
                level.getTotalWorldTime() + 40L);
        item.getEntityData().setLong(EXCHANGE_EXPIRES_AT,
                level.getTotalWorldTime()
                        + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS);
        fr.vanillainstincts.compat.Minecraft112Compat.setMotion(item,
                fr.vanillainstincts.compat.Minecraft112Compat.add(
                        fr.vanillainstincts.compat.Minecraft112Compat.scale(
                                fr.vanillainstincts.compat.Minecraft17Compat.position(recipient).subtract(fr.vanillainstincts.compat.Minecraft17Compat.position(sender)).normalize(), 0.22D),
                        0.0D, 0.20D, 0.0D));
        level.spawnEntityInWorld(item);
        return item;
    }

    private static UUID spawnPayment(WorldServer level, EntityVillager payer,
                                     EntityVillager seller, int emeralds) {
        EntityItem payment = spawnReservedItem(level, payer, seller,
                new ItemStack(Items.emerald, emeralds), true);
        return payment.getUniqueID();
    }

    private static void recordDelivered(EntityVillager sender,
                                        EntityVillager recipient,
                                        ItemStack delivered,
                                        long dayTime,
                                        long gameTime) {
        if (sender.worldObj instanceof WorldServer) { WorldServer level = (WorldServer) (sender.worldObj); 
            VillageMarketController.recordDemand(level,
                    entityBlockPos(recipient), delivered);
            VillagerProductionController.recordSocialSale(sender, level,
                    delivered, gameTime);
        }
        refreshDailyQuota(sender, dayTime);
        refreshDailyQuota(recipient, dayTime);
        sender.getEntityData().setInteger(SOCIAL_COUNT,
                sender.getEntityData().getInteger(SOCIAL_COUNT) + 1);
        recipient.getEntityData().setInteger(SOCIAL_COUNT,
                recipient.getEntityData().getInteger(SOCIAL_COUNT) + 1);
        sender.getEntityData().setLong(partnerKey(recipient.getUniqueID()),
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_PARTNER_COOLDOWN_TICKS);
        recipient.getEntityData().setLong(partnerKey(sender.getUniqueID()),
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_PARTNER_COOLDOWN_TICKS);
        recipient.getEntityData().setLong(SOCIAL_READY_AT,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        recipient.getEntityData().setLong(SOCIAL_ROAM_UNTIL,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_RECIPIENT_ROAM_TICKS);
        clearApproach(sender);
        clearApproach(recipient);
    }

    private static void finishPayment(EntityVillager sender, long gameTime) {
        sender.getEntityData().setLong(SOCIAL_READY_AT,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        sender.getEntityData().setLong(SOCIAL_ROAM_UNTIL,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_ROAM_TICKS);
        clearApproach(sender);
    }

    private static void refund(WorldServer level, Exchange exchange,
                               EntityVillager sender) {
        EntityItem item = resolveItem(level, exchange.currentItemId);
        if (item != null) fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);
        EntityVillager recipient = resolve(level, exchange.recipientId);
        clearVisual(sender, recipient);
        if (exchange.stolen) return;
        if (exchange.stage == Stage.PAYMENT && recipient != null) {
            VillagerWalletController.credit(recipient, exchange.paymentEmeralds);
        }
        if (sender != null) {
            refundReservedGift(sender, exchange.originalGift);
            if (exchange.stage == Stage.GIFT && recipient != null) {
                VillagerWalletController.reverse(sender, recipient,
                        exchange.paymentEmeralds);
            }
            finishPayment(sender, sender.worldObj.getTotalWorldTime());
        }
    }

    private static void refundReservedGift(EntityVillager sender, ItemStack gift) {
        if (sender == null || gift == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(gift)) return;
        ItemStack refund = gift.copy();
        ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender), refund);
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(refund)) fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(sender, refund);
        fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender).markDirty();
    }

    private static void finishInterrupted(EntityVillager sender, EntityVillager recipient,
                                          long gameTime) {
        clearVisual(sender, recipient);
        if (sender != null) finishPayment(sender, gameTime);
        if (recipient != null) {
            recipient.getEntityData().setLong(SOCIAL_READY_AT,
                    gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
            recipient.getEntityData().setLong(SOCIAL_ROAM_UNTIL,
                    gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_RECIPIENT_ROAM_TICKS);
            clearApproach(recipient);
        }
    }

    private static void rememberApproach(EntityVillager villager, UUID targetId,
                                         long gameTime) {
        fr.vanillainstincts.compat.Minecraft18NbtCompat.setUniqueId(villager.getEntityData(), APPROACH_TARGET, targetId);
        villager.getEntityData().setLong(APPROACH_UNTIL,
                gameTime
                        + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS);
    }

    private static void showApproach(EntityVillager sender, EntityVillager recipient,
                                     ItemStack gift) {
        sender.setCurrentItemOrArmor(0, gift.copy());
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(sender, recipient, 30.0F, 30.0F);
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(recipient, sender, 30.0F, 30.0F);
    }

    private static void showPurchaseApproach(EntityVillager buyer,
                                             EntityVillager seller) {
        buyer.setCurrentItemOrArmor(0,
                new ItemStack(Items.emerald));
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(buyer, seller, 30.0F, 30.0F);
        fr.vanillainstincts.compat.Minecraft112Compat.lookAt(seller, buyer, 30.0F, 30.0F);
    }

    private static void clearVisual(EntityVillager sender, EntityVillager recipient) {
        if (sender != null) {
            sender.setCurrentItemOrArmor(0, null);
        }
        if (recipient != null) {
            recipient.setCurrentItemOrArmor(0, null);
        }
    }


    private static boolean isExchangeItem(EntityItem item) {
        return item.getEntityData().getBoolean(EXCHANGE_PAYMENT)
                || item.getEntityData().getBoolean(EXCHANGE_GIFT);
    }

    private static boolean belongsToActiveExchange(WorldServer level,
                                                    UUID itemId) {
        for (Exchange exchange : levelExchanges(level).values()) {
            if (!exchange.stolen && exchange.currentItemId.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static void postponeRecovery(EntityItem item, long gameTime) {
        long expiresAt = item.getEntityData().getLong(EXCHANGE_EXPIRES_AT);
        if (expiresAt > 0L && gameTime > expiresAt) {
            unlockOrphan(item);
        } else {
            item.getEntityData().setLong(EXCHANGE_RECOVERY_AT,
                    gameTime + 40L);
        }
    }

    private static void restoreHeldGift(EntityVillager sender) {
        ItemStack held = sender.getHeldItem();
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(held) || held.getItem().equals(Items.emerald)) return;
        ItemStack refund = held.copy();
        ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender), refund);
        if (!fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(refund)) {
            fr.vanillainstincts.compat.Minecraft112Compat.spawnItem(sender, refund);
        }
        fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(sender).markDirty();
        sender.setCurrentItemOrArmor(0, null);
    }

    private static void recoverGift(EntityItem item, EntityVillager sender,
                                    EntityVillager recipient, WorldServer level,
                                    long gameTime) {
        if (recipient == null) {
            postponeRecovery(item, gameTime);
            return;
        }
        ItemStack delivered = item.getEntityItem().copy();
        ItemStack remaining = delivered.copy();
        ProfessionStockController.insert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(recipient), remaining);
        fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(recipient).markDirty();
        if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(remaining)) {
            fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);
        } else {
            fr.vanillainstincts.compat.Minecraft112Compat.setItem(item, remaining);
            unlockOrphan(item);
        }
        if (sender != null) {
            clearVisual(sender, recipient);
            recordDelivered(sender, recipient, delivered,
                    level.getWorldTime(), gameTime);
            finishPayment(sender, gameTime);
        } else {
            recipient.setCurrentItemOrArmor(0, null);
            recipient.getEntityData().setLong(SOCIAL_READY_AT,
                    gameTime
                            + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        }
    }

    private static void unlockOrphan(EntityItem item) {
        fr.vanillainstincts.compat.Minecraft112Compat.setPickupDelay(item, 0);
        item.getEntityData().removeTag(EXCHANGE_PAYMENT);
        item.getEntityData().removeTag(EXCHANGE_GIFT);
        item.getEntityData().removeTag(EXCHANGE_SENDER);
        item.getEntityData().removeTag(EXCHANGE_RECIPIENT);
        item.getEntityData().removeTag(EXCHANGE_RECOVERY_AT);
        item.getEntityData().removeTag(EXCHANGE_EXPIRES_AT);
    }

    private static EntityPlayerMP nearestInterceptor(WorldServer level,
                                                   EntityItem item) {
        return fr.vanillainstincts.compat.Minecraft112Compat.players(level).stream()
                .filter(player -> player.isEntityAlive() && !fr.vanillainstincts.compat.Minecraft17Compat.isSpectator(player)
                        && fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, item)
                        <= VillageConstructionRules.SOCIAL_EXCHANGE_STEAL_DISTANCE_SQR)
                .min(Comparator.comparingDouble(player ->
                        fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(player, item)))
                .orElse(null);
    }

    private static void steal(WorldServer level, EntityPlayerMP player,
                              EntityItem item, EntityVillager victim,
                              long gameTime) {
        ItemStack stolen = item.getEntityItem().copy();
        if (!fr.vanillainstincts.compat.Minecraft112Compat.give(player, stolen) && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stolen)) {
            fr.vanillainstincts.compat.Minecraft112Compat.drop(player, stolen);
        }
        fr.vanillainstincts.compat.Minecraft112Compat.removeEntity(item);
        if (victim != null) reportTheft(victim, player, level, gameTime);
    }

    private static void reportTheft(EntityVillager victim, EntityPlayerMP player,
                                    WorldServer level, long gameTime) {
        VillagerEconomyController.record(victim, player,
                VillagerEconomyController.Incident.THEFT, gameTime);
        VillageThreatRegistry.ThreatSnapshot threat =
                VillageThreatRegistry.reportVillagerAttack(victim, level,
                        player, 2.0F, gameTime);
        if (threat != null) {
            VillagerRuntimeState state = VillagerStateStore.stateFor(victim);
            VillagerGolemReportController.beginReport(victim, state, threat,
                    gameTime);
            VillagerStateStore.save(victim, state);
        }
    }

    private static Purchase findProfessionalSeller(WorldServer level,
                                                   EntityVillager buyer,
                                                   long gameTime) {
        if (!VillagerWalletController.canAfford(buyer, 1)) return null;
        List<Purchase> candidates = new ArrayList<>();
        for (EntityVillager seller : fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class,
                fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(buyer), 
                        VillageConstructionRules.FARMER_EXCHANGE_RADIUS))) {
            if (seller == buyer || !seller.isEntityAlive() || seller.isChild()
                    || seller.isTrading()
                    || activeFor(level, seller.getUniqueID()) != null
                    || !hasDailyCapacity(seller, level.getWorldTime())
                    || partnerBlocked(buyer, seller, gameTime)) {
                continue;
            }
            LegacyVillagerProfession profession = LegacyVillagerProfession.of(seller);
            if (profession == LegacyVillagerProfession.NONE
                    || profession == LegacyVillagerProfession.NITWIT) {
                continue;
            }
            int slot = findProfessionalSaleSlot(seller);
            if (slot < 0) continue;
            ItemStack source = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(seller).getStackInSlot(slot);
            int amount = professionalSaleAmount(profession, source);
            if (amount <= 0) continue;
            ItemStack preview = Minecraft119Compat.copyWithCount(source, amount);
            if (!canFullyInsert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(buyer), preview)) continue;
            candidates.add(new Purchase(seller, slot, amount, preview));
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparing(purchase ->
                purchase.seller().getUniqueID()));
        int seed = buyer.getUniqueID().hashCode()
                + buyer.getEntityData().getInteger(SOCIAL_COUNT)
                + Long.hashCode(Math.floorDiv(level.getWorldTime(), 24_000L));
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    private static boolean shouldSeekPurchase(EntityVillager villager,
                                              long dayTime) {
        int seed = villager.getUniqueID().hashCode()
                + villager.getEntityData().getInteger(SOCIAL_COUNT)
                + Long.hashCode(Math.floorDiv(dayTime, 24_000L));
        return shouldSeekPurchase(seed);
    }

    public static boolean shouldSeekPurchase(int deterministicSeed) {
        return Math.floorMod(deterministicSeed, 3) != 0;
    }

    /** Selects the most profession-specific product currently in stock. */
    public static int findProfessionalSaleSlot(EntityVillager seller) {
        if (seller == null) return -1;
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(seller);
        InventoryBasic inventory = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(seller);
        int bestSlot = -1;
        int bestPriority = Integer.MAX_VALUE;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (hasAvailableMerchantOffer(seller, stack)) continue;
            int amount = professionalSaleAmount(profession, stack);
            if (amount <= 0) continue;
            int priority = VillagerProductionController.produces(profession,
                    stack) ? 0 : 1;
            if (priority < bestPriority) {
                bestPriority = priority;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    /** Profession product quantity sold for exactly one social emerald. */
    public static int professionalSaleAmount(LegacyVillagerProfession profession,
                                             ItemStack stack) {
        int available = exchangeAmount(profession, stack);
        if (available <= 0) return 0;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        int target = 1;
        if (profession == LegacyVillagerProfession.FARMER) {
            target = stack.getItem().equals(Items.milk_bucket) ? 1
                    : stack.getItem().equals(Items.bread) ? 3 : 4;
        } else if (profession == LegacyVillagerProfession.FLETCHER
                && path.contains("arrow")) {
            target = 4;
        } else if (profession == LegacyVillagerProfession.SHEPHERD
                || profession == LegacyVillagerProfession.MASON
                || stack.getItem().equals(Items.paper)) {
            target = 4;
        } else if (profession == LegacyVillagerProfession.FISHERMAN
                || profession == LegacyVillagerProfession.BUTCHER
                || profession == LegacyVillagerProfession.LEATHERWORKER) {
            target = 2;
        } else if (VillageConstructionCapability
                .isSmithProfession(profession) && stack.getItem().equals(Items.iron_ingot)) {
            target = 2;
        }
        return Math.min(available, target);
    }

    private static EntityVillager findRecipient(WorldServer level, EntityVillager sender,
                                          ItemStack gift, long gameTime) {
        int payment = paymentFor(level, sender, gift);
        return fr.vanillainstincts.compat.Minecraft112Compat.entitiesWithinAABB(level, EntityVillager.class,
                        fr.vanillainstincts.compat.Minecraft112Compat.expandBox(fr.vanillainstincts.compat.Minecraft17Compat.boundingBox(sender), 
                                VillageConstructionRules.FARMER_EXCHANGE_RADIUS),
                        other -> other != sender && other.isEntityAlive()
                                && !other.isChild() && !other.isTrading()
                                && activeFor(level, other.getUniqueID()) == null
                                && hasDailyCapacity(other, level.getWorldTime())
                                && !partnerBlocked(sender, other, gameTime)
                                && VillagerWalletController.canAfford(other,
                                payment)
                                && canFullyInsert(fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(other), gift)
                                && professionWants(LegacyVillagerProfession.of(other), gift))
                .stream()
                .min(Comparator.comparingLong((EntityVillager other) ->
                                sender.getEntityData().getLong(
                                        partnerKey(other.getUniqueID())))
                        .thenComparingDouble((value -> fr.vanillainstincts.compat.Minecraft112Compat.distanceSq(sender, value))))
                .orElse(null);
    }

    public static int findExchangeSlot(EntityVillager villager) {
        if (villager == null) return -1;
        InventoryBasic inventory = fr.vanillainstincts.compat.Minecraft17Compat.villagerInventory(villager);
        LegacyVillagerProfession profession = LegacyVillagerProfession.of(villager);
        int size = inventory.getSizeInventory();
        if (size <= 0) return -1;
        int start = Math.floorMod(villager.getEntityId()
                + villager.getEntityData().getInteger(SOCIAL_COUNT), size);
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            ItemStack stack = inventory.getStackInSlot(slot);
            if (hasAvailableMerchantOffer(villager, stack)) continue;
            if (isProfessionExchangeGood(profession, stack)
                    && exchangeAmount(profession, stack) > 0) {
                return slot;
            }
        }
        return -1;
    }

    public static int findShareableFoodSlot(InventoryBasic inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isShareableFood(stack)
                    && stack.stackSize
                    > VillageConstructionRules.FARMER_MIN_SHAREABLE_FOOD) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean isProfessionExchangeGood(
            LegacyVillagerProfession profession, ItemStack stack) {
        if (profession == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        if (VillagerProductionController.produces(profession, stack)) {
            return true;
        }
        if (profession == LegacyVillagerProfession.FARMER) {
            return isShareableFood(stack) || stack.getItem().equals(Items.milk_bucket);
        }
        if (profession == LegacyVillagerProfession.FISHERMAN) {
            return "fish".equals(path) || "cooked_fished".equals(path);
        }
        if (profession == LegacyVillagerProfession.BUTCHER) {
            return path.contains("beef") || path.contains("porkchop")
                    || path.contains("mutton") || path.contains("chicken")
                    || path.contains("rabbit");
        }
        if (profession == LegacyVillagerProfession.FLETCHER) {
            return path.contains("arrow") || stack.getItem().equals(Items.flint)
                    || stack.getItem().equals(Items.feather);
        }
        if (profession == LegacyVillagerProfession.SHEPHERD) {
            return path.contains("wool") || path.contains("dye");
        }
        if (profession == LegacyVillagerProfession.LIBRARIAN) {
            return stack.getItem().equals(Items.book) || stack.getItem().equals(Items.paper)
                    || stack.getItem().equals(Items.writable_book);
        }
        if (profession == LegacyVillagerProfession.CARTOGRAPHER) {
            return stack.getItem().equals(Items.paper) || path.contains("map")
                    || stack.getItem().equals(Items.compass);
        }
        if (profession == LegacyVillagerProfession.CLERIC) {
            return path.contains("potion") || stack.getItem().equals(Items.redstone)
                    || stack.getItem().equals(Items.glowstone_dust)
                    || stack.getItem().equals(Items.quartz)
                    || stack.getItem().equals(Items.magma_cream)
                    || stack.getItem().equals(Items.ghast_tear);
        }
        if (profession == LegacyVillagerProfession.MASON) {
            return containsAny(path, "brick", "stone", "terracotta", "clay")
                    || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian));
        }
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            return (stack.getItem().equals(Items.iron_ingot)
                    && stack.stackSize > 40) || isEquipmentGood(path)
                    || stack.getItem().equals(Items.bucket)
                    || stack.getItem().equals(Items.flint_and_steel);
        }
        if (profession == LegacyVillagerProfession.LEATHERWORKER) {
            return stack.getItem().equals(Items.leather);
        }
        return false;
    }

    /** Prevents social resale of any result already listed for players. */
    public static boolean hasAvailableMerchantOffer(
            EntityVillager merchant, ItemStack stack) {
        if (merchant == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        for (MerchantRecipe offer : fr.vanillainstincts.compat.Minecraft112Compat.offerList(merchant)) {
            if (fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(offer.getItemToSell(), stack)) {
                return true;
            }
        }
        return false;
    }

    /** Paiement. */
    public static boolean buyerPaysFirst() {
        return true;
    }

    /** Demande. */
    public static boolean professionWants(LegacyVillagerProfession profession,
                                          ItemStack stack) {
        if (profession == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        if (ProfessionRecipeCatalog.isIngredient(profession, stack)) {
            return true;
        }
        if (isShareableFood(stack) || path.contains("potion")) return true;
        // Équipement
        if (isEquipmentGood(path)) return true;
        if (profession == LegacyVillagerProfession.FARMER) {
            return containsAny(path, "seed", "hoe", "bone_meal", "compost")
                    || stack.getItem().equals(Items.bucket) || stack.getItem().equals(Items.lead);
        }
        if (profession == LegacyVillagerProfession.FISHERMAN) {
            return containsAny(path, "string", "boat", "barrel", "cod",
                    "salmon");
        }
        if (profession == LegacyVillagerProfession.BUTCHER) {
            return stack.getItem().equals(Items.wheat) || stack.getItem().equals(Items.carrot)
                    || stack.getItem().equals(Items.potato)
                    || path.contains("coal");
        }
        if (profession == LegacyVillagerProfession.FLETCHER) {
            return stack.getItem().equals(Items.flint) || stack.getItem().equals(Items.feather)
                    || stack.getItem().equals(Items.stick) || stack.getItem().equals(Items.string)
                    || path.contains("arrow");
        }
        if (profession == LegacyVillagerProfession.SHEPHERD) {
            return containsAny(path, "wool", "dye", "shears", "wheat");
        }
        if (profession == LegacyVillagerProfession.LIBRARIAN) {
            return stack.getItem().equals(Items.paper) || stack.getItem().equals(Items.book)
                    || stack.getItem().equals(Items.writable_book)
                    || path.contains("ink_sac");
        }
        if (profession == LegacyVillagerProfession.CARTOGRAPHER) {
            return stack.getItem().equals(Items.paper) || stack.getItem().equals(Items.compass)
                    || path.contains("map");
        }
        if (profession == LegacyVillagerProfession.CLERIC) {
            return ClericBrewingController.isWorkStock(stack)
                    || containsAny(path, "gold", "rotten_flesh")
                    || stack.getItem().equals(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.obsidian))
                    || stack.getItem().equals(Items.flint_and_steel);
        }
        if (profession == LegacyVillagerProfession.MASON) {
            return containsAny(path, "stone", "brick", "clay", "terracotta",
                    "quartz", "iron_pickaxe");
        }
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            return containsAny(path, "iron", "coal", "diamond", "tool",
                    "sword", "armor", "helmet", "chestplate", "leggings",
                    "boots");
        }
        if (profession == LegacyVillagerProfession.LEATHERWORKER) {
            return stack.getItem().equals(Items.leather) || path.contains("rabbit_hide")
                    || stack.getItem().equals(Items.saddle);
        }
        return profession == LegacyVillagerProfession.NONE
                || profession == LegacyVillagerProfession.NITWIT;
    }

    private static int exchangeAmount(LegacyVillagerProfession profession,
                                      ItemStack stack) {
        if (!isProfessionExchangeGood(profession, stack)) return 0;
        String path = fr.vanillainstincts.compat.LegacyResourceLocation.path(LegacyRegistry.ITEM.getKey(stack.getItem()))
                .toLowerCase(Locale.ROOT);
        int reserve;
        int maximum;
        if (stack.getItem().equals(Items.milk_bucket) || stack.getItem().equals(Items.bucket)
                || stack.getItem().equals(Items.flint_and_steel)) {
            reserve = 0;
            maximum = 1;
        } else if (profession == LegacyVillagerProfession.FARMER) {
            reserve = VillageConstructionRules.FARMER_MIN_SHAREABLE_FOOD;
            maximum = VillageConstructionRules.FARMER_MAX_SHARED_FOOD;
        } else if (profession == LegacyVillagerProfession.FISHERMAN
                || VillagerProductionController.produces(profession, stack)
                || profession == LegacyVillagerProfession.CARTOGRAPHER
                && path.contains("map")) {
            reserve = 0;
            maximum = Math.min(4, stack.stackSize);
        } else if (VillageConstructionCapability
                .isSmithProfession(profession)) {
            if (isEquipmentGood(path)) {
                reserve = 0;
                maximum = 1;
            } else {
                reserve = 36;
                maximum = 2;
            }
        } else if (profession == LegacyVillagerProfession.CLERIC
                && path.contains("potion")) {
            // Potion
            reserve = 0;
            maximum = 1;
        } else {
            reserve = 1;
            maximum = 4;
        }
        int available = stack.stackSize - reserve;
        return Math.max(0, Math.min(maximum, available));
    }

    public static int exchangeAmountForTest(LegacyVillagerProfession profession,
                                            ItemStack stack) {
        return exchangeAmount(profession, stack);
    }

    public static boolean isShareableFood(ItemStack stack) {
        return stack != null && !fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)
                && fr.vanillainstincts.compat.Minecraft116Compat.stackIs(stack, VanillaInstinctsTags.VILLAGER_SHAREABLE_FOOD);
    }

    public static int countFood(InventoryBasic inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (isShareableFood(stack)) count += stack.stackSize;
        }
        return count;
    }

    public static boolean canFullyInsert(InventoryBasic inventory,
                                         ItemStack stack) {
        if (inventory == null || stack == null || fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack)) return false;
        int room = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack existing = inventory.getStackInSlot(slot);
            if (fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(existing)) {
                room += stack.getMaxStackSize();
            } else if (fr.vanillainstincts.compat.Minecraft116Compat.isSameItemSameTags(existing, stack)) {
                room += Math.max(0, existing.getMaxStackSize()
                        - existing.stackSize);
            }
            if (room >= stack.stackSize) return true;
        }
        return false;
    }

    private static boolean insertFully(InventoryBasic inventory,
                                       ItemStack stack) {
        if (!canFullyInsert(inventory, stack)) return false;
        return ProfessionStockController.insert(inventory, stack)
                > 0 && fr.vanillainstincts.compat.Minecraft110ItemStackCompat.isEmpty(stack);
    }

    private static boolean partnerBlocked(EntityVillager sender, EntityVillager recipient,
                                          long gameTime) {
        return !partnerReady(sender.getEntityData().getLong(
                partnerKey(recipient.getUniqueID())), gameTime);
    }

    private static boolean hasDailyCapacity(EntityVillager villager,
                                            long dayTime) {
        refreshDailyQuota(villager, dayTime);
        return mayExchangeTodayConfigured(villager.getEntityData()
                .getInteger(SOCIAL_COUNT));
    }

    private static void refreshDailyQuota(EntityVillager villager, long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000L);
        if (villager.getEntityData().getLong(SOCIAL_DAY) != day) {
            villager.getEntityData().setLong(SOCIAL_DAY, day);
            villager.getEntityData().setInteger(SOCIAL_COUNT, 0);
        }
    }

    private static String partnerKey(UUID partner) {
        return PARTNER_PREFIX + partner.toString();
    }

    private static void clearApproach(EntityVillager villager) {
        villager.getEntityData().removeTag(APPROACH_TARGET);
        villager.getEntityData().removeTag(APPROACH_UNTIL);
        villager.setCurrentItemOrArmor(0, null);
    }

    private static Exchange activeFor(WorldServer level, UUID villagerId) {
        for (Exchange exchange : levelExchanges(level).values()) {
            if (!exchange.stolen && (exchange.senderId.equals(villagerId)
                    || exchange.recipientId.equals(villagerId))) {
                return exchange;
            }
        }
        return null;
    }

    private static EntityVillager resolve(WorldServer level, UUID id) {
        if (id == null) return null;
        Entity entity = fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, id);
        return entity instanceof EntityVillager && ((EntityVillager) (entity)).isEntityAlive()
                ? ((EntityVillager) (entity)) : null;
    }

    private static EntityItem resolveItem(WorldServer level, UUID id) {
        Entity entity = id == null ? null : fr.vanillainstincts.compat.Minecraft17Compat.getEntityFromUuid(level, id);
        return entity instanceof EntityItem && ((EntityItem) (entity)).isEntityAlive() ? ((EntityItem) (entity)) : null;
    }

    private static UUID readUuid(EntityItem item, String key) {
        return fr.vanillainstincts.compat.Minecraft18NbtCompat.hasUniqueId(item.getEntityData(), key)
                ? fr.vanillainstincts.compat.Minecraft18NbtCompat.getUniqueId(item.getEntityData(), key) : null;
    }

    private static boolean isEquipmentGood(String path) {
        return containsAny(path, "sword", "axe", "pickaxe", "shovel",
                "hoe", "helmet", "chestplate", "leggings", "boots",
                "shield", "bow", "crossbow", "trident", "mace",
                "fishing_rod", "shears", "flint_and_steel");
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static Map<UUID, Exchange> levelExchanges(WorldServer level) {
        synchronized (EXCHANGES) {
            return EXCHANGES.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private static class Purchase {
        private final EntityVillager seller;
        private final int slot;
        private final int amount;
        private final ItemStack preview;

        public Purchase(EntityVillager seller, int slot, int amount, ItemStack preview) {
            this.seller = seller;
            this.slot = slot;
            this.amount = amount;
            this.preview = preview;
        }

        public EntityVillager seller() { return this.seller; }

        public int slot() { return this.slot; }

        public int amount() { return this.amount; }

        public ItemStack preview() { return this.preview; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Purchase)) return false;
            Purchase that = (Purchase) other;
            return java.util.Objects.equals(this.seller, that.seller) && this.slot == that.slot && this.amount == that.amount && java.util.Objects.equals(this.preview, that.preview);
        }

        @Override
        public int hashCode() { return java.util.Objects.hash(this.seller, this.slot, this.amount, this.preview); }

        @Override
        public String toString() {
            return "Purchase[" + "seller=" + this.seller + ", " + "slot=" + this.slot + ", " + "amount=" + this.amount + ", " + "preview=" + this.preview + "]";
        }

    }

    private enum Stage {
        GIFT,
        PAYMENT
    }

    private static final class Exchange {
        private final UUID exchangeId;
        private final UUID senderId;
        private final UUID recipientId;
        private UUID currentItemId;
        private long expiresAt;
        private final int paymentEmeralds;
        private final ItemStack originalGift;
        private Stage stage = Stage.PAYMENT;
        private boolean stolen;

        private Exchange(UUID exchangeId, UUID senderId, UUID recipientId,
                         UUID currentItemId, long expiresAt,
                         int paymentEmeralds, ItemStack originalGift) {
            this.exchangeId = exchangeId;
            this.senderId = senderId;
            this.recipientId = recipientId;
            this.currentItemId = currentItemId;
            this.expiresAt = expiresAt;
            this.paymentEmeralds = paymentEmeralds;
            this.originalGift = originalGift;
        }
    }
}
