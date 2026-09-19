package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Zoglin;
/**
 * Verrouille les zoglins recrutés par une vache sur l'unique joueur ayant
 * déclenché la mission. Hors phase d'assaut, ils ne peuvent attaquer personne.
 */
public final class MissionZoglinTargetPolicy {
    private MissionZoglinTargetPolicy() {
    }

    public static boolean isMissionZoglin(
            Mob mob, NetherReinforcementState state) {
        return mob instanceof Zoglin
                && state != null
                && state.kind() == NetherReinforcementKind.ZOGLIN
                && state.role() == NetherReinforcementRole.REINFORCEMENT;
    }

    public static boolean canAttackTarget(
            NetherReinforcementState state, Entity candidate,
            long gameTime) {
        if (!(candidate instanceof ServerPlayer player)) {
            return false;
        }
        return canAttackPlayer(state, player.getUUID(), player.isAlive(),
                player.isCreative(), player.isSpectator(), gameTime);
    }

    public static boolean canAttackPlayer(
            NetherReinforcementState state, UUID playerId,
            boolean alive, boolean creative, boolean spectator,
            long gameTime) {
        if (state == null || playerId == null
                || state.kind() != NetherReinforcementKind.ZOGLIN
                || state.role() != NetherReinforcementRole.REINFORCEMENT
                || state.phase() != NetherReinforcementPhase.ASSAULT
                || !state.active(gameTime)
                || !alive || creative || spectator) {
            return false;
        }
        return state.aggressorId().filter(playerId::equals).isPresent();
    }

    /**
     * Corrige immédiatement toute cible vanilla étrangère. La navigation de
     * retour n'est pas interrompue : seul le combat est neutralisé.
     */
    public static boolean enforce(
            Mob mob, NetherReinforcementState state,
            ServerPlayer aggressor, long gameTime) {
        if (!isMissionZoglin(mob, state)) {
            return false;
        }
        if (canAttackTarget(state, aggressor, gameTime)) {
            if (mob.getTarget() != aggressor) {
                mob.setTarget(aggressor);
            }
            mob.setAggressive(true);
        } else {
            mob.setTarget(null);
            mob.setAggressive(false);
        }
        return true;
    }

    /**
     * Barrière finale : même si l'IA vanilla sélectionne une cible entre deux
     * ticks, le zoglin de mission ne peut infliger aucun dégât non autorisé.
     */
    public static boolean shouldBlockAttack(
            Entity victim, Entity attacker) {
        if (!(attacker instanceof Zoglin zoglin)
                || victim == null
                || !NetherReinforcementState.hasPersistentMission(zoglin)) {
            return false;
        }
        NetherReinforcementState state =
                NetherReinforcementState.load(zoglin);
        long gameTime = zoglin.level.getGameTime();
        if (!isMissionZoglin(zoglin, state)) {
            return false;
        }
        return !canAttackTarget(state, victim, gameTime);
    }
}
