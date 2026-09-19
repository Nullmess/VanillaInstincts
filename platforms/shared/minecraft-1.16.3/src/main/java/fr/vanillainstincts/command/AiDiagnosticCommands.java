package fr.vanillainstincts.command;

import net.minecraft.util.registry.Registry;
import com.mojang.brigadier.CommandDispatcher;
import fr.vanillainstincts.ai.AnimalWelfareController;
import fr.vanillainstincts.ai.MobPerceptionMemory;
import fr.vanillainstincts.ai.MobRuntimeState;
import fr.vanillainstincts.ai.MobStateStore;
import fr.vanillainstincts.ai.TargetAcquisitionController;
import fr.vanillainstincts.ai.VanillaInstinctsScheduler;
import fr.vanillainstincts.core.performance.PerformanceSummary;
import fr.vanillainstincts.core.performance.SchedulerTelemetry;
import fr.vanillainstincts.diagnostic.AiPerformanceTracker;
import fr.vanillainstincts.possession.MobPossessionManager;
import java.util.Comparator;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.pathfinding.Path;

/** Operator-facing AI diagnostics. Read-only by design. */
public final class AiDiagnosticCommands {
    private static final double DEFAULT_RADIUS = 10.0D;

    private AiDiagnosticCommands() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("vanillainstincts")
                .then(Commands.literal("control")
                        .requires(source -> source.getEntity() instanceof net.minecraft.entity.player.ServerPlayerEntity)
                        .executes(context -> MobPossessionManager
                                .toggleFromSpectatorCamera(
                                        context.getSource().getPlayerOrException())))
                .then(Commands.literal("ai")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspect")
                                .executes(context -> inspect(context.getSource(),
                                        nearestMob(context.getSource())))
                                .then(Commands.argument("entity",
                                                EntityArgument.entity())
                                        .executes(context -> inspect(
                                                context.getSource(),
                                                asMob(EntityArgument.getEntity(
                                                        context, "entity"))))))
                        .then(Commands.literal("memory")
                                .executes(context -> memory(context.getSource(),
                                        nearestMob(context.getSource())))
                                .then(Commands.argument("entity",
                                                EntityArgument.entity())
                                        .executes(context -> memory(
                                                context.getSource(),
                                                asMob(EntityArgument.getEntity(
                                                        context, "entity"))))))
                        .then(Commands.literal("target")
                                .executes(context -> target(context.getSource(),
                                        nearestMob(context.getSource())))
                                .then(Commands.argument("entity",
                                                EntityArgument.entity())
                                        .executes(context -> target(
                                                context.getSource(),
                                                asMob(EntityArgument.getEntity(
                                                        context, "entity"))))))
                        .then(Commands.literal("path")
                                .executes(context -> path(context.getSource(),
                                        nearestMob(context.getSource())))
                                .then(Commands.argument("entity",
                                                EntityArgument.entity())
                                        .executes(context -> path(
                                                context.getSource(),
                                                asMob(EntityArgument.getEntity(
                                                        context, "entity"))))))
                        .then(Commands.literal("scheduler")
                                .executes(context -> scheduler(
                                        context.getSource())))));
    }

    private static int inspect(CommandSource source, MobEntity mob) {
        if (!requireMob(source, mob)) return 0;
        MobRuntimeState state = MobStateStore.stateFor(mob);
        String type = Registry.ENTITY_TYPE.getKey(mob.getType())
                .toString();
        String target = mob.getTarget() == null ? "none"
                : mob.getTarget().getName().getString() + " / "
                + mob.getTarget().getUUID();
        send(source, "AI inspect: " + type + " / " + mob.getUUID());
        send(source, "state=" + state.currentState()
                + ", intent=" + state.currentIntent()
                + ", owner=" + state.owner()
                + ", priority=" + state.priority());
        send(source, "target=" + target);
        if (mob instanceof AnimalEntity) { AnimalEntity animal = (AnimalEntity) (mob); 
            send(source, "welfare=" + AnimalWelfareController.state(animal)
                    + ", score=" + AnimalWelfareController.score(animal));
        }
        return 1;
    }

    private static int memory(CommandSource source, MobEntity mob) {
        if (!requireMob(source, mob)) return 0;
        long now = source.getLevel().getGameTime();
        String position = MobPerceptionMemory.bestKnownPosition(mob, now)
                .map(Object::toString).orElse("none");
        String stimulus = MobPerceptionMemory.lastStimulusType(mob, now)
                .map(Enum::name).orElse("none");
        String seen = MobPerceptionMemory.lastSeenTarget(mob, now)
                .map(Object::toString).orElse("none");
        send(source, "AI memory: known=" + position
                + ", stimulus=" + stimulus);
        send(source, "last_seen_uuid=" + seen
                + ", interest_confidence="
                + String.format(java.util.Locale.ROOT, "%.2f",
                MobPerceptionMemory.interestConfidence(mob, now)));
        return 1;
    }

    private static int target(CommandSource source, MobEntity mob) {
        if (!requireMob(source, mob)) return 0;
        String current = mob.getTarget() == null ? "none"
                : mob.getTarget().getName().getString() + " / "
                + mob.getTarget().getUUID();
        TargetAcquisitionController.AcquisitionResult acquisition = TargetAcquisitionController.findBestTarget(mob,
                source.getLevel());
        String candidate = acquisition.target()
                .map(entity -> entity.getName().getString() + " / "
                        + entity.getUUID()).orElse("none");
        String relation = acquisition.relation()
                .map(value -> value.relation() + " @ " + value.source())
                .orElse("none");
        send(source, "AI target: current=" + current);
        send(source, "candidate=" + candidate + ", relation=" + relation
                + ", inspected=" + acquisition.inspectedCandidates());
        return 1;
    }

    private static int path(CommandSource source, MobEntity mob) {
        if (!requireMob(source, mob)) return 0;
        Path current = mob.getNavigation().getPath();
        if (current == null) {
            send(source, "AI path: none, navigation_done="
                    + mob.getNavigation().isDone());
            return 1;
        }
        send(source, "AI path: nodes=" + current.getNodeCount()
                + ", next=" + current.getNextNodeIndex()
                + ", done=" + current.isDone());
        send(source, "target=" + current.getTarget());
        return 1;
    }

    private static int scheduler(CommandSource source) {
        SchedulerTelemetry telemetry = VanillaInstinctsScheduler.telemetry(
                source.getLevel());
        send(source, "AI scheduler: tier=" + telemetry.tier()
                + ", cost=" + telemetry.usedCost() + "/"
                + telemetry.effectiveCostBudget());
        send(source, "claims accepted=" + telemetry.acceptedClaims()
                + ", rejected=" + telemetry.rejectedClaims()
                + ", rejected_cost=" + telemetry.rejectedCost());
        send(source, "ai_nanos=" + telemetry.spentAiNanos()
                + ", budget_nanos=" + telemetry.effectiveTimeBudgetNanos());
        PerformanceSummary performance = AiPerformanceTracker.summary(
                source.getLevel());
        send(source, "rolling_samples=" + performance.samples() + "/"
                + performance.capacity() + ", ai_ms avg/p95/p99/max="
                + millis(performance.averageAiNanos()) + "/"
                + millis(performance.p95AiNanos()) + "/"
                + millis(performance.p99AiNanos()) + "/"
                + millis(performance.maxAiNanos()));
        send(source, "tick_ms avg/p95/p99/max="
                + millis(performance.averageTickNanos()) + "/"
                + millis(performance.p95TickNanos()) + "/"
                + millis(performance.p99TickNanos()) + "/"
                + millis(performance.maxTickNanos()));
        return 1;
    }

    private static MobEntity nearestMob(CommandSource source) {
        Entity self = source.getEntity();
        if (self instanceof MobEntity) { MobEntity mob = (MobEntity) (self); return mob; }
        if (self == null) return null;
        ServerWorld level = source.getLevel();
        return level.getEntitiesOfClass(MobEntity.class,
                        self.getBoundingBox().inflate(DEFAULT_RADIUS),
                        MobEntity::isAlive)
                .stream()
                .min(Comparator.comparingDouble(self::distanceToSqr))
                .orElse(null);
    }

    private static MobEntity asMob(Entity entity) {
        return entity instanceof MobEntity ? ((MobEntity) (entity)) : null;
    }

    private static boolean requireMob(CommandSource source, MobEntity mob) {
        if (mob != null) return true;
        source.sendFailure(new net.minecraft.util.text.StringTextComponent(
                "Aucun mob valide. Approchez-vous d'un mob ou fournissez un sélecteur."));
        return false;
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f",
                nanos / 1_000_000.0D);
    }

    private static void send(CommandSource source, String message) {
        source.sendSuccess(new net.minecraft.util.text.StringTextComponent(message), false);
    }
}
