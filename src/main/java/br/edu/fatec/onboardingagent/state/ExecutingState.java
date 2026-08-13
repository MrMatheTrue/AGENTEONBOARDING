package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.PlanStep;

import java.util.Optional;

/**
 * Executa o passo corrente do plano atraves do CommandInvoker.
 *
 * <p>Nao ha try/catch aqui: o Invoker garante que o resultado sempre chega como
 * {@link ExecutionResult}. Este estado so registra o resultado e passa a bola para
 * OBSERVING, que decide o que fazer com ele.</p>
 */
public class ExecutingState implements AgentState {

    private final AgentStateMachine machine;

    public ExecutingState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "EXECUTING";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        Optional<PlanStep> passo = ctx.currentStep();
        if (passo.isEmpty()) {
            // Plano acabou (ou nem tinha passo): nada a executar.
            return new CompletedState(machine);
        }

        PlanStep step = passo.get();
        step.markRunning();

        ExecutionResult resultado = machine.invoker().execute(ctx, step);
        ctx.recordResult(resultado);

        if (resultado.success()) {
            step.markDone();
        } else {
            step.markFailed();
        }

        return new ObservingState(machine);
    }
}
