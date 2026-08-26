package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.strategy.AgentStrategy;

/**
 * Observa o resultado do passo, pergunta a estrategia o que fazer e roteia.
 *
 * <p>O {@code switch} abaixo e sobre a decisao ({@code StepDecision}), nunca sobre o
 * estado. Os estados continuam polimorficos: cada ramo devolve uma instancia diferente.</p>
 *
 * <p>E aqui que as tres estrategias se mostram diferentes diante do mesmo resultado: com
 * uma falha, o ReAct devolve REPLAN, o PlanThenExecute devolve FAIL e o HumanInTheLoop
 * devolve ESCALATE.</p>
 */
public class ObservingState implements AgentState {

    private final AgentStateMachine machine;

    public ObservingState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "OBSERVING";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        ExecutionResult ultimo = ctx.lastResult().orElse(null);
        if (ultimo == null) {
            // Chegar aqui sem resultado nenhum e defeito de fluxo, nao falha do usuario.
            return new ErrorState(machine);
        }

        AgentStrategy strategy = machine.selector().active();

        return switch (strategy.decideNext(ctx, ultimo)) {
            case CONTINUE -> {
                ctx.advance();
                yield ctx.hasMoreSteps() ? new ExecutingState(machine) : new CompletedState(machine);
            }
            case REPLAN -> {
                ctx.recordReplan();
                yield new PlanningState(machine);
            }
            case ESCALATE -> {
                machine.selector().escalate(ctx, strategy.escalationSignal(ctx)
                        .orElseGet(() -> EscalationSignal.of(
                                EscalationSignal.Reason.AMBIGUOUS_INPUT,
                                "Preciso da sua ajuda para seguir. O que voce quer que eu faca agora?")));
                yield new WaitingApprovalState(machine);
            }
            case FAIL -> new ErrorState(machine);
        };
    }
}
