package fr.vanillainstincts.village;

import fr.vanillainstincts.core.economy.WalletPolicy;
import net.minecraft.entity.merchant.villager.AbstractVillagerEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.item.MerchantOffer;

/** Persistent emerald balance used only for villager social transactions. */
public final class VillagerWalletController {
    private static final String INITIALIZED =
            "vanillainstincts_wallet_initialized";
    private static final String BALANCE = "vanillainstincts_wallet_emeralds";

    private VillagerWalletController() {
    }

    public static int balance(VillagerEntity villager) {
        if (villager == null) return 0;
        initialize(villager);
        int value = Math.max(0, Math.min(WalletPolicy.MAX_BALANCE,
                villager.getPersistentData().getInt(BALANCE)));
        if (value != villager.getPersistentData().getInt(BALANCE)) {
            villager.getPersistentData().putInt(BALANCE, value);
        }
        return value;
    }

    public static boolean canAfford(VillagerEntity villager, int emeralds) {
        return WalletPolicy.canAfford(balance(villager), emeralds);
    }

    public static boolean debit(VillagerEntity villager, int emeralds) {
        int current = balance(villager);
        if (!WalletPolicy.canAfford(current, emeralds)) return false;
        villager.getPersistentData().putInt(BALANCE,
                WalletPolicy.debit(current, emeralds));
        return true;
    }

    public static void credit(VillagerEntity villager, int emeralds) {
        if (villager == null || emeralds <= 0) return;
        villager.getPersistentData().putInt(BALANCE,
                WalletPolicy.credit(balance(villager), emeralds));
    }

    public static boolean transfer(VillagerEntity payer, VillagerEntity recipient,
                                   int emeralds) {
        if (payer == null || recipient == null || emeralds <= 0) return false;
        if (!debit(payer, emeralds)) return false;
        credit(recipient, emeralds);
        return true;
    }

    public static void reverse(VillagerEntity recipient, VillagerEntity payer,
                               int emeralds) {
        if (recipient == null || payer == null || emeralds <= 0) return;
        int returned = Math.min(balance(recipient), emeralds);
        if (returned <= 0) return;
        debit(recipient, returned);
        credit(payer, returned);
    }

    public static void recordPlayerTrade(AbstractVillagerEntity merchant,
                                         MerchantOffer offer) {
        if (!(merchant instanceof VillagerEntity) || offer == null) return; VillagerEntity villager = (VillagerEntity) (merchant);
        if (!offer.getCostA().getItem().equals(net.minecraft.item.Items.EMERALD)) {
            return;
        }
        credit(villager, offer.getCostA().getCount());
    }

    public static void setBalanceForTest(VillagerEntity villager, int balance) {
        if (villager == null) return;
        villager.getPersistentData().putBoolean(INITIALIZED, true);
        villager.getPersistentData().putInt(BALANCE,
                Math.max(0, Math.min(WalletPolicy.MAX_BALANCE, balance)));
    }

    private static void initialize(VillagerEntity villager) {
        if (villager.getPersistentData().getBoolean(INITIALIZED)) return;
        int level = villager.getVillagerData().getLevel();
        int initial = WalletPolicy.initialBalance(level,
                villager.getUUID().hashCode());
        villager.getPersistentData().putBoolean(INITIALIZED, true);
        villager.getPersistentData().putInt(BALANCE, initial);
    }
}
