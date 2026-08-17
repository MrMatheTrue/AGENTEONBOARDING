package br.edu.fatec.onboardingagent.state;

import br.edu.fatec.onboardingagent.domain.AgentContext;

/**
 * STATE: cada fase do agente e uma classe, e cada classe decide qual e a proxima.
 *
 * <p>Nao existe {@code enum} de estado nem {@code switch} de estado neste projeto: a
 * transicao e polimorfica. Quem sabe para onde ir depois de EXECUTING e o proprio
 * ExecutingState, e ele devolve a instancia do proximo estado.</p>
 */
public interface AgentState {

    /** Nome do estado como aparece na trilha e no diagrama (INIT, PLANNING, ...). */
    String name();

    /**
     * Executa o trabalho desta fase e devolve o proximo estado.
     *
     * <p>Devolver {@code this} significa "sem progresso": e assim que WAITING_APPROVAL
     * sinaliza que esta bloqueado esperando o humano, e que COMPLETED sinaliza que
     * chegou ao fim.</p>
     */
    AgentState handle(AgentContext ctx);
}
