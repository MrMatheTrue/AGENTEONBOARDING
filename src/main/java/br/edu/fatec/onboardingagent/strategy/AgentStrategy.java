package br.edu.fatec.onboardingagent.strategy;

import br.edu.fatec.onboardingagent.domain.AgentContext;
import br.edu.fatec.onboardingagent.domain.ExecutionResult;
import br.edu.fatec.onboardingagent.domain.EscalationSignal;
import br.edu.fatec.onboardingagent.domain.Plan;
import br.edu.fatec.onboardingagent.domain.PlanStep;
import br.edu.fatec.onboardingagent.domain.StepDecision;

import java.util.Optional;

/**
 * STRATEGY: as tres formas de conduzir o agente (ReAct, PlanThenExecute, HumanInTheLoop).
 *
 * <p>Uma estrategia <strong>nunca</strong> instancia nem chama outra. Quando percebe que
 * nao consegue seguir, ela apenas devolve {@link StepDecision#ESCALATE} e descreve o
 * problema em {@link #escalationSignal(AgentContext)}. Quem troca a estrategia ativa e o
 * {@link StrategySelector} — se uma chamasse a outra, isto viraria Chain of
 * Responsibility e descaracterizaria o padrao.</p>
 */
public interface AgentStrategy {

    /** Nome exibido na trilha e nos eventos (ReAct, PlanThenExecute, HumanInTheLoop). */
    String name();

    /** Monta o plano para o objetivo do contexto. Plano invalido e resposta legitima. */
    Plan buildPlan(AgentContext ctx);

    /** Decide o que fazer depois de observar o resultado do ultimo passo. */
    StepDecision decideNext(AgentContext ctx, ExecutionResult last);

    /** Indica se o passo exige aprovacao humana antes de rodar. */
    boolean requiresApproval(PlanStep step);

    /**
     * Incapacidade auto-declarada: presente quando a estrategia nao tem como seguir
     * sozinha, com o motivo mensuravel e a pergunta a fazer ao humano.
     */
    Optional<EscalationSignal> escalationSignal(AgentContext ctx);
}
