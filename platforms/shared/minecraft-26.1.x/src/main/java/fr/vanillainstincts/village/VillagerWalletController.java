package fr.vanillainstincts.village;

import fr.vanillainstincts.core.economy.WalletPolicy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;

/** Persistent emerald balance used only for villager social transactions. */
public final class VillagerWalletController {
    private static final String INITIALIZED =
            "vanillainstincts_wallet_initialized";
    private static final String BALANCE = "vanillainstincts_wallet_emeralds";

    private VillagerWalletController() {
    }

    public static int balance(Villager villager) {
        if (villager == null) return 0;
        initialize(villager);
        int value = Math.max(0, Math.min(WalletPolicy.MAX_BALANCE,
                fr.vanillainstincts.persistence.NbtCompat.getInt(villager.getPersistentData(), BALANCE)));
        if (value != fr.vanillainstincts.persistence.NbtCompat.getInt(villager.getPersistentData(), BALANCE)) {
            villager.getPersistentData().putInt(BALANCE, value);
        }
        return value;
    }

    public static boolean canAfford(Villager villager, int emeralds) {
        return WalletPolicy.canAfford(balance(villager), emeralds);
    }

    public static boolean debit(Villager villager, int emeralds) {
        int current = balance(villager);
        if (!WalletPolicy.canAfford(current, emeralds)) return false;
        villager.getPersistentData().putInt(BALANCE,
                WalletPolicy.debit(current, emeralds));
        return true;
    }

    public static void credit(Villager villager, int emeralds) {
        if (villager == null || emeralds <= 0) return;
        villager.getPersistentData().putInt(BALANCE,
                WalletPolicy.credit(balance(villager), emeralds));
    }

    public static boolean transfer(Villager payer, Villager recipient,
                                   int emeralds) {
        if (payer == null || recipient == null || emeralds <= 0) return false;
        if (!debit(payer, emeralds)) return false;
        credit(recipient, emeralds);
        return true;
    }

    public static void reverse(Villager recipient, Villager payer,
                               int emeralds) {
        if (recipient == null || payer == null || emeralds <= 0) return;
        int returned = Math.min(balance(recipient), emeralds);
        if (returned <= 0) return;
        debit(recipient, returned);
        credit(payer, returned);
    }

    public static void recordPlayerTrade(AbstractVillager merchant,
                                         MerchantOffer offer) {
        if (!(merchant instanceof Villager villager) || offer == null) return;
        if (!offer.getCostA().is(net.minecraft.world.item.Items.EMERALD)) {
            return;
        }
        credit(villager, offer.getCostA().getCount());
    }

    public static void setBalanceForTest(Villager villager, int balance) {
        if (villager == null) return;
        villager.getPersistentData().putBoolean(INITIALIZED, true);
        villager.getPersistentData().putInt(BALANCE,
                Math.max(0, Math.min(WalletPolicy.MAX_BALANCE, balance)));
    }

    private static void initialize(Villager villager) {
        if (fr.vanillainstincts.persistence.NbtCompat.getBoolean(villager.getPersistentData(), INITIALIZED)) return;
        int level = villager.getVillagerData().level();
        int initial = WalletPolicy.initialBalance(level,
                villager.getUUID().hashCode());
        villager.getPersistentData().putBoolean(INITIALIZED, true);
        villager.getPersistentData().putInt(BALANCE, initial);
    }
}
