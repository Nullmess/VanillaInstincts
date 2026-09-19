package fr.vanillainstincts.village;

import fr.vanillainstincts.compat.LegacyVillagerProfession;

import fr.vanillainstincts.core.economy.WalletPolicy;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.village.MerchantRecipe;

/** Persistent emerald balance used only for villager social transactions. */
public final class VillagerWalletController {
    private static final String INITIALIZED =
            "vanillainstincts_wallet_initialized";
    private static final String BALANCE = "vanillainstincts_wallet_emeralds";

    private VillagerWalletController() {
    }

    public static int balance(EntityVillager villager) {
        if (villager == null) return 0;
        initialize(villager);
        int value = Math.max(0, Math.min(WalletPolicy.MAX_BALANCE,
                villager.getEntityData().getInteger(BALANCE)));
        if (value != villager.getEntityData().getInteger(BALANCE)) {
            villager.getEntityData().setInteger(BALANCE, value);
        }
        return value;
    }

    public static boolean canAfford(EntityVillager villager, int emeralds) {
        return WalletPolicy.canAfford(balance(villager), emeralds);
    }

    public static boolean debit(EntityVillager villager, int emeralds) {
        int current = balance(villager);
        if (!WalletPolicy.canAfford(current, emeralds)) return false;
        villager.getEntityData().setInteger(BALANCE,
                WalletPolicy.debit(current, emeralds));
        return true;
    }

    public static void credit(EntityVillager villager, int emeralds) {
        if (villager == null || emeralds <= 0) return;
        villager.getEntityData().setInteger(BALANCE,
                WalletPolicy.credit(balance(villager), emeralds));
    }

    public static boolean transfer(EntityVillager payer, EntityVillager recipient,
                                   int emeralds) {
        if (payer == null || recipient == null || emeralds <= 0) return false;
        if (!debit(payer, emeralds)) return false;
        credit(recipient, emeralds);
        return true;
    }

    public static void reverse(EntityVillager recipient, EntityVillager payer,
                               int emeralds) {
        if (recipient == null || payer == null || emeralds <= 0) return;
        int returned = Math.min(balance(recipient), emeralds);
        if (returned <= 0) return;
        debit(recipient, returned);
        credit(payer, returned);
    }

    public static void recordPlayerTrade(EntityVillager merchant,
                                         MerchantRecipe offer) {
        if (!(merchant instanceof EntityVillager) || offer == null) return; EntityVillager villager = (EntityVillager) (merchant);
        if (!offer.getItemToBuy().getItem().equals(net.minecraft.init.Items.EMERALD)) {
            return;
        }
        credit(villager, offer.getItemToBuy().getCount());
    }

    public static void setBalanceForTest(EntityVillager villager, int balance) {
        if (villager == null) return;
        villager.getEntityData().setBoolean(INITIALIZED, true);
        villager.getEntityData().setInteger(BALANCE,
                Math.max(0, Math.min(WalletPolicy.MAX_BALANCE, balance)));
    }

    private static void initialize(EntityVillager villager) {
        if (villager.getEntityData().getBoolean(INITIALIZED)) return;
        int level = LegacyVillagerProfession.level(villager);
        int initial = WalletPolicy.initialBalance(level,
                villager.getUniqueID().hashCode());
        villager.getEntityData().setBoolean(INITIALIZED, true);
        villager.getEntityData().setInteger(BALANCE, initial);
    }
}
