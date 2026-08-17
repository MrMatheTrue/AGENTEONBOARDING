package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;

/**
 * Tratamento de falha, em tres degraus mensuraveis — nunca por "achismo".
 *
 * <ol>
 *   <li>ainda ha tentativa ({@code retryCount < maxRetries}) → repete o mesmo passo</li>
 *   <li>tentativas esgotadas, mas ainda da para replanejar → volta a PLANNING</li>
 *   <li>esgotou tudo → escala para o humano (segunda porta do WAITING_APPROVAL)</li>
 * </ol>
 */
public class ErrorState implements AgentState {

    private final AgentStateMachine machine;

    public ErrorState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "ERROR";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        String erro = ctx.lastResult()
                .map(r -> r.errorMessage() == null ? "falha sem mensagem" : r.errorMessage())
                .orElse("falha desconhecida");

        if (ctx.retryCount() < machine.maxRetries()) {
            ctx.recordRetry();
            return new ExecutingState(machine);
        }

        if (ctx.replanCount() < machine.maxReplans()) {
            ctx.recordReplan();
            return new PlanningState(machine);
        }

        ctx.recordEscalation(new EscalationSignal(
                EscalationSignal.Reason.COMMAND_FAILED,
                "Tentei %d vezes e replanejei %d vezes, e continua falhando: %s. Como voce quer seguir?"
                        .formatted(ctx.retryCount(), ctx.replanCount(), erro),
                ctx.currentStep().orElse(null)));
        return new WaitingApprovalState(machine);
    }
}
