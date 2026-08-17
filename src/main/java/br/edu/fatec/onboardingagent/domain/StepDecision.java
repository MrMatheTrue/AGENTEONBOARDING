package br.edu.fatec.onboardingagent.domain;

/**
 * Decisao que a estrategia toma depois de observar o resultado de um passo.
 *
 * <p>E o unico canal por onde uma estrategia comunica incapacidade: ela devolve
 * {@link #ESCALATE} e para por ai. Quem troca a estrategia ativa e o StrategySelector —
 * uma estrategia jamais instancia ou chama outra.</p>
 */
public enum StepDecision {

    /** Passo deu certo; seguir para o proximo. */
    CONTINUE,

    /** O plano nao serve mais; voltar ao planejamento. */
    REPLAN,

    /** A estrategia se declarou incapaz; o humano precisa entrar. */
    ESCALATE,

    /** Falha definitiva; encerrar em erro. */
    FAIL
}
