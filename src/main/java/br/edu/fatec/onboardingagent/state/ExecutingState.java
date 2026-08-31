package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.PlanStep;

import java.util.Optional;

/**
 * Executa o passo corrente do plano atraves do CommandInvoker.
 *
 * <p>Antes de executar, checa o gatilho #5: passo destrutivo nao roda sem autorizacao
 * explicita do humano. A autorizacao fica registrada por ferramenta no contexto, entao
 * so se pergunta uma vez por sessao.</p>
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

        // Gatilho #5 DESTRUCTIVE_STEP — operacao irreversivel exige o humano no circuito.
        if (machine.selector().active().requiresApproval(step) && !ctx.isApproved(step.commandName())) {
            machine.selector().escalate(ctx, new EscalationSignal(
                    EscalationSignal.Reason.DESTRUCTIVE_STEP,
                    "O passo '%s' usa uma operacao irreversivel (%s). Posso executar? (responda sim ou nao)"
                            .formatted(step.description(), step.commandName()),
                    step));
            return new WaitingApprovalState(machine);
        }

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
