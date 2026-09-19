package fr.vanillainstincts.village;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
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

    private static final Map<ServerLevel, Map<UUID, Exchange>> EXCHANGES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private VillagerFoodExchangeController() {
    }

    public static boolean contribute(Villager actor, MobDecisionPlan plan,
                                     ServerLevel level, long gameTime) {
        if (actor == null || actor.isBaby() || actor.isTrading()
                || activeFor(level, actor.getUUID()) != null) {
            return false;
        }
        VillagerSchedulePhase phase = VillagerRoutineController.phaseFor(
                level.getDayTime());
        if (phase != VillagerSchedulePhase.MIDDAY_BREAK
                && phase != VillagerSchedulePhase.SOCIAL) {
            return false;
        }
        refreshDailyQuota(actor, level.getDayTime());
        if (!mayExchangeTodayConfigured(actor.getPersistentData()
                .getInt(SOCIAL_COUNT))
                || gameTime < actor.getPersistentData()
                .getLong(SOCIAL_READY_AT)) {
            return false;
        }

        Purchase purchase = findProfessionalSeller(level, actor, gameTime);
        if (purchase != null && shouldSeekPurchase(actor,
                level.getDayTime())) {
            return contributePurchase(actor, purchase, plan, level, gameTime);
        }
        return contributeSale(actor, plan, level, gameTime);
    }

    private static boolean contributeSale(Villager merchant,
                                           MobDecisionPlan plan,
                                           ServerLevel level,
                                           long gameTime) {
        int slot = findExchangeSlot(merchant);
        if (slot < 0) return false;
        ItemStack source = merchant.getInventory().getItem(slot);
        int amount = exchangeAmount(merchant.getVillagerData().getProfession(),
                source);
        if (amount <= 0) return false;
        ItemStack preview = source.copyWithCount(amount);
        Villager recipient = findRecipient(level, merchant, preview, gameTime);
        if (recipient == null) return false;

        if (merchant.distanceToSqr(recipient)
                > VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
            UUID recipientId = recipient.getUUID();
            ItemStack acceptedPreview = preview.copy();
            plan.offerNavigation(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE,
                    recipient.position(),
                    VillageConstructionRules.FARMER_EXCHANGE_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        rememberApproach(merchant, recipientId, gameTime);
                        merchant.getBrain().eraseMemory(
                                MemoryModuleType.WALK_TARGET);
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

    private static boolean contributePurchase(Villager buyer,
                                               Purchase purchase,
                                               MobDecisionPlan plan,
                                               ServerLevel level,
                                               long gameTime) {
        Villager seller = purchase.seller();
        if (buyer.distanceToSqr(seller)
                > VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
            UUID sellerId = seller.getUUID();
            plan.offerNavigation(VanillaInstinctsState.VILLAGE_FOOD_EXCHANGE,
                    ActionOwner.VILLAGER_SOCIAL,
                    VillageConstructionRules.PRIORITY_SOCIAL_EXCHANGE + 1,
                    seller.position(),
                    VillageConstructionRules.FARMER_EXCHANGE_SPEED,
                    WorldRules.STATE_HOLD_FARM_TICKS, () -> {
                        rememberApproach(buyer, sellerId, gameTime);
                        buyer.getBrain().eraseMemory(
                                MemoryModuleType.WALK_TARGET);
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
    public static boolean maintainParticipant(Villager villager,
                                              ServerLevel level,
                                              long gameTime) {
        if (villager == null) return false;
        if (villager.getPersistentData().hasUUID(APPROACH_TARGET)) {
            if (gameTime > villager.getPersistentData()
                    .getLong(APPROACH_UNTIL)) {
                clearApproach(villager);
                return false;
            }
            Villager target = resolve(level, villager.getPersistentData()
                    .getUUID(APPROACH_TARGET));
            if (target == null || target.isTrading()
                    || partnerBlocked(villager, target, gameTime)) {
                clearApproach(villager);
                return false;
            }
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            if (villager.distanceToSqr(target)
                    <= VillageConstructionRules.FARMER_EXCHANGE_REACH_SQR) {
                villager.getNavigation().stop();
            } else if (villager.getNavigation().isDone()
                    || Math.floorMod(gameTime + villager.getId(), 10L) == 0L) {
                villager.getNavigation().moveTo(target,
                        VillageConstructionRules.FARMER_EXCHANGE_SPEED);
            }
            return true;
        }
        if (shouldSuppressJobReturn(villager, gameTime)) {
            if (Math.floorMod(gameTime + villager.getId(), 20L) == 0L) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            }
            return true;
        }
        return false;
    }

    public static boolean shouldSuppressJobReturn(Villager villager,
                                                   long gameTime) {
        return villager != null && gameTime < villager.getPersistentData()
                .getLong(SOCIAL_ROAM_UNTIL);
    }

    public static void tickLevel(ServerLevel level, long gameTime) {
        Map<UUID, Exchange> exchanges = levelExchanges(level);
        List<UUID> finished = new ArrayList<>();
        for (Exchange exchange : List.copyOf(exchanges.values())) {
            Villager sender = resolve(level, exchange.senderId);
            Villager recipient = resolve(level, exchange.recipientId);
            if (sender == null || recipient == null
                    || gameTime > exchange.expiresAt) {
                refund(level, exchange, sender);
                finished.add(exchange.exchangeId);
                continue;
            }

            if (exchange.stage == Stage.PAYMENT) {
                ItemEntity payment = resolveItem(level, exchange.currentItemId);
                if (payment == null) {
                    VillagerWalletController.credit(recipient,
                            exchange.paymentEmeralds);
                    refundReservedGift(sender, exchange.originalGift);
                    finishInterrupted(sender, recipient, gameTime);
                    finished.add(exchange.exchangeId);
                    continue;
                }
                ServerPlayer thief = nearestInterceptor(level, payment);
                if (thief != null) {
                    // Vol
                    steal(level, thief, payment, recipient, gameTime);
                    refundReservedGift(sender, exchange.originalGift);
                    exchange.stolen = true;
                    finishInterrupted(sender, recipient, gameTime);
                    finished.add(exchange.exchangeId);
                    continue;
                }
                if (sender.distanceToSqr(payment)
                        <= VillageConstructionRules
                        .SOCIAL_EXCHANGE_PICKUP_DISTANCE_SQR) {
                    VillagerWalletController.credit(sender,
                            exchange.paymentEmeralds);
                    payment.discard();
                    ItemEntity gift = spawnReservedItem(level, sender,
                            recipient, exchange.originalGift.copy(), false);
                    exchange.currentItemId = gift.getUUID();
                    exchange.stage = Stage.GIFT;
                    sender.setItemInHand(InteractionHand.MAIN_HAND,
                            exchange.originalGift.copy());
                    recipient.setItemInHand(InteractionHand.MAIN_HAND,
                            ItemStack.EMPTY);
                    sender.swing(InteractionHand.MAIN_HAND);
                    exchange.expiresAt = gameTime
                            + VillageConstructionRules
                            .FARMER_EXCHANGE_TIMEOUT_TICKS;
                }
                continue;
            }

            ItemEntity gift = resolveItem(level, exchange.currentItemId);
            if (gift == null) {
                VillagerWalletController.reverse(sender, recipient,
                        exchange.paymentEmeralds);
                refundReservedGift(sender, exchange.originalGift);
                finishInterrupted(sender, recipient, gameTime);
                finished.add(exchange.exchangeId);
                continue;
            }
            ServerPlayer thief = nearestInterceptor(level, gift);
            if (thief != null) {
                // Vol
                steal(level, thief, gift, recipient, gameTime);
                exchange.stolen = true;
                finishInterrupted(sender, recipient, gameTime);
                finished.add(exchange.exchangeId);
                continue;
            }
            if (recipient.distanceToSqr(gift)
                    <= VillageConstructionRules.SOCIAL_EXCHANGE_PICKUP_DISTANCE_SQR) {
                ItemStack delivered = gift.getItem().copy();
                ItemStack transfer = delivered.copy();
                if (!insertFully(recipient.getInventory(), transfer)) {
                    continue;
                }
                recipient.getInventory().setChanged();
                gift.discard();
                clearVisual(sender, recipient);
                recordDelivered(sender, recipient, delivered,
                        level.getDayTime(), gameTime);
                finishPayment(sender, gameTime);
                finished.add(exchange.exchangeId);
            }
        }
        finished.forEach(exchanges::remove);
    }

    public static void onPlayerPickup(ServerLevel level, ServerPlayer player,
                                      ItemEntity item, long gameTime) {
        if (level == null || player == null || item == null
                || (!item.getPersistentData().getBoolean(EXCHANGE_GIFT)
                && !item.getPersistentData().getBoolean(EXCHANGE_PAYMENT))) {
            return;
        }
        UUID senderId = readUuid(item, EXCHANGE_SENDER);
        Villager sender = resolve(level, senderId);
        if (sender != null) reportTheft(sender, player, level, gameTime);
    }

    public static boolean isVillageOrigin(ItemEntity item) {
        return item != null && (item.getPersistentData()
                .getBoolean(VILLAGE_ORIGIN)
                || item.getOwner() instanceof AbstractVillager);
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
        if (stack == null || stack.isEmpty()) return 1;
        int resale = RecoveredTradeController.emeraldPrice(null, stack);
        return paymentEmeralds(resale);
    }

    public static int paymentFor(ServerLevel level, Villager seller,
                                 ItemStack stack) {
        int base = paymentFor(stack);
        if (level == null || seller == null || stack == null
                || stack.isEmpty()) {
            return base;
        }
        int marketPrice = VillageMarketController.quote(level,
                seller.blockPosition(), stack, base * 3);
        return paymentEmeralds(marketPrice);
    }

    public static int orphanedExchangeCount(ServerLevel level) {
        if (level == null) return 0;
        int count = 0;
        for (Exchange exchange : List.copyOf(levelExchanges(level).values())) {
            if (resolve(level, exchange.senderId) == null
                    || resolve(level, exchange.recipientId) == null) {
                count++;
            }
        }
        return count;
    }

    public static void clearLevel(ServerLevel level) {
        EXCHANGES.remove(level);
    }

    /** Recovers an exchange item after a world reload. */
    public static void tickLoadedExchangeItem(ItemEntity item,
                                              ServerLevel level,
                                              long gameTime) {
        if (item == null || !item.isAlive()
                || !isExchangeItem(item)
                || gameTime < item.getPersistentData()
                .getLong(EXCHANGE_RECOVERY_AT)
                || belongsToActiveExchange(level, item.getUUID())) {
            return;
        }
        boolean payment = item.getPersistentData()
                .getBoolean(EXCHANGE_PAYMENT);
        UUID senderId = readUuid(item, EXCHANGE_SENDER);
        UUID recipientId = readUuid(item, EXCHANGE_RECIPIENT);
        Villager sender = resolve(level, senderId);
        Villager recipient = resolve(level, recipientId);
        if (payment) {
            Villager payer = sender;
            Villager seller = recipient;
            if (payer == null || seller == null) {
                postponeRecovery(item, gameTime);
                return;
            }
            VillagerWalletController.credit(payer,
                    item.getItem().getCount());
            restoreHeldGift(seller);
            clearVisual(seller, payer);
            finishInterrupted(seller, payer, gameTime);
            item.discard();
            return;
        }
        recoverGift(item, sender, recipient, level, gameTime);
    }

    private static void begin(ServerLevel level, Villager sender,
                              Villager recipient, int slot, long gameTime,
                              int requestedAmount, int fixedEmeralds) {
        ItemStack source = sender.getInventory().getItem(slot);
        int allowedAmount = requestedAmount > 0
                ? professionalSaleAmount(sender.getVillagerData()
                .getProfession(), source)
                : exchangeAmount(sender.getVillagerData().getProfession(),
                source);
        int amount = requestedAmount > 0
                ? Math.min(requestedAmount, allowedAmount) : allowedAmount;
        if (amount <= 0) {
            clearVisual(sender, recipient);
            return;
        }
        ItemStack giftStack = source.copyWithCount(amount);
        if (hasAvailableMerchantOffer(sender, giftStack)) {
            clearVisual(sender, recipient);
            return;
        }
        if (!canFullyInsert(recipient.getInventory(), giftStack)) {
            clearVisual(sender, recipient);
            return;
        }
        int emeralds = fixedEmeralds > 0 ? fixedEmeralds
                : paymentFor(level, sender, giftStack);
        if (!VillagerWalletController.debit(recipient, emeralds)) {
            clearVisual(sender, recipient);
            return;
        }
        source.shrink(amount);
        sender.getInventory().setChanged();
        sender.setItemInHand(InteractionHand.MAIN_HAND, giftStack.copy());
        recipient.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.EMERALD, emeralds));
        sender.getLookControl().setLookAt(recipient, 30.0F, 30.0F);
        recipient.getLookControl().setLookAt(sender, 30.0F, 30.0F);
        recipient.swing(InteractionHand.MAIN_HAND);
        UUID paymentId = spawnPayment(level, recipient, sender, emeralds);
        UUID exchangeId = UUID.randomUUID();
        levelExchanges(level).put(exchangeId, new Exchange(exchangeId,
                sender.getUUID(), recipient.getUUID(), paymentId,
                gameTime + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS,
                emeralds, giftStack.copy()));
    }

    private static ItemEntity spawnReservedItem(ServerLevel level,
                                                Villager sender,
                                                Villager recipient,
                                                ItemStack stack,
                                                boolean payment) {
        ItemEntity item = new ItemEntity(level, sender.getX(),
                sender.getY() + 0.7D, sender.getZ(), stack);
        item.setPickUpDelay(32_767);
        item.setThrower(sender.getUUID());
        item.getPersistentData().putBoolean(VILLAGE_ORIGIN, true);
        item.getPersistentData().putBoolean(payment
                ? EXCHANGE_PAYMENT : EXCHANGE_GIFT, true);
        item.getPersistentData().putUUID(EXCHANGE_SENDER, sender.getUUID());
        item.getPersistentData().putUUID(EXCHANGE_RECIPIENT,
                recipient.getUUID());
        item.getPersistentData().putLong(EXCHANGE_RECOVERY_AT,
                level.getGameTime() + 40L);
        item.getPersistentData().putLong(EXCHANGE_EXPIRES_AT,
                level.getGameTime()
                        + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS);
        item.setDeltaMovement(recipient.position().subtract(sender.position())
                .normalize().scale(0.22D).add(0.0D, 0.20D, 0.0D));
        level.addFreshEntity(item);
        return item;
    }

    private static UUID spawnPayment(ServerLevel level, Villager payer,
                                     Villager seller, int emeralds) {
        ItemEntity payment = spawnReservedItem(level, payer, seller,
                new ItemStack(Items.EMERALD, emeralds), true);
        return payment.getUUID();
    }

    private static void recordDelivered(Villager sender,
                                        Villager recipient,
                                        ItemStack delivered,
                                        long dayTime,
                                        long gameTime) {
        if (sender.level instanceof ServerLevel level) {
            VillageMarketController.recordDemand(level,
                    recipient.blockPosition(), delivered);
            VillagerProductionController.recordSocialSale(sender, level,
                    delivered, gameTime);
        }
        refreshDailyQuota(sender, dayTime);
        refreshDailyQuota(recipient, dayTime);
        sender.getPersistentData().putInt(SOCIAL_COUNT,
                sender.getPersistentData().getInt(SOCIAL_COUNT) + 1);
        recipient.getPersistentData().putInt(SOCIAL_COUNT,
                recipient.getPersistentData().getInt(SOCIAL_COUNT) + 1);
        sender.getPersistentData().putLong(partnerKey(recipient.getUUID()),
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_PARTNER_COOLDOWN_TICKS);
        recipient.getPersistentData().putLong(partnerKey(sender.getUUID()),
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_PARTNER_COOLDOWN_TICKS);
        recipient.getPersistentData().putLong(SOCIAL_READY_AT,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        recipient.getPersistentData().putLong(SOCIAL_ROAM_UNTIL,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_RECIPIENT_ROAM_TICKS);
        clearApproach(sender);
        clearApproach(recipient);
    }

    private static void finishPayment(Villager sender, long gameTime) {
        sender.getPersistentData().putLong(SOCIAL_READY_AT,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        sender.getPersistentData().putLong(SOCIAL_ROAM_UNTIL,
                gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_ROAM_TICKS);
        clearApproach(sender);
    }

    private static void refund(ServerLevel level, Exchange exchange,
                               Villager sender) {
        ItemEntity item = resolveItem(level, exchange.currentItemId);
        if (item != null) item.discard();
        Villager recipient = resolve(level, exchange.recipientId);
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
            finishPayment(sender, sender.level.getGameTime());
        }
    }

    private static void refundReservedGift(Villager sender, ItemStack gift) {
        if (sender == null || gift == null || gift.isEmpty()) return;
        ItemStack refund = gift.copy();
        ProfessionStockController.insert(sender.getInventory(), refund);
        if (!refund.isEmpty()) sender.spawnAtLocation(refund);
        sender.getInventory().setChanged();
    }

    private static void finishInterrupted(Villager sender, Villager recipient,
                                          long gameTime) {
        clearVisual(sender, recipient);
        if (sender != null) finishPayment(sender, gameTime);
        if (recipient != null) {
            recipient.getPersistentData().putLong(SOCIAL_READY_AT,
                    gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
            recipient.getPersistentData().putLong(SOCIAL_ROAM_UNTIL,
                    gameTime + VillageConstructionRules.SOCIAL_EXCHANGE_RECIPIENT_ROAM_TICKS);
            clearApproach(recipient);
        }
    }

    private static void rememberApproach(Villager villager, UUID targetId,
                                         long gameTime) {
        villager.getPersistentData().putUUID(APPROACH_TARGET, targetId);
        villager.getPersistentData().putLong(APPROACH_UNTIL,
                gameTime
                        + VillageConstructionRules.FARMER_EXCHANGE_TIMEOUT_TICKS);
    }

    private static void showApproach(Villager sender, Villager recipient,
                                     ItemStack gift) {
        sender.setItemInHand(InteractionHand.MAIN_HAND, gift.copy());
        sender.getLookControl().setLookAt(recipient, 30.0F, 30.0F);
        recipient.getLookControl().setLookAt(sender, 30.0F, 30.0F);
    }

    private static void showPurchaseApproach(Villager buyer,
                                             Villager seller) {
        buyer.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(Items.EMERALD));
        buyer.getLookControl().setLookAt(seller, 30.0F, 30.0F);
        seller.getLookControl().setLookAt(buyer, 30.0F, 30.0F);
    }

    private static void clearVisual(Villager sender, Villager recipient) {
        if (sender != null) {
            sender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        if (recipient != null) {
            recipient.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
    }


    private static boolean isExchangeItem(ItemEntity item) {
        return item.getPersistentData().getBoolean(EXCHANGE_PAYMENT)
                || item.getPersistentData().getBoolean(EXCHANGE_GIFT);
    }

    private static boolean belongsToActiveExchange(ServerLevel level,
                                                    UUID itemId) {
        for (Exchange exchange : levelExchanges(level).values()) {
            if (!exchange.stolen && exchange.currentItemId.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    private static void postponeRecovery(ItemEntity item, long gameTime) {
        long expiresAt = item.getPersistentData().getLong(EXCHANGE_EXPIRES_AT);
        if (expiresAt > 0L && gameTime > expiresAt) {
            unlockOrphan(item);
        } else {
            item.getPersistentData().putLong(EXCHANGE_RECOVERY_AT,
                    gameTime + 40L);
        }
    }

    private static void restoreHeldGift(Villager sender) {
        ItemStack held = sender.getMainHandItem();
        if (held.isEmpty() || held.is(Items.EMERALD)) return;
        ItemStack refund = held.copy();
        ProfessionStockController.insert(sender.getInventory(), refund);
        if (!refund.isEmpty()) {
            sender.spawnAtLocation(refund);
        }
        sender.getInventory().setChanged();
        sender.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void recoverGift(ItemEntity item, Villager sender,
                                    Villager recipient, ServerLevel level,
                                    long gameTime) {
        if (recipient == null) {
            postponeRecovery(item, gameTime);
            return;
        }
        ItemStack delivered = item.getItem().copy();
        ItemStack remaining = delivered.copy();
        ProfessionStockController.insert(recipient.getInventory(), remaining);
        recipient.getInventory().setChanged();
        if (remaining.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remaining);
            unlockOrphan(item);
        }
        if (sender != null) {
            clearVisual(sender, recipient);
            recordDelivered(sender, recipient, delivered,
                    level.getDayTime(), gameTime);
            finishPayment(sender, gameTime);
        } else {
            recipient.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            recipient.getPersistentData().putLong(SOCIAL_READY_AT,
                    gameTime
                            + VillageConstructionRules.SOCIAL_EXCHANGE_NEXT_DELAY_TICKS);
        }
    }

    private static void unlockOrphan(ItemEntity item) {
        item.setPickUpDelay(0);
        item.getPersistentData().remove(EXCHANGE_PAYMENT);
        item.getPersistentData().remove(EXCHANGE_GIFT);
        item.getPersistentData().remove(EXCHANGE_SENDER);
        item.getPersistentData().remove(EXCHANGE_RECIPIENT);
        item.getPersistentData().remove(EXCHANGE_RECOVERY_AT);
        item.getPersistentData().remove(EXCHANGE_EXPIRES_AT);
    }

    private static ServerPlayer nearestInterceptor(ServerLevel level,
                                                   ItemEntity item) {
        return level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()
                        && player.distanceToSqr(item)
                        <= VillageConstructionRules.SOCIAL_EXCHANGE_STEAL_DISTANCE_SQR)
                .min(Comparator.comparingDouble(player ->
                        player.distanceToSqr(item)))
                .orElse(null);
    }

    private static void steal(ServerLevel level, ServerPlayer player,
                              ItemEntity item, Villager victim,
                              long gameTime) {
        ItemStack stolen = item.getItem().copy();
        if (!player.addItem(stolen) && !stolen.isEmpty()) {
            player.drop(stolen, false);
        }
        item.discard();
        if (victim != null) reportTheft(victim, player, level, gameTime);
    }

    private static void reportTheft(Villager victim, ServerPlayer player,
                                    ServerLevel level, long gameTime) {
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

    private static Purchase findProfessionalSeller(ServerLevel level,
                                                   Villager buyer,
                                                   long gameTime) {
        if (!VillagerWalletController.canAfford(buyer, 1)) return null;
        List<Purchase> candidates = new ArrayList<>();
        for (Villager seller : level.getEntitiesOfClass(Villager.class,
                buyer.getBoundingBox().inflate(
                        VillageConstructionRules.FARMER_EXCHANGE_RADIUS))) {
            if (seller == buyer || !seller.isAlive() || seller.isBaby()
                    || seller.isTrading()
                    || activeFor(level, seller.getUUID()) != null
                    || !hasDailyCapacity(seller, level.getDayTime())
                    || partnerBlocked(buyer, seller, gameTime)) {
                continue;
            }
            VillagerProfession profession = seller.getVillagerData()
                    .getProfession();
            if (profession == VillagerProfession.NONE
                    || profession == VillagerProfession.NITWIT) {
                continue;
            }
            int slot = findProfessionalSaleSlot(seller);
            if (slot < 0) continue;
            ItemStack source = seller.getInventory().getItem(slot);
            int amount = professionalSaleAmount(profession, source);
            if (amount <= 0) continue;
            ItemStack preview = source.copyWithCount(amount);
            if (!canFullyInsert(buyer.getInventory(), preview)) continue;
            candidates.add(new Purchase(seller, slot, amount, preview));
        }
        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator.comparing(purchase ->
                purchase.seller().getUUID()));
        int seed = buyer.getUUID().hashCode()
                + buyer.getPersistentData().getInt(SOCIAL_COUNT)
                + Long.hashCode(Math.floorDiv(level.getDayTime(), 24_000L));
        return candidates.get(Math.floorMod(seed, candidates.size()));
    }

    private static boolean shouldSeekPurchase(Villager villager,
                                              long dayTime) {
        int seed = villager.getUUID().hashCode()
                + villager.getPersistentData().getInt(SOCIAL_COUNT)
                + Long.hashCode(Math.floorDiv(dayTime, 24_000L));
        return shouldSeekPurchase(seed);
    }

    public static boolean shouldSeekPurchase(int deterministicSeed) {
        return Math.floorMod(deterministicSeed, 3) != 0;
    }

    /** Selects the most profession-specific product currently in stock. */
    public static int findProfessionalSaleSlot(Villager seller) {
        if (seller == null) return -1;
        VillagerProfession profession = seller.getVillagerData()
                .getProfession();
        SimpleContainer inventory = seller.getInventory();
        int bestSlot = -1;
        int bestPriority = Integer.MAX_VALUE;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
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
    public static int professionalSaleAmount(VillagerProfession profession,
                                             ItemStack stack) {
        int available = exchangeAmount(profession, stack);
        if (available <= 0) return 0;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
        int target = 1;
        if (profession == VillagerProfession.FARMER) {
            target = stack.is(Items.MILK_BUCKET) ? 1
                    : stack.is(Items.BREAD) ? 3 : 4;
        } else if (profession == VillagerProfession.FLETCHER
                && path.contains("arrow")) {
            target = 4;
        } else if (profession == VillagerProfession.SHEPHERD
                || profession == VillagerProfession.MASON
                || stack.is(Items.PAPER)) {
            target = 4;
        } else if (profession == VillagerProfession.FISHERMAN
                || profession == VillagerProfession.BUTCHER
                || profession == VillagerProfession.LEATHERWORKER) {
            target = 2;
        } else if (VillageConstructionCapability
                .isSmithProfession(profession) && stack.is(Items.IRON_INGOT)) {
            target = 2;
        }
        return Math.min(available, target);
    }

    private static Villager findRecipient(ServerLevel level, Villager sender,
                                          ItemStack gift, long gameTime) {
        int payment = paymentFor(level, sender, gift);
        return level.getEntitiesOfClass(Villager.class,
                        sender.getBoundingBox().inflate(
                                VillageConstructionRules.FARMER_EXCHANGE_RADIUS),
                        other -> other != sender && other.isAlive()
                                && !other.isBaby() && !other.isTrading()
                                && activeFor(level, other.getUUID()) == null
                                && hasDailyCapacity(other, level.getDayTime())
                                && !partnerBlocked(sender, other, gameTime)
                                && VillagerWalletController.canAfford(other,
                                payment)
                                && canFullyInsert(other.getInventory(), gift)
                                && professionWants(other.getVillagerData()
                                .getProfession(), gift))
                .stream()
                .min(Comparator.comparingLong((Villager other) ->
                                sender.getPersistentData().getLong(
                                        partnerKey(other.getUUID())))
                        .thenComparingDouble(sender::distanceToSqr))
                .orElse(null);
    }

    public static int findExchangeSlot(Villager villager) {
        if (villager == null) return -1;
        SimpleContainer inventory = villager.getInventory();
        VillagerProfession profession = villager.getVillagerData()
                .getProfession();
        int size = inventory.getContainerSize();
        if (size <= 0) return -1;
        int start = Math.floorMod(villager.getId()
                + villager.getPersistentData().getInt(SOCIAL_COUNT), size);
        for (int offset = 0; offset < size; offset++) {
            int slot = (start + offset) % size;
            ItemStack stack = inventory.getItem(slot);
            if (hasAvailableMerchantOffer(villager, stack)) continue;
            if (isProfessionExchangeGood(profession, stack)
                    && exchangeAmount(profession, stack) > 0) {
                return slot;
            }
        }
        return -1;
    }

    public static int findShareableFoodSlot(SimpleContainer inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isShareableFood(stack)
                    && stack.getCount()
                    > VillageConstructionRules.FARMER_MIN_SHAREABLE_FOOD) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean isProfessionExchangeGood(
            VillagerProfession profession, ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
        if (VillagerProductionController.produces(profession, stack)) {
            return true;
        }
        if (profession == VillagerProfession.FARMER) {
            return isShareableFood(stack) || stack.is(Items.MILK_BUCKET);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return stack.is(Items.COD) || stack.is(Items.SALMON)
                    || stack.is(Items.COOKED_COD)
                    || stack.is(Items.COOKED_SALMON);
        }
        if (profession == VillagerProfession.BUTCHER) {
            return path.contains("beef") || path.contains("porkchop")
                    || path.contains("mutton") || path.contains("chicken")
                    || path.contains("rabbit");
        }
        if (profession == VillagerProfession.FLETCHER) {
            return path.contains("arrow") || stack.is(Items.FLINT)
                    || stack.is(Items.FEATHER);
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return path.contains("wool") || path.contains("dye");
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return stack.is(Items.BOOK) || stack.is(Items.PAPER)
                    || stack.is(Items.WRITABLE_BOOK);
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return stack.is(Items.PAPER) || path.contains("map")
                    || stack.is(Items.COMPASS);
        }
        if (profession == VillagerProfession.CLERIC) {
            return path.contains("potion") || stack.is(Items.REDSTONE)
                    || stack.is(Items.GLOWSTONE_DUST)
                    || stack.is(Items.QUARTZ)
                    || stack.is(Items.MAGMA_CREAM)
                    || stack.is(Items.GHAST_TEAR);
        }
        if (profession == VillagerProfession.MASON) {
            return containsAny(path, "brick", "stone", "terracotta", "clay")
                    || stack.is(Items.OBSIDIAN);
        }
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            return (stack.is(Items.IRON_INGOT)
                    && stack.getCount() > 40) || isEquipmentGood(path)
                    || stack.is(Items.BUCKET)
                    || stack.is(Items.FLINT_AND_STEEL);
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return stack.is(Items.LEATHER);
        }
        return false;
    }

    /** Prevents social resale of any result already listed for players. */
    public static boolean hasAvailableMerchantOffer(
            AbstractVillager merchant, ItemStack stack) {
        if (merchant == null || stack == null || stack.isEmpty()) return false;
        for (MerchantOffer offer : merchant.getOffers()) {
            if (ItemStack.isSameItemSameTags(offer.getResult(), stack)) {
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
    public static boolean professionWants(VillagerProfession profession,
                                          ItemStack stack) {
        if (profession == null || stack == null || stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
        if (ProfessionRecipeCatalog.isIngredient(profession, stack)) {
            return true;
        }
        if (isShareableFood(stack) || path.contains("potion")) return true;
        // Équipement
        if (isEquipmentGood(path)) return true;
        if (profession == VillagerProfession.FARMER) {
            return containsAny(path, "seed", "hoe", "bone_meal", "compost")
                    || stack.is(Items.BUCKET) || stack.is(Items.LEAD);
        }
        if (profession == VillagerProfession.FISHERMAN) {
            return containsAny(path, "string", "boat", "barrel", "cod",
                    "salmon");
        }
        if (profession == VillagerProfession.BUTCHER) {
            return stack.is(Items.WHEAT) || stack.is(Items.CARROT)
                    || stack.is(Items.POTATO) || stack.is(Items.BEETROOT)
                    || path.contains("coal");
        }
        if (profession == VillagerProfession.FLETCHER) {
            return stack.is(Items.FLINT) || stack.is(Items.FEATHER)
                    || stack.is(Items.STICK) || stack.is(Items.STRING)
                    || path.contains("arrow");
        }
        if (profession == VillagerProfession.SHEPHERD) {
            return containsAny(path, "wool", "dye", "shears", "wheat");
        }
        if (profession == VillagerProfession.LIBRARIAN) {
            return stack.is(Items.PAPER) || stack.is(Items.BOOK)
                    || stack.is(Items.WRITABLE_BOOK)
                    || path.contains("ink_sac");
        }
        if (profession == VillagerProfession.CARTOGRAPHER) {
            return stack.is(Items.PAPER) || stack.is(Items.COMPASS)
                    || path.contains("map");
        }
        if (profession == VillagerProfession.CLERIC) {
            return ClericBrewingController.isWorkStock(stack)
                    || containsAny(path, "gold", "rotten_flesh")
                    || stack.is(Items.OBSIDIAN)
                    || stack.is(Items.FLINT_AND_STEEL);
        }
        if (profession == VillagerProfession.MASON) {
            return containsAny(path, "stone", "brick", "clay", "terracotta",
                    "quartz", "iron_pickaxe");
        }
        if (VillageConstructionCapability.isSmithProfession(profession)) {
            return containsAny(path, "iron", "coal", "diamond", "tool",
                    "sword", "armor", "helmet", "chestplate", "leggings",
                    "boots");
        }
        if (profession == VillagerProfession.LEATHERWORKER) {
            return stack.is(Items.LEATHER) || path.contains("rabbit_hide")
                    || stack.is(Items.SADDLE);
        }
        return profession == VillagerProfession.NONE
                || profession == VillagerProfession.NITWIT;
    }

    private static int exchangeAmount(VillagerProfession profession,
                                      ItemStack stack) {
        if (!isProfessionExchangeGood(profession, stack)) return 0;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                .toLowerCase(Locale.ROOT);
        int reserve;
        int maximum;
        if (stack.is(Items.MILK_BUCKET) || stack.is(Items.BUCKET)
                || stack.is(Items.FLINT_AND_STEEL)) {
            reserve = 0;
            maximum = 1;
        } else if (profession == VillagerProfession.FARMER) {
            reserve = VillageConstructionRules.FARMER_MIN_SHAREABLE_FOOD;
            maximum = VillageConstructionRules.FARMER_MAX_SHARED_FOOD;
        } else if (profession == VillagerProfession.FISHERMAN
                || VillagerProductionController.produces(profession, stack)
                || profession == VillagerProfession.CARTOGRAPHER
                && path.contains("map")) {
            reserve = 0;
            maximum = Math.min(4, stack.getCount());
        } else if (VillageConstructionCapability
                .isSmithProfession(profession)) {
            if (isEquipmentGood(path)) {
                reserve = 0;
                maximum = 1;
            } else {
                reserve = 36;
                maximum = 2;
            }
        } else if (profession == VillagerProfession.CLERIC
                && path.contains("potion")) {
            // Potion
            reserve = 0;
            maximum = 1;
        } else {
            reserve = 1;
            maximum = 4;
        }
        int available = stack.getCount() - reserve;
        return Math.max(0, Math.min(maximum, available));
    }

    public static int exchangeAmountForTest(VillagerProfession profession,
                                            ItemStack stack) {
        return exchangeAmount(profession, stack);
    }

    public static boolean isShareableFood(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(VanillaInstinctsTags.VILLAGER_SHAREABLE_FOOD);
    }

    public static int countFood(SimpleContainer inventory) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isShareableFood(stack)) count += stack.getCount();
        }
        return count;
    }

    public static boolean canFullyInsert(SimpleContainer inventory,
                                         ItemStack stack) {
        if (inventory == null || stack == null || stack.isEmpty()) return false;
        int room = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                room += Math.max(0, existing.getMaxStackSize()
                        - existing.getCount());
            }
            if (room >= stack.getCount()) return true;
        }
        return false;
    }

    private static boolean insertFully(SimpleContainer inventory,
                                       ItemStack stack) {
        if (!canFullyInsert(inventory, stack)) return false;
        return ProfessionStockController.insert(inventory, stack)
                > 0 && stack.isEmpty();
    }

    private static boolean partnerBlocked(Villager sender, Villager recipient,
                                          long gameTime) {
        return !partnerReady(sender.getPersistentData().getLong(
                partnerKey(recipient.getUUID())), gameTime);
    }

    private static boolean hasDailyCapacity(Villager villager,
                                            long dayTime) {
        refreshDailyQuota(villager, dayTime);
        return mayExchangeTodayConfigured(villager.getPersistentData()
                .getInt(SOCIAL_COUNT));
    }

    private static void refreshDailyQuota(Villager villager, long dayTime) {
        long day = Math.floorDiv(dayTime, 24_000L);
        if (villager.getPersistentData().getLong(SOCIAL_DAY) != day) {
            villager.getPersistentData().putLong(SOCIAL_DAY, day);
            villager.getPersistentData().putInt(SOCIAL_COUNT, 0);
        }
    }

    private static String partnerKey(UUID partner) {
        return PARTNER_PREFIX + partner.toString();
    }

    private static void clearApproach(Villager villager) {
        villager.getPersistentData().remove(APPROACH_TARGET);
        villager.getPersistentData().remove(APPROACH_UNTIL);
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static Exchange activeFor(ServerLevel level, UUID villagerId) {
        for (Exchange exchange : levelExchanges(level).values()) {
            if (!exchange.stolen && (exchange.senderId.equals(villagerId)
                    || exchange.recipientId.equals(villagerId))) {
                return exchange;
            }
        }
        return null;
    }

    private static Villager resolve(ServerLevel level, UUID id) {
        if (id == null) return null;
        Entity entity = level.getEntity(id);
        return entity instanceof Villager villager && villager.isAlive()
                ? villager : null;
    }

    private static ItemEntity resolveItem(ServerLevel level, UUID id) {
        Entity entity = id == null ? null : level.getEntity(id);
        return entity instanceof ItemEntity item && item.isAlive() ? item : null;
    }

    private static UUID readUuid(ItemEntity item, String key) {
        return item.getPersistentData().hasUUID(key)
                ? item.getPersistentData().getUUID(key) : null;
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

    private static Map<UUID, Exchange> levelExchanges(ServerLevel level) {
        synchronized (EXCHANGES) {
            return EXCHANGES.computeIfAbsent(level, ignored -> new HashMap<>());
        }
    }

    private record Purchase(Villager seller, int slot, int amount,
                            ItemStack preview) {
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
