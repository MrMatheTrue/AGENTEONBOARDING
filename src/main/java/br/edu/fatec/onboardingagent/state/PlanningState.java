package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.Plan;

/**
 * Obtem o plano e decide se da para executar.
 *
 * <p><strong>FASE 3:</strong> o plano ja vem instalado no contexto (mockado pelo teste) e
 * aqui so se valida. <strong>FASE 4:</strong> este estado passa a chamar
 * {@code strategy.buildPlan(ctx)} antes de validar — o resto da logica permanece.</p>
 *
 * <p>Plano invalido nunca vira execucao: vira o gatilho NO_VALID_PLAN e leva a
 * WAITING_APPROVAL. E a regra que impede o agente de "chutar" um plano.</p>
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
        Plan plan = ctx.plan();

        // Validacao completa: alem de ter passos, todo comando citado precisa existir.
        if (plan.isValid(machine.registry().names())) {
            return new ExecutingState(machine);
        }

        // FASE 4: quem formula o sinal passa a ser a estrategia (escalationSignal),
        // e quem troca a estrategia ativa e o StrategySelector. A transicao nao muda.
        ctx.recordEscalation(new EscalationSignal(
                EscalationSignal.Reason.NO_VALID_PLAN,
                explicar(ctx, plan),
                null));
        return new WaitingApprovalState(machine);
    }

    private String explicar(AgentContext ctx, Plan plan) {
        if (plan.size() == 0) {
            return "Nao consegui montar um plano para '%s'. Pode detalhar o que voce quer fazer?"
                    .formatted(ctx.goal().rawText());
        }
        var desconhecidos = plan.unknownCommands(machine.registry().names());
        if (!desconhecidos.isEmpty()) {
            return "O plano usa ferramentas que eu nao tenho (%s). Pode reformular o pedido?"
                    .formatted(String.join(", ", desconhecidos));
        }
        return "O plano que montei nao esta utilizavel. Pode detalhar o que voce quer fazer?";
    }
}
