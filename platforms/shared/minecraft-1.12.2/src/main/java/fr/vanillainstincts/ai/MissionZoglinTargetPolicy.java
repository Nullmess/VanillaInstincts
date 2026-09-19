package fr.vanillainstincts.ai;

import fr.vanillainstincts.core.model.NetherReinforcementKind;
import fr.vanillainstincts.core.model.NetherReinforcementPhase;
import fr.vanillainstincts.core.model.NetherReinforcementRole;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityPigZombie;
/**
 * Verrouille les zoglins recrutés par une vache sur l'unique joueur ayant
 * déclenché la mission. Hors phase d'assaut, ils ne peuvent attaquer personne.
 */
public final class MissionZoglinTargetPolicy {
    private MissionZoglinTargetPolicy() {
    }

    public static boolean isMissionZoglin(
            EntityLiving mob, NetherReinforcementState state) {
        return mob instanceof EntityPigZombie
                && state != null
                && state.kind() == NetherReinforcementKind.ZOGLIN
                && state.role() == NetherReinforcementRole.REINFORCEMENT;
    }

    public static boolean canAttackTarget(
            NetherReinforcementState state, Entity candidate,
            long gameTime) {
        if (!(candidate instanceof EntityPlayerMP)) {
            return false;
        } EntityPlayerMP player = (EntityPlayerMP) (candidate);
        return canAttackPlayer(state, player.getUniqueID(), player.isEntityAlive(),
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
            EntityLiving mob, NetherReinforcementState state,
            EntityPlayerMP aggressor, long gameTime) {
        if (!isMissionZoglin(mob, state)) {
            return false;
        }
        if (canAttackTarget(state, aggressor, gameTime)) {
            if (mob.getAttackTarget() != aggressor) {
                mob.setAttackTarget(aggressor);
            }
            fr.vanillainstincts.compat.Minecraft112Compat.setAggressive(mob, true);
        } else {
            mob.setAttackTarget(null);
            fr.vanillainstincts.compat.Minecraft112Compat.setAggressive(mob, false);
        }
        return true;
    }

    /**
     * Barrière finale : même si l'IA vanilla sélectionne une cible entre deux
     * ticks, le zoglin de mission ne peut infliger aucun dégât non autorisé.
     */
    public static boolean shouldBlockAttack(
            Entity victim, Entity attacker) {
        if (!(attacker instanceof EntityPigZombie)
                || victim == null
                || !NetherReinforcementState.hasPersistentMission(((EntityPigZombie) (attacker)))) {
            return false;
        } EntityPigZombie zoglin = (EntityPigZombie) (attacker);
        NetherReinforcementState state =
                NetherReinforcementState.load(zoglin);
        long gameTime = zoglin.world.getTotalWorldTime();
        if (!isMissionZoglin(zoglin, state)) {
            return false;
        }
        return !canAttackTarget(state, victim, gameTime);
    }
}
