package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;

/** Pure timing policy for Enderman cargo roles. */
public final class EndermanCargoPolicy {
    private EndermanCargoPolicy() {
    }

    public static int carryDuration(EndermanCargoRole role) {
        if (role == null) return 1;
        switch (role) {
            case PLAYER_RELOCATION: return EndermanRules.ENDERMAN_PLAYER_CARRY_TICKS;
            case CREEPER_DELIVERY: return EndermanRules.ENDERMAN_CREEPER_CARRY_TICKS;
            case ARCHER_PLATFORM: return EndermanRules.ENDERMAN_ARCHER_CARRY_TICKS;
            case MONSTER_DELIVERY: return EndermanRules.ENDERMAN_GENERIC_CARRY_TICKS;
            case CREATURE_GIFT:
            case ITEM_PRESENTATION: return EndermanRules.ENDERMAN_CREATURE_GIFT_CARRY_TICKS;
            case ALLY_RESCUE: return EndermanRules.ENDERMAN_RESCUE_CARRY_TICKS;
            case NEUTRAL_PLAYER_RIDE: return EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS + 200;
            case SPIDER_PLATFORM: return EndermanRules.ENDERMAN_SPIDER_CARRY_TICKS;
            default: return 1;
        }
    }
}
