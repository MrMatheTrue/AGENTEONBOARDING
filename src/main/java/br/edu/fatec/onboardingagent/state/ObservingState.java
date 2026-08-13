package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.StepDecision;

/**
 * Observa o resultado do passo e roteia para a proxima fase.
 *
 * <p>O {@code switch} abaixo e sobre {@link StepDecision} — a decisao —, nunca sobre o
 * estado. Os estados continuam polimorficos: cada ramo devolve uma instancia diferente.</p>
 *
 * <p><strong>FASE 3:</strong> a decisao e calculada aqui, a partir do resultado.
 * <strong>FASE 4:</strong> ela passa a vir de {@code strategy.decideNext(ctx, last)}, e as
 * tres estrategias vao responder diferente ao mesmo resultado — o roteamento nao muda.</p>
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

        return switch (decidir(ctx, ultimo)) {
            case CONTINUE -> {
                ctx.advance();
                yield ctx.hasMoreSteps() ? new ExecutingState(machine) : new CompletedState(machine);
            }
            case REPLAN -> {
                ctx.recordReplan();
                yield new PlanningState(machine);
            }
            case ESCALATE -> {
                ctx.recordEscalation(EscalationSignal.of(
                        EscalationSignal.Reason.AMBIGUOUS_INPUT,
                        "Preciso da sua ajuda para seguir. O que voce quer que eu faca agora?"));
                yield new WaitingApprovalState(machine);
            }
            case FAIL -> new ErrorState(machine);
        };
    }

    /**
     * Regra da FASE 3: passo bem-sucedido segue, passo falho vai para tratamento de erro.
     *
     * <p>Na FASE 4 esta linha some e vira {@code strategy.decideNext(ctx, ultimo)}.</p>
     */
    private StepDecision decidir(AgentContext ctx, ExecutionResult ultimo) {
        return ultimo.success() ? StepDecision.CONTINUE : StepDecision.FAIL;
    }
}
