package fr.vanillainstincts.core.policy;

import fr.vanillainstincts.core.model.EndermanCargoRole;
import fr.vanillainstincts.core.rules.EndermanRules;

/** Pure timing policy for Enderman cargo roles. */
public final class EndermanCargoPolicy {
    private EndermanCargoPolicy() {
    }

    public static int carryDuration(EndermanCargoRole role) {
        if (role == null) return 1;
        return switch (role) {
            case PLAYER_RELOCATION ->
                    EndermanRules.ENDERMAN_PLAYER_CARRY_TICKS;
            case CREEPER_DELIVERY ->
                    EndermanRules.ENDERMAN_CREEPER_CARRY_TICKS;
            case ARCHER_PLATFORM ->
                    EndermanRules.ENDERMAN_ARCHER_CARRY_TICKS;
            case MONSTER_DELIVERY ->
                    EndermanRules.ENDERMAN_GENERIC_CARRY_TICKS;
            case CREATURE_GIFT, ITEM_PRESENTATION ->
                    EndermanRules.ENDERMAN_CREATURE_GIFT_CARRY_TICKS;
            case ALLY_RESCUE -> EndermanRules.ENDERMAN_RESCUE_CARRY_TICKS;
            case NEUTRAL_PLAYER_RIDE ->
                    EndermanRules.ENDERMAN_NEUTRAL_PLAYER_MIN_CARRY_TICKS
                            + 200;
            case SPIDER_PLATFORM ->
                    EndermanRules.ENDERMAN_SPIDER_CARRY_TICKS;
            default -> 1;
        };
    }
}
