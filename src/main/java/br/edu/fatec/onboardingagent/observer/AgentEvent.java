package br.edu.fatec.onboardingagent.observer;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.Goal;
import br.edu.fatec.onboardingagent.domain.Plan;

import java.util.Map;

/**
 * Tudo que acontece no agente e digno de nota, como tipos fechados.
 *
 * <p>Interface selada: o compilador garante que nenhum evento novo apareca sem que os
 * observadores sejam revisitados. Cada evento carrega o que o observador precisa para
 * reagir sem ter que perguntar nada a ninguem.</p>
 */
public sealed interface AgentEvent {

    /** A estrategia produziu um plano utilizavel. */
    record PlanCreated(String strategy, Plan plan) implements AgentEvent {
    }

    /** A maquina mudou de estado. {@code from} e nulo na primeira transicao. */
    record StateChanged(String from, String to) implements AgentEvent {
    }

    record CommandStarted(String commandName, Map<String, Object> args) implements AgentEvent {
    }

    /**
     * Comando terminou (com sucesso ou nao).
     *
     * <p>Carrega o contexto porque o ProgressObserver precisa dele para marcar a trilha de
     * aprendizado do desenvolvedor — que vive no AgentContext, nao no evento.</p>
     */
    record CommandCompleted(String commandName, long durationMs, ExecutionResult result,
                            AgentContext context) implements AgentEvent {
    }

    record CommandFailed(String commandName, String errorMessage, int retryCount) implements AgentEvent {
    }

    /** Um passo do raciocinio da estrategia — e o que forma a trilha pedida pelo professor. */
    record ReasoningStep(String strategy, String thought) implements AgentEvent {
    }

    /** Troca de estrategia, com o motivo mensuravel que a disparou. */
    record StrategyEscalated(String from, String to, EscalationSignal signal) implements AgentEvent {
    }

    record StrategyDeescalated(String from, String to) implements AgentEvent {
    }

    /** O agente parou e esta esperando o humano. */
    record UserApprovalRequired(EscalationSignal signal) implements AgentEvent {
    }

    record GoalCompleted(Goal goal, AgentContext context) implements AgentEvent {
    }
}
