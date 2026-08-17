package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;

/**
 * Bloqueia esperando o humano. Destino das duas portas de escalonamento: OBSERVING
 * (a estrategia se declarou incapaz) e ERROR (tentativas esgotadas).
 *
 * <p>Sem resposta, devolve {@code this} — e o sinal de "sem progresso" que faz a maquina
 * pausar em vez de girar em falso. Com resposta, consome-a e volta a planejar.</p>
 *
 * <p>Quem preenche a resposta muda por fase: console ({@code Scanner}) na FASE 4,
 * ApprovalPanel na FASE 6. Este estado nao sabe qual dos dois foi — so le o contexto.</p>
 */
public class WaitingApprovalState implements AgentState {

    private final AgentStateMachine machine;

    public WaitingApprovalState(AgentStateMachine machine) {
        this.machine = machine;
    }

    @Override
    public String name() {
        return "WAITING_APPROVAL";
    }

    @Override
    public AgentState handle(AgentContext ctx) {
        if (ctx.humanResponse().isEmpty()) {
            return this;
        }

        // FASE 4: aqui tambem entra strategySelector.deescalate(ctx), devolvendo o
        // comando ao ReAct com o contexto enriquecido pela resposta.
        ctx.clearEscalation();
        ctx.clearHumanResponse();
        return new PlanningState(machine);
    }
}
