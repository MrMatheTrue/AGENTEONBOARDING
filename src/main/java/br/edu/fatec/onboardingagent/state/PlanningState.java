package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.strategy.AgentStrategy;

/**
 * Pede o plano a estrategia ativa e decide se da para executar.
 *
 * <p>Plano invalido nunca vira execucao: vira escalonamento. E a regra que impede o agente
 * de "chutar" um plano — o gatilho #1 (NO_VALID_PLAN) e o #6 (AMBIGUOUS_INPUT) nascem aqui.</p>
 *
 * <p>Quem troca a estrategia e o {@code StrategySelector}, chamado por este estado. A
 * estrategia so declara a incapacidade; ela nao conhece a substituta.</p>
 */
public class PlanningState implements AgentState {

    private final AgentStateMachine machine;

    public PlanningState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "PLANNING";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        AgentStrategy strategy = machine.selector().active();
        Plan plan = strategy.buildPlan(ctx);
        ctx.installPlan(plan);

        if (plan.isValid(machine.registry().names())) {
            return new ExecutingState(machine);
        }

        // A estrategia diz por que nao conseguiu; se nao disser, assumimos plano invalido.
        EscalationSignal sinal = strategy.escalationSignal(ctx)
                .orElseGet(() -> EscalationSignal.of(
                        EscalationSignal.Reason.NO_VALID_PLAN,
                        "Nao consegui montar um plano para '%s'. Pode detalhar o que voce quer fazer?"
                                .formatted(ctx.goal().rawText())));

        machine.selector().escalate(ctx, sinal);
        return new WaitingApprovalState(machine);
    }
}
