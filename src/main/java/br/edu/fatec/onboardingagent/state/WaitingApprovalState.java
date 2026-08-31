package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;

/**
 * Conversa com o humano. Destino das duas portas de escalonamento: OBSERVING (a
 * estrategia se declarou incapaz) e ERROR (tentativas esgotadas).
 *
 * <p>Enquanto ha escalonamento em aberto, pergunta ao humano atraves da
 * HumanInTheLoopStrategy — que e a estrategia ativa neste momento e a dona da conversa.
 * Com a resposta em maos, chama {@code deescalate}, devolvendo o comando ao ReAct, que
 * replaneja com o esclarecimento no contexto.</p>
 *
 * <p>Sem resposta, devolve {@code this}: o sinal de "sem progresso" que faz a maquina
 * pausar em vez de girar em falso.</p>
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
        if (ctx.isEscalated()) {
            String pergunta = ctx.pendingEscalation()
                    .map(EscalationSignal::questionToHuman)
                    .orElse("Como voce quer seguir?");

            // FASE 4: console. FASE 6: o ApprovalPanel substitui askHuman e mais nada muda.
            String resposta = machine.selector().humanInTheLoop().askHuman(pergunta);

            if (resposta == null || resposta.isBlank()) {
                return this;
            }
            ctx.submitHumanResponse(resposta);
            registrarAprovacao(ctx, resposta);
        }

        // A resposta permanece no contexto de proposito: e ela que enriquece o
        // proximo prompt de planejamento do ReAct.
        machine.selector().deescalate(ctx);
        return new PlanningState(machine);
    }

    /**
     * Autoriza a ferramenta quando o escalonamento foi por passo destrutivo e o humano
     * disse que sim.
     *
     * <p>Se ele recusar, nada e autorizado: a recusa fica no contexto e entra no proximo
     * prompt, para o ReAct planejar outro caminho em vez de insistir no mesmo passo.</p>
     */
    private void registrarAprovacao(AgentContext ctx, String resposta) {
        ctx.pendingEscalation()
                .filter(sinal -> sinal.reason() == EscalationSignal.Reason.DESTRUCTIVE_STEP)
                .map(EscalationSignal::blockedStep)
                .filter(passo -> passo != null && ehAfirmativa(resposta))
                .ifPresent(passo -> ctx.approveCommand(passo.commandName()));
    }

    private static boolean ehAfirmativa(String resposta) {
        String texto = resposta.trim().toLowerCase(java.util.Locale.ROOT);
        return texto.equals("s") || texto.equals("sim") || texto.equals("ok")
                || texto.equals("y") || texto.equals("yes")
                || texto.startsWith("sim,") || texto.startsWith("pode");
    }
}
